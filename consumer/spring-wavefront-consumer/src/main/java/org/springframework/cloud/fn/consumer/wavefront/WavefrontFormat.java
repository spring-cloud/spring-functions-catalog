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

package org.springframework.cloud.fn.consumer.wavefront;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.validation.ValidationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.messaging.Message;

/**
 * The format for Wavefront messages.
 *
 * @author Timo Salm
 */
public class WavefrontFormat {

	private static final Log LOGGER = LogFactory.getLog(WavefrontFormat.class);

	private final WavefrontConsumerProperties properties;

	private final Message<?> message;

	public WavefrontFormat(final WavefrontConsumerProperties properties, Message<?> message) {
		this.properties = properties;
		this.message = message;
	}

	public String getFormattedString() {
		final Number metricValue = extractMetricValueFromPayload();

		final Map<String, Object> pointTagsMap = extractPointTagsMapFromPayload(this.properties.getTagExpression(),
				this.message);
		validatePointTagsKeyValuePairs(pointTagsMap);
		final String formattedPointTagsPart = getFormattedPointTags(pointTagsMap);

		if (this.properties.getTimestampExpression() == null) {
			return String
				.format("\"%s\" %s source=%s %s", this.properties.getMetricName(), metricValue,
						this.properties.getSource(), formattedPointTagsPart)
				.trim();
		}

		final Long timestamp = extractTimestampFromPayload();
		return String
			.format("\"%s\" %s %d source=%s %s", this.properties.getMetricName(), metricValue, timestamp,
					this.properties.getSource(), formattedPointTagsPart)
			.trim();
	}

	private Long extractTimestampFromPayload() {
		try {
			return this.properties.getTimestampExpression().getValue(this.message, Long.class);
		}
		catch (SpelEvaluationException ex) {
			throw new ValidationException(
					"The timestamp value has to be a number that reflects the epoch seconds of the "
							+ "metric (e.g. 1382754475).",
					ex);
		}
	}

	private Number extractMetricValueFromPayload() {
		try {
			return this.properties.getMetricExpression().getValue(this.message, Number.class);
		}
		catch (SpelEvaluationException ex) {
			throw new ValidationException("The metric value has to be a double-precision floating point number or a "
					+ "long integer. It can be positive, negative, or 0.", ex);
		}
	}

	private String getFormattedPointTags(Map<String, Object> pointTagsMap) {
		return pointTagsMap.entrySet()
			.stream()
			.map((it) -> String.format("%s=\"%s\"", it.getKey(), it.getValue()))
			.collect(Collectors.joining(" "));
	}

	private Map<String, Object> extractPointTagsMapFromPayload(Map<String, Expression> pointTagsExpressionsPointValue,
			Message<?> message) {

		return pointTagsExpressionsPointValue.entrySet().stream().map((it) -> {
			try {
				final Object pointValue = it.getValue().getValue(this.message);
				return new AbstractMap.SimpleEntry<>(it.getKey(), pointValue);
			}
			catch (EvaluationException ex) {
				LOGGER.warn("Unable to extract point tag for key " + it.getKey() + " from payload", ex);
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private void validatePointTagsKeyValuePairs(Map<String, Object> pointTagsMap) {
		pointTagsMap.forEach((key, value) -> {
			if (!Pattern.matches("^[a-zA-Z0-9._-]+", key)) {
				throw new ValidationException("Point tag key \"" + key + "\" contains invalid characters: Valid "
						+ "characters are alphanumeric, hyphen (\"-\"), underscore (\"_\"), dot (\".\")");
			}

			final int keyValueCombinationLength = key.length() + value.toString().length();
			if (keyValueCombinationLength > 254) {
				LOGGER.warn("Maximum allowed length for a combination of a point tag key and value "
						+ "is 254 characters. The length of combination for key " + key + " is "
						+ keyValueCombinationLength + ".");
			}
		});
	}

}
