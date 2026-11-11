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

package org.springframework.cloud.fn.supplier.zeromq;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Daniel Frey
 * @author Artem Bilan
 * @since 3.1.0
 */
@SpringBootTest(properties = { "zeromq.supplier.topics=test-topic" })
@DirtiesContext
public class ZeroMqSupplierConfigurationTests {

	private static final ZContext CONTEXT = new ZContext();

	private static ZMQ.Socket socket;

	private static int bindPort;

	@Autowired
	Supplier<Flux<Message<?>>> subject;

	@BeforeAll
	static void setup() {

		socket = CONTEXT.createSocket(SocketType.XPUB);
		socket.setReceiveTimeOut(30_000);
		bindPort = socket.bindToRandomPort("tcp://*");
	}

	@DynamicPropertySource
	static void zeromqConnectUrl(DynamicPropertyRegistry dynamicPropertyRegistry) {
		dynamicPropertyRegistry.add("zeromq.supplier.connectUrl", () -> "tcp://localhost:" + bindPort);
	}

	@AfterAll
	static void tearDown() {
		socket.close();
		CONTEXT.close();
	}

	@Test
	void testSubscriptionConfiguration() throws Exception {
		CompletableFuture<Message<?>> received = this.subject.get().next().toFuture();

		// XPUB emits the subscription frame once the SUB is connected and subscribed,
		// so a single publish afterward is not lost to the "slow joiner"
		byte[] subscription = socket.recv();
		assertThat(subscription).as("The SUB socket has not subscribed").isNotNull();
		assertThat(subscription[0]).isEqualTo((byte) 1);
		assertThat(new String(subscription, 1, subscription.length - 1, ZMQ.CHARSET)).isEqualTo("test-topic");

		ZMsg msg = ZMsg.newStringMsg("test");
		msg.wrap(new ZFrame("test-topic"));
		msg.send(socket);

		assertThat(received.get(30, TimeUnit.SECONDS).getPayload())
			.asInstanceOf(InstanceOfAssertFactories.type(byte[].class))
			.isEqualTo("test".getBytes(ZMQ.CHARSET));
	}

	@SpringBootApplication
	public static class ZeroMqSourceTestApplication {

	}

}
