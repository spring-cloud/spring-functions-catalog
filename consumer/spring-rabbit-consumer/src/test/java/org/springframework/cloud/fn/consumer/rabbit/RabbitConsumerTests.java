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

package org.springframework.cloud.fn.consumer.rabbit;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "rabbit.consumer.routingKey=" + RabbitConsumerTests.TEST_QUEUE)
@DirtiesContext
public class RabbitConsumerTests implements RabbitTestContainer {

	static final String TEST_QUEUE = "test-consumer-queue";

	@Test
	void rabbitSupplierReceivesData(@Autowired RabbitTemplate rabbitTemplate,
			@Autowired Consumer<Message<?>> rabbitConsumer) {

		rabbitConsumer.accept(new GenericMessage<>("test data1"));
		rabbitConsumer.accept(new GenericMessage<>("test data2"));

		Object received = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 10_000);
		assertThat(received).isEqualTo("test data1");
		received = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 10_000);
		assertThat(received).isEqualTo("test data2");
	}

	@SpringBootApplication
	public static class TestConfiguration {

		@Bean
		Queue testQueue() {
			return new Queue(TEST_QUEUE);
		}

	}

}
