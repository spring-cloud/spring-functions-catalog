/*
 * Copyright 2019-2025 the original author or authors.
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

package org.springframework.cloud.fn.supplier.jdbc;

import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.cloud.fn.splitter.SplitterFunctionConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * JDBC supplier auto-configuration.
 *
 * @author Soby Chacko
 * @author Artem Bilan
 */
@AutoConfiguration(after = { DataSourceAutoConfiguration.class, SplitterFunctionConfiguration.class })
@EnableConfigurationProperties(JdbcSupplierProperties.class)
public class JdbcSupplierConfiguration {

	@Bean
	public JdbcPollingChannelAdapter jdbcMessageSource(JdbcSupplierProperties properties, DataSource dataSource,
			@Nullable ComponentCustomizer<JdbcPollingChannelAdapter> jdbcPollingChannelAdapterCustomizer) {

		JdbcPollingChannelAdapter jdbcPollingChannelAdapter = new JdbcPollingChannelAdapter(dataSource,
				properties.getQuery());
		jdbcPollingChannelAdapter.setMaxRows(properties.getMaxRows());
		jdbcPollingChannelAdapter.setUpdateSql(properties.getUpdate());
		if (jdbcPollingChannelAdapterCustomizer != null) {
			jdbcPollingChannelAdapterCustomizer.customize(jdbcPollingChannelAdapter);
		}
		return jdbcPollingChannelAdapter;
	}

	@Bean(name = "jdbcSupplier")
	@ConditionalOnProperty(prefix = "jdbc.supplier", name = "split", matchIfMissing = true)
	public Supplier<Flux<Message<?>>> splittedSupplier(JdbcPollingChannelAdapter jdbcMessageSource,
			Function<Flux<Message<Object>>, Flux<Message<?>>> splitterFunction) {

		return () -> IntegrationReactiveUtils.messageSourceToFlux(jdbcMessageSource).transform(splitterFunction);
	}

	@Bean
	@ConditionalOnProperty(prefix = "jdbc.supplier", name = "split", havingValue = "false")
	public Supplier<Message<?>> jdbcSupplier(MessageSource<Object> jdbcMessageSource) {
		return jdbcMessageSource::receive;
	}

}
