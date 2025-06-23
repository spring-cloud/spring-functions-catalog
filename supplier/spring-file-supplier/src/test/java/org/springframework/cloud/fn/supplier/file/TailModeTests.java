/*
 * Copyright 2024-present the original author or authors.
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

package org.springframework.cloud.fn.supplier.file;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.integration.file.tail.FileTailingMessageProducerSupport;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;

/**
 * @author Artem Bilan
 */
public class TailModeTests extends AbstractFileSupplierTests {

	static Path tempFile;

	@BeforeAll
	static void init() throws IOException {
		tempFile = Files.createFile(tempDir.resolve("test.txt"));
		System.setProperty("file.supplier.tail", tempFile.toAbsolutePath().toString());
		System.setProperty("file.supplier.tailer.end", "false");
		System.setProperty("file.supplier.tailer.polling-delay", "100");
	}

	@Test
	void tailSupplierEmitsData(@Autowired FileTailingMessageProducerSupport fileTailingMessageProducer)
			throws IOException {

		SimpleAsyncTaskExecutor taskExecutor = TestUtils.getPropertyValue(fileTailingMessageProducer, "taskExecutor",
				SimpleAsyncTaskExecutor.class);
		// We have to interrupt org.apache.commons.io.input.Tailer.run() loop to close
		// reader to the file
		taskExecutor.setTaskTerminationTimeout(10_000);

		Flux<String> tailFlux = fileSupplier.get().map(Message::getPayload).cast(String.class);

		StepVerifier stepVerifier = StepVerifier.create(tailFlux)
			.expectNext("one", "two", "three")
			.thenCancel()
			.verifyLater();

		FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile());
		fileOutputStream.write("one\n".getBytes());
		fileOutputStream.write("two\n".getBytes());
		fileOutputStream.write("three\n".getBytes());

		fileOutputStream.flush();
		fileOutputStream.close();

		stepVerifier.verify(Duration.ofSeconds(30));

		// Ensure that Tailer.run() loop is interrupted, so reader to the file si closed
		// before it is deleted by JUnit
		taskExecutor.close();
	}

}
