/*
 * Copyright 2016-2024 the original author or authors.
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

package org.springframework.cloud.fn.supplier.zeromq;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.zeromq.inbound.ZeroMqMessageProducer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

/**
 * A supplier auto-configuration that receives data from ZeroMQ.
 *
 * @author Daniel Frey
 * @author Artem Bilan
 */
@AutoConfiguration
@EnableConfigurationProperties(ZeroMqSupplierProperties.class)
public class ZeroMqSupplierConfiguration {

	@Bean
	public ZContext zContext() {
		return new ZContext();
	}

	@Bean
	public ZeroMqMessageProducer zeroMqSupplierMessageProducer(ZeroMqSupplierProperties properties, ZContext zContext,
			@Autowired(required = false) Consumer<ZMQ.Socket> socketConfigurer) {

		ZeroMqMessageProducer zeroMqMessageProducer = new ZeroMqMessageProducer(zContext, properties.getSocketType());

		if (properties.getConnectUrl() != null) {
			zeroMqMessageProducer.setConnectUrl(properties.getConnectUrl());
		}
		else if (properties.getBindPort() > 0) {
			zeroMqMessageProducer.setBindPort(properties.getBindPort());
		}
		zeroMqMessageProducer.setConsumeDelay(properties.getConsumeDelay());
		if (SocketType.SUB.equals(properties.getSocketType())) {
			zeroMqMessageProducer.setTopics(properties.getTopics());
		}
		zeroMqMessageProducer.setMessageMapper(GenericMessage::new);
		if (socketConfigurer != null) {
			zeroMqMessageProducer.setSocketConfigurer(socketConfigurer);
		}
		return zeroMqMessageProducer;
	}

	@Bean
	Publisher<Message<Object>> zeroMqSupplierFlow(ZeroMqMessageProducer zeroMqSupplierMessageProducer) {
		return IntegrationFlow.from(zeroMqSupplierMessageProducer).toReactivePublisher(true);
	}

	@Bean
	public Supplier<Flux<Message<?>>> zeromqSupplier(Publisher<Message<Object>> zeroMqSupplierFlow) {
		return () -> Flux.from(zeroMqSupplierFlow);
	}

}
