/*
 * Copyright 2020-2024 the original author or authors.
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

import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.CategoryMask;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Joints;
import ai.djl.spring.configuration.DjlAutoConfiguration;
import ai.djl.spring.configuration.DjlConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * Auto-configuration for TensorFlow Object Detection Function.
 *
 * @author Christian Tzolov
 */
@AutoConfiguration(after = DjlAutoConfiguration.class)
@EnableConfigurationProperties({ DjlConfigurationProperties.class, ComputerVisionFunctionProperties.class })
public class ComputerVisionFunctionConfiguration {

	private static final Logger log = LoggerFactory.getLogger(ComputerVisionFunctionConfiguration.class);

	@Bean
	public ImageFactory imageFactory() {
		return ImageFactory.getInstance();
	}

	@Bean
	@ConditionalOnProperty(prefix = "djl", name = "output-class",
			havingValue = "ai.djl.modality.cv.output.DetectedObjects")
	public Function<Message<byte[]>, Message<byte[]>> objectDetection(Supplier<Predictor<?, ?>> predictorProvider,
			ImageFactory imageFactory, ComputerVisionFunctionProperties cvProperties,
			DjlConfigurationProperties djlProperties) {

		Function<DetectedObjects, String> toJson = (detectedObjects) -> JsonHelper.toJson(detectedObjects);

		BiFunction<DetectedObjects, Image, byte[]> augmentImage = (detectedObjects, image) -> {
			Image newImage = image.duplicate();
			newImage.drawBoundingBoxes(detectedObjects);
			return getByteArray((RenderedImage) newImage.getWrappedImage(), cvProperties.getOutputImageFormatName());
		};

		return predictor(predictorProvider, imageFactory, cvProperties, djlProperties, DetectedObjects.class, toJson,
				augmentImage);
	}

	@Bean
	@ConditionalOnProperty(prefix = "djl", name = "output-class",
			havingValue = "ai.djl.modality.cv.output.CategoryMask")
	public Function<Message<byte[]>, Message<byte[]>> semanticSegmentation(Supplier<Predictor<?, ?>> predictorProvider,
			ImageFactory imageFactory, ComputerVisionFunctionProperties cvProperties,
			DjlConfigurationProperties djlProperties) {

		Function<CategoryMask, String> toJson = (mask) -> JsonHelper.toJson(mask);

		BiFunction<CategoryMask, Image, byte[]> augmentImage = (mask, image) -> {
			Image newImage = image.duplicate();
			mask.drawMask(newImage, 200, 0);
			return getByteArray((RenderedImage) newImage.getWrappedImage(), cvProperties.getOutputImageFormatName());
		};

		return predictor(predictorProvider, imageFactory, cvProperties, djlProperties, CategoryMask.class, toJson,
				augmentImage);
	}

	@Bean
	@ConditionalOnProperty(prefix = "djl", name = "output-class", havingValue = "ai.djl.modality.Classifications")
	public Function<Message<byte[]>, Message<byte[]>> imageClassifications(Supplier<Predictor<?, ?>> predictorProvider,
			ImageFactory imageFactory, ComputerVisionFunctionProperties cvProperties,
			DjlConfigurationProperties djlProperties) {

		Function<Classifications, String> toJson = (classifications) -> JsonHelper.toJson(classifications);

		BiFunction<Classifications, Image, byte[]> augmentImage = (classifications, image) -> {
			Image newImage = image.duplicate();
			return getByteArray((RenderedImage) newImage.getWrappedImage(), cvProperties.getOutputImageFormatName());
		};

		return predictor(predictorProvider, imageFactory, cvProperties, djlProperties, Classifications.class, toJson,
				augmentImage);
	}

	@Bean
	@ConditionalOnProperty(prefix = "djl", name = "output-class", havingValue = "ai.djl.modality.cv.output.Joints")
	public Function<Message<byte[]>, Message<byte[]>> poseEstimation(Supplier<Predictor<?, ?>> predictorProvider,
			ImageFactory imageFactory, ComputerVisionFunctionProperties cvProperties,
			DjlConfigurationProperties djlProperties) {

		Function<Joints, String> toJson = (joins) -> JsonHelper.toJson(joins);

		BiFunction<Joints, Image, byte[]> augmentImage = (joints, image) -> {
			Image newImage = image.duplicate();
			newImage.drawJoints(joints);
			return getByteArray((RenderedImage) newImage.getWrappedImage(), cvProperties.getOutputImageFormatName());
		};

		return predictor(predictorProvider, imageFactory, cvProperties, djlProperties, Joints.class, toJson,
				augmentImage);
	}

	private <T> Function<Message<byte[]>, Message<byte[]>> predictor(Supplier<Predictor<?, ?>> predictorProvider,
			ImageFactory imageFactory, ComputerVisionFunctionProperties cvProperties,
			DjlConfigurationProperties djlProperties, Class<T> outputClass, Function<T, String> toJsonFunction,
			BiFunction<T, Image, byte[]> augmentImageFunction) {

		return (input) -> {

			Predictor<Image, T> predictor = (Predictor<Image, T>) predictorProvider.get();

			try {

				Image image = imageFactory.fromInputStream(new ByteArrayInputStream(input.getPayload()));

				T output = predictor.predict(image);

				String outputJson = toJsonFunction.apply(output);

				byte[] outputImageBytes = input.getPayload();

				if (cvProperties.isAugmentEnabled()) {
					outputImageBytes = augmentImageFunction.apply(output, image);
				}

				String headerName = cvProperties.getOutputHeaderName();
				Message<byte[]> outMessage = MessageBuilder.withPayload(outputImageBytes)
					.setHeader(headerName, outputJson)
					.build();

				return outMessage;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
			finally {
				predictor.close();
			}
		};
	}

	private static byte[] getByteArray(RenderedImage image, String formatName) {
		try {
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			ImageIO.write(image, formatName, byteArrayOutputStream);
			return byteArrayOutputStream.toByteArray();
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

}
