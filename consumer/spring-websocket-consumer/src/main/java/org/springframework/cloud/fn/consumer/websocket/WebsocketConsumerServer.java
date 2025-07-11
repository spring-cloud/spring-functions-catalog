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

package org.springframework.cloud.fn.consumer.websocket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Bootstraps a Netty server using the {@link WebsocketConsumerServerInitializer}. Also
 * adds a {@link LoggingHandler} and uses the <code>logLevel</code> from
 * {@link WebsocketConsumerProperties#logLevel}.
 *
 * @author Oliver Moser
 * @author Gary Russell
 * @author Chris Bono
 * @author Artem Bilan
 */
public class WebsocketConsumerServer {

	private static final Log LOGGER = LogFactory.getLog(WebsocketConsumerServer.class);

	static final List<Channel> CHANNELS = new CopyOnWriteArrayList<>();

	private final WebsocketConsumerProperties properties;

	private final WebsocketConsumerServerInitializer initializer;

	private EventLoopGroup bossGroup;

	private EventLoopGroup workerGroup;

	private int port;

	public WebsocketConsumerServer(WebsocketConsumerProperties properties,
			WebsocketConsumerServerInitializer initializer) {

		this.properties = properties;
		this.initializer = initializer;
	}

	public int getPort() {
		return this.port;
	}

	@PostConstruct
	public void init() {
		this.bossGroup = new NioEventLoopGroup(this.properties.getThreads());
		this.workerGroup = new NioEventLoopGroup();
	}

	@PreDestroy
	public void shutdown() {
		this.bossGroup.shutdownGracefully();
		this.workerGroup.shutdownGracefully();
	}

	public void run() throws InterruptedException {
		NioServerSocketChannel channel = (NioServerSocketChannel) new ServerBootstrap()
			.group(this.bossGroup, this.workerGroup)
			.channel(NioServerSocketChannel.class)
			.handler(new LoggingHandler(nettyLogLevel()))
			.childHandler(this.initializer)
			.bind(this.properties.getPort())
			.sync()
			.channel();
		this.port = channel.localAddress().getPort();
		dumpProperties();
	}

	private void dumpProperties() {
		LOGGER.info("███████████████████████████████████████████████████████████");
		LOGGER.info("                >> websocket-sink config <<                ");
		LOGGER.info("");
		LOGGER.info(String.format("port:     %s", this.port));
		LOGGER.info(String.format("ssl:               %s", this.properties.isSsl()));
		LOGGER.info(String.format("path:     %s", this.properties.getPath()));
		LOGGER.info(String.format("logLevel: %s", this.properties.getLogLevel()));
		LOGGER.info(String.format("threads:           %s", this.properties.getThreads()));
		LOGGER.info("");
		LOGGER.info("████████████████████████████████████████████████████████████");
	}

	//
	// HELPERS
	//
	private LogLevel nettyLogLevel() {
		return LogLevel.valueOf(this.properties.getLogLevel().toUpperCase());
	}

}
