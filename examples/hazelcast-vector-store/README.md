# Hazelcast Vector Store Example

This is a standalone Spring Boot application that uses the Spring AI Hazelcast vector store starter.
It is intentionally not a module of the Spring AI Maven reactor and does not inherit from the Spring AI parent build.

The application starts three Hazelcast members in the same JVM, connects to them with a Hazelcast client bean, and lets Spring Boot auto-configure the Spring AI `HazelcastVectorStore` from that client and a local deterministic `EmbeddingModel`.

## Prerequisites

The example pins Hazelcast artifacts to `5.8.0-SNAPSHOT` and expects them to already be available in the local Maven repository.

Required local artifacts:

```text
com.hazelcast:hazelcast:5.8.0-SNAPSHOT
com.hazelcast:hazelcast-vector:5.8.0-SNAPSHOT
```

The Spring AI artifacts are resolved through the `spring-ai-bom` version configured in `pom.xml`.
Because this example uses snapshot Spring AI artifacts, install or otherwise place the matching `2.0.1-SNAPSHOT` Spring AI artifacts in local `~/.m2` before running offline.
From this repository, one way to install the relevant Spring AI artifacts is:

```shell
./mvnw -pl "spring-ai-bom,spring-ai-commons,spring-ai-model,spring-ai-vector-store,vector-stores/spring-ai-hazelcast-store,auto-configurations/vector-stores/spring-ai-autoconfigure-vector-store-observation,auto-configurations/vector-stores/spring-ai-autoconfigure-vector-store-hazelcast,starters/spring-ai-starter-vector-store-hazelcast" -am -DskipTests install
```

## Run

From the repository root:

```shell
./mvnw -o -f examples/hazelcast-vector-store/pom.xml spring-boot:run
```

Or from this directory:

```shell
mvn -o spring-boot:run
```

The `-o` flag keeps Maven offline so the pinned Hazelcast snapshot artifacts are taken from local `~/.m2`.

Expected output includes the embedded Hazelcast cluster startup, the Hazelcast client connection, and similarity search results from the Spring AI vector store.
