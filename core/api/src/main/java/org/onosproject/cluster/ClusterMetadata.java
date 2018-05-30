/*
 * Copyright 2015-present Open Networking Foundation
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
package org.onosproject.cluster;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;
import org.onosproject.net.Provided;
import org.onosproject.net.provider.ProviderId;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Cluster metadata.
 * <p>
 * Metadata specifies how a ONOS cluster is constituted and is made up of the collection
 * of {@link org.onosproject.cluster.ControllerNode nodes} and the collection of data
 * {@link org.onosproject.cluster.Partition partitions}.
 */
public final class ClusterMetadata implements Provided {

    private final ProviderId providerId;
    private final String name;
    private final ControllerNode localNode;
    private final Set<Node> nodes;

    public static final Funnel<ClusterMetadata> HASH_FUNNEL = new Funnel<ClusterMetadata>() {
        @Override
        public void funnel(ClusterMetadata cm, PrimitiveSink into) {
            into.putString(cm.name, UTF_8);
        }
    };

    @SuppressWarnings("unused")
    private ClusterMetadata() {
        providerId = null;
        name = null;
        localNode = null;
        nodes = null;
    }

    public ClusterMetadata(
        ProviderId providerId,
        String name,
        ControllerNode localNode,
        Set<Node> nodes) {
        this.providerId = checkNotNull(providerId);
        this.name = checkNotNull(name);
        this.localNode = localNode;
        this.nodes = ImmutableSet.copyOf(checkNotNull(nodes));
    }

    public ClusterMetadata(String name, ControllerNode localNode, Set<Node> nodes) {
        this(new ProviderId("none", "none"), name, localNode, nodes);
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the name of the cluster.
     *
     * @return cluster name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the local controller node.
     * @return the local controller node
     */
    public ControllerNode getLocalNode() {
        return localNode;
    }

    /**
     * Returns the collection of {@link org.onosproject.cluster.ControllerNode nodes} that make up the cluster.
     * @return cluster nodes
     */
    public Collection<ControllerNode> getNodes() {
        return (Collection) nodes;
    }

    /**
     * Returns the collection of consensus nodes.
     *
     * @return the collection of consensus nodes
     */
    public Collection<Node> getConsensusNodes() {
        return nodes;
    }

    /**
     * Returns the collection of {@link org.onosproject.cluster.Partition partitions} that make
     * up the cluster.
     * @return collection of partitions.
     * @deprecated since 1.14
     */
    @Deprecated
    public Collection<Partition> getPartitions() {
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(ClusterMetadata.class)
                .add("providerId", providerId)
                .add("name", name)
                .add("nodes", nodes)
                .toString();
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(new Object[] {providerId, name, nodes});
    }

    /*
     * Provide a deep equality check of the cluster metadata (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!ClusterMetadata.class.isInstance(object)) {
            return false;
        }
        ClusterMetadata that = (ClusterMetadata) object;

        return Objects.equals(this.name, that.name) &&
               this.localNode.equals(that.localNode) &&
               Objects.equals(this.nodes.size(), that.nodes.size()) &&
               Sets.symmetricDifference(this.nodes, that.nodes).isEmpty();
    }
}
