/*
 * Copyright 2018-present the original author or authors.
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

package org.springframework.cloud.fn.http.request;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.expression.Expression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.config.IntegrationConverter;
import org.springframework.messaging.Message;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

/**
 * Auto-configuration for a {@link Function} that makes HTTP requests to a resource and
 * for each request, returns a {@link ResponseEntity}.
 *
 * @author David Turanski
 * @author Sunny Hemdev
 * @author Corneil du Plessis
 **/
@AutoConfiguration
@EnableConfigurationProperties(HttpRequestFunctionProperties.class)
public class HttpRequestFunctionConfiguration {

	@Bean
	public HttpRequestFunction httpRequestFunction(WebClient.Builder webClientBuilder,
			HttpRequestFunctionProperties properties) {

		return new HttpRequestFunction(webClientBuilder.build(), properties);
	}

	@Bean
	@IntegrationConverter
	public Converter<String, HttpMethod> httpMethodConverter() {
		return new HttpMethodConverter();
	}

	/**
	 * Function that accepts a {@code Flux<Message<?>>} containing body and headers and
	 * returns a {@code Flux<ResponseEntity<?>>}.
	 */
	public static class HttpRequestFunction implements Function<Message<?>, Object> {

		private final WebClient webClient;

		private final UriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();

		private final HttpRequestFunctionProperties properties;

		public HttpRequestFunction(WebClient webClient, HttpRequestFunctionProperties properties) {
			this.webClient = webClient;
			this.properties = properties;
		}

		@Override
		public Object apply(Message<?> message) {
			return this.webClient.method(resolveHttpMethod(message))
				.uri(this.uriBuilderFactory.uriString(resolveUrl(message)).build())
				.bodyValue(resolveBody(message))
				.headers((httpHeaders) -> httpHeaders.addAll(resolveHeaders(message)))
				.retrieve()
				.toEntity(this.properties.getExpectedResponseType())
				.map((responseEntity) -> this.properties.getReplyExpression().getValue(responseEntity))
				.timeout(Duration.ofMillis(this.properties.getTimeout()))
				.block();
		}

		private String resolveUrl(Message<?> message) {
			return this.properties.getUrlExpression().getValue(message, String.class);
		}

		private HttpMethod resolveHttpMethod(Message<?> message) {
			return this.properties.getHttpMethodExpression().getValue(message, HttpMethod.class);
		}

		private Object resolveBody(Message<?> message) {
			return (this.properties.getBodyExpression() != null) ? this.properties.getBodyExpression().getValue(message)
					: message.getPayload();
		}

		private HttpHeaders resolveHeaders(Message<?> message) {
			HttpHeaders headers = new HttpHeaders();
			Expression headersExpression = this.properties.getHeadersExpression();
			if (headersExpression != null) {
				Map<?, ?> headersMap = headersExpression.getValue(message, Map.class);
				if (!CollectionUtils.isEmpty(headersMap)) {
					headersMap.entrySet()
						.stream()
						.filter((entry) -> entry.getKey() != null && entry.getValue() != null)
						.forEach((entry) -> headers.add(entry.getKey().toString(), entry.getValue().toString()));
				}
			}
			return headers;
		}

	}

	public static class HttpMethodConverter implements Converter<String, HttpMethod> {

		@Override
		public HttpMethod convert(String source) {
			return HttpMethod.valueOf(source);
		}

	}

}
