/*
 * Copyright 2018-present the original author or authors.
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

package org.springframework.cloud.fn.supplier.websocket;

import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.websocket.autoconfigure.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.websocket.IntegrationWebSocketContainer;
import org.springframework.integration.websocket.ServerWebSocketContainer;
import org.springframework.integration.websocket.inbound.WebSocketInboundChannelAdapter;
import org.springframework.messaging.Message;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Auto-configuration for supplier that receives data over WebSocket.
 *
 * @author Krishnaprasad A S
 * @author Artem Bilan
 *
 */
@AutoConfiguration(before = { WebSocketMessagingAutoConfiguration.class, SecurityAutoConfiguration.class })
@EnableConfigurationProperties(WebsocketSupplierProperties.class)
public class WebsocketSupplierConfiguration {

	@Bean
	public Supplier<Flux<Message<?>>> websocketSupplier(
			@Qualifier("websocketPublisher") Publisher<Message<byte[]>> websocketPublisher) {

		return () -> Flux.from(websocketPublisher);
	}

	@Bean
	public Publisher<Message<byte[]>> websocketPublisher(
			@Qualifier("webSocketInboundChannelAdapter") WebSocketInboundChannelAdapter webSocketInboundChannelAdapter) {

		return IntegrationFlow.from(webSocketInboundChannelAdapter).toReactivePublisher(true);
	}

	@Bean
	public WebSocketInboundChannelAdapter webSocketInboundChannelAdapter(
			@Qualifier("serverWebSocketContainer") IntegrationWebSocketContainer serverWebSocketContainer) {

		return new WebSocketInboundChannelAdapter(serverWebSocketContainer);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "websocket.sockJs", name = "enable", havingValue = "true")
	public ServerWebSocketContainer.SockJsServiceOptions sockJsServiceOptions() {
		// TODO Expose SockJsServiceOptions as configuration properties
		return new ServerWebSocketContainer.SockJsServiceOptions();
	}

	@Bean
	public IntegrationWebSocketContainer serverWebSocketContainer(WebsocketSupplierProperties properties,
			ObjectProvider<ServerWebSocketContainer.SockJsServiceOptions> sockJsServiceOptions) {

		return new ServerWebSocketContainer(properties.getPath()).setAllowedOrigins(properties.getAllowedOrigins())
			.withSockJs(sockJsServiceOptions.getIfAvailable())
			.setHandshakeHandler(new DefaultHandshakeHandler());
	}

}
