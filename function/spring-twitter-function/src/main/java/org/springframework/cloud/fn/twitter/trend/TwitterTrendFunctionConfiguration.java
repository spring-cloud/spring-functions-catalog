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

package org.springframework.cloud.fn.twitter.trend;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import twitter4j.GeoLocation;
import twitter4j.Location;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.twitter.TwitterConnectionConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for Twitter Trend function.
 *
 * @author Christian Tzolov
 * @author Artem Bilan
 */
@ConditionalOnProperty(prefix = "twitter.trend", name = "trend-query-type")
@AutoConfiguration(after = TwitterConnectionConfiguration.class)
@EnableConfigurationProperties(TwitterTrendFunctionProperties.class)
public class TwitterTrendFunctionConfiguration {

	private static final Log LOGGER = LogFactory.getLog(TwitterTrendFunctionConfiguration.class);

	@Bean
	public Function<Message<?>, Message<byte[]>> twitterTrendFunction(TwitterTrendFunctionProperties properties,
			Twitter twitter, Function<Object, Message<byte[]>> managedJson) {

		Function<Message<?>, Trends> trendsFunction = trendsFunction(properties, twitter);
		Function<Message<?>, List<Location>> closestOrAvailableTrends = closestOrAvailableTrends(properties, twitter);

		return (properties.getTrendQueryType() == TwitterTrendFunctionProperties.TrendQueryType.trend)
				? trendsFunction.andThen(managedJson) : closestOrAvailableTrends.andThen(managedJson);
	}

	private Function<Message<?>, Trends> trendsFunction(TwitterTrendFunctionProperties properties, Twitter twitter) {
		return (message) -> {
			try {
				int woeid = properties.getLocationId().getValue(message, int.class);
				return twitter.getPlaceTrends(woeid);
			}
			catch (TwitterException ex) {
				LOGGER.error("Twitter API error!", ex);
			}
			return null;
		};
	}

	private Function<Message<?>, List<Location>> closestOrAvailableTrends(TwitterTrendFunctionProperties properties,
			Twitter twitter) {

		return (message) -> {
			try {
				if (properties.getClosest().getLat() != null && properties.getClosest().getLon() != null) {
					double lat = properties.getClosest().getLat().getValue(message, double.class);
					double lon = properties.getClosest().getLon().getValue(message, double.class);
					return twitter.getClosestTrends(new GeoLocation(lat, lon));
				}
				else {
					return twitter.getAvailableTrends();
				}
			}
			catch (TwitterException ex) {
				LOGGER.error("Twitter API error!", ex);
			}
			return null;
		};
	}

}
