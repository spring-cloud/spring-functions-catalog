/*
 * Copyright 2018-2024 the original author or authors.
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

package org.springframework.cloud.fn.supplier.sftp;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.sshd.sftp.client.SftpClient;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.NameMatchMethodPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.file.FileConsumerProperties;
import org.springframework.cloud.fn.common.file.FileUtils;
import org.springframework.cloud.fn.common.file.remote.RemoteFileDeletingAdvice;
import org.springframework.cloud.fn.common.file.remote.RemoteFileRenamingAdvice;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.integration.aop.ReceiveMessageAdvice;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.GenericSelector;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowBuilder;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.filters.ChainFileListFilter;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.dsl.SftpInboundChannelAdapterSpec;
import org.springframework.integration.sftp.dsl.SftpOutboundGatewaySpec;
import org.springframework.integration.sftp.dsl.SftpStreamingInboundChannelAdapterSpec;
import org.springframework.integration.sftp.filters.SftpPersistentAcceptOnceFileListFilter;
import org.springframework.integration.sftp.filters.SftpRegexPatternFileListFilter;
import org.springframework.integration.sftp.filters.SftpSimplePatternFileListFilter;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.integration.util.IntegrationReactiveUtils;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * The auto-configuration for SFTP supplier.
 *
 * @author Gary Russell
 * @author Artem Bilan
 * @author Chris Schaefer
 * @author Christian Tzolov
 * @author David Turanski
 * @author Corneil du Plessis
 */

@AutoConfiguration
@EnableConfigurationProperties({ SftpSupplierProperties.class, FileConsumerProperties.class })
@Import({ SftpSupplierFactoryConfiguration.class })
public class SftpSupplierConfiguration {

	private static final String METADATA_STORE_PREFIX = "sftpSource/";

	private static final String FILE_MODIFIED_TIME_HEADER = "FILE_MODIFIED_TIME";

	@Bean
	public Supplier<Flux<? extends Message<?>>> sftpSupplier(
			@Qualifier("sftpMessageSource") MessageSource<?> sftpMessageSource,
			@Nullable Publisher<Message<Object>> sftpReadingFlow, SftpSupplierProperties sftpSupplierProperties) {

		Flux<? extends Message<?>> flux = (sftpReadingFlow != null) ? Flux.from(sftpReadingFlow)
				: sftpMessageFlux(sftpMessageSource, sftpSupplierProperties);

		if (sftpMessageSource instanceof Lifecycle lifecycle) {
			lifecycle.start();
		}
		return () -> flux;
	}

	@Bean
	@Primary
	public MessageSource<?> sftpMessageSource(@Qualifier("targetMessageSource") MessageSource<?> messageSource,
			BeanFactory beanFactory, @Nullable List<ReceiveMessageAdvice> receiveMessageAdvice) {

		if (CollectionUtils.isEmpty(receiveMessageAdvice)) {
			return messageSource;
		}

		ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
		proxyFactoryBean.setTarget(messageSource);
		proxyFactoryBean.setBeanFactory(beanFactory);
		receiveMessageAdvice.stream().map((advice) -> {
			NameMatchMethodPointcutAdvisor advisor = new NameMatchMethodPointcutAdvisor(advice);
			advisor.addMethodName("receive");
			return advisor;
		}).forEach(proxyFactoryBean::addAdvisor);

		return (MessageSource<?>) proxyFactoryBean.getObject();
	}

	/*
	 * Configure the standard filters for SFTP inbound adapters.
	 */
	@Bean
	public FileListFilter<SftpClient.DirEntry> chainFilter(SftpSupplierProperties sftpSupplierProperties,
			ConcurrentMetadataStore metadataStore) {

		ChainFileListFilter<SftpClient.DirEntry> chainFilter = new ChainFileListFilter<>();

		if (StringUtils.hasText(sftpSupplierProperties.getFilenamePattern())) {
			chainFilter.addFilter(new SftpSimplePatternFileListFilter(sftpSupplierProperties.getFilenamePattern()));
		}
		else if (sftpSupplierProperties.getFilenameRegex() != null) {
			chainFilter.addFilter(new SftpRegexPatternFileListFilter(sftpSupplierProperties.getFilenameRegex()));
		}

		chainFilter.addFilter(new SftpPersistentAcceptOnceFileListFilter(metadataStore, METADATA_STORE_PREFIX));
		return chainFilter;
	}

