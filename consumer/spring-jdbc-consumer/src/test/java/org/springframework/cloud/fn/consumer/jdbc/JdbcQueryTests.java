/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.fn.consumer.jdbc;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

/**
 * @author Max Brauer
 */
@SuppressWarnings("unchecked")
class JdbcQueryTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(JdbcConsumerApplicationTests.JdbcConsumerTestApplication.class)
		.withPropertyValues("spring.datasource.generate-unique-name=true", "spring.sql.init.mode=never");

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void shouldInsertWhenQueryIsBlank(String query) {
		// String property pairs trim whitespace; use a map to retain the blank query.
		this.contextRunner.withPropertyValues("jdbc.consumer.initialize=true")
			.withInitializer((context) -> TestPropertyValues.of(Map.of("jdbc.consumer.query", query)).applyTo(context))
			.run((context) -> {
				Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);
				JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

				consumer.accept(MessageBuilder.withPayload("hello").build());

				assertThat(context.getBean(JdbcConsumerProperties.class).getQuery()).isEqualTo(query);
				assertThat(jdbc.queryForObject("SELECT payload FROM messages", String.class)).isEqualTo("hello");
			});
	}

	@Test
	void shouldUpdateRowWhenKeyMatches() {
		updateRunner().run((context) -> {
			JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
			jdbc.execute("CREATE TABLE people (id INTEGER PRIMARY KEY, name VARCHAR(40))");
			jdbc.update("INSERT INTO people VALUES (?, ?)", 1, "Josiah");
			Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);

			consumer.accept(MessageBuilder.withPayload("Josiah Imani").setHeader("personId", 1).build());

			assertThat(jdbc.queryForObject("SELECT name FROM people", String.class)).isEqualTo("Josiah Imani");
		});
	}

	@Test
	void shouldNotInsertRowWhenKeyIsMissing() {
		updateRunner().run((context) -> {
			JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
			jdbc.execute("CREATE TABLE people (id INTEGER PRIMARY KEY, name VARCHAR(40))");
			jdbc.update("INSERT INTO people VALUES (?, ?)", 1, "Josiah");
			Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);

			consumer.accept(MessageBuilder.withPayload("Bob").setHeader("personId", 2).build());

			assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT name FROM people", String.class)).isEqualTo("Josiah");
		});
	}

	@Test
	void shouldApplyIncrementAgainWhenMessageIsReplayed() {
		this.contextRunner
			.withPropertyValues("jdbc.consumer.columns=id,increment:payload.increment",
					"jdbc.consumer.query=UPDATE counters SET total=total+:increment WHERE id=:id")
			.run((context) -> {
				JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
				jdbc.execute("CREATE TABLE counters (id INTEGER PRIMARY KEY, total INTEGER)");
				jdbc.update("INSERT INTO counters VALUES (?, ?)", 1, 0);
				Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);
				Message<?> message = MessageBuilder.withPayload(Map.of("id", 1, "increment", 1)).build();

				consumer.accept(message);
				consumer.accept(message);

				assertThat(jdbc.queryForObject("SELECT total FROM counters", Integer.class)).isEqualTo(2);
			});
	}

	@Test
	void shouldWriteJsonBytePayloadsWhenBatchIsFull() {
		this.contextRunner
			.withPropertyValues("jdbc.consumer.columns=id,name", "jdbc.consumer.batch-size=2",
					"jdbc.consumer.query=INSERT INTO people (id,name) VALUES (:id,:name)")
			.run((context) -> {
				JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
				jdbc.execute("CREATE TABLE people (id INTEGER PRIMARY KEY, name VARCHAR(40))");
				Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);

				consumer.accept(MessageBuilder.withPayload("{\"id\":1,\"name\":\"Josiah\"}".getBytes(UTF_8)).build());

				assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class)).isZero();

				consumer.accept(MessageBuilder.withPayload("{\"id\":2,\"name\":\"Bob\"}".getBytes(UTF_8)).build());

				assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class)).isEqualTo(2);
			});
	}

	@Test
	void shouldWritePartialBatchWhenIdleTimeoutExpires() {
		this.contextRunner.withPropertyValues("jdbc.consumer.columns=id,name", "jdbc.consumer.batch-size=10",
				"jdbc.consumer.idle-timeout=100", "jdbc.consumer.query=INSERT INTO people (id,name) VALUES (:id,:name)")
			.run((context) -> {
				JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
				jdbc.execute("CREATE TABLE people (id INTEGER PRIMARY KEY, name VARCHAR(40))");
				Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);

				consumer.accept(MessageBuilder.withPayload(Map.of("id", 1, "name", "Josiah")).build());

				await().atMost(Duration.ofSeconds(10))
					.untilAsserted(() -> assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class))
						.isEqualTo(1));
			});
	}

	@Test
	void shouldPropagateFailureWhenQueryParameterHasNoMapping() {
		this.contextRunner
			.withPropertyValues("jdbc.consumer.initialize=true",
					"jdbc.consumer.query=INSERT INTO messages (payload) VALUES (:missing)")
			.run((context) -> {
				Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);
				JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

				assertThatExceptionOfType(InvalidDataAccessApiUsageException.class)
					.isThrownBy(() -> consumer.accept(MessageBuilder.withPayload("hello").build()))
					.withStackTraceContaining("missing");

				assertThat(jdbc.queryForObject("SELECT count(*) FROM messages", Integer.class)).isZero();
			});
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void shouldLogWriteFailureWhenIdleTimeoutFlushesBatch(CapturedOutput output) {
		this.contextRunner
			.withPropertyValues("jdbc.consumer.initialize=true", "jdbc.consumer.batch-size=10",
					"jdbc.consumer.idle-timeout=100",
					"jdbc.consumer.query=INSERT INTO missing_table (payload) VALUES (:payload)")
			.run((context) -> {
				Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);
				JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

				// The call returns before the partial batch is flushed and the SQL fails.
				consumer.accept(MessageBuilder.withPayload("hello").build());

				await().atMost(Duration.ofSeconds(10))
					.untilAsserted(
							() -> assertThat(output.getAll()).contains("Unexpected error occurred in scheduled task",
									"INSERT INTO missing_table (payload) VALUES (?)"));
				assertThat(jdbc.queryForObject("SELECT count(*) FROM messages", Integer.class)).isZero();
			});
	}

	private ApplicationContextRunner updateRunner() {
		return this.contextRunner.withPropertyValues("jdbc.consumer.columns=key:headers['personId'],personName:payload",
				// Configured SQL selects its table independently of table-name.
				"jdbc.consumer.table-name=unused",
				"jdbc.consumer.query=UPDATE people SET name=:personName WHERE id=:key");
	}

}
