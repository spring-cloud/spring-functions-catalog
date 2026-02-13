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

package org.springframework.cloud.fn.supplier.file;

import java.io.File;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.cloud.fn.common.file.FileConsumerProperties;
import org.springframework.cloud.fn.common.file.FileReadingMode;
import org.springframework.cloud.fn.common.file.FileUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.JavaUtils;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowBuilder;
import org.springframework.integration.file.dsl.FileInboundChannelAdapterSpec;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.dsl.TailAdapterSpec;
import org.springframework.integration.file.filters.ChainFileListFilter;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.file.filters.FileSystemPersistentAcceptOnceFileListFilter;
import org.springframework.integration.file.filters.RegexPatternFileListFilter;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.file.inbound.FileReadingMessageSource;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

/**
 * The auto-configuration for file supplier.
 *
 * @author Artem Bilan
 * @author Soby Chacko
 */
@AutoConfiguration
@EnableConfigurationProperties({ FileSupplierProperties.class, FileConsumerProperties.class })
public class FileSupplierConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "file.supplier", name = "tail")
	public Publisher<Message<String>> fileTailingFlow(FileSupplierProperties fileSupplierProperties) {
		FileSupplierProperties.Tailer tail = fileSupplierProperties.getTailer();
		TailAdapterSpec tailAdapterSpec = Files.tailAdapter(fileSupplierProperties.getTail())
			.fileDelay(tail.getAttemptsDelay().toMillis())
			// OS native tail command
			.nativeOptions(tail.getNativeOptions())
			.enableStatusReader(tail.isStatusReader());

		JavaUtils.INSTANCE
			.acceptIfNotNull(tail.getIdleEventInterval(),
					(duration) -> tailAdapterSpec.idleEventInterval(duration.toMillis()))
			// Apache Commons Tailer
			.acceptIfNotNull(tail.isEnd(), tailAdapterSpec::end)
			.acceptIfNotNull(tail.isReopen(), tailAdapterSpec::reopen)
			.acceptIfNotNull(tail.getPollingDelay(), (duration) -> tailAdapterSpec.delay(duration.toMillis()));

		return IntegrationFlow.from(tailAdapterSpec).toReactivePublisher(true);
	}

	@Bean
	public Supplier<Flux<Message<?>>> fileSupplier(FileConsumerProperties fileConsumerProperties,
			@Qualifier("fileMessageFlux") @Nullable Flux<Message<File>> fileMessageFlux,
			@Qualifier("fileReadingFlow") @Nullable Publisher<Message<Object>> fileReadingFlow,
			@Qualifier("fileTailingFlow") @Nullable Publisher<Message<String>> fileTailingFlow) {

		if (fileConsumerProperties.getMode() == FileReadingMode.ref && fileMessageFlux != null) {
			return () -> Flux.from(fileMessageFlux);
		}
		else if (fileReadingFlow != null) {
			return () -> Flux.from(fileReadingFlow);
		}
		else if (fileTailingFlow != null) {
			return () -> Flux.from(fileTailingFlow);
		}
		else {
			throw new BeanInitializationException("Cannot creat 'fileSupplier' bean: no 'fileReadingFlow', "
					+ "or 'fileTailingFlow' dependency, and is not 'FileReadingMode.ref'.");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnExpression("environment['file.supplier.tail'] == null")
	protected static class FileMessageSourceConfiguration {

		private static final String METADATA_STORE_PREFIX = "local-file-system-metadata-";

		private final FileSupplierProperties fileSupplierProperties;

		private final FileConsumerProperties fileConsumerProperties;

		FileMessageSourceConfiguration(FileSupplierProperties fileSupplierProperties,
				FileConsumerProperties fileConsumerProperties) {

			this.fileSupplierProperties = fileSupplierProperties;
			this.fileConsumerProperties = fileConsumerProperties;
		}

		@Bean
		public ChainFileListFilter<File> fileListFilter(ConcurrentMetadataStore metadataStore) {
			ChainFileListFilter<File> chainFilter = new ChainFileListFilter<>();
			if (StringUtils.hasText(this.fileSupplierProperties.getFilenamePattern())) {
				chainFilter
					.addFilter(new SimplePatternFileListFilter(this.fileSupplierProperties.getFilenamePattern()));
			}
			else if (this.fileSupplierProperties.getFilenameRegex() != null) {
				chainFilter.addFilter(new RegexPatternFileListFilter(this.fileSupplierProperties.getFilenameRegex()));
			}

			if (this.fileSupplierProperties.isPreventDuplicates()) {
				chainFilter
					.addFilter(new FileSystemPersistentAcceptOnceFileListFilter(metadataStore, METADATA_STORE_PREFIX));
			}

			return chainFilter;
		}

		@Bean("fileReadingMessageSource")
		public FileInboundChannelAdapterSpec fileMessageSource(FileListFilter<File> fileListFilter,
				@Nullable ComponentCustomizer<FileInboundChannelAdapterSpec> fileInboundChannelAdapterSpecCustomizer) {

			FileInboundChannelAdapterSpec adapterSpec = Files.inboundAdapter(this.fileSupplierProperties.getDirectory())
				.filter(fileListFilter);
			if (fileInboundChannelAdapterSpecCustomizer != null) {
				fileInboundChannelAdapterSpecCustomizer.customize(adapterSpec);
			}
			return adapterSpec;
		}

		@Bean
		public Flux<Message<File>> fileMessageFlux(FileReadingMessageSource fileReadingMessageSource) {
			return IntegrationReactiveUtils.messageSourceToFlux(fileReadingMessageSource)
				.contextWrite(Context.of(IntegrationReactiveUtils.DELAY_WHEN_EMPTY_KEY,
						this.fileSupplierProperties.getDelayWhenEmpty()))
				.doOnRequest((r) -> fileReadingMessageSource.start());
		}

		@Bean
		@ConditionalOnExpression("environment['file.consumer.mode'] != 'ref'")
		public Publisher<Message<Object>> fileReadingFlow(Flux<Message<?>> fileMessageFlux) {
			IntegrationFlowBuilder flowBuilder = IntegrationFlow.from(fileMessageFlux);
			return FileUtils.enhanceFlowForReadingMode(flowBuilder, this.fileConsumerProperties)
				.toReactivePublisher(true);
		}

	}

}
