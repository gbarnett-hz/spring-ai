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

package org.springframework.ai.vectorstore.hazelcast;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.hazelcast.config.Config;
import com.hazelcast.config.vector.Metric;
import com.hazelcast.config.vector.VectorCollectionConfig;
import com.hazelcast.config.vector.VectorIndexConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link HazelcastVectorStore}.
 *
 * @author Spring AI
 */
class HazelcastVectorStoreTests {

	private HazelcastInstance hazelcastInstance;

	private TestEmbeddingModel embeddingModel;

	private HazelcastVectorStore vectorStore;

	@BeforeEach
	void setUp() {
		Config config = new Config();
		config.setClusterName("hazelcast-vector-store-tests-" + System.nanoTime());
		this.hazelcastInstance = Hazelcast.newHazelcastInstance(config);
		this.embeddingModel = new TestEmbeddingModel();
		this.vectorStore = HazelcastVectorStore.builder(this.hazelcastInstance, this.embeddingModel)
			.collectionName("documents-" + System.nanoTime())
			.initializeSchema(true)
			.metric(Metric.COSINE)
			.build();
	}

	@AfterEach
	void tearDown() {
		if (this.hazelcastInstance != null) {
			this.hazelcastInstance.shutdown();
		}
	}

	@Test
	void addAndSimilaritySearch() {
		this.vectorStore.add(List.of(new Document("cat", "cat purrs", Map.of("kind", "cat")),
				new Document("dog", "dog barks", Map.of("kind", "dog"))));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("cat query").topK(1).similarityThresholdAll().build());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo("cat");
	}

	@Test
	void returnedDocumentsPreservePayloadAndScore() {
		this.vectorStore.add(List.of(new Document("cat", "cat purrs", Map.of("kind", "cat", "index", 1))));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("cat query").topK(1).similarityThresholdAll().build());

		assertThat(results).hasSize(1);
		Document document = results.get(0);
		assertThat(document.getId()).isEqualTo("cat");
		assertThat(document.getText()).isEqualTo("cat purrs");
		assertThat(document.getMetadata()).containsEntry("kind", "cat").containsEntry("index", 1);
		assertThat(document.getScore()).isNotNull().isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(1.0);
	}

	@Test
	void deleteByIdRemovesDocuments() {
		this.vectorStore
			.add(List.of(new Document("cat", "cat purrs", Map.of()), new Document("dog", "dog barks", Map.of())));

		this.vectorStore.delete(List.of("cat"));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("cat query").topK(2).similarityThresholdAll().build());
		assertThat(results).extracting(Document::getId).containsExactly("dog");
	}

	@Test
	void topKLimitsResults() {
		this.vectorStore.add(List.of(new Document("cat", "cat purrs", Map.of()),
				new Document("cat2", "cat naps", Map.of()), new Document("dog", "dog barks", Map.of())));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("cat query").topK(2).similarityThresholdAll().build());

		assertThat(results).hasSize(2);
	}

	@Test
	void similarityThresholdFiltersResults() {
		this.vectorStore
			.add(List.of(new Document("cat", "cat purrs", Map.of()), new Document("dog", "dog barks", Map.of())));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("cat query").topK(2).similarityThreshold(0.9).build());

		assertThat(results).extracting(Document::getId).containsExactly("cat");
	}

	@Test
	void searchMetadataFiltersAreUnsupported() {
		this.vectorStore.add(List.of(new Document("cat", "cat purrs", Map.of("kind", "cat"))));
		Filter.Expression expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("kind"),
				new Filter.Value("cat"));

		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> this.vectorStore.similaritySearch(
					SearchRequest.builder().query("cat query").topK(1).filterExpression(expression).build()))
			.withMessageContaining("metadata filter expressions");
	}

	@Test
	void filterBasedDeleteIsUnsupported() {
		Filter.Expression expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("kind"),
				new Filter.Value("cat"));

		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> this.vectorStore.delete(expression))
			.withMessageContaining("filter-based delete");
	}

	@Test
	void schemaInitializationCanBeDisabled() {
		String collectionName = "existing-" + System.nanoTime();
		VectorCollectionConfig existingConfig = new VectorCollectionConfig(collectionName)
			.addVectorIndexConfig(new VectorIndexConfig("existing-index", Metric.COSINE, 3));
		this.hazelcastInstance.getConfig().addVectorCollectionConfig(existingConfig);

		HazelcastVectorStore.builder(this.hazelcastInstance, this.embeddingModel)
			.collectionName(collectionName)
			.initializeSchema(false)
			.build();

		var collectionConfig = this.hazelcastInstance.getConfig().getVectorCollectionConfigOrNull(collectionName);
		assertThat(collectionConfig).isNotNull();
		assertThat(collectionConfig.getVectorIndexConfigs()).hasSize(1);
		assertThat(collectionConfig.getVectorIndexConfigs().get(0).getName()).isEqualTo("existing-index");
	}

	@Test
	void schemaInitializationCreatesCollectionConfiguration() {
		String collectionName = "initialized-" + System.nanoTime();
		HazelcastVectorStore.builder(this.hazelcastInstance, this.embeddingModel)
			.collectionName(collectionName)
			.initializeSchema(true)
			.metric(Metric.DOT)
			.indexName("test-index")
			.backupCount(1)
			.asyncBackupCount(0)
			.maxDegree(16)
			.efConstruction(128)
			.useDeduplication(false)
			.build();

		var collectionConfig = this.hazelcastInstance.getConfig().getVectorCollectionConfigOrNull(collectionName);
		assertThat(collectionConfig).isNotNull();
		assertThat(collectionConfig.getVectorIndexConfigs()).hasSize(1);
		assertThat(collectionConfig.getVectorIndexConfigs().get(0).getName()).isEqualTo("test-index");
		assertThat(collectionConfig.getVectorIndexConfigs().get(0).getMetric()).isEqualTo(Metric.DOT);
		assertThat(collectionConfig.getVectorIndexConfigs().get(0).getDimension()).isEqualTo(3);
	}

	@Test
	void exposesNativeHazelcastInstance() {
		assertThat(this.vectorStore.<HazelcastInstance>getNativeClient()).containsSame(this.hazelcastInstance);
	}

	private static final class TestEmbeddingModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			AtomicInteger index = new AtomicInteger();
			return new EmbeddingResponse(request.getInstructions()
				.stream()
				.map(text -> new Embedding(vector(text), index.getAndIncrement()))
				.toList());
		}

		@Override
		public float[] embed(Document document) {
			return vector(document.getText());
		}

		@Override
		public int dimensions() {
			return 3;
		}

		private static float[] vector(String text) {
			if (text != null && text.contains("dog")) {
				return new float[] { 0.0f, 1.0f, 0.0f };
			}
			if (text != null && text.contains("bird")) {
				return new float[] { 0.0f, 0.0f, 1.0f };
			}
			return new float[] { 1.0f, 0.0f, 0.0f };
		}

	}

}
