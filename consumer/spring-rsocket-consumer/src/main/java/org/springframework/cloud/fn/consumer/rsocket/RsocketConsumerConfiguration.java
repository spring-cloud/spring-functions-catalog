/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.cloud.fn.consumer.rsocket;

import java.util.function.Consumer;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.rsocket.RSocketRequester;

/**
 * Auto-configuration for RSocket consumer.
 *
 * @author Artem Bilan
 */
@AutoConfiguration(after = RSocketRequesterAutoConfiguration.class)
@EnableConfigurationProperties(RsocketConsumerProperties.class)
public class RsocketConsumerConfiguration {

	@Bean
	public Consumer<Flux<Message<?>>> rsocketConsumer(
			@Qualifier("rsocketFunctionConsumer") Function<Flux<Message<?>>, Mono<Void>> rsocketFunctionConsumer) {

		return (data) -> rsocketFunctionConsumer.apply(data).block();
	}

	@Bean
	public Function<Flux<Message<?>>, Mono<Void>> rsocketFunctionConsumer(RSocketRequester.Builder builder,
			RsocketConsumerProperties rsocketConsumerProperties) {

		RSocketRequester rSocketRequester = (rsocketConsumerProperties.getUri() != null)
				? builder.websocket(rsocketConsumerProperties.getUri())
				: builder.tcp(rsocketConsumerProperties.getHost(), rsocketConsumerProperties.getPort());

		String route = rsocketConsumerProperties.getRoute();

		return (input) -> input.flatMap((message) -> rSocketRequester.route(route).data(message.getPayload()).send())
			.ignoreElements();
	}

}
