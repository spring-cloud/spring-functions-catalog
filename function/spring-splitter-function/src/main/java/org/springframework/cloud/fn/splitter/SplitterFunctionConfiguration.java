/*
 * Copyright 2011-2025 the original author or authors.
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

package org.springframework.cloud.fn.splitter;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.file.splitter.FileSplitter;
import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.integration.splitter.DefaultMessageSplitter;
import org.springframework.integration.splitter.ExpressionEvaluatingSplitter;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for Splitter function.
 *
 * @author Artem Bilan
 * @author Soby Chacko
 */
@AutoConfiguration
@EnableConfigurationProperties(SplitterFunctionProperties.class)
public class SplitterFunctionConfiguration {

	@Bean
	public Function<Flux<Message<?>>, Flux<Message<?>>> splitterFunction(
			@Qualifier("expressionSplitter") Optional<AbstractMessageSplitter> expressionSplitter,
			@Qualifier("fileSplitter") Optional<AbstractMessageSplitter> fileSplitter,
			@Qualifier("defaultSplitter") Optional<AbstractMessageSplitter> defaultSplitter,
			SplitterFunctionProperties splitterFunctionProperties) {

		AbstractMessageSplitter messageSplitter = expressionSplitter.or(() -> fileSplitter)
			.or(() -> defaultSplitter)
			.get();

		messageSplitter.setApplySequence(splitterFunctionProperties.isApplySequence());
		FluxMessageChannel inputChannel = new FluxMessageChannel();
		inputChannel.subscribe(messageSplitter);
		FluxMessageChannel outputChannel = new FluxMessageChannel();
		messageSplitter.setOutputChannel(outputChannel);
		return (messageFlux) -> Flux.from(outputChannel).doOnRequest((__) -> inputChannel.subscribeTo(messageFlux));
	}

	@Bean
	@ConditionalOnProperty(prefix = "splitter", name = "expression")
	public AbstractMessageSplitter expressionSplitter(SplitterFunctionProperties splitterFunctionProperties) {
		return new ExpressionEvaluatingSplitter(splitterFunctionProperties.getExpression());
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(FileSplitterCondition.class)
	public AbstractMessageSplitter fileSplitter(SplitterFunctionProperties splitterFunctionProperties) {
		Boolean markers = splitterFunctionProperties.getFileMarkers();
		String charset = splitterFunctionProperties.getCharset();
		if (markers == null) {
			markers = false;
		}
		FileSplitter fileSplitter = new FileSplitter(true, markers, splitterFunctionProperties.getMarkersJson());
		if (charset != null) {
			fileSplitter.setCharset(Charset.forName(charset));
		}
		return fileSplitter;
	}

	@Bean
	@ConditionalOnMissingBean
	public AbstractMessageSplitter defaultSplitter(SplitterFunctionProperties splitterFunctionProperties) {
		DefaultMessageSplitter defaultMessageSplitter = new DefaultMessageSplitter();
		defaultMessageSplitter.setDelimiters(splitterFunctionProperties.getDelimiters());
		return defaultMessageSplitter;
	}

	static class FileSplitterCondition extends AnyNestedCondition {

		FileSplitterCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(prefix = "splitter", name = "charset")
		static class Charset {

		}

		@ConditionalOnProperty(prefix = "splitter", name = "fileMarkers")
		static class FileMarkers {

		}

	}

}
