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

package org.springframework.cloud.fn.consumer.wavefront.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

/**
 * The {@link WavefrontService} implementation for connection over proxy.
 *
 * @author Timo Salm
 */
public class ProxyConnectionWavefrontService implements WavefrontService {

	private static final Log LOGGER = LogFactory.getLog(ProxyConnectionWavefrontService.class);

	private final RestTemplate restTemplate;

	private final String wavefrontProxyUrl;

	public ProxyConnectionWavefrontService(final RestTemplateBuilder restTemplateBuilder,
			final String wavefrontProxyUri) {

		this.restTemplate = restTemplateBuilder.build();
		this.wavefrontProxyUrl = wavefrontProxyUri;
	}

	@Override
	public void send(String metricInWavefrontFormat) {
		LOGGER.debug("Send metric to Wavefront proxy");
		this.restTemplate.postForEntity(this.wavefrontProxyUrl, metricInWavefrontFormat, Void.class);
	}

}
