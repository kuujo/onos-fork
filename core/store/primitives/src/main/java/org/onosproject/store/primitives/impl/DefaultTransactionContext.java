/*
 * Copyright 2016-present Open Networking Laboratory
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.onosproject.store.primitives.TransactionId;
import org.onosproject.store.service.CommitStatus;
import org.onosproject.store.service.IsolationLevel;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.TransactionContext;
import org.onosproject.store.service.TransactionalMap;
import org.onosproject.utils.MeteringAgent;

/**
 * Default transaction context.
 */
public class DefaultTransactionContext implements TransactionContext {

    private final AtomicBoolean isOpen = new AtomicBoolean(false);
    private final TransactionId transactionId;
    private final IsolationLevel isolationLevel;
    private final TransactionCoordinator transactionCoordinator;
    private final MeteringAgent monitor;

    public DefaultTransactionContext(TransactionId transactionId,
            IsolationLevel isolationLevel,
            TransactionCoordinator transactionCoordinator) {
        this.transactionId = transactionId;
        this.isolationLevel = isolationLevel;
        this.transactionCoordinator = transactionCoordinator;
        this.monitor = new MeteringAgent("transactionContext", "*", true);
    }

    @Override
    public String name() {
        return transactionId.toString();
    }

    @Override
    public TransactionId transactionId() {
        return transactionId;
    }

    @Override
    public IsolationLevel isolationLevel() {
        return isolationLevel;
    }

    @Override
    public boolean isOpen() {
        return isOpen.get();
    }

    @Override
    public void begin() {
        if (!isOpen.compareAndSet(false, true)) {
            throw new IllegalStateException("TransactionContext is already open");
        }
    }

    @Override
    public CompletableFuture<CommitStatus> commit() {
        final MeteringAgent.Context timer = monitor.startTimer("commit");
        return transactionCoordinator.commit().whenComplete((r, e) -> timer.stop(e));
    }

    @Override
    public void abort() {
        isOpen.set(false);
    }

    @Override
    public <K, V> TransactionalMap<K, V> getTransactionalMap(String mapName, Serializer serializer) {
        return transactionCoordinator.getTransactionalMap(mapName, serializer);
    }
}