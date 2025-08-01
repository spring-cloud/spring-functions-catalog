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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.config.EnableIntegration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author David Turanski
 **/
public class TaskLaunchRequestFunctionPropertiesTests {

	@Test
	public void deploymentPropertiesCanBeCustomized() {
		TaskLaunchRequestFunctionProperties properties = getBatchProperties(
				"task.launch.request.deploymentProperties:prop1=val1,prop2=val2");
		assertThat(properties.getDeploymentProperties()).isEqualTo("prop1=val1,prop2=val2");
	}

	@Test
	public void parametersCanBeCustomized() {
		TaskLaunchRequestFunctionProperties properties = getBatchProperties(
				"task.launch.request.args:jp1=jpv1,jp2=jpv2");
		List<String> args = properties.getArgs();

		assertThat(args).isNotNull();
		assertThat(args).hasSize(2);
		assertThat(args.get(0)).isEqualTo("jp1=jpv1");
		assertThat(args.get(1)).isEqualTo("jp2=jpv2");
	}

	private TaskLaunchRequestFunctionProperties getBatchProperties(String... var) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

		if (var != null) {
			TestPropertyValues.of(var).applyTo(context);
		}

		context.register(Conf.class);
		context.refresh();

		return context.getBean(TaskLaunchRequestFunctionProperties.class);
	}

	@Configuration
	@EnableIntegration
	@EnableConfigurationProperties(TaskLaunchRequestFunctionProperties.class)
	@Import(TaskLaunchRequestFunctionConfiguration.class)
	static class Conf {

	}

}
