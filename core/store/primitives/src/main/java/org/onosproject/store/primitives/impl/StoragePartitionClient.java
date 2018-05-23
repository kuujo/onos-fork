/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.store.primitives.impl;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionMetadata;
import io.atomix.protocols.raft.RaftClient;
import io.atomix.protocols.raft.ReadConsistency;
import io.atomix.protocols.raft.protocol.RaftClientProtocol;
import io.atomix.protocols.raft.proxy.CommunicationStrategy;
import org.onlab.util.HexString;
import org.onosproject.store.primitives.DistributedPrimitiveCreator;
import org.onosproject.store.primitives.resources.impl.AtomixAtomicCounterMap;
import org.onosproject.store.primitives.resources.impl.AtomixConsistentMap;
import org.onosproject.store.primitives.resources.impl.AtomixConsistentSetMultimap;
import org.onosproject.store.primitives.resources.impl.AtomixConsistentTreeMap;
import org.onosproject.store.primitives.resources.impl.AtomixCounter;
import org.onosproject.store.primitives.resources.impl.AtomixDistributedLock;
import org.onosproject.store.primitives.resources.impl.AtomixDocumentTree;
import org.onosproject.store.primitives.resources.impl.AtomixIdGenerator;
import org.onosproject.store.primitives.resources.impl.AtomixLeaderElector;
import org.onosproject.store.primitives.resources.impl.AtomixWorkQueue;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.AsyncAtomicCounter;
import org.onosproject.store.service.AsyncAtomicCounterMap;
import org.onosproject.store.service.AsyncAtomicIdGenerator;
import org.onosproject.store.service.AsyncAtomicValue;
import org.onosproject.store.service.AsyncConsistentMap;
import org.onosproject.store.service.AsyncConsistentMultimap;
import org.onosproject.store.service.AsyncConsistentTreeMap;
import org.onosproject.store.service.AsyncDistributedLock;
import org.onosproject.store.service.AsyncDistributedSet;
import org.onosproject.store.service.AsyncDocumentTree;
import org.onosproject.store.service.AsyncLeaderElector;
import org.onosproject.store.service.AtomicCounterMapOptions;
import org.onosproject.store.service.AtomicCounterOptions;
import org.onosproject.store.service.AtomicIdGeneratorOptions;
import org.onosproject.store.service.AtomicValueOptions;
import org.onosproject.store.service.ConsistentMapOptions;
import org.onosproject.store.service.ConsistentMultimapOptions;
import org.onosproject.store.service.ConsistentTreeMapOptions;
import org.onosproject.store.service.DistributedLockOptions;
import org.onosproject.store.service.DistributedSetOptions;
import org.onosproject.store.service.DocumentTreeOptions;
import org.onosproject.store.service.LeaderElectorOptions;
import org.onosproject.store.service.PartitionClientInfo;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.WorkQueue;
import org.onosproject.store.service.WorkQueueOptions;
import org.slf4j.Logger;

import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.ATOMIC_COUNTER;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.ATOMIC_COUNTER_MAP;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.CONSISTENT_MAP;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.CONSISTENT_MULTIMAP;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.CONSISTENT_TREEMAP;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.DOCUMENT_TREE;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.LEADER_ELECTOR;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.LOCK;
import static org.onosproject.store.primitives.resources.impl.AtomixPrimitiveTypes.WORK_QUEUE;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * StoragePartition client.
 */
public class StoragePartitionClient implements DistributedPrimitiveCreator, Managed<StoragePartitionClient> {

    private static final int MAX_RETRIES = 8;
    private static final String ATOMIC_VALUES_CONSISTENT_MAP_NAME = "onos-atomic-values";

    private static final String MIN_TIMEOUT_PROPERTY = "onos.cluster.raft.client.minTimeoutMillis";
    private static final String MAX_TIMEOUT_PROPERTY = "onos.cluster.raft.client.maxTimeoutMillis";

    private static final Duration MIN_TIMEOUT;
    private static final Duration MAX_TIMEOUT;

