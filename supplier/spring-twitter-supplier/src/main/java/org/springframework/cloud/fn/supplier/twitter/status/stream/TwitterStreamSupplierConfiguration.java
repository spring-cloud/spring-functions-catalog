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

package org.springframework.cloud.fn.supplier.twitter.status.stream;

import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.twitter.TwitterConnectionConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

/**
 * The auto-configuration for real-time Twitter streaming API via supplier.
 *
 * @author Christian Tzolov
 */

@ConditionalOnProperty(prefix = "twitter.stream", name = "enabled")
@EnableConfigurationProperties(TwitterStreamSupplierProperties.class)
@AutoConfiguration(after = TwitterConnectionConfiguration.class)
public class TwitterStreamSupplierConfiguration {

	private static final Log LOGGER = LogFactory.getLog(TwitterStreamSupplierConfiguration.class);

	private final FluxMessageChannel twitterStatusInputChannel = new FluxMessageChannel();

	@Bean
	public StatusListener twitterStatusListener(TwitterStream twitterStream, ObjectMapper objectMapper) {

		StatusListener statusListener = new StatusListener() {

			@Override
			public void onException(Exception e) {
				LOGGER.error("Status Error: ", e);
				throw new RuntimeException("Status Error: ", e);
			}

			@Override
			public void onDeletionNotice(StatusDeletionNotice arg) {
				LOGGER.info("StatusDeletionNotice: " + arg);
			}

			@Override
			public void onScrubGeo(long userId, long upToStatusId) {
				LOGGER.info("onScrubGeo: " + userId + ", " + upToStatusId);
			}

			@Override
			public void onStallWarning(StallWarning warning) {
				LOGGER.warn("Stall Warning: " + warning);
				throw new RuntimeException("Stall Warning: " + warning);
			}

			@Override
			public void onStatus(Status status) {

				try {
					String json = objectMapper.writeValueAsString(status);
					Message<byte[]> message = MessageBuilder.withPayload(json.getBytes())
						.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON_VALUE)
						.build();
					TwitterStreamSupplierConfiguration.this.twitterStatusInputChannel.send(message);
				}
				catch (JsonProcessingException ex) {
					String errorMessage = "Status to JSON conversion error!";
					LOGGER.error(errorMessage, ex);
					throw new RuntimeException(errorMessage, ex);
				}
			}

			@Override
			public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
				LOGGER.warn("Track Limitation Notice: " + numberOfLimitedStatuses);
			}
		};

		twitterStream.addListener(statusListener);

		return statusListener;
	}

	@Bean
	public Supplier<Flux<Message<?>>> twitterStreamSupplier(TwitterStream twitterStream,
			TwitterStreamSupplierProperties streamProperties) {

		return () -> Flux.from(this.twitterStatusInputChannel).doOnSubscribe((subscription) -> {
			try {
				switch (streamProperties.getType()) {
					case filter -> twitterStream.filter(streamProperties.getFilter().toFilterQuery());
					case sample -> twitterStream.sample();
					case firehose -> twitterStream.firehose(streamProperties.getFilter().getCount());
					case link -> twitterStream.links(streamProperties.getFilter().getCount());
					default -> throw new IllegalArgumentException("Unknown stream type:" + streamProperties.getType());
				}
			}
			catch (Exception ex) {
				LOGGER.error("Filter is not property set");
			}
		}).doAfterTerminate(() -> {
			LOGGER.info("Proactive cancel for twitter stream");
			twitterStream.shutdown();
		}).doOnError((throwable) -> LOGGER.error(throwable.getMessage(), throwable));
	}

}
