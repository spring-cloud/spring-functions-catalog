package com.example;

import java.io.File;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.zip.transformer.UnZipTransformer;
import org.springframework.messaging.Message;

@SpringBootApplication
public class ZipSplitRabbitBinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZipSplitRabbitBinderApplication.class, args);
	}

	@Bean
	UnZipTransformer unZipTransformer() {
		return new UnZipTransformer();
	}

	@Bean
	@SuppressWarnings("unchecked")
	Function<Flux<Message<File>>, Flux<File>> unzipFunction(UnZipTransformer unZipTransformer) {
		return messageFlux -> messageFlux.map(unZipTransformer::transform)
			.map(Message::getPayload)
			.map(map -> (Map<String, File>) map) // The result of UnZipTransformer
			.flatMapIterable(Map::values);
	}

}