    private static final long DEFAULT_MIN_TIMEOUT_MILLIS = 5000;
    private static final long DEFAULT_MAX_TIMEOUT_MILLIS = 30000;

    static {
        Duration minTimeout;
        try {
            minTimeout = Duration.ofMillis(Long.parseLong(
                System.getProperty(MIN_TIMEOUT_PROPERTY,
                    String.valueOf(DEFAULT_MIN_TIMEOUT_MILLIS))));
        } catch (NumberFormatException e) {
            minTimeout = Duration.ofMillis(DEFAULT_MIN_TIMEOUT_MILLIS);
        }
        MIN_TIMEOUT = minTimeout;

        Duration maxTimeout;
        try {
            maxTimeout = Duration.ofMillis(Long.parseLong(
                System.getProperty(MAX_TIMEOUT_PROPERTY,
                    String.valueOf(DEFAULT_MAX_TIMEOUT_MILLIS))));
        } catch (NumberFormatException e) {
            maxTimeout = Duration.ofMillis(DEFAULT_MAX_TIMEOUT_MILLIS);
        }
        MAX_TIMEOUT = maxTimeout;
    }

    private final Logger log = getLogger(getClass());

    private final StoragePartition partition;
    private final MemberId localMemberId;
    private final RaftClientProtocol protocol;
    private RaftClient client;
    private final com.google.common.base.Supplier<AsyncConsistentMap<String, byte[]>> onosAtomicValuesMap =
            Suppliers.memoize(() -> newAsyncConsistentMap(ATOMIC_VALUES_CONSISTENT_MAP_NAME,
                                                          Serializer.using(KryoNamespaces.BASIC)));

    public StoragePartitionClient(StoragePartition partition, MemberId localMemberId, RaftClientProtocol protocol) {
        this.partition = partition;
        this.localMemberId = localMemberId;
        this.protocol = protocol;
    }

