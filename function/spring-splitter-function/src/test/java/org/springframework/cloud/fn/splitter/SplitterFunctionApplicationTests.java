/*
 * Copyright 2011-present the original author or authors.
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

package org.springframework.cloud.fn.splitter;

import java.time.Duration;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "splitter.expression=payload.split(',')")
@DirtiesContext
public class SplitterFunctionApplicationTests {

	@Autowired
	Function<Flux<Message<?>>, Flux<Message<?>>> splitter;

	@Test
	public void testExpressionSplitter() {
		Flux<Message<?>> messageFlux = this.splitter.apply(Flux.just(new GenericMessage<>("hello,world")));
		Flux<String> payloads = messageFlux.map(Message::getPayload).map(Object::toString);
		StepVerifier.create(payloads).expectNext("hello", "world").thenCancel().verify(Duration.ofSeconds(30));
	}

	@SpringBootApplication
	static class SplitterFunctionTestApplication {

	}

}
