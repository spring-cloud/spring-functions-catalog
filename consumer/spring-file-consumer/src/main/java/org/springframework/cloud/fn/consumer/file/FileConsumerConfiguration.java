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

package org.springframework.cloud.fn.consumer.file;

import java.util.function.Consumer;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.Expression;
import org.springframework.integration.file.DefaultFileNameGenerator;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * The auto-configuration for file consumer.
 *
 * @author Mark Fisher
 * @author Artem Bilan
 * @author Soby Chacko
 */
@AutoConfiguration
@EnableConfigurationProperties(FileConsumerProperties.class)
public class FileConsumerConfiguration {

	private final FileConsumerProperties properties;

	public FileConsumerConfiguration(FileConsumerProperties properties) {
		this.properties = properties;
	}

	@Bean
	public Consumer<Message<?>> fileConsumer(
			@Qualifier("fileConsumerWritingMessageHandler") FileWritingMessageHandler fileWritingMessageHandler) {

		return fileWritingMessageHandler::handleMessage;
	}

	@Bean
	public FileWritingMessageHandler fileConsumerWritingMessageHandler(BeanFactory beanFactory,
			@Nullable ComponentCustomizer<FileWritingMessageHandler> fileWritingMessageHandlerCustomizer) {

		Expression directoryExpression = this.properties.getDirectoryExpression();
		FileWritingMessageHandler handler = (directoryExpression != null)
				? new FileWritingMessageHandler(directoryExpression)
				: new FileWritingMessageHandler(this.properties.getDirectory());
		handler.setAutoCreateDirectory(true);
		handler.setAppendNewLine(!this.properties.isBinary());
		handler.setCharset(this.properties.getCharset());
		handler.setExpectReply(false);
		handler.setFileExistsMode(this.properties.getMode());
		DefaultFileNameGenerator fileNameGenerator = new DefaultFileNameGenerator();
		fileNameGenerator.setExpression(this.properties.getNameExpression());
		fileNameGenerator.setBeanFactory(beanFactory);
		handler.setFileNameGenerator(fileNameGenerator);

		if (fileWritingMessageHandlerCustomizer != null) {
			fileWritingMessageHandlerCustomizer.customize(handler);
		}
		return handler;
	}

}
