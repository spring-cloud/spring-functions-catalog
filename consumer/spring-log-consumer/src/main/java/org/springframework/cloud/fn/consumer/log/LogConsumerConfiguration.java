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

package org.springframework.cloud.fn.consumer.log;

import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.gateway.AnnotationGatewayProxyFactoryBean;
import org.springframework.messaging.Message;

/**
 * The Auto-configuration class for {@link Consumer} which logs incoming data. For the
 * logging logic a Spring Integration
 * {@link org.springframework.integration.handler.LoggingHandler} is used. If incoming
 * payload is a {@code byte[]} and incoming message {@code contentType} header is
 * text-compatible (e.g. {@code application/json}), it is converted into a {@link String}.
 * Otherwise, the payload is passed to logger as is.
 *
 * @author Artem Bilan
 */
@AutoConfiguration
@EnableConfigurationProperties(LogConsumerProperties.class)
public class LogConsumerConfiguration {

	@Bean
	IntegrationFlow logConsumerFlow(LogConsumerProperties logSinkProperties) {
		return (flow) -> flow
			.log(logSinkProperties.getLevel(), logSinkProperties.getName(), logSinkProperties.getExpression())
			.nullChannel();
	}

	@Bean
	@SuppressWarnings({ "unchecked", "rawtypes" })
	AnnotationGatewayProxyFactoryBean<Consumer<Message<?>>> logConsumer() {
		var gatewayProxyFactoryBean = new AnnotationGatewayProxyFactoryBean<>(Consumer.class);
		gatewayProxyFactoryBean.setDefaultRequestChannelName("logConsumerFlow.input");
		return (AnnotationGatewayProxyFactoryBean) gatewayProxyFactoryBean;
	}

}
