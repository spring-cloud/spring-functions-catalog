/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.fn.common.tcp;

import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.integration.ip.tcp.serializer.AbstractByteArraySerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArrayLengthHeaderSerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArrayLfSerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArrayRawSerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArraySingleTerminatorSerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArrayStxEtxSerializer;
import org.springframework.util.Assert;

/**
 * Factory bean for an encoder/decoder based on {@link Encoding}.
 *
 * @author Gary Russell
 * @author Christian Tzolov
 */
public class EncoderDecoderFactoryBean extends AbstractFactoryBean<AbstractByteArraySerializer>
		implements ApplicationEventPublisherAware {

	private final Encoding encoding;

	private ApplicationEventPublisher applicationEventPublisher;

	private Integer maxMessageSize;

	public EncoderDecoderFactoryBean(Encoding encoding) {
		Assert.notNull(encoding, "'encoding' cannot be null");
		this.encoding = encoding;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 * The maximum message size allowed when decoding.
	 * @param maxMessageSize the maximum message size.
	 */
	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	@Override
	protected AbstractByteArraySerializer createInstance() {
		AbstractByteArraySerializer codec = switch (this.encoding) {
			case CRLF -> new ByteArrayCrLfSerializer();
			case LF -> new ByteArrayLfSerializer();
			case NULL -> new ByteArraySingleTerminatorSerializer((byte) 0);
			case STXETX -> new ByteArrayStxEtxSerializer();
			case L1 -> new ByteArrayLengthHeaderSerializer(1);
			case L2 -> new ByteArrayLengthHeaderSerializer(2);
			case L4 -> new ByteArrayLengthHeaderSerializer(4);
			case RAW -> new ByteArrayRawSerializer();
		};
		codec.setApplicationEventPublisher(this.applicationEventPublisher);
		if (this.maxMessageSize != null) {
			codec.setMaxMessageSize(this.maxMessageSize);
		}
		return codec;
	}

	@Override
	public Class<?> getObjectType() {
		return AbstractByteArraySerializer.class;
	}

}
