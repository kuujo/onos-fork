package net.onrc.onos.api.link;

import net.onrc.onos.api.ProviderService;

/**
 * Means for injecting link information into the core.
 */
public interface LinkProviderService extends ProviderService {

    /**
     * Signals that an infrastructure link has been connected.
     *
     * @param linkDescription link information
     */
    void linkConnected(LinkDescription linkDescription);

    /**
     * Signals that an infrastructure link has been disconnected.
     *
     * @param linkDescription link information
     */
    void linkDisconnected(LinkDescription linkDescription);

}
