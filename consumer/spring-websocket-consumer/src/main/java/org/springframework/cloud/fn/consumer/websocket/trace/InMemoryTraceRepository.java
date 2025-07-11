/*
 * Copyright 2018-present the original author or authors.
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

package org.springframework.cloud.fn.consumer.websocket.trace;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A repository for {@link Trace}s.
 * <p>
 * It is a copy of {@code InMemoryTraceRepository} from Spring Boot 1.5.x. Since Spring
 * Boot 2.0 traces are only available for HTTP.
 *
 * @author Dave Syer
 * @author Olivier Bourgain
 * @author Artem Bilan
 * @author Omer Celik
 * @since 2.0
 */
public class InMemoryTraceRepository {

	private int capacity = 100;

	private boolean reverse = true;

	private final List<Trace> traces = new LinkedList<>();

	private final Lock tracesLock = new ReentrantLock();

	/**
	 * Flag to say that the repository lists traces in reverse order.
	 * @param reverse flag value (default true)
	 */
	public void setReverse(boolean reverse) {
		this.reverse = reverse;
	}

	/**
	 * Set the capacity of the in-memory repository.
	 * @param capacity the capacity
	 */
	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public List<Trace> findAll() {
		return Collections.unmodifiableList(this.traces);
	}

	public void add(Map<String, Object> map) {
		Trace trace = new Trace(new Date(), map);
		this.tracesLock.lock();
		try {
			while (this.traces.size() >= this.capacity) {
				this.traces.remove(this.reverse ? this.capacity - 1 : 0);
			}
			if (this.reverse) {
				this.traces.add(0, trace);
			}
			else {
				this.traces.add(trace);
			}
		}
		finally {
			this.tracesLock.unlock();
		}
	}

}