	/*
	 * Create a Flux from a MessageSource that will be used by the supplier.
	 */
	private Flux<? extends Message<?>> sftpMessageFlux(
			@Qualifier("sftpMessageSource") MessageSource<?> sftpMessageSource,
			SftpSupplierProperties sftpSupplierProperties) {

		return IntegrationReactiveUtils.messageSourceToFlux(sftpMessageSource)
			.contextWrite(Context.of(IntegrationReactiveUtils.DELAY_WHEN_EMPTY_KEY,
					sftpSupplierProperties.getDelayWhenEmpty()));

	}

	private static String remoteDirectory(SftpSupplierProperties sftpSupplierProperties) {
		return (sftpSupplierProperties.isMultiSource())
				? SftpSupplierProperties.keyDirectories(sftpSupplierProperties).get(0).getDirectory()
				: sftpSupplierProperties.getRemoteDir();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "sftp.supplier", name = "stream")
	static class StreamingConfiguration {

		@Bean
		SftpRemoteFileTemplate sftpTemplate(SftpSupplierFactoryConfiguration.DelegatingFactoryWrapper wrapper) {
			return new SftpRemoteFileTemplate(wrapper.getFactory());
		}

		/**
		 * Streaming {@link MessageSource} that provides an InputStream for each remote
		 * file. It does not synchronize files to a local directory.
		 * @param sftpTemplate the {@link SftpRemoteFileTemplate} to use.
		 * @param sftpSupplierProperties the {@link SftpSupplierProperties} to use.
		 * @param fileListFilter the {@link FileListFilter} to use.
		 * @return a {@link MessageSource}.
		 */
		@Bean
		SftpStreamingInboundChannelAdapterSpec targetMessageSource(SftpRemoteFileTemplate sftpTemplate,
				SftpSupplierProperties sftpSupplierProperties, FileListFilter<SftpClient.DirEntry> fileListFilter) {

			return Sftp.inboundStreamingAdapter(sftpTemplate)
				.remoteDirectory(remoteDirectory(sftpSupplierProperties))
				.remoteFileSeparator(sftpSupplierProperties.getRemoteFileSeparator())
				.filter(fileListFilter)
				.maxFetchSize(sftpSupplierProperties.getMaxFetch());
		}

		@Bean
		Publisher<Message<Object>> sftpReadingFlow(@Qualifier("sftpMessageSource") MessageSource<?> sftpMessageSource,
				SftpSupplierProperties sftpSupplierProperties, FileConsumerProperties fileConsumerProperties) {

			return FileUtils
				.enhanceStreamFlowForReadingMode(
						IntegrationFlow.from(IntegrationReactiveUtils.messageSourceToFlux(sftpMessageSource)
							.contextWrite(Context.of(IntegrationReactiveUtils.DELAY_WHEN_EMPTY_KEY,
									sftpSupplierProperties.getDelayWhenEmpty()))),
						fileConsumerProperties)
				.toReactivePublisher(true);
		}

		@Bean
		@ConditionalOnProperty(prefix = "sftp.supplier", value = "delete-remote-files")
		RemoteFileDeletingAdvice remoteFileDeletingAdvice(SftpRemoteFileTemplate sftpTemplate,
				SftpSupplierProperties sftpSupplierProperties) {

			return new RemoteFileDeletingAdvice(sftpTemplate, sftpSupplierProperties.getRemoteFileSeparator());
		}

