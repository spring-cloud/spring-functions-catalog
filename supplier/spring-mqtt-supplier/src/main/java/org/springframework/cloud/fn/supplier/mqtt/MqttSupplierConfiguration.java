/*
 * Copyright 2017-present the original author or authors.
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

package org.springframework.cloud.fn.supplier.mqtt;

import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.cloud.fn.common.mqtt.MqttConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * A supplier that receives data from MQTT.
 *
 * @author Janne Valkealahti
 * @author Soby Chacko
 * @author Artem Bilan
 */
@EnableConfigurationProperties(MqttSupplierProperties.class)
@AutoConfiguration(after = MqttConfiguration.class)
public class MqttSupplierConfiguration {

	@Bean
	public Supplier<Flux<Message<?>>> mqttSupplier(
			@Qualifier("mqttPublisher") Publisher<Message<byte[]>> mqttPublisher) {

		return () -> Flux.from(mqttPublisher);
	}

	@Bean
	public MqttPahoMessageDrivenChannelAdapter mqttInbound(MqttSupplierProperties properties,
			MqttPahoClientFactory mqttClientFactory, BeanFactory beanFactory,
			@Nullable ComponentCustomizer<MqttPahoMessageDrivenChannelAdapter> mqttMessageProducerCustomizer) {

		MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(properties.getClientId(),
				mqttClientFactory, properties.getTopics());
		adapter.setQos(properties.getQos());
		adapter.setConverter(pahoMessageConverter(properties, beanFactory));

		if (mqttMessageProducerCustomizer != null) {
			mqttMessageProducerCustomizer.customize(adapter);
		}

		return adapter;
	}

	@Bean
	public Publisher<Message<byte[]>> mqttPublisher(
			@Qualifier("mqttInbound") MqttPahoMessageDrivenChannelAdapter mqttInbound) {

		return IntegrationFlow.from(mqttInbound).toReactivePublisher(true);
	}

	private static DefaultPahoMessageConverter pahoMessageConverter(MqttSupplierProperties properties,
			BeanFactory beanFactory) {

		DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter(properties.getCharset());
		converter.setPayloadAsBytes(properties.isBinary());
		converter.setBeanFactory(beanFactory);
		return converter;
	}

}
