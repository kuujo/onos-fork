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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import io.atomix.protocols.raft.cluster.MemberId;
import io.atomix.protocols.raft.protocol.AppendRequest;
import io.atomix.protocols.raft.protocol.AppendResponse;
import io.atomix.protocols.raft.protocol.CloseSessionRequest;
import io.atomix.protocols.raft.protocol.CloseSessionResponse;
import io.atomix.protocols.raft.protocol.CommandRequest;
import io.atomix.protocols.raft.protocol.CommandResponse;
import io.atomix.protocols.raft.protocol.ConfigureRequest;
import io.atomix.protocols.raft.protocol.ConfigureResponse;
import io.atomix.protocols.raft.protocol.InstallRequest;
import io.atomix.protocols.raft.protocol.InstallResponse;
import io.atomix.protocols.raft.protocol.JoinRequest;
import io.atomix.protocols.raft.protocol.JoinResponse;
import io.atomix.protocols.raft.protocol.KeepAliveRequest;
import io.atomix.protocols.raft.protocol.KeepAliveResponse;
import io.atomix.protocols.raft.protocol.LeaveRequest;
import io.atomix.protocols.raft.protocol.LeaveResponse;
import io.atomix.protocols.raft.protocol.MetadataRequest;
import io.atomix.protocols.raft.protocol.MetadataResponse;
import io.atomix.protocols.raft.protocol.OpenSessionRequest;
import io.atomix.protocols.raft.protocol.OpenSessionResponse;
import io.atomix.protocols.raft.protocol.PollRequest;
import io.atomix.protocols.raft.protocol.PollResponse;
import io.atomix.protocols.raft.protocol.PublishRequest;
import io.atomix.protocols.raft.protocol.QueryRequest;
import io.atomix.protocols.raft.protocol.QueryResponse;
import io.atomix.protocols.raft.protocol.RaftServerProtocol;
import io.atomix.protocols.raft.protocol.ReconfigureRequest;
import io.atomix.protocols.raft.protocol.ReconfigureResponse;
import io.atomix.protocols.raft.protocol.ResetRequest;
import io.atomix.protocols.raft.protocol.VoteRequest;
import io.atomix.protocols.raft.protocol.VoteResponse;
import io.atomix.protocols.raft.session.SessionId;
import org.onosproject.cluster.NodeId;
import org.onosproject.cluster.PartitionId;
import org.onosproject.store.cluster.messaging.ClusterCommunicationService;
import org.onosproject.store.cluster.messaging.MessageSubject;
import org.onosproject.store.service.Serializer;

/**
 * Raft server protocol that uses a {@link ClusterCommunicationService}.
 */
public class RaftServerCommunicator implements RaftServerProtocol {
    private final RaftMessageContext context;
    private final Serializer serializer;
    private final ClusterCommunicationService clusterCommunicator;

    public RaftServerCommunicator(
            PartitionId partitionId,
            Serializer serializer,
            ClusterCommunicationService clusterCommunicator) {
        this(String.format("partition-%d", partitionId.id()), serializer, clusterCommunicator);
    }

    public RaftServerCommunicator(
            String prefix,
            Serializer serializer,
            ClusterCommunicationService clusterCommunicator) {
        this.context = new RaftMessageContext(prefix);
        this.serializer = Preconditions.checkNotNull(serializer, "serializer cannot be null");
        this.clusterCommunicator = Preconditions.checkNotNull(
                clusterCommunicator,
                "clusterCommunicator cannot be null");
    }

    private <T, U> CompletableFuture<U> sendAndReceive(MessageSubject subject, T request, MemberId memberId) {
        return clusterCommunicator.sendAndReceive(
                request, subject, serializer::encode, serializer::decode, NodeId.nodeId(memberId.id()));
    }

    @Override
    public CompletableFuture<OpenSessionResponse> openSession(MemberId memberId, OpenSessionRequest request) {
        return sendAndReceive(context.openSessionSubject, request, memberId);
    }

    @Override
    public CompletableFuture<CloseSessionResponse> closeSession(MemberId memberId, CloseSessionRequest request) {
        return sendAndReceive(context.closeSessionSubject, request, memberId);
    }

    @Override
    public CompletableFuture<KeepAliveResponse> keepAlive(MemberId memberId, KeepAliveRequest request) {
        return sendAndReceive(context.keepAliveSubject, request, memberId);
    }

    @Override
    public CompletableFuture<QueryResponse> query(MemberId memberId, QueryRequest request) {
        return sendAndReceive(context.querySubject, request, memberId);
    }

    @Override
    public CompletableFuture<CommandResponse> command(MemberId memberId, CommandRequest request) {
        return sendAndReceive(context.commandSubject, request, memberId);
    }

    @Override
    public CompletableFuture<MetadataResponse> metadata(MemberId memberId, MetadataRequest request) {
        return sendAndReceive(context.metadataSubject, request, memberId);
    }

    @Override
    public CompletableFuture<JoinResponse> join(MemberId memberId, JoinRequest request) {
        return sendAndReceive(context.joinSubject, request, memberId);
    }

    @Override
    public CompletableFuture<LeaveResponse> leave(MemberId memberId, LeaveRequest request) {
        return sendAndReceive(context.leaveSubject, request, memberId);
    }

    @Override
    public CompletableFuture<ConfigureResponse> configure(MemberId memberId, ConfigureRequest request) {
        return sendAndReceive(context.configureSubject, request, memberId);
    }