		@Bean
		@ConditionalOnProperty(prefix = "sftp.supplier", value = "rename-remote-files-to")
		RemoteFileRenamingAdvice remoteFileRenamingAdvice(SftpRemoteFileTemplate sftpTemplate,
				SftpSupplierProperties sftpSupplierProperties) {

			return new RemoteFileRenamingAdvice(sftpTemplate, sftpSupplierProperties.getRemoteFileSeparator(),
					sftpSupplierProperties.getRenameRemoteFilesTo());
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnExpression("environment['sftp.supplier.stream'] != 'true'")
	static class NonStreamingConfiguration {

		/**
		 * Enrich the flow to provide some standard headers, depending on
		 * {@link FileConsumerProperties}, when consuming file contents.
		 * @param sftpMessageSource the {@link MessageSource}.
		 * @param sftpSupplierProperties the {@link SftpSupplierProperties} to use.
		 * @param fileConsumerProperties the {@link FileConsumerProperties}.
		 * @param renameRemoteFileHandler the {@link MessageHandler} for SFTP protocol.
		 * @return a {@code Publisher<Message>}.
		 */
		@Bean
		@ConditionalOnExpression("environment['file.consumer.mode']!='ref' && environment['sftp.supplier.list-only']!='true'")
		Publisher<Message<Object>> sftpReadingFlow(@Qualifier("sftpMessageSource") MessageSource<?> sftpMessageSource,
				SftpSupplierProperties sftpSupplierProperties, FileConsumerProperties fileConsumerProperties,
				@Nullable @Qualifier("renameRemoteFileHandler") MessageHandler renameRemoteFileHandler) {

			IntegrationFlowBuilder flowBuilder = FileUtils.enhanceFlowForReadingMode(
					IntegrationFlow.from(IntegrationReactiveUtils.messageSourceToFlux(sftpMessageSource)
						.contextWrite(Context.of(IntegrationReactiveUtils.DELAY_WHEN_EMPTY_KEY,
								sftpSupplierProperties.getDelayWhenEmpty()))),
					fileConsumerProperties);

			if (renameRemoteFileHandler != null) {
				flowBuilder.publishSubscribeChannel((pubsub) -> pubsub
					.subscribe((subFlow) -> subFlow.handle(renameRemoteFileHandler).nullChannel()));
			}

			return flowBuilder.toReactivePublisher(true);
		}

		/**
		 * A {@link MessageSource} that synchronizes files to a local directory.
		 * @param sftpSupplierProperties the properties.
		 * @param delegatingFactoryWrapper the
		 * {@link SftpSupplierFactoryConfiguration.DelegatingFactoryWrapper} to use.
		 * @param fileListFilter the {@link FileListFilter} to use.
		 * @return the {code MessageSource}.
		 */
		@ConditionalOnExpression("environment['sftp.supplier.list-only'] != 'true'")
		@Bean
		SftpInboundChannelAdapterSpec targetMessageSource(SftpSupplierProperties sftpSupplierProperties,
				SftpSupplierFactoryConfiguration.DelegatingFactoryWrapper delegatingFactoryWrapper,
				FileListFilter<SftpClient.DirEntry> fileListFilter) {

			return Sftp.inboundAdapter(delegatingFactoryWrapper.getFactory())
				.preserveTimestamp(sftpSupplierProperties.isPreserveTimestamp())
				.autoCreateLocalDirectory(sftpSupplierProperties.isAutoCreateLocalDir())
				.deleteRemoteFiles(sftpSupplierProperties.isDeleteRemoteFiles())
				.localDirectory(sftpSupplierProperties.getLocalDir())
				.remoteDirectory(remoteDirectory(sftpSupplierProperties))
				.remoteFileSeparator(sftpSupplierProperties.getRemoteFileSeparator())
				.temporaryFileSuffix(sftpSupplierProperties.getTmpFileSuffix())
				.metadataStorePrefix(METADATA_STORE_PREFIX)
				.maxFetchSize(sftpSupplierProperties.getMaxFetch())
				.filter(fileListFilter);
		}

		@Bean
		@ConditionalOnProperty(prefix = "sftp.supplier", value = "rename-remote-files-to")
		SftpOutboundGatewaySpec renameRemoteFileHandler(
				SftpSupplierFactoryConfiguration.DelegatingFactoryWrapper delegatingFactoryWrapper,
				SftpSupplierProperties sftpSupplierProperties) {

			return Sftp
				.outboundGateway(delegatingFactoryWrapper.getFactory(),
						AbstractRemoteFileOutboundGateway.Command.MV.getCommand(),
						String.format("headers.get('%s') + '%s' + headers.get('%s')", FileHeaders.REMOTE_DIRECTORY,
								sftpSupplierProperties.getRemoteFileSeparator(), FileHeaders.REMOTE_FILE))
				.renameExpression(sftpSupplierProperties.getRenameRemoteFilesTo());
		}

	}

	/*
	 * List only configuration
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "sftp.supplier", name = "list-only")
	static class ListingOnlyConfiguration {

		private final PollableChannel listingChannel = new QueueChannel();

		@Bean
		@SuppressWarnings("unchecked")
		MessageSource<?> targetMessageSource(SftpListingMessageProducer sftpListingMessageProducer) {
			return () -> {
				sftpListingMessageProducer.listNames();
				return (Message<Object>) this.listingChannel.receive();
			};

		}

		@Bean
		SftpListingMessageProducer sftpListingMessageProducer(SftpSupplierProperties sftpSupplierProperties,
				SftpSupplierFactoryConfiguration.DelegatingFactoryWrapper delegatingFactoryWrapper) {

			return new SftpListingMessageProducer(delegatingFactoryWrapper.getFactory(),
					remoteDirectory(sftpSupplierProperties), sftpSupplierProperties.getRemoteFileSeparator(),
					sftpSupplierProperties.getSortBy());
		}

		@Bean
		GenericSelector<String> listOnlyFilter(SftpSupplierProperties sftpSupplierProperties) {
			Predicate<String> predicate = (s) -> true;
			if (StringUtils.hasText(sftpSupplierProperties.getFilenamePattern())) {
				predicate = Pattern.compile(sftpSupplierProperties.getFilenamePattern()).asPredicate();
			}
			else if (sftpSupplierProperties.getFilenameRegex() != null) {
				predicate = sftpSupplierProperties.getFilenameRegex().asPredicate();
			}

			return predicate::test;
		}

		@Bean
		IntegrationFlow listingFlow(MessageProducerSupport listingMessageProducer,
				MessageProcessor<?> lsEntryToStringTransformer, GenericSelector<Message<?>> duplicateFilter,
				GenericSelector<String> listOnlyFilter) {

			return IntegrationFlow.from(listingMessageProducer)
				.split()
				.transform(lsEntryToStringTransformer)
				.filter(duplicateFilter)
				.filter(listOnlyFilter)
				.channel(this.listingChannel)
				.get();
		}

		@Bean
		MessageProcessor<Message<?>> lsEntryToStringTransformer() {
			return (Message<?> message) -> {
				SftpClient.DirEntry dirEntry = (SftpClient.DirEntry) message.getPayload();

				String fileName = message.getHeaders().get(FileHeaders.REMOTE_DIRECTORY) + dirEntry.getFilename();

				return MessageBuilder.withPayload(fileName)
					.copyHeaders(message.getHeaders())
					.setHeader(FILE_MODIFIED_TIME_HEADER, String.valueOf(dirEntry.getAttributes().getModifyTime()))
					.setHeader(MessageHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
					.build();
			};

		}

		@Bean
		GenericSelector<Message<?>> duplicateFilter(ConcurrentMetadataStore metadataStore) {
			// Must be a specific type
			return new GenericSelector<Message<?>>() {
				@Override
				public boolean accept(Message<?> message) {

					String lastModifiedTime = (String) message.getHeaders().get(FILE_MODIFIED_TIME_HEADER);
					String storedLastModifiedTime = metadataStore.get(METADATA_STORE_PREFIX + message.getPayload());

					boolean result = !lastModifiedTime.equals(storedLastModifiedTime);

					if (result) {
						metadataStore.put(METADATA_STORE_PREFIX + message.getPayload(),
								message.getHeaders().get(FILE_MODIFIED_TIME_HEADER).toString());
					}
					return result;
				}
			};
		}

		static class SftpListingMessageProducer extends MessageProducerSupport {

			private final String remoteDirectory;

			private final SessionFactory<SftpClient.DirEntry> sessionFactory;

			private final String remoteFileSeparator;

			private final SftpSupplierProperties.SortSpec sort;

			SftpListingMessageProducer(SessionFactory<SftpClient.DirEntry> sessionFactory, String remoteDirectory,
					String remoteFileSeparator, SftpSupplierProperties.SortSpec sort) {

				this.sessionFactory = sessionFactory;
				this.remoteDirectory = remoteDirectory;
				this.remoteFileSeparator = remoteFileSeparator;
				this.sort = sort;
			}

			void listNames() {
				Stream<SftpClient.DirEntry> stream;
				try {
					stream = Stream.of(this.sessionFactory.getSession().list(this.remoteDirectory))
						.filter((x) -> !(x.getAttributes().isDirectory() || x.getAttributes().isSymbolicLink()));

					if (this.sort != null) {
						stream = stream.sorted(this.sort.comparator());
					}
				}
				catch (IOException ex) {
					throw new MessagingException(ex.getMessage(), ex);
				}
				sendMessage(MessageBuilder.withPayload(stream)
					.setHeader(FileHeaders.REMOTE_DIRECTORY, this.remoteDirectory + this.remoteFileSeparator)
					.build());
			}

		}

	}

}
