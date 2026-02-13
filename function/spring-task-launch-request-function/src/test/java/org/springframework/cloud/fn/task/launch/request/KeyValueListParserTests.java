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

package org.springframework.cloud.fn.task.launch.request;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Chris Schaefer
 * @author David Turanski
 * @author Artem Bilan
 */
public class KeyValueListParserTests {

	@Test
	public void testParseSimpleDeploymentProperty() {
		Map<String, String> deploymentProperties = KeyValueListParser
			.parseCommaDelimitedKeyValuePairs("app.sftp.param=value");
		assertThat(deploymentProperties).hasSize(1).containsEntry("app.sftp.param", "value");
	}

	@Test
	public void testParseSimpleDeploymentPropertyMultipleValues() {
		Map<String, String> deploymentProperties = KeyValueListParser
			.parseCommaDelimitedKeyValuePairs("app.sftp.param=value1,value2,value3");

		assertThat(deploymentProperties).hasSize(1).containsEntry("app.sftp.param", "value1,value2,value3");
	}

	@Test
	public void testParseSpelExpressionMultipleValues() {
		Map<String, String> argExpressions = KeyValueListParser.parseCommaDelimitedKeyValuePairs(
				"arg1=payload.substr(0,2),arg2=headers['foo'],arg3=headers['bar']==false");

		assertThat(argExpressions).hasSize(3)
			.containsEntry("arg1", "payload.substr(0,2)")
			.containsEntry("arg2", "headers['foo']")
			.containsEntry("arg3", "headers['bar']==false");
	}

	@Test
	public void testParseMultipleDeploymentPropertiesSingleValue() {
		Map<String, String> deploymentProperties = KeyValueListParser
			.parseCommaDelimitedKeyValuePairs("app.sftp.param=value1,app.sftp.other.param=value2");

		assertThat(deploymentProperties).hasSize(2)
			.containsEntry("app.sftp.param", "value1")
			.containsEntry("app.sftp.other.param", "value2");
	}

	@Test
	public void testParseMultipleDeploymentPropertiesMultipleValues() {
		TaskLaunchRequestFunctionProperties taskLaunchRequestProperties = new TaskLaunchRequestFunctionProperties();

		Map<String, String> deploymentProperties = KeyValueListParser
			.parseCommaDelimitedKeyValuePairs("app.sftp.param=value1,value2,app.sftp.other.param=other1,other2");

		assertThat(deploymentProperties).hasSize(2)
			.containsEntry("app.sftp.param", "value1,value2")
			.containsEntry("app.sftp.other.param", "other1,other2");
	}

}
