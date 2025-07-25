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

package org.springframework.cloud.fn.consumer.twitter.status.update;

import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import twitter4j.GeoLocation;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.twitter.TwitterConnectionConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

/**
 * The auto-configuration for Twitter messages.
 *
 * @author Christian Tzolov
 * @author Artem Bilan
 */
@AutoConfiguration(after = TwitterConnectionConfiguration.class)
@EnableConfigurationProperties(TwitterUpdateConsumerProperties.class)
public class TwitterUpdateConsumerConfiguration {

	private static final Log LOGGER = LogFactory.getLog(TwitterUpdateConsumerConfiguration.class);

	@Bean
	public Consumer<StatusUpdate> twitterUpdateStatusConsumer(Twitter twitter) {
		return (statusUpdate) -> {
			try {
				Status status = twitter.updateStatus(statusUpdate);

				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug(status);
				}
			}
			catch (TwitterException ex) {
				LOGGER.error("Failed apply update status: " + statusUpdate, ex);
			}
		};
	}

	@Bean
	public Function<Message<?>, StatusUpdate> messageToStatusUpdateFunction(
			TwitterUpdateConsumerProperties updateProperties) {

		return (message) -> {

			String updateText = updateProperties.getText().getValue(message, String.class);

			StatusUpdate statusUpdate = new StatusUpdate(updateText);

			if (updateProperties.getAttachmentUrl() != null) {
				statusUpdate.setAttachmentUrl(updateProperties.getAttachmentUrl().getValue(message, String.class));
			}

			if (updateProperties.getPlaceId() != null) {
				statusUpdate.setPlaceId(updateProperties.getPlaceId().getValue(message, String.class));
			}

			if (updateProperties.getInReplyToStatusId() != null) {
				statusUpdate.setInReplyToStatusId(updateProperties.getInReplyToStatusId().getValue(message, int.class));
				statusUpdate.setAutoPopulateReplyMetadata(true);
			}

			if (updateProperties.getDisplayCoordinates() != null) {
				statusUpdate
					.setDisplayCoordinates(updateProperties.getDisplayCoordinates().getValue(message, boolean.class));
			}

			if (updateProperties.getMediaIds() != null) {
				long[] mediaIds = updateProperties.getMediaIds().getValue(message, long[].class);
				statusUpdate.setMediaIds(mediaIds);
			}

			if (updateProperties.getLocation().getLat() != null) {
				double lat = updateProperties.getLocation().getLat().getValue(message, Double.class);
				double lon = updateProperties.getLocation().getLon().getValue(message, Double.class);
				statusUpdate.setLocation(new GeoLocation(lat, lon));
			}

			return statusUpdate;
		};
	}

	@Bean
	public Consumer<Message<?>> twitterStatusUpdateConsumer(
			@Qualifier("messageToStatusUpdateFunction") Function<Message<?>, StatusUpdate> statusUpdateQuery,
			@Qualifier("twitterUpdateStatusConsumer") Consumer<StatusUpdate> updateStatus) {

		return (message) -> updateStatus.accept(statusUpdateQuery.apply(message));
	}

}
