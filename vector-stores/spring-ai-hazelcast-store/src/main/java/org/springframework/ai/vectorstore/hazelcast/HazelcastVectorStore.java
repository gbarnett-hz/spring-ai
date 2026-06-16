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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import com.hazelcast.config.vector.Metric;
import com.hazelcast.config.vector.VectorCollectionConfig;
import com.hazelcast.config.vector.VectorIndexConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.vector.SearchOptions;
import com.hazelcast.vector.SearchResult;
import com.hazelcast.vector.SearchResults;
import com.hazelcast.vector.VectorCollection;
import com.hazelcast.vector.VectorDocument;
import com.hazelcast.vector.VectorValues;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.util.Assert;

/**
 * Hazelcast-backed {@link VectorStore} implementation using a Hazelcast
 * {@link VectorCollection}.
 *
 * @author Spring AI
 * @since 2.0.0
 */
public class HazelcastVectorStore extends AbstractObservationVectorStore {

	public static final String DEFAULT_COLLECTION_NAME = "spring-ai-vector-store";

	public static final String DEFAULT_INDEX_NAME = "default";

	private final HazelcastInstance hazelcastInstance;

	private final AtomicReference<@Nullable VectorCollection<String, HazelcastDocument>> collection = new AtomicReference<>();

	private final String collectionName;

	private final Metric metric;

	private final int dimensions;

	protected HazelcastVectorStore(Builder builder) {
		super(builder);
		this.hazelcastInstance = builder.hazelcastInstance;
		this.collectionName = builder.collectionName;
		this.metric = builder.metric;
		this.dimensions = this.embeddingModel.dimensions();
		if (builder.initializeSchema) {
			this.collection.set(initializeCollection(builder));
		}
	}

	public static Builder builder(HazelcastInstance hazelcastInstance, EmbeddingModel embeddingModel) {
		return new Builder(hazelcastInstance, embeddingModel);
	}

	@Override
	public void doAdd(List<Document> documents) {
		Assert.notNull(documents, "Documents must not be null");
		Map<String, VectorDocument<HazelcastDocument>> vectorDocuments = new HashMap<>();
		for (Document document : documents) {
			float[] embedding = this.embeddingModel.embed(document);
			HazelcastDocument value = new HazelcastDocument(Objects.requireNonNullElse(document.getText(), ""),
					document.getMetadata());
			vectorDocuments.put(document.getId(), VectorDocument.of(value, VectorValues.of(embedding)));
		}
		join(collection().putAllAsync(vectorDocuments));
	}

