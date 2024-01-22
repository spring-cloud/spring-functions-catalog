/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.cloud.fn.computer.vision;

import java.util.List;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.output.CategoryMask;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class JsonHelperTests {

	@Test
	public void categoryMask() {

		var categoryMask = new CategoryMask(List.of("a", "b", "c"), new int[][] { { 1, 2, 3 }, { 4, 5, 6 } });

		var json = JsonHelper.toJson(categoryMask);

		assertThat(json).isNotEmpty();

		var categoryMask2 = JsonHelper.toCategoryMask(json);

		assertThat(categoryMask.getClasses()).isEqualTo(categoryMask2.getClasses());
		assertThat(categoryMask.getMask()).isEqualTo(categoryMask2.getMask());
	}

	@Test
	public void classifications() {

		var classifications = new Classifications(List.of("a", "b", "c"), List.of(0.1, 0.2, 0.3));
		classifications.setTopK(3);

		var json = JsonHelper.toJson(classifications);

		assertThat(json).isNotEmpty();

		var classifications2 = JsonHelper.toClassifications(json);

		assertThat(classifications2.getClassNames()).isEqualTo(classifications.getClassNames());
		assertThat(classifications2.getProbabilities()).isEqualTo(classifications.getProbabilities());
		assertThat(classifications2.topK()).hasSize(3);
	}

	@Test
	public void detectedObjects() {
		DetectedObjects detectedObjects = new DetectedObjects(List.of("a", "b", "c"), List.of(0.1, 0.2, 0.3),
				List.of(new Rectangle(1, 2, 3, 4), new Rectangle(5, 6, 7, 8), new Rectangle(9, 10, 11, 12)));
		detectedObjects.setTopK(3);

		var json = JsonHelper.toJson(detectedObjects);

		assertThat(json).isNotEmpty();

		var detectedObjects2 = JsonHelper.toDetectedObjects(json);

		assertThat(detectedObjects2.getClassNames()).isEqualTo(detectedObjects.getClassNames());
		assertThat(detectedObjects2.getProbabilities()).isEqualTo(detectedObjects.getProbabilities());
		assertThat(detectedObjects2.topK()).hasSize(3);

		assertThat(detectedObjects2.getNumberOfObjects()).isEqualTo(3);
	}

}