    @Override
    public CompletableFuture<Void> open() {
        synchronized (StoragePartitionClient.this) {
            client = newRaftClient(protocol);
        }
        return client.connect(partition.getMemberIds()).whenComplete((r, e) -> {
            if (e == null) {
                log.info("Successfully started client for partition {}", partition.getId());
            } else {
                log.info("Failed to start client for partition {}", partition.getId(), e);
            }
        }).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> close() {
        return client != null ? client.close() : CompletableFuture.completedFuture(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> AsyncConsistentMap<K, V> newAsyncConsistentMap(ConsistentMapOptions options) {
        AtomixConsistentMap rawMap =
                new AtomixConsistentMap(
                    client.proxyBuilder(options.name(), CONSISTENT_MAP, new ServiceConfig())
                        .withReadConsistency(ReadConsistency.SEQUENTIAL)
                        .withCommunicationStrategy(CommunicationStrategy.ANY)
                        .withMinTimeout(MIN_TIMEOUT)
                        .withMaxTimeout(MAX_TIMEOUT)
                        .withMaxRetries(MAX_RETRIES)
                        .build()
                        .connect()
                        .join());

        if (options.serializer() != null) {
            return DistributedPrimitives.newTranscodingMap(rawMap,
                    key -> HexString.toHexString(options.serializer().encode(key)),
                    string -> options.serializer().decode(HexString.fromHexString(string)),
                    value -> value == null ? null : options.serializer().encode(value),
                    bytes -> options.serializer().decode(bytes));
        }
        return (AsyncConsistentMap<K, V>) rawMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> AsyncConsistentTreeMap<V> newAsyncConsistentTreeMap(ConsistentTreeMapOptions options) {
        AtomixConsistentTreeMap rawMap =
                new AtomixConsistentTreeMap(
                    client.proxyBuilder(options.name(), CONSISTENT_TREEMAP, new ServiceConfig())
                        .withReadConsistency(ReadConsistency.SEQUENTIAL)
                        .withCommunicationStrategy(CommunicationStrategy.ANY)
                        .withMinTimeout(MIN_TIMEOUT)
                        .withMaxTimeout(MAX_TIMEOUT)
                        .withMaxRetries(MAX_RETRIES)
                        .build()
                        .connect()
                        .join());

        if (options.serializer() != null) {
            return DistributedPrimitives.newTranscodingTreeMap(
                            rawMap,
                            value -> value == null ? null : options.serializer().encode(value),
                            bytes -> options.serializer().decode(bytes));
        }
        return (AsyncConsistentTreeMap<V>) rawMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> AsyncConsistentMultimap<K, V> newAsyncConsistentSetMultimap(ConsistentMultimapOptions options) {
        AtomixConsistentSetMultimap rawMap =
                new AtomixConsistentSetMultimap(
                    client.proxyBuilder(options.name(), CONSISTENT_MULTIMAP, new ServiceConfig())
                        .withReadConsistency(ReadConsistency.SEQUENTIAL)
                        .withCommunicationStrategy(CommunicationStrategy.ANY)
                        .withMinTimeout(MIN_TIMEOUT)
                        .withMaxTimeout(MAX_TIMEOUT)
                        .withMaxRetries(MAX_RETRIES)
                        .build()
                        .connect()
                        .join());

        if (options.serializer() != null) {
            return DistributedPrimitives.newTranscodingMultimap(
                            rawMap,
                            key -> HexString.toHexString(options.serializer().encode(key)),
                            string -> options.serializer().decode(HexString.fromHexString(string)),
                            value -> options.serializer().encode(value),
                            bytes -> options.serializer().decode(bytes));
        }
        return (AsyncConsistentMultimap<K, V>) rawMap;
    }

    @Override
    public <E> AsyncDistributedSet<E> newAsyncDistributedSet(DistributedSetOptions options) {
        return DistributedPrimitives.newSetFromMap(newAsyncConsistentMap(options.name(), options.serializer()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K> AsyncAtomicCounterMap<K> newAsyncAtomicCounterMap(AtomicCounterMapOptions options) {
        AtomixAtomicCounterMap rawMap = new AtomixAtomicCounterMap(
            client.proxyBuilder(options.name(), ATOMIC_COUNTER_MAP, new ServiceConfig())
                .withReadConsistency(ReadConsistency.LINEARIZABLE_LEASE)
                .withCommunicationStrategy(CommunicationStrategy.LEADER)
                .withMinTimeout(MIN_TIMEOUT)
                .withMaxTimeout(MAX_TIMEOUT)
                .withMaxRetries(MAX_RETRIES)
                .build()
                .connect()
                .join());

        if (options.serializer() != null) {
            return DistributedPrimitives.newTranscodingAtomicCounterMap(
                            rawMap,
                            key -> HexString.toHexString(options.serializer().encode(key)),
                            string -> options.serializer().decode(HexString.fromHexString(string)));
        }
        return (AsyncAtomicCounterMap<K>) rawMap;
    }

    @Override
    public AsyncAtomicCounter newAsyncCounter(AtomicCounterOptions options) {
        return new AtomixCounter(
            client.proxyBuilder(options.name(), ATOMIC_COUNTER, new ServiceConfig())
                .withReadConsistency(ReadConsistency.LINEARIZABLE_LEASE)
                .withCommunicationStrategy(CommunicationStrategy.LEADER)
                .withMinTimeout(MIN_TIMEOUT)
                .withMaxTimeout(MAX_TIMEOUT)
                .withMaxRetries(MAX_RETRIES)
                .build()
                .connect()
                .join());
    }

    @Override
    public AsyncAtomicIdGenerator newAsyncIdGenerator(AtomicIdGeneratorOptions options) {
        return new AtomixIdGenerator(newAsyncCounter(options.name()));
    }

    @Override
    public <V> AsyncAtomicValue<V> newAsyncAtomicValue(AtomicValueOptions options) {
        return new DefaultAsyncAtomicValue<>(options.name(), options.serializer(), onosAtomicValuesMap.get());
    }

    @Override
    public <E> WorkQueue<E> newWorkQueue(WorkQueueOptions options) {
        AtomixWorkQueue atomixWorkQueue = new AtomixWorkQueue(
            client.proxyBuilder(options.name(), WORK_QUEUE, new ServiceConfig())
                .withReadConsistency(ReadConsistency.LINEARIZABLE_LEASE)
                .withCommunicationStrategy(CommunicationStrategy.LEADER)
                .withMinTimeout(MIN_TIMEOUT)
                .withMaxTimeout(MAX_TIMEOUT)
                .withMaxRetries(MAX_RETRIES)
                .build()
                .connect()
                .join());
        return new DefaultDistributedWorkQueue<>(atomixWorkQueue, options.serializer());
    }

    @Override
    public <V> AsyncDocumentTree<V> newAsyncDocumentTree(DocumentTreeOptions options) {
        AtomixDocumentTree atomixDocumentTree = new AtomixDocumentTree(
            client.proxyBuilder(options.name(), DOCUMENT_TREE, new ServiceConfig())
                .withReadConsistency(ReadConsistency.SEQUENTIAL)
                .withCommunicationStrategy(CommunicationStrategy.ANY)
                .withMinTimeout(MIN_TIMEOUT)
                .withMaxTimeout(MAX_TIMEOUT)
                .withMaxRetries(MAX_RETRIES)
                .build()
                .connect()
                .join());
        return new DefaultDistributedDocumentTree<>(options.name(), atomixDocumentTree, options.serializer());
    }

    @Override
    public AsyncDistributedLock newAsyncDistributedLock(DistributedLockOptions options) {
        return new AtomixDistributedLock(
            client.proxyBuilder(options.name(), LOCK, new ServiceConfig())
                .withReadConsistency(ReadConsistency.LINEARIZABLE)
                .withCommunicationStrategy(CommunicationStrategy.LEADER)
                .withMinTimeout(MIN_TIMEOUT)
                .withMaxTimeout(MIN_TIMEOUT)
                .withMaxRetries(MAX_RETRIES)
                .build()
                .connect()
                .join());
    }

    @Override
    public AsyncLeaderElector newAsyncLeaderElector(LeaderElectorOptions options) {
        return new AtomixLeaderElector(
            client.proxyBuilder(options.name(), LEADER_ELECTOR, new ServiceConfig())
                .withReadConsistency(ReadConsistency.LINEARIZABLE)
                .withCommunicationStrategy(CommunicationStrategy.LEADER)
                .withMinTimeout(Duration.ofMillis(options.electionTimeoutMillis()))
                .withMaxTimeout(MIN_TIMEOUT)
                .withMaxRetries(MAX_RETRIES)
                .build()
                .connect()
                .join());
    }

    @Override
    public Set<String> getAsyncConsistentMapNames() {
        return client.metadata().getSessions(CONSISTENT_MAP)
                .join()
                .stream()
                .map(SessionMetadata::primitiveName)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getAsyncAtomicCounterNames() {
        return client.metadata().getSessions(ATOMIC_COUNTER)
                .join()
                .stream()
                .map(SessionMetadata::primitiveName)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getWorkQueueNames() {
        return client.metadata().getSessions(WORK_QUEUE)
                .join()
                .stream()
                .map(SessionMetadata::primitiveName)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isOpen() {
        return client != null;
    }

    /**
     * Returns the {@link PartitionClientInfo information} for this client.
     * @return partition client information
     */
    public PartitionClientInfo clientInfo() {
        return new PartitionClientInfo(partition.getId(), partition.getMembers());
    }

    private RaftClient newRaftClient(RaftClientProtocol protocol) {
        return RaftClient.newBuilder()
                .withClientId("partition-" + partition.getId())
                .withPartitionId(PartitionId.from("onos", partition.getId().id()))
                .withMemberId(localMemberId)
                .withProtocol(protocol)
                .build();
    }
}
