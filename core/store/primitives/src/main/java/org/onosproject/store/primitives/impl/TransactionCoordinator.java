/*
 * Copyright 2017-present Open Networking Laboratory
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

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.onlab.util.Tools;
import org.onosproject.store.primitives.TransactionId;
import org.onosproject.store.service.CommitStatus;
import org.onosproject.store.service.IsolationLevel;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.TransactionalMap;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * Transaction coordinator.
 */
public class TransactionCoordinator {
    protected final TransactionId transactionId;
    protected final IsolationLevel isolationLevel;
    protected final TransactionManager transactionManager;
    protected final Set<TransactionParticipant> transactionParticipants = Sets.newConcurrentHashSet();

    public TransactionCoordinator(
            TransactionId transactionId,
            IsolationLevel isolationLevel,
            TransactionManager transactionManager) {
        this.transactionId = transactionId;
        this.isolationLevel = isolationLevel;
        this.transactionManager = transactionManager;
    }

    /**
     * Returns a transactional map for this transaction.
     *
     * @param name the transactional map name
     * @param serializer the serializer
     * @param <K> key type
     * @param <V> value type
     * @return a transactional map for this transaction
     */
    public <K, V> TransactionalMap<K, V> getTransactionalMap(String name, Serializer serializer) {
        PartitionedTransactionalMap<K, V> map = transactionManager.getTransactionalMap(name, serializer, this);
        transactionParticipants.addAll(map.participants());
        return map;
    }

    /**
     * Commits the transaction.
     *
     * @return the transaction commit status
     */
    public CompletableFuture<CommitStatus> commit() {
        long totalParticipants = transactionParticipants.stream()
                .filter(TransactionParticipant::hasPendingUpdates)
                .count();

        if (totalParticipants == 0) {
            return CompletableFuture.completedFuture(CommitStatus.SUCCESS);
        } else if (totalParticipants == 1) {
            return transactionParticipants.stream()
                    .filter(TransactionParticipant::hasPendingUpdates)
                    .findFirst()
                    .get()
                    .prepareAndCommit()
                    .thenApply(v -> v ? CommitStatus.SUCCESS : CommitStatus.FAILURE);
        } else {
            Set<TransactionParticipant> transactionParticipants = this.transactionParticipants.stream()
                    .filter(TransactionParticipant::hasPendingUpdates)
                    .collect(Collectors.toSet());

            CompletableFuture<CommitStatus> status = transactionManager.updateState(
                    transactionId, Transaction.State.PREPARING)
                    .thenCompose(v -> prepare(transactionParticipants))
                    .thenCompose(result -> result
                            ? transactionManager.updateState(transactionId, Transaction.State.COMMITTING)
                            .thenCompose(v -> commit(transactionParticipants))
                            .thenApply(v -> CommitStatus.SUCCESS)
                            : transactionManager.updateState(transactionId, Transaction.State.ROLLING_BACK)
                            .thenCompose(v -> rollback(transactionParticipants))
                            .thenApply(v -> CommitStatus.FAILURE));
            return status.thenCompose(v -> transactionManager.remove(transactionId).thenApply(u -> v));
        }
    }

    /**
     * Performs the prepare phase of the two-phase commit protocol for the given transaction participants.
     *
     * @param transactionParticipants the transaction participants for which to prepare the transaction
     * @return a completable future indicating whether <em>all</em> prepares succeeded
     */
    protected CompletableFuture<Boolean> prepare(Set<TransactionParticipant> transactionParticipants) {
        return Tools.allOf(transactionParticipants.stream()
                .map(TransactionParticipant::prepare)
                .collect(Collectors.toList()))
                .thenApply(list -> list.stream().reduce(Boolean::logicalAnd).orElse(true));
    }

    /**
     * Performs the commit phase of the two-phase commit protocol for the given transaction participants.
     *
     * @param transactionParticipants the transaction participants for which to commit the transaction
     * @return a completable future to be completed once the commits are complete
     */
    protected CompletableFuture<Void> commit(Set<TransactionParticipant> transactionParticipants) {
        return CompletableFuture.allOf(transactionParticipants.stream()
                .map(TransactionParticipant::commit)
                .toArray(CompletableFuture[]::new));
    }

    /**
     * Rolls back transactions for the given participants.
     *
     * @param transactionParticipants the transaction participants for which to roll back the transaction
     * @return a completable future to be completed once the rollbacks are complete
     */
    protected CompletableFuture<Void> rollback(Set<TransactionParticipant> transactionParticipants) {
        return CompletableFuture.allOf(transactionParticipants.stream()
                .map(TransactionParticipant::rollback)
                .toArray(CompletableFuture[]::new));
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("transactionId", transactionId)
                .add("isolationLevel", isolationLevel)
                .add("participants", transactionParticipants)
                .toString();
    }
}
