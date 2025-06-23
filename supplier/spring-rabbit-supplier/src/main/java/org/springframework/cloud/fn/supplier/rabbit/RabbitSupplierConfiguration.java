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

package org.springframework.cloud.fn.supplier.rabbit;

import java.util.function.Supplier;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.amqp.dsl.AmqpInboundChannelAdapterSMLCSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.util.Assert;

/**
 * Auto-configuration for supplier that receives data from RabbitMQ.
 *
 * @author Gary Russell
 * @author Chris Schaefer
 * @author Roger Perez
 * @author Chris Bono
 * @author Artem Bilan
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@EnableConfigurationProperties(RabbitSupplierProperties.class)
public class RabbitSupplierConfiguration {

	private static final MessagePropertiesConverter INBOUND_MESSAGE_PROPERTIES_CONVERTER = new DefaultMessagePropertiesConverter() {

		@Override
		public MessageProperties toMessageProperties(AMQP.BasicProperties source, Envelope envelope, String charset) {

			MessageProperties properties = super.toMessageProperties(source, envelope, charset);
			properties.setDeliveryMode(null);
			return properties;
		}

	};

	@Bean
	public SimpleMessageListenerContainer container(RabbitProperties rabbitProperties,
			RabbitSupplierProperties rabbitSupplierProperties, ConnectionFactory connectionFactory,
			RetryOperationsInterceptor rabbitSourceRetryInterceptor) {

		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
		container.setAutoStartup(false);
		RabbitProperties.SimpleContainer simpleContainer = rabbitProperties.getListener().getSimple();

		AcknowledgeMode acknowledgeMode = simpleContainer.getAcknowledgeMode();
		if (acknowledgeMode != null) {
			container.setAcknowledgeMode(acknowledgeMode);
		}
		Integer concurrency = simpleContainer.getConcurrency();
		if (concurrency != null) {
			container.setConcurrentConsumers(concurrency);
		}
		Integer maxConcurrency = simpleContainer.getMaxConcurrency();
		if (maxConcurrency != null) {
			container.setMaxConcurrentConsumers(maxConcurrency);
		}
		Integer prefetch = simpleContainer.getPrefetch();
		if (prefetch != null) {
			container.setPrefetchCount(prefetch);
		}
		Integer transactionSize = simpleContainer.getBatchSize();
		if (transactionSize != null) {
			container.setBatchSize(transactionSize);
		}
		container.setDefaultRequeueRejected(rabbitSupplierProperties.getRequeue());
		container.setChannelTransacted(rabbitSupplierProperties.getTransacted());
		String[] queues = rabbitSupplierProperties.getQueues();
		Assert.state(queues.length > 0, "At least one queue is required");
		Assert.noNullElements(queues, "queues cannot have null elements");
		container.setQueueNames(queues);
		if (rabbitSupplierProperties.isEnableRetry()) {
			container.setAdviceChain(rabbitSourceRetryInterceptor);
		}
		container.setMessagePropertiesConverter(INBOUND_MESSAGE_PROPERTIES_CONVERTER);
		return container;
	}

	@Bean
	public Publisher<Message<byte[]>> rabbitPublisher(SimpleMessageListenerContainer container,
			RabbitSupplierProperties rabbitSupplierProperties,
			@Nullable ComponentCustomizer<AmqpInboundChannelAdapterSMLCSpec> amqpMessageProducerCustomizer) {

		AmqpInboundChannelAdapterSMLCSpec messageProducerSpec = Amqp.inboundAdapter(container)
			.mappedRequestHeaders(rabbitSupplierProperties.getMappedRequestHeaders());

		if (amqpMessageProducerCustomizer != null) {
			amqpMessageProducerCustomizer.customize(messageProducerSpec);
		}

		return IntegrationFlow.from(messageProducerSpec).toReactivePublisher(true);
	}

	@Bean
	public Supplier<Flux<Message<byte[]>>> rabbitSupplier(Publisher<Message<byte[]>> rabbitPublisher) {
		return () -> Flux.from(rabbitPublisher);
	}

	@Bean
	public RetryOperationsInterceptor rabbitSourceRetryInterceptor(RabbitSupplierProperties rabbitSupplierProperties) {
		return RetryInterceptorBuilder.stateless()
			.maxAttempts(rabbitSupplierProperties.getMaxAttempts())
			.backOffOptions(rabbitSupplierProperties.getInitialRetryInterval(),
					rabbitSupplierProperties.getRetryMultiplier(), rabbitSupplierProperties.getMaxRetryInterval())
			.recoverer(new RejectAndDontRequeueRecoverer())
			.build();
	}

}
