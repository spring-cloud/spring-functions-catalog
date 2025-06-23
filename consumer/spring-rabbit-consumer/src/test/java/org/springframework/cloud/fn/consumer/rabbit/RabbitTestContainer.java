/*
 * Copyright 2022-present the original author or authors.
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

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Provides a static {@link RabbitMQContainer} that can be shared across test classes.
 *
 * @author Chris Bono
 * @author Gary Russell
 * @author Artem Bilan
 */
@Testcontainers(disabledWithoutDocker = true)
public interface RabbitTestContainer {

	RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq").withExposedPorts(5672, 5552);

	@BeforeAll
	static void startContainer() throws IOException, InterruptedException {
		RABBITMQ.start();
		RABBITMQ.execInContainer("rabbitmq-plugins", "enable", "rabbitmq_stream");
	}

	@DynamicPropertySource
	static void RabbitMqProperties(DynamicPropertyRegistry propertyRegistry) {
		propertyRegistry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
		propertyRegistry.add("spring.rabbitmq.stream.port", () -> RABBITMQ.getMappedPort(5552));
	}

}
