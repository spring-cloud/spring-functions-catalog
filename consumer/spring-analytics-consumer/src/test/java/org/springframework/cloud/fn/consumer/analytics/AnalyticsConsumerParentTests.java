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

package org.springframework.cloud.fn.consumer.analytics;

import java.util.function.Consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "management.wavefront.metrics.export.enabled=false" })
@DirtiesContext
public abstract class AnalyticsConsumerParentTests {

	@Autowired
	protected SimpleMeterRegistry meterRegistry;

	@Autowired
	protected Consumer<Message<?>> analyticsConsumer;

	protected Message<byte[]> message(String payload) {
		return MessageBuilder.withPayload(payload.getBytes()).build();
	}

	@SpringBootApplication
	static class AnalyticsConsumerTestApplication {

	}

}
