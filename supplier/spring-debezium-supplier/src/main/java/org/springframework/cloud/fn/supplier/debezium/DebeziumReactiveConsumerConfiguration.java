/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.cloud.fn.supplier.debezium;

import java.util.function.Supplier;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.DebeziumEngine.Builder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.cloud.fn.common.debezium.DebeziumEngineBuilderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.debezium.dsl.Debezium;
import org.springframework.integration.debezium.dsl.DebeziumMessageProducerSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * The Debezium supplier auto-configuration.
 *
 * @author Christian Tzolov
 * @author Artem Bilan
 */
@AutoConfiguration(after = DebeziumEngineBuilderAutoConfiguration.class)
@EnableConfigurationProperties(DebeziumSupplierProperties.class)
@ConditionalOnBean(DebeziumEngine.Builder.class)
public class DebeziumReactiveConsumerConfiguration {

	@Bean
	public Supplier<Flux<Message<?>>> debeziumSupplier(Publisher<Message<?>> debeziumPublisher) {
		return () -> Flux.from(debeziumPublisher);
	}

	@Bean
	public Publisher<Message<byte[]>> debeziumPublisher(Builder<ChangeEvent<byte[], byte[]>> debeziumEngineBuilder,
			DebeziumSupplierProperties supplierProperties,
			@Nullable ComponentCustomizer<DebeziumMessageProducerSpec> debeziumMessageProducerSpecComponentCustomizer) {

		DebeziumMessageProducerSpec debeziumMessageProducerSpec = Debezium.inboundChannelAdapter(debeziumEngineBuilder)
			.enableEmptyPayload(supplierProperties.isEnableEmptyPayload())
			.headerNames(supplierProperties.getHeaderNamesToMap())
			// TODO until Spring Integration 6.3.0-M2
			.autoStartup(false);

		if (debeziumMessageProducerSpecComponentCustomizer != null) {
			debeziumMessageProducerSpecComponentCustomizer.customize(debeziumMessageProducerSpec);
		}

		return IntegrationFlow.from(debeziumMessageProducerSpec).toReactivePublisher(true);
	}

}
