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

package org.springframework.cloud.fn.filter;

import java.util.function.Function;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.transformer.ExpressionEvaluatingTransformer;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for Filter function.
 *
 * @author Artem Bilan
 * @author David Turanski
 */
@AutoConfiguration
@EnableConfigurationProperties(FilterFunctionProperties.class)
public class FilterFunctionConfiguration {

	@Bean
	public Function<Message<?>, Message<?>> filterFunction(
			ExpressionEvaluatingTransformer filterExpressionEvaluatingTransformer) {

		return (message) -> {
			if ((Boolean) filterExpressionEvaluatingTransformer.transform(message).getPayload()) {
				return message;
			}
			else {
				return null;
			}
		};
	}

	@Bean
	public ExpressionEvaluatingTransformer filterExpressionEvaluatingTransformer(
			FilterFunctionProperties filterFunctionProperties) {

		return new ExpressionEvaluatingTransformer(filterFunctionProperties.getExpression());
	}

}
