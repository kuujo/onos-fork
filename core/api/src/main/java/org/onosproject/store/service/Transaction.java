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
package org.onosproject.store.service;

import java.util.concurrent.CompletableFuture;

import org.onosproject.store.primitives.TransactionId;

/**
 * Transaction.
 */
public interface Transaction extends DistributedPrimitive {

    /**
     * Transaction state.
     */
    enum State {

        /**
         * Active transaction state.
         */
        ACTIVE,

        /**
         * Preparing transaction state.
         */
        PREPARING,

        /**
         * Prepared transaction state.
         */
        PREPARED,

        /**
         * Rolling back transaction state.
         */
        ROLLING_BACK,

        /**
         * Rolled back transaction state.
         */
        ROLLED_BACK,

        /**
         * Committing transaction state.
         */
        COMMITTING,

        /**
         * Committed transaction state.
         */
        COMMITTED,
    }

    /**
     * Transaction status.
     */
    enum Status {

        /**
         * Successful transaction status.
         */
        SUCCESS,

        /**
         * Failure transaction status.
         */
        FAILURE,
    }

    @Override
    default Type primitiveType() {
        return Type.TRANSACTION;
    }

    /**
     * Returns the transaction identifier.
     *
     * @return the transaction identifier
     */
    TransactionId transactionId();

    /**
     * Returns the transaction state.
     *
     * @return The transaction state.
     */
    State state();

    /**
     * Returns the transaction's lock mode.
     *
     * @return the transaction's lock mode
     */
    LockMode lockMode();

    /**
     * Returns the transaction's isolation level.
     *
     * @return the transaction's isolation level
     */
    IsolationLevel isolationLevel();

    /**
     * Aborts the transaction.
     */
    CompletableFuture<Status> abort();

    /**
     * Commits the transaction.
     *
     * @return a completable future to be completed once the transaction has been committed. If the transaction
     * is committed successfully, the returned future will be completed {@code true}, otherwise {@code false}
     */
    CompletableFuture<Status> commit();

}
