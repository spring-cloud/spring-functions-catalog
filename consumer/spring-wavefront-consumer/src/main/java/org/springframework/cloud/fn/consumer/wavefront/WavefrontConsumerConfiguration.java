/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.cloud.fn.consumer.wavefront;

import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.fn.consumer.wavefront.service.DirectConnectionWavefrontService;
import org.springframework.cloud.fn.consumer.wavefront.service.ProxyConnectionWavefrontService;
import org.springframework.cloud.fn.consumer.wavefront.service.WavefrontService;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

/**
 * The auto-configuration for Wavefront consumer.
 *
 * @author Timo Salm
 * @author Artem Bilan
 */
@AutoConfiguration(after = RestTemplateAutoConfiguration.class)
@EnableConfigurationProperties(WavefrontConsumerProperties.class)
public class WavefrontConsumerConfiguration {

	private static final Log logger = LogFactory.getLog(WavefrontConsumerConfiguration.class);

	@Bean
	public Consumer<Message<?>> wavefrontConsumer(WavefrontConsumerProperties properties, WavefrontService service) {
		return (message) -> {
			WavefrontFormat wavefrontFormat = new WavefrontFormat(properties, message);
			String formattedString = wavefrontFormat.getFormattedString();
			service.send(formattedString);
			logger.debug(formattedString);
		};
	}

	@Bean
	public WavefrontService wavefrontService(WavefrontConsumerProperties properties,
			RestTemplateBuilder restTemplateBuilder) {

		String proxyUri = properties.getProxyUri();
		if (StringUtils.hasText(proxyUri)) {
			return new ProxyConnectionWavefrontService(restTemplateBuilder, proxyUri);
		}
		else {
			return new DirectConnectionWavefrontService(restTemplateBuilder, properties.getUri(),
					properties.getApiToken());
		}
	}

}
