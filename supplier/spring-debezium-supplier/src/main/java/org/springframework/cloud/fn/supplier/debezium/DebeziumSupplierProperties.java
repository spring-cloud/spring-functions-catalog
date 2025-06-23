/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.fn.supplier.debezium;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Debezium supplier configuration properties.
 *
 * @author Christian Tzolov
 * @author Artem Bilan
 */
@ConfigurationProperties("debezium.supplier")
public class DebeziumSupplierProperties {

	/**
	 * Enable support for tombstone (aka delete) messages.
	 */
	private boolean enableEmptyPayload = true;

	/**
	 * Patterns for {@code ChangeEvent.headers()} to map.
	 */
	private String[] headerNamesToMap = { "*" };

	public boolean isEnableEmptyPayload() {
		return this.enableEmptyPayload;
	}

	public void setEnableEmptyPayload(boolean enableEmptyPayload) {
		this.enableEmptyPayload = enableEmptyPayload;
	}

	public String[] getHeaderNamesToMap() {
		return this.headerNamesToMap;
	}

	public void setHeaderNamesToMap(String[] headerNamesToMap) {
		this.headerNamesToMap = headerNamesToMap;
	}

}
