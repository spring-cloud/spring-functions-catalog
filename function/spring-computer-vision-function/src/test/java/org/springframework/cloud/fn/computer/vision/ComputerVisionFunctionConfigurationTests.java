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

import java.util.function.Function;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.CategoryMask;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Joints;
import ai.djl.modality.cv.translator.SemanticSegmentationTranslatorFactory;
import ai.djl.modality.cv.translator.YoloV8TranslatorFactory;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.spring.configuration.ApplicationType;
import ai.djl.spring.configuration.DjlAutoConfiguration;
import ai.djl.spring.configuration.DjlConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.fn.computer.vision.translator.TensorflowSavedModelObjectDetectionTranslatorFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class ComputerVisionFunctionConfigurationTests {

	private ApplicationContextRunner applicationContextRunner;

	@BeforeEach
	public void setUp() {
		applicationContextRunner = new ApplicationContextRunner().withConfiguration(
				AutoConfigurations.of(DjlAutoConfiguration.class, ComputerVisionFunctionConfiguration.class));
	}

	/**
	 * This configuration can be used to load any of the Tensorflow2 models for object
	 * detection from here:
	 * https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/tf2_detection_zoo.md
	 */
	@Test
	public void tf2SavedModel() {
		applicationContextRunner.withPropertyValues(
		// @formatter:off
			"computer.vision.function.augment-enabled=true",
				"djl.application-type=" + ApplicationType.OBJECT_DETECTION,
				"djl.input-class=" + Image.class.getName(),
				"djl.output-class=" + DetectedObjects.class.getName(),
				"djl.engine=TensorFlow",
				"djl.urls=http://download.tensorflow.org/models/object_detection/tf2/20200711/faster_rcnn_inception_resnet_v2_1024x1024_coco17_tpu-8.tar.gz",
				"djl.model-name=saved_model",
				"djl.translator-factory=" + TensorflowSavedModelObjectDetectionTranslatorFactory.class.getName(),
				"djl.arguments.threshold=0.3")
			// @formatter:on
			.run((context) -> {
				assertThat(context).hasSingleBean(ZooModel.class);
				assertThat(context).hasBean("predictorProvider");
				@SuppressWarnings("unchecked")
				Function<Message<byte[]>, Message<byte[]>> predictor = (Function<Message<byte[]>, Message<byte[]>>) context
					.getBean("computerVisionFunction");

				var djlProperties = context.getBean(DjlConfigurationProperties.class);

				assertThat(djlProperties.getApplicationType()).isEqualTo(ApplicationType.OBJECT_DETECTION);
				assertThat(djlProperties.getInputClass()).isEqualTo(Image.class);
				assertThat(djlProperties.getOutputClass()).isEqualTo(DetectedObjects.class);
				assertThat(djlProperties.getEngine()).isEqualTo("TensorFlow");
				assertThat(djlProperties.getUrls()).contains(
						"http://download.tensorflow.org/models/object_detection/tf2/20200711/faster_rcnn_inception_resnet_v2_1024x1024_coco17_tpu-8.tar.gz");
				assertThat(djlProperties.getModelName()).isEqualTo("saved_model");
				assertThat(djlProperties.getTranslatorFactory())
					.isEqualTo(TensorflowSavedModelObjectDetectionTranslatorFactory.class.getName());

				byte[] inputImage = new ClassPathResource("/object-detection.jpg").getInputStream().readAllBytes();

				Message<byte[]> outputMessage = predictor.apply(MessageBuilder.withPayload(inputImage).build());

				assertThat(outputMessage).isNotNull();
				assertThat(outputMessage.getPayload()).isNotNull();
				assertThat(outputMessage.getPayload().length).isGreaterThan(0);
				assertThat(outputMessage.getHeaders()).containsKey("cvjson");

				var json = outputMessage.getHeaders().get("cvjson", String.class);

				assertThat(JsonHelper.toDetectedObjects(json)).isNotNull();
			});
	}

	@Test
	public void yolov8Detection() {
		applicationContextRunner.withPropertyValues(
		// @formatter:off
					"computer.vision.function.augment-enabled=true",
					"djl.application-type=" + ApplicationType.OBJECT_DETECTION,
					"djl.input-class=" + Image.class.getName(),
					"djl.output-class=" + DetectedObjects.class.getName(),
					"djl.engine=OnnxRuntime",
					"djl.urls=djl://ai.djl.onnxruntime/yolov8n",
					"djl.translator-factory=" + YoloV8TranslatorFactory.class.getName(),
					"djl.arguments.threshold=0.3",
					"djl.arguments.width=640",
					"djl.arguments.height=640",
					"djl.arguments.resize=true",
					"djl.arguments.toTensor=true",
					"djl.arguments.applyRatio=true",
					"djl.arguments.maxBox=1000")
				// @formatter:on
			.run((context) -> {
				assertThat(context).hasSingleBean(ZooModel.class);
				assertThat(context).hasBean("predictorProvider");
				@SuppressWarnings("unchecked")
				Function<Message<byte[]>, Message<byte[]>> predictor = (Function<Message<byte[]>, Message<byte[]>>) context
					.getBean("computerVisionFunction");

				var djlProperties = context.getBean(DjlConfigurationProperties.class);

				assertThat(djlProperties.getApplicationType()).isEqualTo(ApplicationType.OBJECT_DETECTION);
				assertThat(djlProperties.getInputClass()).isEqualTo(Image.class);
				assertThat(djlProperties.getOutputClass()).isEqualTo(DetectedObjects.class);
				assertThat(djlProperties.getEngine()).isEqualTo("OnnxRuntime");
				assertThat(djlProperties.getUrls()).contains("djl://ai.djl.onnxruntime/yolov8n");
				assertThat(djlProperties.getTranslatorFactory()).isEqualTo(YoloV8TranslatorFactory.class.getName());

				byte[] inputImage = new ClassPathResource("/object-detection.jpg").getInputStream().readAllBytes();

				Message<byte[]> outputMessage = predictor.apply(MessageBuilder.withPayload(inputImage).build());

				assertThat(outputMessage).isNotNull();
				assertThat(outputMessage.getPayload()).isNotNull();
				assertThat(outputMessage.getPayload().length).isGreaterThan(0);
				assertThat(outputMessage.getHeaders()).containsKey("cvjson");

				var json = outputMessage.getHeaders().get("cvjson", String.class);

				var detectionObjects = JsonHelper.toDetectedObjects(json);

				assertThat(detectionObjects).isNotNull();
			});

	}

	@Test
	public void instanceSegmentation() {
		applicationContextRunner.withPropertyValues(
		// @formatter:off
					"computer.vision.function.augment-enabled=true",
					"djl.application-type=" + ApplicationType.INSTANCE_SEGMENTATION,
					"djl.input-class=" + Image.class.getName(),
					"djl.output-class=" + DetectedObjects.class.getName(),
					"djl.arguments.threshold=0.3",

					"djl.model-filter.backbone=resnet18",
					"djl.model-filter.flavor=v1b",
					"djl.model-filter.dataset=coco")
				// @formatter:on
			.run((context) -> {
				assertThat(context).hasSingleBean(ZooModel.class);
				assertThat(context).hasBean("predictorProvider");
				@SuppressWarnings("unchecked")
				Function<Message<byte[]>, Message<byte[]>> predictor = (Function<Message<byte[]>, Message<byte[]>>) context
					.getBean("computerVisionFunction");

				var djlProperties = context.getBean(DjlConfigurationProperties.class);

				assertThat(djlProperties.getApplicationType()).isEqualTo(ApplicationType.INSTANCE_SEGMENTATION);
				assertThat(djlProperties.getInputClass()).isEqualTo(Image.class);
				assertThat(djlProperties.getOutputClass()).isEqualTo(DetectedObjects.class);

				// byte[] inputImage = new
				// ClassPathResource("/object-detection.jpg").getInputStream().readAllBytes();
				byte[] inputImage = new ClassPathResource("/amsterdam-cityscape.jpg").getInputStream().readAllBytes();

				Message<byte[]> outputMessage = predictor.apply(MessageBuilder.withPayload(inputImage).build());

				assertThat(outputMessage).isNotNull();
				assertThat(outputMessage.getPayload()).isNotNull();
				assertThat(outputMessage.getPayload().length).isGreaterThan(0);
				assertThat(outputMessage.getHeaders()).containsKey("cvjson");
				String json = outputMessage.getHeaders().get("cvjson", String.class);

				assertThat(JsonHelper.toDetectedObjects(json)).isNotNull();
			});
	}

	@DisabledOnOs(OS.WINDOWS)
	@Test
	public void semanticSegmentation() {
		applicationContextRunner.withPropertyValues(
		// @formatter:off
					"computer.vision.function.augment-enabled=true",
					"djl.application-type=" + ApplicationType.SEMANTIC_SEGMENTATION,
					"djl.input-class=" + Image.class.getName(),
					"djl.output-class=" + CategoryMask.class.getName(),
					"djl.arguments.threshold=0.3",

					"djl.urls=https://mlrepo.djl.ai/model/cv/semantic_segmentation/ai/djl/pytorch/deeplabv3/0.0.1/deeplabv3.zip",
					"djl.translator-factory=" + SemanticSegmentationTranslatorFactory.class.getName(),
					"djl.engine=PyTorch")
				// @formatter:on
			.run((context) -> {
				assertThat(context).hasSingleBean(ZooModel.class);
				assertThat(context).hasBean("predictorProvider");

				@SuppressWarnings("unchecked")
				Function<Message<byte[]>, Message<byte[]>> predictor = (Function<Message<byte[]>, Message<byte[]>>) context
					.getBean("computerVisionFunction");

				var djlProperties = context.getBean(DjlConfigurationProperties.class);

				assertThat(djlProperties.getApplicationType()).isEqualTo(ApplicationType.SEMANTIC_SEGMENTATION);
				assertThat(djlProperties.getInputClass()).isEqualTo(Image.class);
				assertThat(djlProperties.getOutputClass()).isEqualTo(CategoryMask.class);

				byte[] inputImage = new ClassPathResource("/amsterdam-cityscape.jpg").getInputStream().readAllBytes();

				Message<byte[]> outputMessage = predictor.apply(MessageBuilder.withPayload(inputImage).build());

				assertThat(outputMessage).isNotNull();
				assertThat(outputMessage.getPayload()).isNotNull();
				assertThat(outputMessage.getPayload().length).isGreaterThan(0);
				assertThat(outputMessage.getHeaders()).containsKey("cvjson");

				String ssJson = outputMessage.getHeaders().get("cvjson", String.class);

				assertThat(JsonHelper.toCategoryMask(ssJson)).isNotNull();
			});
	}

	@Test
	public void imageClassifications() {
		applicationContextRunner.withPropertyValues(
		// @formatter:off
					"computer.vision.function.augment-enabled=false",
					"djl.application-type=" + ApplicationType.IMAGE_CLASSIFICATION,
					"djl.input-class=" + Image.class.getName(),
					"djl.output-class=" + Classifications.class.getName(),
					"djl.arguments.threshold=0.3",
					"djl.engine=MXNet")
				// @formatter:on
			.run((context) -> {
				assertThat(context).hasSingleBean(ZooModel.class);
				assertThat(context).hasBean("predictorProvider");

				@SuppressWarnings("unchecked")
				Function<Message<byte[]>, Message<byte[]>> predictor = (Function<Message<byte[]>, Message<byte[]>>) context
					.getBean("computerVisionFunction");

				var djlProperties = context.getBean(DjlConfigurationProperties.class);

				assertThat(djlProperties.getApplicationType()).isEqualTo(ApplicationType.IMAGE_CLASSIFICATION);
				assertThat(djlProperties.getInputClass()).isEqualTo(Image.class);
				assertThat(djlProperties.getOutputClass()).isEqualTo(Classifications.class);

				byte[] inputImage = new ClassPathResource("/karakatschan.jpg").getInputStream().readAllBytes();

				Message<byte[]> outputMessage = predictor.apply(MessageBuilder.withPayload(inputImage).build());

				assertThat(outputMessage).isNotNull();
				assertThat(outputMessage.getPayload()).isNotNull();
				assertThat(outputMessage.getPayload().length).isGreaterThan(0);
				assertThat(outputMessage.getHeaders()).containsKey("cvjson");

				String json = outputMessage.getHeaders().get("cvjson", String.class);

				assertThat(JsonHelper.toClassifications(json)).isNotNull();
			});
	}

	@Test
	public void poseEstimation() {
		applicationContextRunner.withPropertyValues(
		// @formatter:off
					"computer.vision.function.augment-enabled=true",
					"djl.application-type=" + ApplicationType.POSE_ESTIMATION,
					"djl.input-class=" + Image.class.getName(),
					"djl.output-class=" + Joints.class.getName(),
					"djl.arguments.threshold=0.3",
					"djl.model-filter.backbone=resnet18",
					"djl.model-filter.flavor=v1b",
					"djl.model-filter.dataset=imagenet")
				// @formatter:on
			.run((context) -> {
				assertThat(context).hasSingleBean(ZooModel.class);
				assertThat(context).hasBean("predictorProvider");

				@SuppressWarnings("unchecked")
				Function<Message<byte[]>, Message<byte[]>> predictor = (Function<Message<byte[]>, Message<byte[]>>) context
					.getBean("computerVisionFunction");

				var djlProperties = context.getBean(DjlConfigurationProperties.class);

				assertThat(djlProperties.getApplicationType()).isEqualTo(ApplicationType.POSE_ESTIMATION);
				assertThat(djlProperties.getInputClass()).isEqualTo(Image.class);
				assertThat(djlProperties.getOutputClass()).isEqualTo(Joints.class);

				byte[] inputImage = new ClassPathResource("/pose.png").getInputStream().readAllBytes();

				Message<byte[]> outputMessage = predictor.apply(MessageBuilder.withPayload(inputImage).build());

				assertThat(outputMessage).isNotNull();
				assertThat(outputMessage.getPayload()).isNotNull();
				assertThat(outputMessage.getPayload().length).isGreaterThan(0);
				assertThat(outputMessage.getHeaders()).containsKey("cvjson");

				String ssJson = outputMessage.getHeaders().get("cvjson", String.class);

				assertThat(JsonHelper.toJoints(ssJson)).isNotNull();
			});
	}

}
