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

package org.springframework.cloud.fn.computer.vision;

import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * A configuration class that provides the necessary beans for the Computer Vision
 * Function.
 *
 * @author Christian Tzolov
 */
@AutoConfiguration(after = DjlAutoConfiguration.class)
@EnableConfigurationProperties(ComputerVisionFunctionProperties.class)
public class ComputerVisionFunctionConfiguration {

	private static final ImageFactory IMAGE_FACTORY = ImageFactory.getInstance();

	private final Supplier<Predictor<?, ?>> predictorProvider;

	private final ComputerVisionFunctionProperties cvProperties;

	public ComputerVisionFunctionConfiguration(Supplier<Predictor<?, ?>> predictorProvider,
			ComputerVisionFunctionProperties cvProperties) {

		this.predictorProvider = predictorProvider;
		this.cvProperties = cvProperties;
	}

	@Bean(name = "computerVisionFunction")
	@ConditionalOnProperty(prefix = "djl", name = "output-class",
			havingValue = "ai.djl.modality.cv.output.DetectedObjects")
	public Function<Message<byte[]>, Message<byte[]>> objectDetection() {

		BiFunction<DetectedObjects, Image, byte[]> augmentImage = (detectedObjects, image) -> {
			Image newImage = image.duplicate();
			newImage.drawBoundingBoxes(detectedObjects);
			return getByteArray((RenderedImage) newImage.getWrappedImage(),
					this.cvProperties.getOutputImageFormatName());
		};

		return predictor(JsonHelper::toJson, augmentImage);
	}

	@Bean(name = "computerVisionFunction")
	@ConditionalOnProperty(prefix = "djl", name = "output-class",
			havingValue = "ai.djl.modality.cv.output.CategoryMask")
	public Function<Message<byte[]>, Message<byte[]>> semanticSegmentation() {
		BiFunction<CategoryMask, Image, byte[]> augmentImage = (mask, image) -> {
			Image newImage = image.duplicate();
			mask.drawMask(newImage, 200, 0);
			return getByteArray((RenderedImage) newImage.getWrappedImage(),
					this.cvProperties.getOutputImageFormatName());
		};

		return predictor(JsonHelper::toJson, augmentImage);
	}

	@Bean(name = "computerVisionFunction")
	@ConditionalOnProperty(prefix = "djl", name = "output-class", havingValue = "ai.djl.modality.Classifications")
	public Function<Message<byte[]>, Message<byte[]>> imageClassifications() {
		BiFunction<Classifications, Image, byte[]> augmentImage = (classifications, image) -> {
			Image newImage = image.duplicate();
			return getByteArray((RenderedImage) newImage.getWrappedImage(),
					this.cvProperties.getOutputImageFormatName());
		};

		return predictor(JsonHelper::toJson, augmentImage);
	}

	@Bean(name = "computerVisionFunction")
	@ConditionalOnProperty(prefix = "djl", name = "output-class", havingValue = "ai.djl.modality.cv.output.Joints")
	public Function<Message<byte[]>, Message<byte[]>> poseEstimation() {
		BiFunction<Joints, Image, byte[]> augmentImage = (joints, image) -> {
			Image newImage = image.duplicate();
			newImage.drawJoints(joints);
			return getByteArray((RenderedImage) newImage.getWrappedImage(),
					this.cvProperties.getOutputImageFormatName());
		};

		return predictor(JsonHelper::toJson, augmentImage);
	}

	@SuppressWarnings("unchecked")
	private <T> Function<Message<byte[]>, Message<byte[]>> predictor(Function<T, String> toJsonFunction,
			BiFunction<T, Image, byte[]> augmentImageFunction) {

		return (input) -> {

			try (Predictor<Image, T> predictor = (Predictor<Image, T>) this.predictorProvider.get()) {
				Image image = IMAGE_FACTORY.fromInputStream(new ByteArrayInputStream(input.getPayload()));

				T output = predictor.predict(image);

				String outputJson = toJsonFunction.apply(output);

				byte[] outputImageBytes = input.getPayload();

				if (this.cvProperties.isAugmentEnabled()) {
					outputImageBytes = augmentImageFunction.apply(output, image);
				}

				String headerName = this.cvProperties.getOutputHeaderName();

				return MessageBuilder.withPayload(outputImageBytes).setHeader(headerName, outputJson).build();
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
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
			throw new UncheckedIOException(ex);
		}
	}

}
