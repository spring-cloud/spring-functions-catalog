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

import java.lang.reflect.Type;
import java.util.List;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.CategoryMask;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Joints;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.util.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Helper class to serialize and deserialize {@link DetectedObjects},
 * {@link Classifications}, {@link CategoryMask} and {@link Joints} to/from JSON.
 *
 * @author Christian Tzolov
 */
public final class JsonHelper {

	private static final Gson GSON = JsonUtils.builder().create();

	private JsonHelper() {
	}

	public static String toJson(Joints joints) {
		return GSON.toJson(joints);
	}

	public static Joints toJoints(String json) {
		return GSON.fromJson(json, Joints.class);
	}

	public static String toJson(CategoryMask categoryMask) {
		return GSON.toJson(Mask.fromCategoryMask(categoryMask));
	}

	public static CategoryMask toCategoryMask(String json) {
		return GSON.fromJson(json, Mask.class).toCategoryMask();
	}

	public static String toJson(Classifications classifications) {
		return GSON.toJson(classifications);
	}

	public static Classifications toClassifications(String json) {
		return GSON.fromJson(json, Classifications.class);
	}

	private static final Gson GSON2 = JsonUtils.builder()
		.registerTypeAdapter(BoundingBox.class, new BoundingBoxAdapter())
		.create();

	public static String toJson(DetectedObjects detectedObject) {
		return GSON2.toJson(detectedObject);
	}

	public static DetectedObjects toDetectedObjects(String json) {
		return GSON2.fromJson(json, DetectedObjects.class);
	}

	public record Mask(List<String> classes, int[][] mask) {

		public static Mask fromCategoryMask(CategoryMask categoryMask) {
			return new Mask(categoryMask.getClasses(), categoryMask.getMask());
		}

		public CategoryMask toCategoryMask() {
			return new CategoryMask(this.classes, this.mask);
		}
	}

	public static class BoundingBoxAdapter implements JsonSerializer<BoundingBox>, JsonDeserializer<BoundingBox> {

		@Override
		public JsonElement serialize(BoundingBox boundingBox, Type typeOfSrc, JsonSerializationContext context) {
			return context.serialize(boundingBox);
		}

		@Override
		public BoundingBox deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			return context.deserialize(json, Rectangle.class);
		}

	}

}
