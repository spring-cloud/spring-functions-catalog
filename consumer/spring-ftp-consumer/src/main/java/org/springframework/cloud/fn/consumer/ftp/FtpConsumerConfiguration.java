/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.fn.consumer.ftp;

import java.util.function.Consumer;

import org.apache.commons.net.ftp.FTPFile;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.cloud.fn.common.ftp.FtpSessionFactoryConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.JavaUtils;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowBuilder;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.ftp.dsl.Ftp;
import org.springframework.integration.ftp.dsl.FtpMessageHandlerSpec;
import org.springframework.integration.ftp.session.FtpRemoteFileTemplate;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

@EnableConfigurationProperties(FtpConsumerProperties.class)
@AutoConfiguration(after = FtpSessionFactoryConfiguration.class)
public class FtpConsumerConfiguration {

	@Bean
	public IntegrationFlow ftpInboundFlow(FtpConsumerProperties properties, SessionFactory<FTPFile> ftpSessionFactory,
			@Nullable ComponentCustomizer<FtpMessageHandlerSpec> ftpMessageHandlerSpecCustomizer) {

		IntegrationFlowBuilder integrationFlowBuilder = IntegrationFlow.from(MessageConsumer.class,
				(gateway) -> gateway.beanName("ftpConsumer"));

		FtpMessageHandlerSpec handlerSpec = Ftp
			.outboundAdapter(new FtpRemoteFileTemplate(ftpSessionFactory), properties.getMode())
			.remoteDirectory(properties.getRemoteDir())
			.remoteFileSeparator(properties.getRemoteFileSeparator())
			.autoCreateDirectory(properties.isAutoCreateDir())
			.temporaryFileSuffix(properties.getTmpFileSuffix());

		JavaUtils.INSTANCE.acceptIfNotNull(properties.getFilenameExpression(), handlerSpec::fileNameExpression);

		if (ftpMessageHandlerSpecCustomizer != null) {
			ftpMessageHandlerSpecCustomizer.customize(handlerSpec);
		}

		return integrationFlowBuilder.handle(handlerSpec).get();
	}

	private interface MessageConsumer extends Consumer<Message<?>> {

	}

}