	@Override
	public void doDelete(List<String> idList) {
		Assert.notNull(idList, "Document id list must not be null");
		for (String id : idList) {
			join(collection().deleteAsync(id));
		}
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		throw new UnsupportedOperationException("HazelcastVectorStore does not support filter-based delete");
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		Assert.notNull(request, "Search request must not be null");
		if (request.hasFilterExpression()) {
			throw new UnsupportedOperationException(
					"HazelcastVectorStore does not support metadata filter expressions");
		}
		float[] queryEmbedding = this.embeddingModel.embed(request.getQuery());
		SearchOptions options = SearchOptions.builder().includeValue().limit(request.getTopK()).build();
		SearchResults<String, HazelcastDocument> results = join(
				collection().searchAsync(VectorValues.of(queryEmbedding), options));
		List<Document> documents = new ArrayList<>();
		var iterator = results.results();
		while (iterator.hasNext()) {
			Document document = toDocument(iterator.next());
			if (document.getScore() != null && document.getScore() >= request.getSimilarityThreshold()) {
				documents.add(document);
			}
		}
		return documents;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Optional<T> getNativeClient() {
		return Optional.of((T) this.hazelcastInstance);
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		VectorStoreSimilarityMetric similarityMetric = switch (this.metric) {
			case COSINE -> VectorStoreSimilarityMetric.COSINE;
			case EUCLIDEAN -> VectorStoreSimilarityMetric.EUCLIDEAN;
			case DOT -> VectorStoreSimilarityMetric.DOT;
		};
		return VectorStoreObservationContext.builder(VectorStoreProvider.HAZELCAST.value(), operationName)
			.collectionName(this.collectionName)
			.dimensions(this.dimensions)
			.similarityMetric(similarityMetric.value());
	}

	private VectorCollection<String, HazelcastDocument> initializeCollection(Builder builder) {
		VectorIndexConfig indexConfig = new VectorIndexConfig(builder.indexName, builder.metric, this.dimensions,
				builder.maxDegree, builder.efConstruction, builder.useDeduplication);
		VectorCollectionConfig collectionConfig = new VectorCollectionConfig(builder.collectionName)
			.addVectorIndexConfig(indexConfig)
			.setBackupCount(builder.backupCount)
			.setAsyncBackupCount(builder.asyncBackupCount);
		return VectorCollection.getCollection(this.hazelcastInstance, collectionConfig);
	}

	private VectorCollection<String, HazelcastDocument> collection() {
		VectorCollection<String, HazelcastDocument> result = this.collection.updateAndGet(current -> {
			if (current != null) {
				return current;
			}
			return VectorCollection.getCollection(this.hazelcastInstance, this.collectionName);
		});
		Assert.state(result != null, "VectorCollection must not be null");
		return result;
	}

	private Document toDocument(SearchResult<String, HazelcastDocument> result) {
		HazelcastDocument value = result.getValue();
		Assert.notNull(value, "Hazelcast search result value must not be null");
		String key = result.getKey();
		Assert.notNull(key, "Hazelcast search result key must not be null");
		double score = normalizeScore(result.getScore());
		Map<String, Object> metadata = new HashMap<>(value.metadata());
		metadata.put(DocumentMetadata.DISTANCE.value(), 1.0 - score);
		return Document.builder().id(key).text(value.text()).metadata(metadata).score(score).build();
	}

	/**
	 * Normalizes a raw search score to a similarity value in [0, 1] where higher is more
	 * similar. Hazelcast's {@link SearchResult#getScore()} returns the score as computed
	 * by the HNSW index, whose meaning depends on the configured {@link Metric}:
	 * <ul>
	 * <li><b>COSINE</b>: cosine similarity, range [-1, 1], higher is better. Mapped to
	 * [0, 1] with {@code (score + 1) / 2} &mdash; consistent with how
	 * {@code RedisVectorStore} normalizes cosine metrics.</li>
	 * <li><b>EUCLIDEAN</b>: Euclidean (L2) distance, range [0, &infin;), lower is better.
	 * Converted with the standard distance-to-similarity transformation
	 * {@code 1 / (1 + score)}.</li>
	 * <li><b>DOT</b>: dot product, range (-&infin;, &infin;), higher is better. For
	 * unit-normalized vectors the range is typically [-1, 1]; mapped with
	 * {@code (score + 1) / 2} and clamped to [0, 1].</li>
	 * </ul>
	 * @param score the raw score from the HNSW search
	 * @return a normalized similarity score in {@code [0, 1]}
	 */
	private double normalizeScore(float score) {
		return switch (this.metric) {
			case COSINE -> (score + 1.0) / 2.0;
			case EUCLIDEAN -> 1.0 / (1.0 + score);
			case DOT -> Math.min(Math.max((score + 1.0) / 2.0, 0.0), 1.0);
		};
	}

	private static <T> T join(CompletionStage<T> stage) {
		try {
			return stage.toCompletableFuture().join();
		}
		catch (CompletionException ex) {
			if (ex.getCause() instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw ex;
		}
	}

	/**
	 * Builder for {@link HazelcastVectorStore}.
	 *
	 * @since 2.0.0
	 */
	public static final class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final HazelcastInstance hazelcastInstance;

		private String collectionName = DEFAULT_COLLECTION_NAME;

		private boolean initializeSchema;

		private Metric metric = Metric.COSINE;

		private String indexName = DEFAULT_INDEX_NAME;

		private int backupCount = VectorCollectionConfig.DEFAULT_BACKUP_COUNT;

		private int asyncBackupCount;

		private int maxDegree = VectorIndexConfig.DEFAULT_MAX_DEGREE;

		private int efConstruction = VectorIndexConfig.DEFAULT_EF_CONSTRUCTION;

		private boolean useDeduplication = VectorIndexConfig.DEFAULT_USE_DEDUPLICATION;

		private Builder(HazelcastInstance hazelcastInstance, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(hazelcastInstance, "HazelcastInstance must not be null");
			this.hazelcastInstance = hazelcastInstance;
		}

		public Builder collectionName(String collectionName) {
			Assert.hasText(collectionName, "Collection name must not be empty");
			this.collectionName = collectionName;
			return this;
		}

		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		public Builder metric(Metric metric) {
			Assert.notNull(metric, "Metric must not be null");
			this.metric = metric;
			return this;
		}

		public Builder indexName(String indexName) {
			Assert.hasText(indexName, "Index name must not be empty");
			this.indexName = indexName;
			return this;
		}

		public Builder backupCount(int backupCount) {
			this.backupCount = backupCount;
			return this;
		}

		public Builder asyncBackupCount(int asyncBackupCount) {
			this.asyncBackupCount = asyncBackupCount;
			return this;
		}

		public Builder maxDegree(int maxDegree) {
			this.maxDegree = maxDegree;
			return this;
		}

		public Builder efConstruction(int efConstruction) {
			this.efConstruction = efConstruction;
			return this;
		}

		public Builder useDeduplication(boolean useDeduplication) {
			this.useDeduplication = useDeduplication;
			return this;
		}

		public HazelcastVectorStore build() {
			return new HazelcastVectorStore(this);
		}

	}

	/**
	 * Stored Hazelcast document payload.
	 *
	 * @param text the document text
	 * @param metadata the document metadata
	 */
	public record HazelcastDocument(String text, Map<String, Object> metadata) implements Serializable {

		public HazelcastDocument {
			metadata = new HashMap<>(metadata);
		}

	}

}
