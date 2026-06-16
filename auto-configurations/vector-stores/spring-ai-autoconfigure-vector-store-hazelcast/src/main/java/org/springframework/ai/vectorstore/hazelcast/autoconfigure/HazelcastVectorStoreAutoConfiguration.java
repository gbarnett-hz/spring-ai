/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.hazelcast.autoconfigure;

import com.hazelcast.core.HazelcastInstance;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.hazelcast.HazelcastVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Hazelcast Vector Store.
 *
 * @author Spring AI
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ HazelcastVectorStore.class, HazelcastInstance.class, EmbeddingModel.class })
@ConditionalOnBean({ HazelcastInstance.class, EmbeddingModel.class })
@EnableConfigurationProperties(HazelcastVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.HAZELCAST,
		matchIfMissing = true)
public class HazelcastVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	HazelcastVectorStore hazelcastVectorStore(HazelcastInstance hazelcastInstance, EmbeddingModel embeddingModel,
			HazelcastVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {
		return HazelcastVectorStore.builder(hazelcastInstance, embeddingModel)
			.collectionName(properties.getCollectionName())
			.initializeSchema(properties.isInitializeSchema())
			.metric(properties.getMetric())
			.indexName(properties.getIndexName())
			.backupCount(properties.getBackupCount())
			.asyncBackupCount(properties.getAsyncBackupCount())
			.maxDegree(properties.getMaxDegree())
			.efConstruction(properties.getEfConstruction())
			.useDeduplication(properties.isUseDeduplication())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.build();
	}

}
