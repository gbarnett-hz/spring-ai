package example.hazelcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HazelcastVectorStoreExampleApplication {

	private static final String CLUSTER_NAME = "spring-ai-hazelcast-example";

	private static final int MEMBER_COUNT = 3;

	private static final int FIRST_PORT = 5701;

	private static final Logger logger = LoggerFactory.getLogger(HazelcastVectorStoreExampleApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(HazelcastVectorStoreExampleApplication.class, args);
	}

	@Bean(destroyMethod = "stop")
	HazelcastCluster hazelcastCluster() {
		return new HazelcastCluster();
	}

	@Bean(destroyMethod = "shutdown")
	HazelcastInstance hazelcastClient(HazelcastCluster cluster) {
		cluster.start();
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.setClusterName(CLUSTER_NAME);
		for (int port = FIRST_PORT; port < FIRST_PORT + MEMBER_COUNT; port++) {
			clientConfig.getNetworkConfig().addAddress("127.0.0.1:" + port);
		}
		return HazelcastClient.newHazelcastClient(clientConfig);
	}

	@Bean
	EmbeddingModel embeddingModel() {
		return new KeywordEmbeddingModel();
	}

	@Bean
	CommandLineRunner demo(VectorStore vectorStore, ConfigurableApplicationContext context) {
		return args -> {
			try {
				List<Document> documents = List.of(
						new Document("cat", "Cats purr and like quiet sunny windows.", Map.of("kind", "cat")),
						new Document("dog", "Dogs bark and like long walks outside.", Map.of("kind", "dog")),
						new Document("bird", "Birds fly and sing from tall trees.", Map.of("kind", "bird")));

				vectorStore.add(documents);

				List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("Which animal likes to purr?")
					.topK(2)
					.similarityThresholdAll()
					.build());

				logger.info("Similarity search results:");
				for (Document result : results) {
					logger.info("id={} score={} text={}", result.getId(), result.getScore(), result.getText());
				}
			}
			finally {
				context.close();
			}
		};
	}

	static final class HazelcastCluster {

		private final List<HazelcastInstance> members = new ArrayList<>();

		private volatile boolean running;

		public synchronized void start() {
			if (this.running) {
				return;
			}
			for (int i = 0; i < MEMBER_COUNT; i++) {
				this.members.add(Hazelcast.newHazelcastInstance(memberConfig(FIRST_PORT + i)));
			}
			this.running = true;
		}

		public synchronized void stop() {
			for (HazelcastInstance member : this.members) {
				member.shutdown();
			}
			this.members.clear();
			this.running = false;
		}

		private static Config memberConfig(int port) {
			Config config = new Config();
			config.setClusterName(CLUSTER_NAME);
			config.setProperty("hazelcast.phone.home.enabled", "false");
			config.getNetworkConfig().setPort(port).setPortAutoIncrement(false);
			JoinConfig join = config.getNetworkConfig().getJoin();
			join.getMulticastConfig().setEnabled(false);
			join.getTcpIpConfig().setEnabled(true);
			for (int memberPort = FIRST_PORT; memberPort < FIRST_PORT + MEMBER_COUNT; memberPort++) {
				join.getTcpIpConfig().addMember("127.0.0.1:" + memberPort);
			}
			return config;
		}

	}

	static final class KeywordEmbeddingModel implements EmbeddingModel {

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
			String value = text != null ? text.toLowerCase() : "";
			if (value.contains("dog") || value.contains("bark") || value.contains("walk")) {
				return new float[] { 0.0f, 1.0f, 0.0f };
			}
			if (value.contains("bird") || value.contains("fly") || value.contains("sing")) {
				return new float[] { 0.0f, 0.0f, 1.0f };
			}
			return new float[] { 1.0f, 0.0f, 0.0f };
		}

	}

}
