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

import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@TestPropertySource(
		properties = { "analytics.name=counter666", "analytics.tag.fixed.foo=bar", "analytics.tag.fixed.gork=bork" })
public class FixedTagsTests extends AnalyticsConsumerParentTests {

	@Test
	void testAnalyticsSink() {
		IntStream.range(0, 13).forEach((i) -> analyticsConsumer.accept(new GenericMessage<>("hello")));
		Meter counterMeter = meterRegistry.find("counter666").meter();
		assertThat(StreamSupport.stream(counterMeter.measure().spliterator(), false)
			.mapToDouble(Measurement::getValue)
			.sum()).isEqualTo(13.0);

		assertThat(counterMeter.getId().getTags().size()).isEqualTo(2);
		assertThat(counterMeter.getId().getTag("foo")).isEqualTo("bar");
		assertThat(counterMeter.getId().getTag("gork")).isEqualTo("bork");
	}

}
