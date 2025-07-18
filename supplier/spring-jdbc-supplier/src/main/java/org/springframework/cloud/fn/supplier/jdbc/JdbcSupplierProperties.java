/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.fn.supplier.jdbc;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JDBC supplier configuration properties.
 *
 * @author Soby Chacko
 * @author Artem Bilan
 */
@ConfigurationProperties("jdbc.supplier")
@Validated
public class JdbcSupplierProperties {

	/**
	 * The query to use to select data.
	 */
	private String query;

	/**
	 * An SQL update statement to execute for marking polled messages as 'seen'.
	 */
	private String update;

	/**
	 * Whether to split the SQL result as individual messages.
	 */
	private boolean split = true;

	/**
	 * Max numbers of rows to process for query.
	 */
	private int maxRows = 0;

	@NotNull
	public String getQuery() {
		return this.query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getUpdate() {
		return this.update;
	}

	public void setUpdate(String update) {
		this.update = update;
	}

	public boolean isSplit() {
		return this.split;
	}

	public void setSplit(boolean split) {
		this.split = split;
	}

	public int getMaxRows() {
		return this.maxRows;
	}

	public void setMaxRows(int maxRows) {
		this.maxRows = maxRows;
	}

}
