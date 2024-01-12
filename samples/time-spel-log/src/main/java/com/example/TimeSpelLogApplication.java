package com.example;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@EnableScheduling
@SpringBootApplication
public class TimeSpelLogApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimeSpelLogApplication.class, args);
	}

	@Autowired
	private FunctionCatalog functionCatalog;

	private Runnable composedFunction;

	@PostConstruct
	void init() {
		this.composedFunction = this.functionCatalog.lookup(null);
	}

	@Scheduled(fixedDelay = 1000)
	void scheduleFunctionCall() {
		this.composedFunction.run();
	}

}
