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

package org.springframework.cloud.fn.aggregator;

import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.fn.common.config.ComponentCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.aggregator.CorrelationStrategy;
import org.springframework.integration.aggregator.DefaultAggregatingMessageGroupProcessor;
import org.springframework.integration.aggregator.ExpressionEvaluatingCorrelationStrategy;
import org.springframework.integration.aggregator.ExpressionEvaluatingMessageGroupProcessor;
import org.springframework.integration.aggregator.ExpressionEvaluatingReleaseStrategy;
import org.springframework.integration.aggregator.MessageGroupProcessor;
import org.springframework.integration.aggregator.ReleaseStrategy;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.config.AggregatorFactoryBean;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * The auto-configuration for aggregator function.
 *
 * @author Artem Bilan
 * @author Corneil du Plessis
 */
@AutoConfiguration
@EnableConfigurationProperties(AggregatorFunctionProperties.class)
public class AggregatorFunctionConfiguration {

	private final FluxMessageChannel outputChannel = new FluxMessageChannel();

	@Autowired
	private AggregatorFunctionProperties properties;

	@Autowired
	private BeanFactory beanFactory;

	@Bean
	public Function<Flux<Message<?>>, Flux<Message<?>>> aggregatorFunction(
			@Qualifier("aggregatorInputChannel") FluxMessageChannel aggregatorInputChannel) {

		return (input) -> Flux.from(this.outputChannel)
			.doOnRequest((request) -> aggregatorInputChannel.subscribeTo(input
				.map((inputMessage) -> MessageBuilder.fromMessage(inputMessage).removeHeader("kafka_consumer").build())
				.publishOn(Schedulers.boundedElastic())));
	}

	@Bean
	public FluxMessageChannel aggregatorInputChannel() {
		return new FluxMessageChannel();
	}

	@Bean
	@ServiceActivator(inputChannel = "aggregatorInputChannel")
	public AggregatorFactoryBean aggregator(@Nullable CorrelationStrategy correlationStrategy,
			@Nullable ReleaseStrategy releaseStrategy, @Nullable MessageGroupProcessor messageGroupProcessor,
			@Nullable MessageGroupStore messageStore,
			@Nullable ComponentCustomizer<AggregatorFactoryBean> aggregatorCustomizer) {

		AggregatorFactoryBean aggregator = new AggregatorFactoryBean();
		aggregator.setExpireGroupsUponCompletion(true);
		aggregator.setSendPartialResultOnExpiry(true);
		aggregator.setGroupTimeoutExpression(this.properties.getGroupTimeout());

		if (correlationStrategy != null) {
			aggregator.setCorrelationStrategy(correlationStrategy);
		}
		if (releaseStrategy != null) {
			aggregator.setReleaseStrategy(releaseStrategy);
		}

		MessageGroupProcessor groupProcessor = messageGroupProcessor;

		if (groupProcessor == null) {
			groupProcessor = new DefaultAggregatingMessageGroupProcessor();
			((BeanFactoryAware) groupProcessor).setBeanFactory(this.beanFactory);
		}
		aggregator.setProcessorBean(groupProcessor);

		if (messageStore != null) {
			aggregator.setMessageStore(messageStore);
		}
		aggregator.setOutputChannel(this.outputChannel);

		if (aggregatorCustomizer != null) {
			aggregatorCustomizer.customize(aggregator);
		}

		return aggregator;
	}

	@Bean
	@ConditionalOnProperty(prefix = AggregatorFunctionProperties.PREFIX, name = "correlation")
	@ConditionalOnMissingBean
	public CorrelationStrategy correlationStrategy() {
		return new ExpressionEvaluatingCorrelationStrategy(this.properties.getCorrelation());
	}

	@Bean
	@ConditionalOnProperty(prefix = AggregatorFunctionProperties.PREFIX, name = "release")
	@ConditionalOnMissingBean
	public ReleaseStrategy releaseStrategy() {
		return new ExpressionEvaluatingReleaseStrategy(this.properties.getRelease());
	}

	@Bean
	@ConditionalOnProperty(prefix = AggregatorFunctionProperties.PREFIX, name = "aggregation")
	@ConditionalOnMissingBean
	public MessageGroupProcessor messageGroupProcessor() {
		return new ExpressionEvaluatingMessageGroupProcessor(this.properties.getAggregation().getExpressionString());
	}

	@Configuration
	@ConditionalOnMissingBean(MessageGroupStore.class)
	@Import({ MessageStoreConfiguration.Mongo.class, MessageStoreConfiguration.Redis.class,
			MessageStoreConfiguration.Jdbc.class })
	protected static class MessageStoreAutoConfiguration {

	}

}
