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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.IoThreadFactory;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.function.Function;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.transport.api.ContextFilter.ACCEPT_ALL;
import static io.servicetalk.transport.api.FlushStrategy.defaultFlushStrategy;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;

public abstract class AbstractTcpServerTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    protected static NettyIoExecutor serverIoExecutor;
    protected static NettyIoExecutor clientIoExecutor;

    private Executor executor;
    private ContextFilter contextFilter = ACCEPT_ALL;
    private Function<Connection<Buffer, Buffer>, Completable> service =
            conn -> conn.write(conn.read(), defaultFlushStrategy());
    protected ServerContext serverContext;
    protected int serverPort;
    protected TcpClient client;
    protected TcpServer server;

    public void setContextFilter(final ContextFilter contextFilter) {
        this.contextFilter = contextFilter;
    }

    public void setService(final Function<Connection<Buffer, Buffer>, Completable> service) {
        this.service = service;
    }

    public Executor getExecutor() {
        return executor;
    }

    @BeforeClass
    public static void setupIoExecutors() {
        serverIoExecutor = toNettyIoExecutor(createIoExecutor(new IoThreadFactory("server-io-executor")));
        clientIoExecutor = toNettyIoExecutor(createIoExecutor(new IoThreadFactory("client-io-executor")));
    }

    @Before
    public void createExecutor() {
        executor = Executors.newCachedThreadExecutor();
    }

    @Before
    public void startServer() throws Exception {
        server = createServer();
        serverContext = server.start(0, contextFilter, service);
        serverPort = TcpServer.getServerPort(serverContext);
        client = new TcpClient(clientIoExecutor);
    }

    // Visible for overriding.
    TcpServer createServer() {
        return new TcpServer(serverIoExecutor);
    }

    @After
    public void stopServer() throws Exception {
        awaitIndefinitely(newCompositeCloseable().concat(serverContext, executor).closeAsync());
    }

    @AfterClass
    public static void shutdownIoExectors() throws Exception {
        awaitIndefinitely(newCompositeCloseable().merge(serverIoExecutor, clientIoExecutor).closeAsync());
    }
}
