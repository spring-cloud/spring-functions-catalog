/*
 * Copyright 2014-present the original author or authors.
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

package org.springframework.cloud.fn.consumer.xmpp;

import java.util.function.Consumer;

import org.jivesoftware.smack.XMPPConnection;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.xmpp.XmppConnectionFactoryConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.xmpp.XmppHeaders;
import org.springframework.integration.xmpp.outbound.ChatMessageSendingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * The XMPP consumer auto-configuration.
 *
 * @author Daniel Frey
 * @since 4.0.0
 */
@AutoConfiguration(after = XmppConnectionFactoryConfiguration.class)
@EnableConfigurationProperties(XmppConsumerProperties.class)
public class XmppConsumerConfiguration {

	@Bean
	public ChatMessageSendingMessageHandler chatMessageSendingMessageHandler(XMPPConnection xmppConnection) {
		return new ChatMessageSendingMessageHandler(xmppConnection);
	}

	@Bean
	public Consumer<Message<?>> xmppConsumer(ChatMessageSendingMessageHandler chatMessageSendingMessageHandler,
			XmppConsumerProperties properties) {

		return (message) -> {

			var send = MessageBuilder.fromMessage(message)
				.setHeaderIfAbsent(XmppHeaders.TO, properties.getChatTo())
				.build();

			chatMessageSendingMessageHandler.handleMessage(send);

		};
	}

}
