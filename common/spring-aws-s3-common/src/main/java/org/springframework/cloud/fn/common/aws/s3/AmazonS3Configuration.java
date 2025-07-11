/*
 * Copyright 2016-present the original author or authors.
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

package org.springframework.cloud.fn.common.aws.s3;

import java.net.URI;

import io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration;
import io.awspring.cloud.autoconfigure.s3.S3CrtAsyncClientAutoConfiguration;
import io.awspring.cloud.autoconfigure.s3.properties.S3Properties;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.aws.support.S3SessionFactory;

/**
 * The auto-configuration for {@link S3SessionFactory}.
 *
 * @author Artem Bilan
 */
@AutoConfiguration(after = { S3AutoConfiguration.class, S3CrtAsyncClientAutoConfiguration.class })
public class AmazonS3Configuration {

	@Bean
	@ConditionalOnMissingBean
	public S3SessionFactory s3SessionFactory(S3Client amazonS3, S3Properties s3Properties) {
		S3SessionFactory s3SessionFactory = new S3SessionFactory(amazonS3);
		URI endpoint = s3Properties.getEndpoint();
		if (endpoint != null) {
			s3SessionFactory.setEndpoint(String.join(":", endpoint.getHost(), String.valueOf(endpoint.getPort())));
		}
		return s3SessionFactory;
	}

}
