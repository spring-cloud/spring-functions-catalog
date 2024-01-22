/*
 * Copyright 2020-2020 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the TensorFlow Object Detection Function.
 *
 * @author Christian Tzolov
 */
@ConfigurationProperties("computer.vision.function")
public class ComputerVisionFunctionProperties {

	/**
	 * Enable image augmentation.
	 */
	private boolean augmentEnabled = false;

	/**
	 * Output augmented image format name.
	 */
	private String outputImageFormatName = "png";

	/**
	 * Name of the header that contains the JSON payload computed by the functions.
	 */
	private String outputHeaderName = "cvjson";

	public boolean isAugmentEnabled() {
		return this.augmentEnabled;
	}

	public void setAugmentEnabled(boolean augmentImage) {
		this.augmentEnabled = augmentImage;
	}

	public String getOutputImageFormatName() {
		return this.outputImageFormatName;
	}

	public void setOutputImageFormatName(String outputImageFormatName) {
		this.outputImageFormatName = outputImageFormatName;
	}

	public String getOutputHeaderName() {
		return this.outputHeaderName;
	}

	public void setOutputHeaderName(String jsonHeaderName) {
		this.outputHeaderName = jsonHeaderName;
	}

}
