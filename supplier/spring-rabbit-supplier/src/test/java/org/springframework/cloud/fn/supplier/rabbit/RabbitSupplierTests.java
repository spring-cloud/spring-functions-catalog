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

package org.springframework.cloud.fn.supplier.rabbit;

import java.time.Duration;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.fn.consumer.rabbit.RabbitTestContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "rabbit.supplier.queues=" + RabbitSupplierTests.TEST_QUEUE)
@DirtiesContext
public class RabbitSupplierTests implements RabbitTestContainer {

	static final String TEST_QUEUE = "test-supplier-queue";

	@Test
	void rabbitSupplierReceivesData(@Autowired RabbitTemplate rabbitTemplate,
			@Autowired Supplier<Flux<Message<byte[]>>> rabbitSupplier) {

		Flux<String> mapped = rabbitSupplier.get().map(Message::getPayload).map(String::new);
		StepVerifier stepVerifier = StepVerifier.create(mapped).expectNext("test1", "test2").thenCancel().verifyLater();

		rabbitTemplate.convertAndSend(TEST_QUEUE, "test1".getBytes());
		rabbitTemplate.convertAndSend(TEST_QUEUE, "test2".getBytes());

		stepVerifier.verify(Duration.ofSeconds(10));
	}

	@SpringBootApplication
	public static class TestConfiguration {

		@Bean
		Queue testQueue() {
			return new Queue(TEST_QUEUE);
		}

	}

}
