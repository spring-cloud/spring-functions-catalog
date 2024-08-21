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

package org.springframework.cloud.fn.consumer.analytics;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * The auto-configuration for analytics consumer.
 *
 * @author Christian Tzolov
 */
@AutoConfiguration(after = MetricsAutoConfiguration.class)
@EnableConfigurationProperties(AnalyticsConsumerProperties.class)
public class AnalyticsConsumerConfiguration {

	/**
	 * Default tag value. Used to fill the tag when the actual value is missing.
	 */
	public static final String UNAVAILABLE_TAG = "NA";

	private final Map<Meter.Id, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

	@Bean
	public Consumer<Message<?>> analyticsConsumer(AnalyticsConsumerProperties properties, MeterRegistry meterRegistry) {

		return (message) -> {
			CharSequence meterNameRaw = properties.getComputedNameExpression().getValue(message, CharSequence.class);
			String meterName = StringUtils.hasText(meterNameRaw) ? meterNameRaw.toString() : "empty";

			@SuppressWarnings("deprecation")
			// All fixed tags together are passed with every meter update.
			Tags fixedTags = this.toTags(properties.getTag().getFixed());

			Double amount = properties.getComputedAmountExpression().getValue(message, Double.class);

			Map<String, List<Tag>> allGroupedTags = new HashMap<>();
			// Tag Expressions
			if (properties.getTag().getExpression() != null) {

				Map<String, List<Tag>> groupedTags = properties.getTag()
					.getExpression()
					.entrySet()
					.stream()
					// maps a <name, expr> pair into [<name, expr#val_1>, ... <name,
					// expr#val_N>] Tag array.
					.map((namedExpression) -> toList(namedExpression.getValue().getValue(message)).stream()
						.map((tagValue) -> Tag.of(namedExpression.getKey(), tagValue))
						.collect(Collectors.toList()))
					.flatMap(List::stream)
					.collect(Collectors.groupingBy(Tag::getKey, Collectors.toList()));
				allGroupedTags.putAll(groupedTags);
			}

			recordMetrics(meterRegistry, meterName, fixedTags, allGroupedTags, amount, properties.getMeterType());
		};
	}

	/**
	 * Converts a key/value Map into Tag(key,value) list. Filters out the empty key/value
	 * pairs.
	 * @param keyValueMap key/value map to convert into tags.
	 * @return tags list representing every non-empty key/value pair.
	 */
	protected Tags toTags(Map<String, String> keyValueMap) {
		return CollectionUtils.isEmpty(keyValueMap) ? Tags.empty()
				: Tags.of(keyValueMap.entrySet()
					.stream()
					.filter((e) -> StringUtils.hasText(e.getKey()) && StringUtils.hasText(e.getValue()))
					.map((e) -> Tag.of(e.getKey(), e.getValue()))
					.toList());
	}

	/**
	 * Convert the input value into a list of values. If the value is not a
	 * collection/array type the result is a single element list. For collection/array
	 * input value the result is the list of "stringified" content of this collection.
	 * @param value input value can be an array, collection or single value.
	 * @return the value list.
	 */
	protected List<String> toList(Object value) {
		if (value == null) {
			// Ensure that the tag is present in the meter metrics, even if empty.
			// TSDB as Prometheus do not tolerate same meters to have different tags
			// signatures.
			return Collections.singletonList(UNAVAILABLE_TAG);
		}

		if ((value instanceof Collection) || ObjectUtils.isArray(value)) {

			Collection<?> valueCollection = (value instanceof Collection) ? (Collection<?>) value
					: Arrays.asList(ObjectUtils.toObjectArray(value));
			List<String> list = valueCollection.stream()
				.filter(Objects::nonNull)
				.map(Object::toString)
				.filter(StringUtils::hasText)
				.toList();
			return CollectionUtils.isEmpty(list) ? Collections.singletonList(UNAVAILABLE_TAG) : list;
		}
		else {
			return Collections.singletonList(value.toString());
		}
	}

	private void recordMetrics(MeterRegistry meterRegistry, String meterName, Tags fixedTags,
			Map<String, List<Tag>> groupedTags, Double amount, AnalyticsConsumerProperties.MeterType meterType) {

		if (!CollectionUtils.isEmpty(groupedTags)) {
			groupedTags.values().stream().map(List::size).max(Integer::compareTo).ifPresent((max) -> {
				for (int i = 0; i < max; i++) {
					Tags currentTags = Tags.of(fixedTags);
					for (Map.Entry<String, List<Tag>> e : groupedTags.entrySet()) {
						currentTags = (e.getValue().size() > i) ? currentTags.and(e.getValue().get(i))
								: currentTags.and(Tags.of(e.getKey(), ""));
					}

					// Update the meterName for every configured MaterRegistry.
					record(meterRegistry, meterName, currentTags, amount, meterType);
				}
			});
		}
		else {
			// Update the meterName for every configured MaterRegistry.
			record(meterRegistry, meterName, fixedTags, amount, meterType);
		}
	}

	private void record(MeterRegistry meterRegistry, String meterName, Iterable<Tag> tags, double meterAmount,
			AnalyticsConsumerProperties.MeterType meterType) {

		if (meterType == AnalyticsConsumerProperties.MeterType.gauge) {
			Meter.Id gaugeId = new Meter.Id(meterName, Tags.of(tags), null, null, Meter.Type.GAUGE);
			if (!this.gaugeValues.containsKey(gaugeId)) {
				this.gaugeValues.put(gaugeId, new AtomicLong((long) meterAmount));
			}
			else {
				this.gaugeValues.get(gaugeId).set((long) meterAmount);
			}
			if (!isMeterRegistryContainsGauge(meterRegistry, gaugeId)) {
				meterRegistry.gauge(meterName, tags, this.gaugeValues.get(gaugeId), AtomicLong::doubleValue);
			}
		}
		else if (meterType == AnalyticsConsumerProperties.MeterType.counter) {
			meterRegistry.counter(meterName, tags).increment(meterAmount);
		}
		else {
			throw new RuntimeException("Unknown meter type: " + meterType);
		}
	}

	private static boolean isMeterRegistryContainsGauge(MeterRegistry meterRegistry, Meter.Id gaugeId) {
		return meterRegistry.find(gaugeId.getName())
			.gauges()
			.stream()
			.anyMatch((gauge) -> gauge.getId().equals(gaugeId));
	}

}
