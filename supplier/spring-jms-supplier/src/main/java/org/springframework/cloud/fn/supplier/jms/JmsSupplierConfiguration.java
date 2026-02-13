/*
 * Copyright 2016-present the original author or authors.
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

package org.springframework.cloud.fn.supplier.jms;

import java.util.function.Supplier;

import jakarta.jms.ConnectionFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jms.autoconfigure.AcknowledgeMode;
import org.springframework.boot.jms.autoconfigure.JmsAutoConfiguration;
import org.springframework.boot.jms.autoconfigure.JmsProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.JavaUtils;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.jms.dsl.Jms;
import org.springframework.integration.jms.dsl.JmsMessageDrivenChannelAdapterSpec;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for JMS supplier.
 *
 * @author Gary Russell
 * @author Soby Chako
 * @author Artem Bilan
 */
@AutoConfiguration(after = JmsAutoConfiguration.class)
@EnableConfigurationProperties(JmsSupplierProperties.class)
public class JmsSupplierConfiguration {

	@Autowired
	JmsSupplierProperties properties;

	@Autowired
	private JmsProperties jmsProperties;

	@Autowired
	private ConnectionFactory connectionFactory;

	@Bean
	public Supplier<Flux<Message<?>>> jmsSupplier(@Qualifier("jmsPublisher") Publisher<Message<byte[]>> jmsPublisher) {
		return () -> Flux.from(jmsPublisher);
	}

	@Bean
	public Publisher<Message<byte[]>> jmsPublisher(
			@Qualifier("jmsContainer") AbstractMessageListenerContainer container,
			@Nullable ComponentCustomizer<JmsMessageDrivenChannelAdapterSpec<?>> jmsMessageDrivenChannelAdapterSpecCustomizer) {

		JmsMessageDrivenChannelAdapterSpec<?> messageProducerSpec = Jms.messageDrivenChannelAdapter(container);

		if (jmsMessageDrivenChannelAdapterSpecCustomizer != null) {
			jmsMessageDrivenChannelAdapterSpecCustomizer.customize(messageProducerSpec);
		}

		return IntegrationFlow.from(messageProducerSpec).toReactivePublisher(true);
	}

	@Bean
	public AbstractMessageListenerContainer jmsContainer() {
		AbstractMessageListenerContainer container;

		JmsProperties.Listener listenerProperties = this.jmsProperties.getListener();
		Integer concurrency = listenerProperties.getMinConcurrency();
		if (this.properties.isSessionTransacted()) {
			DefaultMessageListenerContainer dmlc = new DefaultMessageListenerContainer();
			dmlc.setSessionTransacted(true);
			if (concurrency != null) {
				dmlc.setConcurrentConsumers(concurrency);
			}
			Integer maxConcurrency = listenerProperties.getMaxConcurrency();
			if (maxConcurrency != null) {
				dmlc.setMaxConcurrentConsumers(maxConcurrency);
			}
			container = dmlc;
		}
		else {
			SimpleMessageListenerContainer smlc = new SimpleMessageListenerContainer();
			smlc.setSessionTransacted(false);
			if (concurrency != null) {
				smlc.setConcurrentConsumers(concurrency);
			}
			container = smlc;
		}

		container.setConnectionFactory(this.connectionFactory);
		container.setDestinationName(this.properties.getDestination());
		container.setPubSubDomain(this.jmsProperties.isPubSubDomain());

		String messageSelector = this.properties.getMessageSelector();
		AcknowledgeMode acknowledgeMode = listenerProperties.getSession().getAcknowledgeMode();
		if (messageSelector != null) {
			container.setSessionAcknowledgeMode(acknowledgeMode.getMode());
		}

		JavaUtils.INSTANCE.acceptIfNotNull(this.properties.getClientId(), container::setClientId)
			.acceptIfNotNull(messageSelector, container::setMessageSelector)
			.acceptIfNotNull(this.properties.getSubscriptionDurable(), container::setSubscriptionDurable)
			.acceptIfNotNull(this.properties.getSubscriptionName(), container::setSubscriptionName)
			.acceptIfNotNull(this.properties.getSubscriptionShared(), container::setSubscriptionShared);

		return container;
	}

}
