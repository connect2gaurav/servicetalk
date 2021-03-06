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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.resolver.dns.DnsNameResolverTimeoutException;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.dns.discovery.netty.DnsClients.asHostAndPortDiscoverer;
import static io.servicetalk.dns.discovery.netty.DnsClients.asSrvDiscoverer;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * Builder for DNS {@link ServiceDiscoverer} which will attempt to resolve {@code A}, {@code AAAA}, {@code CNAME}, and
 * {@code SRV} type queries.
 */
public final class DefaultDnsServiceDiscovererBuilder {
    @Nullable
    private DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    @Nullable
    private DnsResolverAddressTypes dnsResolverAddressTypes;
    @Nullable
    private Integer ndots;
    private Predicate<Throwable> invalidateHostsOnDnsFailure = defaultInvalidateHostsOnDnsFailurePredicate();
    @Nullable
    private Boolean optResourceEnabled;
    @Nullable
    private IoExecutor ioExecutor;
    @Nullable
    private Duration queryTimeout;
    private boolean applyRetryFilter = true;
    private int minTTLSeconds = 10;
    @Nullable
    private DnsClientFilterFactory filterFactory;

    /**
     * The minimum allowed TTL. This will be the minimum poll interval.
     *
     * @param minTTLSeconds The minimum amount of time a cache entry will be considered valid (in seconds).
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder minTTL(final int minTTLSeconds) {
        if (minTTLSeconds < 1) {
            throw new IllegalArgumentException("minTTLSeconds: " + minTTLSeconds + " (expected > 1)");
        }
        this.minTTLSeconds = minTTLSeconds;
        return this;
    }

    /**
     * Set the {@link DnsServerAddressStreamProvider} which determines which DNS server should be used per query.
     *
     * @param dnsServerAddressStreamProvider the {@link DnsServerAddressStreamProvider} which determines which DNS
     * server should be used per query.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder dnsServerAddressStreamProvider(
            @Nullable final DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        this.dnsServerAddressStreamProvider = dnsServerAddressStreamProvider;
        return this;
    }

    /**
     * Enable the automatic inclusion of a optional records that tries to give the remote DNS server a hint about
     * how much data the resolver can read per response. Some DNSServer may not support this and so fail to answer
     * queries. If you find problems you may want to disable this.
     *
     * @param optResourceEnabled if optional records inclusion is enabled.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder optResourceEnabled(final boolean optResourceEnabled) {
        this.optResourceEnabled = optResourceEnabled;
        return this;
    }

    /**
     * Set the number of dots which must appear in a name before an initial absolute query is made.
     *
     * @param ndots the ndots value.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder ndots(final int ndots) {
        this.ndots = ndots;
        return this;
    }

    /**
     * Sets the timeout of each DNS query performed by this service discoverer.
     *
     * @param queryTimeout the query timeout value
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder queryTimeout(final Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
        return this;
    }

    /**
     * Allows sending 'unavailable' events for all current active hosts for particular DNS errors.
     * <p>
     * Note: The default does not send 'unavailable' events when a DNS lookup times out.
     *
     * @param invalidateHostsOnDnsFailure determines whether or not to send 'unavailable' events.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder invalidateHostsOnDnsFailure(
            final Predicate<Throwable> invalidateHostsOnDnsFailure) {
        this.invalidateHostsOnDnsFailure = invalidateHostsOnDnsFailure;
        return this;
    }

    /**
     * Returns a default value for {@link #invalidateHostsOnDnsFailure(Predicate)}.
     *
     * @return a default value for {@link #invalidateHostsOnDnsFailure(Predicate)}
     */
    public static Predicate<Throwable> defaultInvalidateHostsOnDnsFailurePredicate() {
        return t -> t instanceof UnknownHostException &&
                !(t.getCause() instanceof DnsNameResolverTimeoutException);
    }

    /**
     * Sets the list of the protocol families of the address resolved.
     *
     * @param dnsResolverAddressTypes the address types.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder dnsResolverAddressTypes(
            @Nullable final DnsResolverAddressTypes dnsResolverAddressTypes) {
        this.dnsResolverAddressTypes = dnsResolverAddressTypes;
        return this;
    }

    /**
     * Do not perform retries if DNS lookup fails. Instead, terminate the {@link Publisher} with the error.
     *
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder noRetriesOnDnsFailures() {
        this.applyRetryFilter = false;
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link ServiceDiscoverer} created by this
     * builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a service discoverer wrapped by this filter chain the order of invocation of these filters
     * will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service discoverer
     * </pre>
     *
     * @param factory {@link DnsClientFilterFactory} to decorate a {@link DnsClient} for the purpose of
     * filtering.
     * @return {@code this}
     */
    DefaultDnsServiceDiscovererBuilder appendFilter(final DnsClientFilterFactory factory) {
        filterFactory = filterFactory == null ? requireNonNull(factory) : filterFactory.append(factory);
        return this;
    }

    /**
     * Sets the {@link IoExecutor}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder ioExecutor(final IoExecutor ioExecutor) {
        this.ioExecutor = ioExecutor;
        return this;
    }

    /**
     * Build a new {@link ServiceDiscoverer} which queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a> corresponding to {@code serviceName}. For
     * each SRV answer capture the <strong>Port</strong> and resolve the <strong>Target</strong>.
     * @return a new {@link ServiceDiscoverer} which queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a> corresponding to {@code serviceName}. For
     * each SRV answer capture the <strong>Port</strong> and resolve the <strong>Target</strong>.
     */
    public ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
            buildSrvDiscoverer() {
        return asSrvDiscoverer(build());
    }

    /**
     * Build a new {@link ServiceDiscoverer} which targets
     * <a href="https://tools.ietf.org/html/rfc1035">host addresses</a> (e.g. A or AAAA records) and uses
     * a fixed port derived from the {@link HostAndPort}.
     * @return a new {@link ServiceDiscoverer} which targets
     * <a href="https://tools.ietf.org/html/rfc1035">host addresses</a> (e.g. A or AAAA records) and uses
     * a fixed port derived from the {@link HostAndPort}.
     */
    public ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
            buildARecordDiscoverer() {
        return asHostAndPortDiscoverer(build());
    }

    /**
     * Create a new instance of {@link DnsClient}.
     *
     * @return a new instance of {@link DnsClient}.
     */
    DnsClient build() {
        DnsClient rawClient = new DefaultDnsClient(
                ioExecutor == null ? globalExecutionContext().ioExecutor() : ioExecutor, minTTLSeconds, ndots,
                invalidateHostsOnDnsFailure, optResourceEnabled, queryTimeout, dnsResolverAddressTypes,
                dnsServerAddressStreamProvider);
        DnsClientFilterFactory rawFilterFactory = filterFactory;
        if (applyRetryFilter) {
            DnsClientFilterFactory retryFilterFactory = new RetryingDnsClientFilter();
            rawFilterFactory = rawFilterFactory == null ? retryFilterFactory :
                    retryFilterFactory.append(rawFilterFactory);
        }
        return rawFilterFactory == null ? rawClient : rawFilterFactory.create(rawClient);
    }
}
