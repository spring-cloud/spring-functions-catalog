/*
 * Copyright 2016-present the original author or authors.
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

package org.springframework.cloud.fn.consumer.zeromq;

import java.util.function.Consumer;
import java.util.function.Function;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.JavaUtils;
import org.springframework.integration.mapping.OutboundMessageMapper;
import org.springframework.integration.zeromq.outbound.ZeroMqMessageHandler;
import org.springframework.messaging.Message;

/**
 * The auto-configuration for ZeroMQ consumer.
 *
 * @author Daniel Frey
 * @author Artem Bilan
 */
@AutoConfiguration
@EnableConfigurationProperties(ZeroMqConsumerProperties.class)
public class ZeroMqConsumerConfiguration {

	@Bean
	public ZContext zContext() {
		return new ZContext();
	}

	@Bean
	public ZeroMqMessageHandler zeromqMessageHandler(ZeroMqConsumerProperties properties, ZContext zContext,
			@Autowired(required = false) Consumer<ZMQ.Socket> socketConfigurer,
			@Autowired(required = false) OutboundMessageMapper<byte[]> messageMapper) {

		ZeroMqMessageHandler zeroMqMessageHandler = new ZeroMqMessageHandler(zContext, properties.getConnectUrl(),
				properties.getSocketType());

		JavaUtils.INSTANCE.acceptIfNotNull(properties.getTopic(), zeroMqMessageHandler::setTopicExpression)
			.acceptIfNotNull(socketConfigurer, zeroMqMessageHandler::setSocketConfigurer)
			.acceptIfNotNull(messageMapper, zeroMqMessageHandler::setMessageMapper);

		return zeroMqMessageHandler;
	}

	@Bean
	public Function<Flux<Message<?>>, Mono<Void>> zeromqConsumer(
			@Qualifier("zeromqMessageHandler") ZeroMqMessageHandler zeromqMessageHandler) {

		return (input) -> input.doOnSubscribe((sub) -> zeromqMessageHandler.start())
			.flatMap(zeromqMessageHandler::handleMessage)
			.ignoreElements();
	}

}
