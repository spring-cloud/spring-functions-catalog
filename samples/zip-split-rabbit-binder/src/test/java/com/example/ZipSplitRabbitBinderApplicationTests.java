package com.example;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "file.supplier.directory=classpath:/dirWithZips")
@DirtiesContext
@Testcontainers(disabledWithoutDocker = true)
class ZipSplitRabbitBinderApplicationTests {

	static Log LOG = LogFactory.getLog(ZipSplitRabbitBinderApplicationTests.class);

	static BlockingQueue<String> DATA_SINK = new LinkedBlockingQueue<>();

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitContainer = new RabbitMQContainer(DockerImageName.parse("rabbitmq:latest"));

	@Test
	void zippedFilesAreSplittedToRabbitBinding() throws InterruptedException {
		List<String> expected = List.of("data111", "data112", "data113", "data121", "data122", "data123", "data124",
				"data211", "data212", "data221", "data231", "data232", "data233", "data234", "data235");

		for (int i = 0; i < expected.size(); i++) {
			assertThat(DATA_SINK.poll(10, TimeUnit.SECONDS)).isIn(expected);
		}
	}

	@TestConfiguration
	static class RabbitListenerTestConfiguration {

		@RabbitListener(bindings = @QueueBinding(value = @Queue,
				exchange = @Exchange(value = "unzipped_data_exchange", type = ExchangeTypes.TOPIC), key = "#"))
		void receiveDataFromSplittedZips(String payload) {
			LOG.info("A line from zip entry: " + payload);
			DATA_SINK.offer(payload);
		}

	}

}
