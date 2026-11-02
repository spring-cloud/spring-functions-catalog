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

package org.springframework.cloud.fn.consumer.jdbc;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The configuration properties for JDBC consumer.
 *
 * @author Eric Bottard
 * @author Artem Bilan
 * @author Oliver Flasch
 * @author Max Brauer
 */
@ConfigurationProperties("jdbc.consumer")
public class JdbcConsumerProperties {

	@Autowired
	private ShorthandMapConverter shorthandMapConverter;

	/**
	 * The table name for generated INSERT statements and built-in schema initialization.
	 * A configured query specifies its own table name.
	 */
	private String tableName = "messages";

	/**
	 * Comma-separated mappings of SQL parameter names to SpEL expressions, in the form
	 * name:expression. For generated INSERT statements and built-in schema
	 * initialization, parameter names are also used as column names.
	 */
	private String columns = "payload:payload.toString()";

	/**
	 * SQL statement to execute, with named parameters supplied by columns. When empty or
	 * blank, an INSERT statement is generated from tableName and columns.
	 */
	private String query = "";

	/**
	 * 'true', 'false' or the location of a custom initialization script for the table.
	 */
	private String initialize = "false";

	/**
	 * Threshold in number of messages when data will be flushed to database table.
	 */
	private int batchSize = 1;

	/**
	 * Idle timeout in milliseconds when data is automatically flushed to database table.
	 */
	private long idleTimeout = -1L;

	private Map<String, String> columnsMap;

	public String getTableName() {
		return this.tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getColumns() {
		return this.columns;
	}

	public void setColumns(String columns) {
		this.columns = columns;
	}

	/**
	 * Return the SQL statement to execute.
	 * @return the SQL statement
	 * @since 6.0.1
	 */
	public String getQuery() {
		return this.query;
	}

	/**
	 * Set the SQL statement to execute. An empty or blank value selects generated INSERT
	 * statements.
	 * @param query the SQL statement
	 * @since 6.0.1
	 */
	public void setQuery(String query) {
		this.query = query;
	}

	public String getInitialize() {
		return this.initialize;
	}

	public void setInitialize(String initialize) {
		this.initialize = initialize;
	}

	public int getBatchSize() {
		return this.batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public long getIdleTimeout() {
		return this.idleTimeout;
	}

	public void setIdleTimeout(long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	Map<String, String> getColumnsMap() {
		if (this.columnsMap == null) {
			this.columnsMap = this.shorthandMapConverter.convert(this.columns);
		}
		return this.columnsMap;
	}

}
