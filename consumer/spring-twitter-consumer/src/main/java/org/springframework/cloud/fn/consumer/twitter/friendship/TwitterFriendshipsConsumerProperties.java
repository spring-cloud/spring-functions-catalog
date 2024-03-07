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

package org.springframework.cloud.fn.consumer.twitter.friendship;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.expression.ValueExpression;
import org.springframework.stereotype.Component;

/**
 * The Twitter friendships properties.
 *
 * @author Christian Tzolov
 * @author Artem Bilan
 */
@Component
@ConfigurationProperties("twitter.friendships.update")
public class TwitterFriendshipsConsumerProperties {

	public enum OperationType {

		/** Friendship operation types. */
		create, update, destroy

	}

	/**
	 * The screen name of the user to follow (String).
	 */
	private Expression screenName;

	/**
	 * The ID of the user to follow (Integer).
	 */
	private Expression userId;

	/**
	 * Type of Friendships request.
	 */
	private Expression type = new SpelExpressionParser().parseExpression("'create'");

	/**
	 * Additional properties for the Friendships create requests.
	 */
	private final Create create = new Create();

	/**
	 * Additional properties for the Friendships update requests.
	 */
	private final Update update = new Update();

	public Expression getScreenName() {
		return this.screenName;
	}

	public void setScreenName(Expression screenName) {
		this.screenName = screenName;
	}

	public Expression getUserId() {
		return this.userId;
	}

	public void setUserId(Expression userId) {
		this.userId = userId;
	}

	public Expression getType() {
		return this.type;
	}

	public void setType(Expression type) {
		this.type = type;
	}

	public Create getCreate() {
		return this.create;
	}

	public Update getUpdate() {
		return this.update;
	}

	public static class Create {

		/**
		 * The ID of the user to follow (boolean).
		 */
		private Expression follow = new ValueExpression<>(true);

		public Expression getFollow() {
			return this.follow;
		}

		public void setFollow(Expression follow) {
			this.follow = follow;
		}

	}

	public static class Update {

		/**
		 * Enable/disable device notifications from the target user.
		 */
		private Expression device = new ValueExpression<>(true);

		/**
		 * Enable/disable Retweets from the target user.
		 */
		private Expression retweets = new ValueExpression<>(true);

		public Expression getDevice() {
			return this.device;
		}

		public void setDevice(Expression device) {
			this.device = device;
		}

		public Expression getRetweets() {
			return this.retweets;
		}

		public void setRetweets(Expression retweets) {
			this.retweets = retweets;
		}

	}

}
