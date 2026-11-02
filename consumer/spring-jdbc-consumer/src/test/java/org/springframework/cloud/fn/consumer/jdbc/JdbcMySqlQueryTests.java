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

import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Max Brauer
 */
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("unchecked")
class JdbcMySqlQueryTests {

	private static final String MYSQL_UPSERT = """
			INSERT INTO people (id,name) VALUES (:id,:name) \
			ON DUPLICATE KEY UPDATE name=:name""";

	@Container
	private static final MySQLContainer MYSQL = new MySqlFixture();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(JdbcConsumerApplicationTests.JdbcConsumerTestApplication.class)
		.withPropertyValues("spring.sql.init.mode=never", "jdbc.consumer.columns=id,name");

	@Test
	void shouldKeepOnePersonWhenAnUpsertIsReplayed() {
		mysqlRunner().withPropertyValues("jdbc.consumer.query=" + MYSQL_UPSERT).run((context) -> {
			JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
			createPeople(jdbc, "id INTEGER PRIMARY KEY");
			Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);

			consumer.accept(person("Josiah"));

			assertThat(jdbc.queryForObject("SELECT name FROM people", String.class)).isEqualTo("Josiah");

			consumer.accept(person("Josiah Imani"));
			consumer.accept(person("Josiah Imani"));

			assertThat(jdbc.queryForObject("SELECT name FROM people", String.class)).isEqualTo("Josiah Imani");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class)).isEqualTo(1);
		});
	}

	@Test
	void shouldUpsertRepeatedKeyWithinOneBatch() {
		mysqlRunner().withPropertyValues("jdbc.consumer.batch-size=2", "jdbc.consumer.query=" + MYSQL_UPSERT)
			.run((context) -> {
				JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
				createPeople(jdbc, "id INTEGER PRIMARY KEY");
				Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);

				consumer.accept(person("Josiah"));

				assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class)).isZero();

				consumer.accept(person("Josiah Imani"));

				assertThat(jdbc.queryForObject("SELECT name FROM people", String.class)).isEqualTo("Josiah Imani");
				assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class)).isEqualTo(1);
			});
	}

	@Test
	void shouldInsertDuplicateWhenTableHasNoUniqueKey() {
		mysqlRunner().withPropertyValues("jdbc.consumer.query=" + MYSQL_UPSERT).run((context) -> {
			JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
			createPeople(jdbc, "id INTEGER");
			Consumer<Message<?>> consumer = context.getBean("jdbcConsumer", Consumer.class);

			consumer.accept(person("Josiah"));
			consumer.accept(person("Josiah Imani"));

			assertThat(jdbc.queryForObject("SELECT count(*) FROM people", Integer.class)).isEqualTo(2);
		});
	}

	private ApplicationContextRunner mysqlRunner() {
		return this.contextRunner.withPropertyValues("spring.datasource.url=" + MYSQL.getJdbcUrl(),
				"spring.datasource.username=" + MYSQL.getUsername(),
				"spring.datasource.password=" + MYSQL.getPassword(),
				"spring.datasource.driver-class-name=org.mariadb.jdbc.Driver");
	}

	private static void createPeople(JdbcTemplate jdbc, String idColumn) {
		jdbc.execute("DROP TABLE IF EXISTS people");
		jdbc.execute("CREATE TABLE people (" + idColumn + ", name VARCHAR(40))");
	}

	private static Message<?> person(String name) {
		return MessageBuilder.withPayload(Map.of("id", 1, "name", name)).build();
	}

	private static final class MySqlFixture extends MySQLContainer {

		private MySqlFixture() {
			super("mysql:8.0");
			withUrlParam("permitMysqlScheme", "true");
			withUrlParam("sslMode", "trust");
		}

		@Override
		public String getDriverClassName() {
			return "org.mariadb.jdbc.Driver";
		}

	}

}
