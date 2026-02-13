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

package org.springframework.cloud.fn.common.metadata.store;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * An {@link EnvironmentPostProcessor} to set
 * {@code spring.cloud.aws.dynamodb.enabled = false} since this module doesn't support
 * {@link software.amazon.awssdk.services.dynamodb.DynamoDbClient} and expose its own
 * {@link software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient} bean.
 *
 * @author Artem Bilan
 * @since 6.0
 */
class DisableDynamoDbAutoConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		MutablePropertySources propertySources = environment.getPropertySources();
		propertySources.addLast(new MapPropertySource("disable.dynamodb.autoconfiguration",
				Map.of("spring.cloud.aws.dynamodb.enabled", false)));
	}

}
