/*
 * Copyright 2020-2024 the original author or authors.
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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration;
import org.springframework.boot.rsocket.context.RSocketServerBootstrap;
import org.springframework.boot.rsocket.server.RSocketServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = { "spring.rsocket.server.port=0" })
@DirtiesContext
public class RsocketConsumerTests {

	private static final ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RSocketRequesterAutoConfiguration.class,
				RSocketStrategiesAutoConfiguration.class, RsocketConsumerConfiguration.class));

	@Autowired
	ApplicationContext applicationContext;

	@SuppressWarnings("unchecked")
	@Test
	void testRsocketConsumer() {
		RSocketServerBootstrap serverBootstrap = applicationContext.getBean(RSocketServerBootstrap.class);
		RSocketServer server = (RSocketServer) ReflectionTestUtils.getField(serverBootstrap, "server");
		final int port = server.address().getPort();

		applicationContextRunner
			.withPropertyValues("rsocket.consumer.port=" + port, "rsocket.consumer.route=test-route")
			.run((context) -> {
				Consumer<Flux<Message<?>>> rsocketConsumer = context.getBean("rsocketConsumer", Consumer.class);
				rsocketConsumer.accept(Flux.just(new GenericMessage<>("Hello RSocket")));

				StepVerifier.create(RSocketserverApplication.fireForgetPayloads.asFlux())
					.expectNext("Hello RSocket")
					.thenCancel()
					.verify();
			});

	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Controller
	static class RSocketserverApplication {

		static final Sinks.Many<String> fireForgetPayloads = Sinks.many().replay().all();

		@MessageMapping("test-route")
		void someMethod(String payload) {
			fireForgetPayloads.emitNext(payload, Sinks.EmitFailureHandler.FAIL_FAST);
		}

	}

}
