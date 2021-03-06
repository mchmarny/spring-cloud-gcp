/*
 *  Copyright 2018 original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.gcp.data.datastore.it;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.cloud.datastore.Blob;
import com.google.common.collect.ImmutableList;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

/**
 * @author Chengyuan Zhao
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { DatastoreIntegrationTestConfiguration.class })
public class DatastoreIntegrationTests {

	// queries are eventually consistent, so we may need to retry a few times.
	private static final int QUERY_WAIT_ATTEMPTS = 100;

	private static final int QUERY_WAIT_INTERVAL_MILLIS = 1000;

	@Autowired
	private TestEntityRepository testEntityRepository;

	@BeforeClass
	public static void checkToRun() {
		assumeThat(
				"Datastre integration tests are disabled. Please use '-Dit.datastore=true' "
						+ "to enable them. ",
				System.getProperty("it.datastore"), is("true"));
	}

	@Test
	public void testSaveAndDeleteRepository() throws InterruptedException {

		TestEntity testEntityA = new TestEntity("a", "red", "round", null);

		TestEntity testEntityB = new TestEntity("b", "blue", "round", null);

		TestEntity testEntityC = new TestEntity("c", "red", "round", null);

		TestEntity testEntityD = new TestEntity("d", "red", "round", null);

		this.testEntityRepository.saveAll(
				ImmutableList.of(testEntityA, testEntityB, testEntityC, testEntityD));

		assertNull(this.testEntityRepository.findById("a").get().getBlobField());

		testEntityA.setBlobField(Blob.copyFrom("testValueA".getBytes()));

		this.testEntityRepository.save(testEntityA);

		assertEquals(Blob.copyFrom("testValueA".getBytes()),
				this.testEntityRepository.findById("a").get().getBlobField());

		List<TestEntity> foundByCustomQuery = Collections.emptyList();
		for (int i = 0; i < QUERY_WAIT_ATTEMPTS; i++) {
			if (!foundByCustomQuery.isEmpty() && this.testEntityRepository
					.findTop3ByShapeAndColor("round", "red").size() == 3) {
				break;
			}
			Thread.sleep(QUERY_WAIT_INTERVAL_MILLIS);
			foundByCustomQuery = this.testEntityRepository
					.findEntitiesWithCustomQuery("a");
		}
		assertEquals(1, this.testEntityRepository.findTop3ByShapeAndColor("round", "blue")
				.size());
		assertEquals(3,
				this.testEntityRepository.findTop3ByShapeAndColor("round", "red").size());
		assertThat(
				this.testEntityRepository.findTop3ByShapeAndColor("round", "red").stream()
						.map(TestEntity::getId).collect(Collectors.toList()),
				containsInAnyOrder("a", "c", "d"));
		assertEquals(1, foundByCustomQuery.size());
		assertEquals(Blob.copyFrom("testValueA".getBytes()),
				foundByCustomQuery.get(0).getBlobField());

		testEntityA.setBlobField(null);

		this.testEntityRepository.save(testEntityA);

		assertNull(this.testEntityRepository.findById("a").get().getBlobField());

		assertThat(this.testEntityRepository.findAllById(ImmutableList.of("a", "b")),
				iterableWithSize(2));

		this.testEntityRepository.delete(testEntityA);

		assertFalse(this.testEntityRepository.findById("a").isPresent());

		this.testEntityRepository.deleteAll();

		assertFalse(this.testEntityRepository.findAllById(ImmutableList.of("a", "b"))
				.iterator().hasNext());
	}
}