    @Override
    public CompletableFuture<ReconfigureResponse> reconfigure(MemberId memberId, ReconfigureRequest request) {
        return sendAndReceive(context.reconfigureSubject, request, memberId);
    }

    @Override
    public CompletableFuture<InstallResponse> install(MemberId memberId, InstallRequest request) {
        return sendAndReceive(context.installSubject, request, memberId);
    }

    @Override
    public CompletableFuture<PollResponse> poll(MemberId memberId, PollRequest request) {
        return sendAndReceive(context.pollSubject, request, memberId);
    }

    @Override
    public CompletableFuture<VoteResponse> vote(MemberId memberId, VoteRequest request) {
        return sendAndReceive(context.voteSubject, request, memberId);
    }

    @Override
    public CompletableFuture<AppendResponse> append(MemberId memberId, AppendRequest request) {
        return sendAndReceive(context.appendSubject, request, memberId);
    }

    @Override
    public void publish(MemberId memberId, PublishRequest request) {
        clusterCommunicator.unicast(request,
                context.publishSubject(request.session()), serializer::encode, NodeId.nodeId(memberId.id()));
    }

    @Override
    public void registerOpenSessionHandler(
            Function<OpenSessionRequest, CompletableFuture<OpenSessionResponse>> handler) {
        clusterCommunicator.addSubscriber(context.openSessionSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterOpenSessionHandler() {
        clusterCommunicator.removeSubscriber(context.openSessionSubject);
    }

    @Override
    public void registerCloseSessionHandler(
            Function<CloseSessionRequest, CompletableFuture<CloseSessionResponse>> handler) {
        clusterCommunicator.addSubscriber(context.closeSessionSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterCloseSessionHandler() {
        clusterCommunicator.removeSubscriber(context.closeSessionSubject);
    }

    @Override
    public void registerKeepAliveHandler(Function<KeepAliveRequest, CompletableFuture<KeepAliveResponse>> handler) {
        clusterCommunicator.addSubscriber(context.keepAliveSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterKeepAliveHandler() {
        clusterCommunicator.removeSubscriber(context.keepAliveSubject);
    }

    @Override
    public void registerQueryHandler(Function<QueryRequest, CompletableFuture<QueryResponse>> handler) {
        clusterCommunicator.addSubscriber(context.querySubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterQueryHandler() {
        clusterCommunicator.removeSubscriber(context.querySubject);
    }

    @Override
    public void registerCommandHandler(Function<CommandRequest, CompletableFuture<CommandResponse>> handler) {
        clusterCommunicator.addSubscriber(context.commandSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterCommandHandler() {
        clusterCommunicator.removeSubscriber(context.commandSubject);
    }

    @Override
    public void registerMetadataHandler(Function<MetadataRequest, CompletableFuture<MetadataResponse>> handler) {
        clusterCommunicator.addSubscriber(context.metadataSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterMetadataHandler() {
        clusterCommunicator.removeSubscriber(context.metadataSubject);
    }

    @Override
    public void registerJoinHandler(Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
        clusterCommunicator.addSubscriber(context.joinSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterJoinHandler() {
        clusterCommunicator.removeSubscriber(context.joinSubject);
    }

    @Override
    public void registerLeaveHandler(Function<LeaveRequest, CompletableFuture<LeaveResponse>> handler) {
        clusterCommunicator.addSubscriber(context.leaveSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterLeaveHandler() {
        clusterCommunicator.removeSubscriber(context.leaveSubject);
    }

    @Override
    public void registerConfigureHandler(Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
        clusterCommunicator.addSubscriber(context.configureSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterConfigureHandler() {
        clusterCommunicator.removeSubscriber(context.configureSubject);
    }

    @Override
    public void registerReconfigureHandler(
            Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
        clusterCommunicator.addSubscriber(context.reconfigureSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterReconfigureHandler() {
        clusterCommunicator.removeSubscriber(context.reconfigureSubject);
    }

    @Override
    public void registerInstallHandler(Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
        clusterCommunicator.addSubscriber(context.installSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterInstallHandler() {
        clusterCommunicator.removeSubscriber(context.installSubject);
    }

    @Override
    public void registerPollHandler(Function<PollRequest, CompletableFuture<PollResponse>> handler) {
        clusterCommunicator.addSubscriber(context.pollSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterPollHandler() {
        clusterCommunicator.removeSubscriber(context.pollSubject);
    }

    @Override
    public void registerVoteHandler(Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
        clusterCommunicator.addSubscriber(context.voteSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterVoteHandler() {
        clusterCommunicator.removeSubscriber(context.voteSubject);
    }

    @Override
    public void registerAppendHandler(Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
        clusterCommunicator.addSubscriber(context.appendSubject, serializer::decode, handler, serializer::encode);
    }

    @Override
    public void unregisterAppendHandler() {
        clusterCommunicator.removeSubscriber(context.appendSubject);
    }

    @Override
    public void registerResetListener(SessionId sessionId, Consumer<ResetRequest> listener, Executor executor) {
        clusterCommunicator.addSubscriber(context.resetSubject(sessionId.id()), serializer::decode, listener, executor);
    }

    @Override
    public void unregisterResetListener(SessionId sessionId) {
        clusterCommunicator.removeSubscriber(context.resetSubject(sessionId.id()));
    }
}
