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

package org.springframework.cloud.fn.spel;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.transformer.ExpressionEvaluatingTransformer;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for SpEL function.
 *
 * @author Soby Chacko
 */
@AutoConfiguration
@EnableConfigurationProperties(SpelFunctionProperties.class)
public class SpelFunctionConfiguration {

	@Bean
	public Function<Message<?>, Message<?>> spelFunction(
			@Qualifier("expressionEvaluatingTransformer") ExpressionEvaluatingTransformer expressionEvaluatingTransformer) {

		return expressionEvaluatingTransformer::transform;
	}

	@Bean
	public ExpressionEvaluatingTransformer expressionEvaluatingTransformer(
			SpelFunctionProperties spelFunctionProperties) {

		return new ExpressionEvaluatingTransformer(spelFunctionProperties.getExpression());
	}

}
