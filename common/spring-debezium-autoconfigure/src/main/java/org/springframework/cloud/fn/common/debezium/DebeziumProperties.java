/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.cloud.fn.common.debezium;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Debezium engine auto-configuration properties.
 *
 * @author Christian Tzolov
 * @author Artem Bilan
 */
@ConfigurationProperties("debezium")
public class DebeziumProperties {

	public enum DebeziumFormat {

		/**
		 * JSON change event format.
		 */
		JSON("application/json"),
		/**
		 * AVRO change event format.
		 */
		AVRO("application/avro"),
		/**
		 * ProtoBuf change event format.
		 */
		PROTOBUF("application/x-protobuf"),;

		private final String contentType;

		DebeziumFormat(String contentType) {
			this.contentType = contentType;
		}

		public final String contentType() {
			return this.contentType;
		}

	};

	/**
	 * Spring pass-trough wrapper for debezium configuration properties. All properties
	 * with a 'debezium.properties.*' prefix are native Debezium properties.
	 */
	private final Map<String, String> properties = new HashMap<>();

	/**
	 * {@code io.debezium.engine.ChangeEvent} Key and Payload formats. Defaults to 'JSON'.
	 */
	private DebeziumFormat payloadFormat = DebeziumFormat.JSON;

	/**
	 * The policy that defines when the offsets should be committed to offset storage.
	 */
	private DebeziumOffsetCommitPolicy offsetCommitPolicy = DebeziumOffsetCommitPolicy.DEFAULT;

	public Map<String, String> getProperties() {
		return this.properties;
	}

	public DebeziumFormat getPayloadFormat() {
		return this.payloadFormat;
	}

	public void setPayloadFormat(DebeziumFormat format) {
		this.payloadFormat = format;
	}

	public enum DebeziumOffsetCommitPolicy {

		/**
		 * Commits offsets as frequently as possible. This may result in reduced
		 * performance, but it has the least potential for seeing source records more than
		 * once upon restart.
		 */
		ALWAYS,
		/**
		 * Commits offsets no more than the specified time period. If the specified time
		 * is less than {@code 0} then the policy will behave as ALWAYS policy. Requires
		 * the 'debezium.properties.offset.flush.interval.ms' native property to be set.
		 */
		PERIODIC,
		/**
		 * Uses the default Debezium engine policy (PERIODIC).
		 */
		DEFAULT;

	}

	public DebeziumOffsetCommitPolicy getOffsetCommitPolicy() {
		return this.offsetCommitPolicy;
	}

	public void setOffsetCommitPolicy(DebeziumOffsetCommitPolicy offsetCommitPolicy) {
		this.offsetCommitPolicy = offsetCommitPolicy;
	}

	/**
	 * Convert the Spring Framework "debezium.properties.*" properties into native
	 * Debezium configuration.
	 * @return the properties for Debezium native configuration
	 */
	public Properties getDebeziumNativeConfiguration() {
		Properties outProps = new java.util.Properties();
		outProps.putAll(getProperties());
		return outProps;
	}

}
