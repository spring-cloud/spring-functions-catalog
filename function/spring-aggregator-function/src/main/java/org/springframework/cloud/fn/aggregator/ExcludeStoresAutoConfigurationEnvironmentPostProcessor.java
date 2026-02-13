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

import java.util.Properties;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

/**
 * An {@link EnvironmentPostProcessor} to add {@code spring.autoconfigure.exclude}
 * property since we can't use {@code application.properties} from the library
 * perspective.
 *
 * @author Artem Bilan
 * @author Corneil du Plessis
 */
class ExcludeStoresAutoConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		MutablePropertySources propertySources = environment.getPropertySources();
		Properties properties = new Properties();

		properties.setProperty("spring.autoconfigure.exclude",
				DataSourceAutoConfiguration.class.getName() + ", "
						+ DataSourceTransactionManagerAutoConfiguration.class.getName() + ", "
						+ MongoAutoConfiguration.class.getName() + ", " + DataMongoAutoConfiguration.class.getName()
						+ ", " + DataMongoRepositoriesAutoConfiguration.class.getName() + ", "
						+ DataRedisAutoConfiguration.class.getName() + ", "
						+ DataRedisRepositoriesAutoConfiguration.class.getName());

		propertySources
			.addLast(new PropertiesPropertySource("aggregator.exclude.stores.auto-configuration", properties));
	}

}
