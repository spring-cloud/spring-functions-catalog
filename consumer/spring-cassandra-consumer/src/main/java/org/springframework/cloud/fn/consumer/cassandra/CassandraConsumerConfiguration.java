/*
 * Copyright 2015-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.fn.consumer.cassandra;

import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import reactor.core.publisher.Mono;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.StdDateFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.cassandra.autoconfigure.DataCassandraReactiveAutoConfiguration;
import org.springframework.cloud.fn.consumer.cassandra.query.ColumnNameExtractor;
import org.springframework.cloud.fn.consumer.cassandra.query.InsertQueryColumnNameExtractor;
import org.springframework.cloud.fn.consumer.cassandra.query.UpdateQueryColumnNameExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.cassandra.core.ReactiveCassandraOperations;
import org.springframework.data.cassandra.core.UpdateOptions;
import org.springframework.data.cassandra.core.WriteResult;
import org.springframework.data.cassandra.core.cql.WriteOptions;
import org.springframework.integration.JavaUtils;
import org.springframework.integration.cassandra.outbound.CassandraMessageHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.gateway.AnnotationGatewayProxyFactoryBean;
import org.springframework.integration.support.json.JacksonJsonObjectMapper;
import org.springframework.integration.transformer.AbstractPayloadTransformer;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

/**
 * Apache Cassandra consumer auto-configuration.
 *
 * @author Artem Bilan
 * @author Thomas Risberg
 * @author Ashu Gairola
 * @author Akos Ratku
 * @author Omer Celik
 */
@AutoConfiguration(after = DataCassandraReactiveAutoConfiguration.class)
@EnableConfigurationProperties(CassandraConsumerProperties.class)
public class CassandraConsumerConfiguration {

	@Autowired
	private CassandraConsumerProperties cassandraSinkProperties;

	@Bean
	Consumer<Object> cassandraConsumer(CassandraConsumerFunction cassandraConsumerFunction) {
		return (payload) -> cassandraConsumerFunction.apply(payload).block();
	}

	@Bean
	public IntegrationFlow cassandraConsumerFlow(
			@Qualifier("cassandraMessageHandler") MessageHandler cassandraMessageHandler, JsonMapper jsonMapper) {

		return (flow) -> {
			String ingestQuery = this.cassandraSinkProperties.getIngestQuery();
			if (StringUtils.hasText(ingestQuery)) {
				flow.transform(new PayloadToMatrixTransformer(jsonMapper, ingestQuery,
						(CassandraMessageHandler.Type.UPDATE == this.cassandraSinkProperties.getQueryType())
								? new UpdateQueryColumnNameExtractor() : new InsertQueryColumnNameExtractor()));
			}
			flow.handle(cassandraMessageHandler);
		};
	}

	@Bean
	public MessageHandler cassandraMessageHandler(ReactiveCassandraOperations cassandraOperations) {
		CassandraMessageHandler.Type queryType = Optional.ofNullable(this.cassandraSinkProperties.getQueryType())
			.orElse(CassandraMessageHandler.Type.INSERT);

		CassandraMessageHandler cassandraMessageHandler = new CassandraMessageHandler(cassandraOperations, queryType);
		cassandraMessageHandler.setProducesReply(true);
		int ttl = this.cassandraSinkProperties.getTtl();
		ConsistencyLevel consistencyLevel = this.cassandraSinkProperties.getConsistencyLevel();
		if (consistencyLevel != null || ttl > 0) {

			WriteOptions.WriteOptionsBuilder writeOptionsBuilder = switch (queryType) {
				case INSERT -> InsertOptions.builder();
				case UPDATE -> UpdateOptions.builder();
				default -> WriteOptions.builder();
			};

			JavaUtils.INSTANCE.acceptIfNotNull(consistencyLevel, writeOptionsBuilder::consistencyLevel)
				.acceptIfCondition(ttl > 0, ttl, writeOptionsBuilder::ttl);

			cassandraMessageHandler.setWriteOptions(writeOptionsBuilder.build());
		}

		JavaUtils.INSTANCE
			.acceptIfHasText(this.cassandraSinkProperties.getIngestQuery(), cassandraMessageHandler::setIngestQuery)
			.acceptIfNotNull(this.cassandraSinkProperties.getStatementExpression(),
					cassandraMessageHandler::setStatementExpression);

		return cassandraMessageHandler;
	}

	@Bean
	AnnotationGatewayProxyFactoryBean<CassandraConsumerFunction> cassandraConsumerFunction() {
		var gatewayProxyFactoryBean = new AnnotationGatewayProxyFactoryBean<>(CassandraConsumerFunction.class);
		gatewayProxyFactoryBean.setDefaultRequestChannelName("cassandraConsumerFlow.input");
		return gatewayProxyFactoryBean;
	}

	private static boolean isUuid(String uuid) {
		if (uuid.length() == 36) {
			String[] parts = uuid.split("-");
			if (parts.length == 5) {
				return (parts[0].length() == 8) && (parts[1].length() == 4) && (parts[2].length() == 4)
						&& (parts[3].length() == 4) && (parts[4].length() == 12);
			}
		}
		return false;
	}

	private static class PayloadToMatrixTransformer extends AbstractPayloadTransformer<Object, List<List<Object>>> {

		private final JacksonJsonObjectMapper jsonObjectMapper;

		private final List<String> columns = new LinkedList<>();

		private final ISO8601StdDateFormat dateFormat = new ISO8601StdDateFormat();

		private final Lock dateLock = new ReentrantLock();

		PayloadToMatrixTransformer(JsonMapper objectMapper, String query, ColumnNameExtractor columnNameExtractor) {
			JsonMapper jsonMapper = objectMapper.rebuild()
				.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
				.build();
			this.jsonObjectMapper = new JacksonJsonObjectMapper(jsonMapper);
			this.columns.addAll(columnNameExtractor.extract(query));
		}

		@Override
		@SuppressWarnings("unchecked")
		protected List<List<Object>> transformPayload(Object payload) {
			if (payload instanceof List) {
				return (List<List<Object>>) payload;
			}
			else {
				try {
					List<Map<String, Object>> model = this.jsonObjectMapper.fromJson(payload, List.class);
					List<List<Object>> data = new ArrayList<>(model.size());
					for (Map<String, Object> entity : model) {
						List<Object> row = new ArrayList<>(this.columns.size());
						for (String column : this.columns) {
							Object value = entity.get(column);
							if (value instanceof String string) {
								if (this.dateFormat.looksLikeISO8601(string)) {
									this.dateLock.lock();
									try {
										value = new Date(this.dateFormat.parse(string).getTime()).toLocalDate();
									}
									finally {
										this.dateLock.unlock();
									}
								}
								if (isUuid(string)) {
									value = UUID.fromString(string);
								}
							}
							row.add(value);
						}
						data.add(row);
					}
					return data;
				}
				catch (Exception ex) {
					throw new IllegalArgumentException("Cannot parse json into matrix", ex);
				}
			}
		}

		@Override
		public String getComponentType() {
			return "payload-to-matrix-transformer";
		}

	}

	/*
	 * We need this to provide visibility to the protected method.
	 */
	@SuppressWarnings("serial")
	private static class ISO8601StdDateFormat extends StdDateFormat {

		@Override
		protected boolean looksLikeISO8601(String dateStr) {
			return super.looksLikeISO8601(dateStr);
		}

	}

	interface CassandraConsumerFunction extends Function<Object, Mono<? extends WriteResult>> {

	}

}
