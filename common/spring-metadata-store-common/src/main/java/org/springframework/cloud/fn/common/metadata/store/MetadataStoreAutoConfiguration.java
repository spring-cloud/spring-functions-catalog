/*
 * Copyright 2018-present the original author or authors.
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

package org.springframework.cloud.fn.common.metadata.store;

import com.hazelcast.core.HazelcastInstance;
import io.awspring.cloud.autoconfigure.core.AwsClientBuilderConfigurer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.integration.aws.metadata.DynamoDbMetadataStore;
import org.springframework.integration.hazelcast.metadata.HazelcastMetadataStore;
import org.springframework.integration.jdbc.metadata.JdbcMetadataStore;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.metadata.MetadataStoreListener;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.mongodb.metadata.MongoDbMetadataStore;
import org.springframework.integration.redis.metadata.RedisMetadataStore;
import org.springframework.integration.zookeeper.metadata.ZookeeperMetadataStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The auto-configuration for metadata store.
 *
 * @author Artem Bilan
 * @author David Turanski
 * @author Corneil du Plessis
 * @since 2.0.2
 */
@AutoConfiguration(
		after = { RedisAutoConfiguration.class, MongoAutoConfiguration.class, HazelcastAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class },
		afterName = "io.awspring.cloud.autoconfigure.dynamodb.DynamoDbAutoConfiguration")
@ConditionalOnClass(ConcurrentMetadataStore.class)
@EnableConfigurationProperties(MetadataStoreProperties.class)
public class MetadataStoreAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "metadata.store", name = "type", havingValue = "memory", matchIfMissing = true)
	@ConditionalOnMissingBean
	public ConcurrentMetadataStore simpleMetadataStore() {
		return new SimpleMetadataStore();
	}

	@ConditionalOnProperty(prefix = "metadata.store", name = "type", havingValue = "redis")
	static class Redis {

		@Bean
		@ConditionalOnMissingBean
		ConcurrentMetadataStore redisMetadataStore(RedisTemplate<String, ?> redisTemplate,
				MetadataStoreProperties metadataStoreProperties) {

			return new RedisMetadataStore(redisTemplate, metadataStoreProperties.getRedis().getKey());
		}

	}

	@ConditionalOnProperty(prefix = "metadata.store", name = "type", havingValue = "mongodb")
	static class Mongo {

		@Bean
		@ConditionalOnMissingBean
		ConcurrentMetadataStore mongoDbMetadataStore(MongoTemplate mongoTemplate,
				MetadataStoreProperties metadataStoreProperties) {

			return new MongoDbMetadataStore(mongoTemplate, metadataStoreProperties.getMongoDb().getCollection());
		}

	}

	@ConditionalOnProperty(prefix = "metadata.store", name = "type", havingValue = "hazelcast")
	static class Hazelcast {

		@Bean
		@ConditionalOnMissingBean
		HazelcastInstance hazelcastInstance() {
			return com.hazelcast.core.Hazelcast.newHazelcastInstance();
		}

		@Bean
		@ConditionalOnMissingBean
		ConcurrentMetadataStore hazelcastMetadataStore(HazelcastInstance hazelcastInstance,
				ObjectProvider<MetadataStoreListener> metadataStoreListenerObjectProvider) {

			HazelcastMetadataStore hazelcastMetadataStore = new HazelcastMetadataStore(hazelcastInstance);
			metadataStoreListenerObjectProvider.ifAvailable(hazelcastMetadataStore::addListener);
			return hazelcastMetadataStore;
		}

	}

	@ConditionalOnProperty(prefix = "metadata.store", name = "type", havingValue = "zookeeper")
	static class Zookeeper {

		@Bean(initMethod = "start")
		@ConditionalOnMissingBean
		CuratorFramework curatorFramework(MetadataStoreProperties metadataStoreProperties) {
			MetadataStoreProperties.Zookeeper zookeeperProperties = metadataStoreProperties.getZookeeper();
			return CuratorFrameworkFactory.newClient(zookeeperProperties.getConnectString(),
					new RetryForever(zookeeperProperties.getRetryInterval()));
		}

		@Bean
		@ConditionalOnMissingBean
		ConcurrentMetadataStore zookeeperMetadataStore(CuratorFramework curatorFramework,
				MetadataStoreProperties metadataStoreProperties,
				ObjectProvider<MetadataStoreListener> metadataStoreListenerObjectProvider) {

			MetadataStoreProperties.Zookeeper zookeeperProperties = metadataStoreProperties.getZookeeper();
			ZookeeperMetadataStore zookeeperMetadataStore = new ZookeeperMetadataStore(curatorFramework);
			zookeeperMetadataStore.setEncoding(zookeeperProperties.getEncoding().name());
			zookeeperMetadataStore.setRoot(zookeeperProperties.getRoot());
			metadataStoreListenerObjectProvider.ifAvailable(zookeeperMetadataStore::addListener);
			return zookeeperMetadataStore;
		}

	}

	@ConditionalOnProperty(prefix = "metadata.store", name = "type", havingValue = "dynamodb")
	static class DynamoDb {

		@Bean
		@ConditionalOnMissingBean
		DynamoDbAsyncClient dynamoDB(AwsClientBuilderConfigurer awsClientBuilderConfigurer) {
			return awsClientBuilderConfigurer.configure(DynamoDbAsyncClient.builder()).build();
		}

		@Bean
		@ConditionalOnMissingBean
		ConcurrentMetadataStore dynamoDbMetadataStore(DynamoDbAsyncClient dynamoDB,
				MetadataStoreProperties metadataStoreProperties) {

			MetadataStoreProperties.DynamoDb dynamoDbProperties = metadataStoreProperties.getDynamoDb();

			DynamoDbMetadataStore dynamoDbMetadataStore = new DynamoDbMetadataStore(dynamoDB,
					dynamoDbProperties.getTable());

			dynamoDbMetadataStore.setReadCapacity(dynamoDbProperties.getReadCapacity());
			dynamoDbMetadataStore.setWriteCapacity(dynamoDbProperties.getWriteCapacity());
			dynamoDbMetadataStore.setCreateTableDelay(dynamoDbProperties.getCreateDelay());
			dynamoDbMetadataStore.setCreateTableRetries(dynamoDbProperties.getCreateRetries());
			if (dynamoDbProperties.getTimeToLive() != null) {
				dynamoDbMetadataStore.setTimeToLive(dynamoDbProperties.getTimeToLive());
			}

			return dynamoDbMetadataStore;
		}

	}

	@ConditionalOnProperty(prefix = "metadata.store", name = "type", havingValue = "jdbc")
	static class Jdbc {

		@Bean
		@ConditionalOnMissingBean
		ConcurrentMetadataStore jdbcMetadataStore(JdbcTemplate jdbcTemplate,
				MetadataStoreProperties metadataStoreProperties) {

			MetadataStoreProperties.Jdbc jdbcProperties = metadataStoreProperties.getJdbc();

			JdbcMetadataStore jdbcMetadataStore = new JdbcMetadataStore(jdbcTemplate);
			jdbcMetadataStore.setTablePrefix(jdbcProperties.getTablePrefix());
			jdbcMetadataStore.setRegion(jdbcProperties.getRegion());

			return jdbcMetadataStore;
		}

	}

}
