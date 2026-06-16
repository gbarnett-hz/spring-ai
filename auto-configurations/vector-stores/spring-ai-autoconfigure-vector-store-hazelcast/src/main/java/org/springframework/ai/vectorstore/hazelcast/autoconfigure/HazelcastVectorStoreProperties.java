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

import com.hazelcast.config.vector.Metric;

import org.springframework.ai.vectorstore.hazelcast.HazelcastVectorStore;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Hazelcast Vector Store.
 *
 * @author Spring AI
 * @since 2.0.0
 */
@ConfigurationProperties(HazelcastVectorStoreProperties.CONFIG_PREFIX)
public class HazelcastVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.hazelcast";

	/**
	 * Hazelcast vector collection name.
	 */
	private String collectionName = HazelcastVectorStore.DEFAULT_COLLECTION_NAME;

	/**
	 * Hazelcast vector index name.
	 */
	private String indexName = HazelcastVectorStore.DEFAULT_INDEX_NAME;

	/**
	 * Vector metric used by the Hazelcast vector index.
	 */
	private Metric metric = Metric.COSINE;

	/**
	 * Hazelcast synchronous backup count for the vector collection.
	 */
	private int backupCount = 1;

	/**
	 * Hazelcast asynchronous backup count for the vector collection.
	 */
	private int asyncBackupCount;

	/**
	 * HNSW max degree for the Hazelcast vector index.
	 */
	private int maxDegree = 32;

	/**
	 * HNSW ef construction for the Hazelcast vector index.
	 */
	private int efConstruction = 128;

	/**
	 * Whether Hazelcast should deduplicate equal vectors in the index.
	 */
	private boolean useDeduplication = true;

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public Metric getMetric() {
		return this.metric;
	}

	public void setMetric(Metric metric) {
		this.metric = metric;
	}

	public int getBackupCount() {
		return this.backupCount;
	}

	public void setBackupCount(int backupCount) {
		this.backupCount = backupCount;
	}

	public int getAsyncBackupCount() {
		return this.asyncBackupCount;
	}

	public void setAsyncBackupCount(int asyncBackupCount) {
		this.asyncBackupCount = asyncBackupCount;
	}

	public int getMaxDegree() {
		return this.maxDegree;
	}

	public void setMaxDegree(int maxDegree) {
		this.maxDegree = maxDegree;
	}

	public int getEfConstruction() {
		return this.efConstruction;
	}

	public void setEfConstruction(int efConstruction) {
		this.efConstruction = efConstruction;
	}

	public boolean isUseDeduplication() {
		return this.useDeduplication;
	}

	public void setUseDeduplication(boolean useDeduplication) {
		this.useDeduplication = useDeduplication;
	}

}
