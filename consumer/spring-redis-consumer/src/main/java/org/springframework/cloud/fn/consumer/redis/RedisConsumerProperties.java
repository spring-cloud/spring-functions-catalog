/*
 * Copyright 2015-2024 the original author or authors.
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

package org.springframework.cloud.fn.consumer.redis;

import java.util.Arrays;
import java.util.Collections;

import jakarta.validation.constraints.AssertTrue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * The configuration properties for Redis consumer.
 *
 * @author Eric Bottard
 * @author Mark Pollack
 * @author Artem Bilan
 * @author Soby Chacko
 */
@ConfigurationProperties("redis.consumer")
@Validated
public class RedisConsumerProperties {

	/**
	 * A SpEL expression to use for topic.
	 */
	private Expression topicExpression;

	/**
	 * A SpEL expression to use for queue.
	 */
	private Expression queueExpression;

	/**
	 * A SpEL expression to use for storing to a key.
	 */
	private Expression keyExpression;

	/**
	 * A literal key name to use when storing to a key.
	 */
	private String key;

	/**
	 * A literal queue name to use when storing in a queue.
	 */
	private String queue;

	/**
	 * A literal topic name to use when publishing to a topic.
	 */
	private String topic;

	public Expression keyExpression() {
		return (this.key != null) ? new LiteralExpression(this.key) : this.keyExpression;
	}

	public Expression queueExpression() {
		return (this.queue != null) ? new LiteralExpression(this.queue) : this.queueExpression;
	}

	public Expression topicExpression() {
		return (this.topic != null) ? new LiteralExpression(this.topic) : this.topicExpression;
	}

	boolean isKeyPresent() {
		return StringUtils.hasText(this.key) || this.keyExpression != null;
	}

	boolean isQueuePresent() {
		return StringUtils.hasText(this.queue) || this.queueExpression != null;
	}

	boolean isTopicPresent() {
		return StringUtils.hasText(this.topic) || this.topicExpression != null;
	}

	public Expression getTopicExpression() {
		return this.topicExpression;
	}

	public void setTopicExpression(Expression topicExpression) {
		this.topicExpression = topicExpression;
	}

	public Expression getQueueExpression() {
		return this.queueExpression;
	}

	public void setQueueExpression(Expression queueExpression) {
		this.queueExpression = queueExpression;
	}

	public Expression getKeyExpression() {
		return this.keyExpression;
	}

	public void setKeyExpression(Expression keyExpression) {
		this.keyExpression = keyExpression;
	}

	public String getKey() {
		return this.key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getQueue() {
		return this.queue;
	}

	public void setQueue(String queue) {
		this.queue = queue;
	}

	public String getTopic() {
		return this.topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	// The javabean property name is what will be reported in case of violation. Make it
	// meaningful
	@AssertTrue(message = "Exactly one of 'queue', 'queueExpression', 'key', 'keyExpression', "
			+ "'topic' and 'topicExpression' must be set")
	public boolean isMutuallyExclusive() {
		Object[] props = { this.queue, this.queueExpression, this.key, this.keyExpression, this.topic,
				this.topicExpression };
		return (props.length - 1) == Collections.frequency(Arrays.asList(props), null);
	}

}
