package com.example;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(ZipSplitRabbitBinderApplicationTests.TestConfiguration.class)
@SpringBootTest
@DirtiesContext
class ZipSplitRabbitBinderApplicationTests {

	static Log LOG = LogFactory.getLog(ZipSplitRabbitBinderApplicationTests.class);

	static BlockingQueue<String> DATA_SINK = new LinkedBlockingQueue<>();

	@DynamicPropertySource
	static void testProperties(DynamicPropertyRegistry registry) {
		registry.add("file.supplier.directory", () -> new ClassPathResource("/dirWithZips").getPath());
	}

	@Test
	void zippedFilesAreSplittedToRabbitBinding() throws InterruptedException {
		List<String> expected = List.of("data111", "data112", "data113", "data121", "data122", "data123", "data124",
				"data211", "data212", "data221", "data231", "data232", "data233", "data234", "data235");

		for (int i = 0; i < expected.size(); i++) {
			assertThat(DATA_SINK.poll(10, TimeUnit.SECONDS)).isIn(expected);
		}
	}

	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class TestConfiguration {

		@Bean
		@ServiceConnection
		RabbitMQContainer rabbitContainer() {
			return new RabbitMQContainer(DockerImageName.parse("rabbitmq:latest"));
		}

		@RabbitListener(bindings = @QueueBinding(value = @Queue,
				exchange = @Exchange(value = "unzipped_data_exchange", type = ExchangeTypes.TOPIC), key = "#"))
		void receiveDataFromSplittedZips(String payload) {
			LOG.info("A line from zip entry: " + payload);
			DATA_SINK.offer(payload);
		}

	}

}
