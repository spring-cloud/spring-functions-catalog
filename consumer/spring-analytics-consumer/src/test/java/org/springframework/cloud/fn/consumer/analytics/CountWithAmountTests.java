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

import org.junit.jupiter.api.Test;

import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@TestPropertySource(properties = { "analytics.name=counter666", "analytics.tag.expression.foo='bar'",
		"analytics.amount-expression=payload.length()" })
class CountWithAmountTests extends AnalyticsConsumerParentTests {

	@Test
	void testCounterSink() {
		String message = "hello world message";
		double messageSize = Long.valueOf(message.length()).doubleValue();
		analyticsConsumer.accept(new GenericMessage<>(message));
		assertThat(meterRegistry.find("counter666").counter().count()).isEqualTo(messageSize);
	}

}
