/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.cloud.fn.computer.vision.translator;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;
import ai.djl.util.JsonUtils;
import com.google.gson.annotations.SerializedName;

/**
 * A {@link NoBatchifyTranslator} that post-processes the output of a TensorFlow
 * SavedModel Object Detection model.
 *
 * @author Christian Tzolov
 */
public final class TensorflowSavedModelObjectDetectionTranslator
		implements NoBatchifyTranslator<Image, DetectedObjects> {

	private static final String ITEM_DELIMITER = "item ";

	private static final String DEFAULT_MSCOCO_LABELS_URL = "https://raw.githubusercontent.com/tensorflow/models/master/research/object_detection/data/mscoco_label_map.pbtxt";

	private static final String DETECTION_BOXES = "detection_boxes";

	private static final String DETECTION_SCORES = "detection_scores";

	private static final String DETECTION_CLASSES = "detection_classes";

	private String classLabelsUrl;

	private Map<Integer, String> classLabels;

	private int maxBoxes;

	private float threshold;

	public TensorflowSavedModelObjectDetectionTranslator() {
		this(DEFAULT_MSCOCO_LABELS_URL, 10, 0.3f);
	}

	public TensorflowSavedModelObjectDetectionTranslator(String categoryLabelsUrl, int maxBoxes, float threshold) {
		this.classLabelsUrl = categoryLabelsUrl;
		this.maxBoxes = maxBoxes;
		this.threshold = threshold;
	}

	/** {@inheritDoc} */
	@Override
	public NDList processInput(TranslatorContext ctx, Image input) {
		// input to tf object-detection models is a list of tensors, hence NDList
		NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR);
		// optionally resize the image for faster processing
		array = NDImageUtils.resize(array, 224);
		// tf object-detection models expect 8 bit unsigned integer tensor
		array = array.toType(DataType.UINT8, true);
		// tf object-detection models expect a 4 dimensional input
		array = array.expandDims(0);

		return new NDList(array);
	}

	/** {@inheritDoc} */
	@Override
	public void prepare(TranslatorContext ctx) throws IOException {
		if (this.classLabels == null) {
			this.classLabels = loadSynset();
		}
	}

	private Map<Integer, String> loadSynset() throws IOException {
		Map<Integer, String> map = new ConcurrentHashMap<>();
		int maxId = 0;
		try (InputStream is = new BufferedInputStream(URI.create(this.classLabelsUrl).toURL().openStream());
				Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {

			scanner.useDelimiter(ITEM_DELIMITER);
			while (scanner.hasNext()) {
				String content = scanner.next();
				content = content.replaceAll("(\"|\\d)\\n\\s", "$1,");
				Item item = JsonUtils.GSON.fromJson(content, Item.class);
				map.put(item.id, item.displayName);
				if (item.id > maxId) {
					maxId = item.id;
				}
			}
		}
		return map;
	}

	/** {@inheritDoc} */
	@Override
	public DetectedObjects processOutput(TranslatorContext ctx, NDList list) {
		// output of tf object-detection models is a list of tensors, hence NDList in djl
		// output NDArray order in the list are not guaranteed

		int[] classIds = null;
		float[] probabilities = null;
		NDArray boundingBoxes = null;
		for (NDArray array : list) {
			if (DETECTION_BOXES.equals(array.getName())) {
				boundingBoxes = array.get(0);
			}
			else if (DETECTION_SCORES.equals(array.getName())) {
				probabilities = array.get(0).toFloatArray();
			}
			else if (DETECTION_CLASSES.equals(array.getName())) {
				// class id is between 1 - number of classes
				classIds = array.get(0).toType(DataType.INT32, true).toIntArray();
			}
		}
		Objects.requireNonNull(classIds);
		Objects.requireNonNull(probabilities);
		Objects.requireNonNull(boundingBoxes);

		List<String> retNames = new ArrayList<>();
		List<Double> retProbs = new ArrayList<>();
		List<BoundingBox> retBB = new ArrayList<>();

		// result are already sorted
		for (int i = 0; i < Math.min(classIds.length, this.maxBoxes); ++i) {
			int classId = classIds[i];
			double probability = probabilities[i];
			// classId starts from 1, -1 means background
			if (classId > 0 && probability > this.threshold) {
				String className = this.classLabels.getOrDefault(classId, "#" + classId);
				float[] box = boundingBoxes.get(i).toFloatArray();
				float yMin = box[0];
				float xMin = box[1];
				float yMax = box[2];
				float xMax = box[3];
				Rectangle rect = new Rectangle(xMin, yMin, xMax - xMin, yMax - yMin);
				retNames.add(className);
				retProbs.add(probability);
				retBB.add(rect);
			}
		}

		return new DetectedObjects(retNames, retProbs, retBB);
	}

	private static final class Item {

		int id;

		@SerializedName("display_name")
		String displayName;

	}

}
