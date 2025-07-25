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

package org.springframework.cloud.fn.supplier.xmpp;

import java.util.function.Supplier;

import org.jivesoftware.smack.XMPPConnection;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.xmpp.XmppConnectionFactoryConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.xmpp.inbound.ChatMessageListeningEndpoint;
import org.springframework.messaging.Message;

/**
 * A auto-configuration for XMPP supplier.
 *
 * @author Daniel Frey
 * @author Artem Bilan
 */
@AutoConfiguration(after = XmppConnectionFactoryConfiguration.class)
@EnableConfigurationProperties(XmppSupplierProperties.class)
public class XmppSupplierConfiguration {

	@Bean
	public ChatMessageListeningEndpoint chatMessageListeningEndpoint(XMPPConnection xmppConnection,
			XmppSupplierProperties properties) {

		var chatMessageListeningEndpoint = new ChatMessageListeningEndpoint(xmppConnection);

		if (properties.getPayloadExpression() != null) {
			chatMessageListeningEndpoint.setPayloadExpression(properties.getPayloadExpression());
		}

		chatMessageListeningEndpoint.setStanzaFilter(properties.getStanzaFilter());
		return chatMessageListeningEndpoint;
	}

	@Bean
	Publisher<Message<Object>> xmppSupplierFlow(ChatMessageListeningEndpoint chatMessageListeningEndpoint) {
		return IntegrationFlow.from(chatMessageListeningEndpoint).toReactivePublisher(true);
	}

	@Bean
	public Supplier<Flux<Message<?>>> xmppSupplier(Publisher<Message<Object>> xmppSupplierFlow) {
		return () -> Flux.from(xmppSupplierFlow);
	}

}
