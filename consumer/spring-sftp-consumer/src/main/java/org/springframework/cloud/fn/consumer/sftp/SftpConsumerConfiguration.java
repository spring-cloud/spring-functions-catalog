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

package org.springframework.cloud.fn.consumer.sftp;

import java.util.function.Consumer;

import org.apache.sshd.sftp.client.SftpClient;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.gateway.AnnotationGatewayProxyFactoryBean;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.dsl.SftpMessageHandlerSpec;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for SFTP Consumer.
 *
 * @author Soby Chacko
 * @author Corneil du Plessis
 */
@AutoConfiguration
@EnableConfigurationProperties(SftpConsumerProperties.class)
@Import(SftpConsumerSessionFactoryConfiguration.class)
public class SftpConsumerConfiguration {

	@Bean
	public IntegrationFlow ftpOutboundFlow(SftpConsumerProperties properties,
			SessionFactory<SftpClient.DirEntry> ftpSessionFactory,
			@Nullable ComponentCustomizer<SftpMessageHandlerSpec> sftpMessageHandlerSpecCustomizer) {

		SftpMessageHandlerSpec handlerSpec = Sftp
			.outboundAdapter(new SftpRemoteFileTemplate(ftpSessionFactory), properties.getMode())
			.remoteDirectory(properties.getRemoteDir())
			.temporaryRemoteDirectory(properties.getTemporaryRemoteDir())
			.remoteFileSeparator(properties.getRemoteFileSeparator())
			.autoCreateDirectory(properties.isAutoCreateDir())
			.useTemporaryFileName(properties.isUseTemporaryFilename())
			.temporaryFileSuffix(properties.getTmpFileSuffix());
		if (properties.getFilenameExpression() != null) {
			handlerSpec.fileNameExpression(properties.getFilenameExpression());
		}

		if (sftpMessageHandlerSpecCustomizer != null) {
			sftpMessageHandlerSpecCustomizer.customize(handlerSpec);
		}

		return (flow) -> flow.handle(handlerSpec);
	}

	@Bean
	@SuppressWarnings({ "unchecked", "rawtypes" })
	AnnotationGatewayProxyFactoryBean<Consumer<Message<?>>> sftpConsumer() {
		var gatewayProxyFactoryBean = new AnnotationGatewayProxyFactoryBean<>(Consumer.class);
		gatewayProxyFactoryBean.setDefaultRequestChannelName("ftpOutboundFlow.input");
		return (AnnotationGatewayProxyFactoryBean) gatewayProxyFactoryBean;
	}

}
