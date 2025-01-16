/*
 * Copyright 2019-2025 the original author or authors.
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

package org.springframework.cloud.fn.supplier.mongo;

import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.fn.consumer.mongo.MongoDbTestContainerSupport;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@SpringBootTest(
		properties = { "mongodb.supplier.collection=testing", "mongodb.supplier.query={ name: { $exists: true }}",
				"mongodb.supplier.update-expression='{ $unset: { name: 0 } }'" })
@DirtiesContext
class MongodbSupplierApplicationTests implements MongoDbTestContainerSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private Supplier<Flux<Message<?>>> mongodbSupplier;

	@Autowired
	private MongoClient mongo;

	@BeforeEach
	void setUp() {
		MongoDatabase database = this.mongo.getDatabase("test");
		database.createCollection("testing");
		MongoCollection<Document> collection = database.getCollection("testing");
		collection.insertOne(new Document("greeting", "hello").append("name", "foo"));
		collection.insertOne(new Document("greeting", "hola").append("name", "bar"));
	}

	@Test
	void testMongodbSupplier() {
		// given
		Flux<Message<?>> messageFlux = this.mongodbSupplier.get();
		// when
		StepVerifier.create(messageFlux)
			// then
			.assertNext(
					(message) -> assertThat(toMap(message)).contains(entry("greeting", "hello"), entry("name", "foo")))
			.assertNext(
					(message) -> assertThat(toMap(message)).contains(entry("greeting", "hola"), entry("name", "bar")))
			.thenCancel()
			.verify();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(Message<?> message) {
		Map<String, Object> map = null;
		try {
			map = objectMapper.readValue(message.getPayload().toString(), Map.class);
		}
		catch (Exception ex) {
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		return map;
	}

	@SpringBootApplication
	static class MongoDbSupplierTestApplication {

	}

}
