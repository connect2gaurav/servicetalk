/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;

import java.io.ByteArrayOutputStream;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static java.util.Objects.requireNonNull;

/**
 * Dummy adapter between {@link java.io.OutputStream} and {@link Publisher} of {@link HttpPayloadChunk}.
 */
final class DummyBufferPublisherOutputStream extends ByteArrayOutputStream {
    private final BufferAllocator allocator;

    DummyBufferPublisherOutputStream(final BufferAllocator allocator) {
        this.allocator = requireNonNull(allocator);
    }

    Publisher<HttpPayloadChunk> getChunkPublisher() {
        return defer(() ->
                just(newPayloadChunk(
                        allocator.wrap(DummyBufferPublisherOutputStream.this.toByteArray())), immediate()));
    }
}
