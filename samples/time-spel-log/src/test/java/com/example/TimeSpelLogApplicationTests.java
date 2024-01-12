package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class TimeSpelLogApplicationTests {

	@Test
	void theTimeIsEmittedThroughSpelToLog(CapturedOutput output) {
		await().untilAsserted(() -> assertThat(output.getOut()).contains("Current seconds: "));
	}

}
