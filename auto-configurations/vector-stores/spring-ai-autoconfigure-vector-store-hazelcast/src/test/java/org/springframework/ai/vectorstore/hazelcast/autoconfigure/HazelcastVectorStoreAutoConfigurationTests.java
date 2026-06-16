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

import java.util.List;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.hazelcast.HazelcastVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HazelcastVectorStoreAutoConfiguration}.
 *
 * @author Spring AI
 */
class HazelcastVectorStoreAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(HazelcastVectorStoreAutoConfiguration.class));

	@Test
	void createsVectorStoreWhenHazelcastInstanceAndEmbeddingModelExist() {
		HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(new Config().setClusterName("auto-1"));
		this.contextRunner.withBean(HazelcastInstance.class, () -> hazelcastInstance)
			.withBean(EmbeddingModel.class, TestEmbeddingModel::new)
			.run(context -> {
				assertThat(context).hasSingleBean(HazelcastVectorStore.class);
				assertThat(context).hasSingleBean(VectorStore.class);
				context.getBean(HazelcastVectorStore.class)
					.getNativeClient()
					.ifPresent(nativeClient -> assertThat(nativeClient).isSameAs(hazelcastInstance));
			});
		hazelcastInstance.shutdown();
	}

	@Test
	void doesNotCreateHazelcastInstanceWhenAbsent() {
		this.contextRunner.withBean(EmbeddingModel.class, TestEmbeddingModel::new).run(context -> {
			assertThat(context).doesNotHaveBean(HazelcastInstance.class);
			assertThat(context).doesNotHaveBean(HazelcastVectorStore.class);
		});
	}

	private static final class TestEmbeddingModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			return new EmbeddingResponse(request.getInstructions()
				.stream()
				.map(text -> new Embedding(new float[] { 1.0f, 0.0f, 0.0f }, 0))
				.toList());
		}

		@Override
		public float[] embed(Document document) {
			return new float[] { 1.0f, 0.0f, 0.0f };
		}

		@Override
		public List<float[]> embed(List<String> texts) {
			return texts.stream().map(text -> new float[] { 1.0f, 0.0f, 0.0f }).toList();
		}

		@Override
		public int dimensions() {
			return 3;
		}

	}

}
