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

package org.springframework.cloud.fn.consumer.rabbit;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.Expression;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.amqp.dsl.AmqpOutboundChannelAdapterSpec;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

/**
 * Auto-configuration for RabbitMQ Consumer function. Uses a
 * {@link AmqpOutboundChannelAdapterSpec} to save payload contents to RabbitMQ.
 *
 * @author Soby Chako
 * @author Nicolas Labrot
 * @author Chris Bono
 * @author Artem Bilan
 */
@EnableConfigurationProperties(RabbitConsumerProperties.class)
@AutoConfiguration(after = RabbitAutoConfiguration.class)
public class RabbitConsumerConfiguration {

	@Autowired
	private RabbitConsumerProperties properties;

	@Bean
	public Consumer<Message<?>> rabbitConsumer(@Qualifier("amqpChannelAdapter") MessageHandler messageHandler) {
		return messageHandler::handleMessage;
	}

	@Bean
	public AmqpOutboundChannelAdapterSpec amqpChannelAdapter(RabbitTemplate rabbitTemplate,
			@Nullable ComponentCustomizer<AmqpOutboundChannelAdapterSpec> amqpOutboundChannelAdapterSpecCustomizer) {

		AmqpOutboundChannelAdapterSpec handler = Amqp.outboundAdapter(rabbitTemplate)
			.mappedRequestHeaders(this.properties.getMappedRequestHeaders())
			.defaultDeliveryMode((this.properties.getPersistentDeliveryMode()) ? MessageDeliveryMode.PERSISTENT
					: MessageDeliveryMode.NON_PERSISTENT)
			.headersMappedLast(this.properties.isHeadersMappedLast());

		Expression exchangeExpression = this.properties.getExchangeExpression();
		if (exchangeExpression != null) {
			handler.exchangeNameExpression(exchangeExpression);
		}
		else {
			handler.exchangeName(this.properties.getExchange());
		}

		Expression routingKeyExpression = this.properties.getRoutingKeyExpression();
		if (routingKeyExpression != null) {
			handler.routingKeyExpression(routingKeyExpression);
		}
		else {
			handler.routingKey(this.properties.getRoutingKey());
		}

		if (amqpOutboundChannelAdapterSpecCustomizer != null) {
			amqpOutboundChannelAdapterSpecCustomizer.customize(handler);
		}

		return handler;
	}

	@Bean
	@ConditionalOnProperty(name = "rabbit.converterBeanName", havingValue = RabbitConsumerProperties.JSON_CONVERTER)
	public JacksonJsonMessageConverter jsonConverter() {
		return new JacksonJsonMessageConverter();
	}

}
