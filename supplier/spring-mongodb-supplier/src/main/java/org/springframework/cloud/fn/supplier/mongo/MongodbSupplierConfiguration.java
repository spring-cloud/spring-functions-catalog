/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.fn.supplier.mongo;

import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.cloud.fn.splitter.SplitterFunctionConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.mongodb.inbound.MongoDbMessageSource;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for MongoDB supplier. Produces {@link MongoDbMessageSource} which
 * polls collection with the query after startup according to the polling properties.
 *
 * @author Adam Zwickey
 * @author Artem Bilan
 * @author David Turanski
 */
@AutoConfiguration(after = { MongoAutoConfiguration.class, SplitterFunctionConfiguration.class })
@EnableConfigurationProperties({ MongodbSupplierProperties.class })
public class MongodbSupplierConfiguration {

	@Bean(name = "mongodbSupplier")
	@ConditionalOnProperty(prefix = "mongodb", name = "split", matchIfMissing = true)
	public Supplier<Flux<Message<?>>> splittedSupplier(@Qualifier("mongoDbSource") MongoDbMessageSource mongoDbSource,
			Function<Flux<Message<Object>>, Flux<Message<?>>> splitterFunction) {

		return () -> IntegrationReactiveUtils.messageSourceToFlux(mongoDbSource).transform(splitterFunction);
	}

	@Bean
	@ConditionalOnProperty(prefix = "mongodb", name = "split", havingValue = "false")
	public Supplier<Message<?>> mongodbSupplier(@Qualifier("mongoDbSource") MongoDbMessageSource mongoDbSource) {
		return mongoDbSource::receive;
	}

	@Bean
	public MongoDbMessageSource mongoDbSource(MongodbSupplierProperties properties, MongoTemplate mongoTemplate,
			@Nullable ComponentCustomizer<MongoDbMessageSource> mongoDbMessageSourceCustomizer) {

		Expression queryExpression = properties.getQueryExpression();
		if (queryExpression == null) {
			queryExpression = new LiteralExpression(properties.getQuery());
		}

		MongoDbMessageSource mongoDbMessageSource = new MongoDbMessageSource(mongoTemplate, queryExpression);
		mongoDbMessageSource.setCollectionNameExpression(new LiteralExpression(properties.getCollection()));
		mongoDbMessageSource.setEntityClass(String.class);
		mongoDbMessageSource.setUpdateExpression(properties.getUpdateExpression());

		if (mongoDbMessageSourceCustomizer != null) {
			mongoDbMessageSourceCustomizer.customize(mongoDbMessageSource);
		}

		return mongoDbMessageSource;
	}

}
