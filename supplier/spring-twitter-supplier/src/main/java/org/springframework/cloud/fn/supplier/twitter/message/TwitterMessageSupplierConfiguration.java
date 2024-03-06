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

package org.springframework.cloud.fn.supplier.twitter.message;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import twitter4j.DirectMessage;
import twitter4j.DirectMessageList;
import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.RateLimitStatus;
import twitter4j.SymbolEntity;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.twitter.TwitterConnectionConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.messaging.Message;

/**
 * The auto-configuration for receiving Twitter messages via supplier.
 *
 * @author Christian Tzolov
 * @author Artem Bilan
 */
@ConditionalOnProperty(prefix = "twitter.message.source", name = "enabled")
@AutoConfiguration(after = TwitterConnectionConfiguration.class)
@EnableConfigurationProperties(TwitterMessageSupplierProperties.class)
public class TwitterMessageSupplierConfiguration {

	private static final Log logger = LogFactory.getLog(TwitterMessageSupplierConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public MetadataStore metadataStore() {
		return new SimpleMetadataStore();
	}

	@Bean
	@ConditionalOnMissingBean
	public MessageCursor cursor() {
		return new MessageCursor();
	}

	@Bean
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Supplier<List<DirectMessage>> twitterMessagesSupplier(TwitterMessageSupplierProperties properties,
			Twitter twitter, MessageCursor cursorState) {

		return () -> {
			try {
				DirectMessageList messages = (cursorState.getCursor() == null)
						? twitter.getDirectMessages(properties.getCount())
						: twitter.getDirectMessages(properties.getCount(), cursorState.getCursor());

				if (messages != null) {
					cursorState.updateCursor(messages.getNextCursor());
					return (List<DirectMessage>) (List) messages.stream().map(DirectMessageAdapter::new).toList();
				}

				logger.error(String.format("NULL messages response for properties: %s and cursor: %s!", properties,
						cursorState));
				cursorState.updateCursor(null);
			}
			catch (TwitterException ex) {
				logger.error("Twitter API error:", ex);
			}

			return new ArrayList<>();
		};
	}

	@Bean
	public Function<List<DirectMessage>, List<DirectMessage>> messageDeduplicate(MetadataStore metadataStore) {
		return (messages) -> {
			List<DirectMessage> uniqueMessages = new ArrayList<>();
			for (DirectMessage message : messages) {
				long id = message.getId();
				if (metadataStore.get(id + "") == null) {
					metadataStore.put(id + "", message.getCreatedAt() + "");
					uniqueMessages.add(message);
				}
			}
			return uniqueMessages;
		};
	}

	@Bean
	public Supplier<Message<byte[]>> twitterMessageSupplier(
			Function<List<DirectMessage>, List<DirectMessage>> messageDeduplicate,
			Function<Object, Message<byte[]>> managedJson, Supplier<List<DirectMessage>> directMessagesSupplier) {

		return () -> messageDeduplicate.andThen(managedJson).apply(directMessagesSupplier.get());
	}

	public static class MessageCursor {

		private String cursor = null;

		public String getCursor() {
			return this.cursor;
		}

		public void updateCursor(String newCursor) {
			this.cursor = newCursor;
		}

		@Override
		public String toString() {
			return "Cursor{" + "cursor=" + this.cursor + '}';
		}

	}

	@SuppressWarnings("serial")
	private static class DirectMessageAdapter implements DirectMessage {

		private final DirectMessage delegate;

		private DirectMessageAdapter(DirectMessage delegate) {
			this.delegate = delegate;
		}

		@Override
		public long getId() {
			return this.delegate.getId();
		}

		@Override
		public String getText() {
			return this.delegate.getText();
		}

		@Override
		public long getSenderId() {
			return this.delegate.getSenderId();
		}

		@Override
		public long getRecipientId() {
			return this.delegate.getRecipientId();
		}

		@Override
		public Date getCreatedAt() {
			return this.delegate.getCreatedAt();
		}

		@Override
		public RateLimitStatus getRateLimitStatus() {
			return this.delegate.getRateLimitStatus();
		}

		@Override
		public int getAccessLevel() {
			return this.delegate.getAccessLevel();
		}

		@Override
		public UserMentionEntity[] getUserMentionEntities() {
			return this.delegate.getUserMentionEntities();
		}

		@Override
		public URLEntity[] getURLEntities() {
			return this.delegate.getURLEntities();
		}

		@Override
		public HashtagEntity[] getHashtagEntities() {
			return this.delegate.getHashtagEntities();
		}

		@Override
		public MediaEntity[] getMediaEntities() {
			return this.delegate.getMediaEntities();
		}

		@Override
		public SymbolEntity[] getSymbolEntities() {
			return this.delegate.getSymbolEntities();
		}

		@Deprecated
		@JsonIgnore
		@Override
		public String getSenderScreenName() {
			return this.delegate.getSenderScreenName();
		}

		@Deprecated
		@JsonIgnore
		@Override
		public String getRecipientScreenName() {
			return this.delegate.getRecipientScreenName();
		}

		@Deprecated
		@JsonIgnore
		@Override
		public User getSender() {
			return this.delegate.getSender();
		}

		@Deprecated
		@JsonIgnore
		@Override
		public User getRecipient() {
			return this.delegate.getRecipient();
		}

	}

}
