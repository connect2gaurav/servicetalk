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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static java.util.Objects.requireNonNull;

/**
 * Dummy adapter between {@link Publisher} of {@link HttpPayloadChunk} and {@link InputStream}.
 */
final class DummyBufferPublisherInputStream extends InputStream {
    private final Publisher<HttpPayloadChunk> chunkPublisher;

    @Nullable
    private InputStream inputStream;

    DummyBufferPublisherInputStream(final Publisher<HttpPayloadChunk> chunkPublisher) {
        this.chunkPublisher = requireNonNull(chunkPublisher);
    }

    Publisher<HttpPayloadChunk> getChunkPublisher() {
        return chunkPublisher;
    }

    @Override
    public int read() throws IOException {
        if (inputStream == null) {
            try {
                inputStream = requireNonNull(awaitIndefinitely(
                        chunkPublisher
                                .map(HttpPayloadChunk::getContent)
                                .reduce(ByteArrayOutputStream::new, (baos, buf) -> {
                                    final byte[] bytes = new byte[buf.getReadableBytes()];
                                    buf.readBytes(bytes);
                                    baos.write(bytes, 0, bytes.length);
                                    return baos;
                                })
                                .map(ByteArrayOutputStream::toByteArray)
                                .map(ByteArrayInputStream::new)));
            } catch (final Exception e) {
                throw new IOException("Failed to create from: " + chunkPublisher, e);
            }
        }
        return inputStream.read();
    }
}
