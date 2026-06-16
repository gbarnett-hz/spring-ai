# Spring AI Hazelcast Vector Store

A Hazelcast-based vector store implementation for Spring AI using the Hazelcast VectorCollection API with HNSW indexing.

## Documentation

For comprehensive documentation, see
the [Hazelcast Vector Store Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/hazelcast.html).

## Features

- Vector similarity search using HNSW
- Support for multiple distance metrics (COSINE, EUCLIDEAN, DOT)
- Automatic score normalization to [0, 1] range per metric
- Configurable backup and index parameters
- Embedded or client-server Hazelcast deployments
- Observation and metrics support via Micrometer

## Usage

### Programmatic Configuration

```java
// Create an embedded Hazelcast instance
HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance();

// Build the vector store
HazelcastVectorStore vectorStore = HazelcastVectorStore.builder(hazelcastInstance, embeddingModel)
    .collectionName("my-collection")
    .initializeSchema(true)
    .metric(Metric.COSINE)
    .build();

// Add documents
vectorStore.add(List.of(
    new Document("content1", Map.of("category", "AI")),
    new Document("content2", Map.of("category", "DB"))
));

// Search
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("AI and machine learning")
        .topK(5)
        .similarityThreshold(0.7)
        .build()
);
```

### Spring Boot Auto-Configuration

Add the starter dependency:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-hazelcast</artifactId>
</dependency>
```

Configure via `application.properties`:

```properties
spring.ai.vectorstore.hazelcast.collection-name=my-collection
spring.ai.vectorstore.hazelcast.initialize-schema=true
spring.ai.vectorstore.hazelcast.metric=COSINE
spring.ai.vectorstore.hazelcast.index-name=my-index
spring.ai.vectorstore.hazelcast.backup-count=1
spring.ai.vectorstore.hazelcast.async-backup-count=0
spring.ai.vectorstore.hazelcast.max-degree=32
spring.ai.vectorstore.hazelcast.ef-construction=128
spring.ai.vectorstore.hazelcast.use-deduplication=true
```

## Configuration Options

The Hazelcast Vector Store supports the following builder options:

| Option | Default | Description |
|---|---|---|
| `collectionName` | `spring-ai-vector-store` | Vector collection name |
| `initializeSchema` | `false` | Create the collection on build |
| `metric` | `COSINE` | Similarity metric |
| `indexName` | `default` | HNSW index name |
| `backupCount` | `1` | Sync backup count |
| `asyncBackupCount` | `0` | Async backup count |
| `maxDegree` | `16` | HNSW max degree |
| `efConstruction` | `200` | HNSW ef construction |
| `useDeduplication` | `true` | Deduplicate equal vectors |

```java
HazelcastVectorStore vectorStore = HazelcastVectorStore.builder(hazelcastInstance, embeddingModel)
    .collectionName("my-collection")
    .initializeSchema(true)
    .metric(Metric.EUCLIDEAN)
    .indexName("my-index")
    .backupCount(2)
    .asyncBackupCount(1)
    .maxDegree(32)
    .efConstruction(128)
    .useDeduplication(false)
    .build();
```

## Distance Metrics

The Hazelcast Vector Store supports three distance metrics via `com.hazelcast.config.vector.Metric`:

- **COSINE**: Cosine similarity (default). Raw range [-1, 1], normalized to [0, 1] with `(score + 1) / 2`.
- **EUCLIDEAN**: Euclidean (L2) distance. Raw range [0, ∞), normalized to [0, 1] with `1 / (1 + score)`.
- **DOT**: Dot product. Raw range (-∞, ∞), normalized to [0, 1] with clamped `(score + 1) / 2`.

Scores are automatically normalized to a 0-1 similarity range, where 1 is most similar.

## Limitations

- Metadata filter expressions are not supported.
- Filter-based delete is not supported.

## Building and Testing

### Build

```bash
# Build this module and required upstream modules
./mvnw -am -pl vector-stores/spring-ai-hazelcast-store package

# Full project build
./mvnw clean package
```

### Test

```bash
# Run all unit tests
./mvnw -am -pl vector-stores/spring-ai-hazelcast-store test

# Run a specific test class
./mvnw -am -pl vector-stores/spring-ai-hazelcast-store -Dtest=HazelcastVectorStoreTests test
```

Tests use embedded Hazelcast instances and require no external infrastructure.
