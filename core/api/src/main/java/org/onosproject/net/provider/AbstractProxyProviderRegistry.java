/*
 * Copyright 2018-present Open Networking Foundation
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
package org.onosproject.net.provider;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.cluster.ProxyFactory;
import org.onosproject.cluster.ProxyRoleService;
import org.onosproject.cluster.ProxyService;
import org.onosproject.net.DeviceId;
import org.onosproject.store.service.Serializer;

/**
 * Abstract proxy provider registry.
 */
@Component
public abstract class AbstractProxyProviderRegistry<P extends Provider, S extends ProviderService<P>, X, C>
    extends AbstractProviderRegistry<P, S> {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ProxyRoleService proxyRoleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ProxyService proxyService;

    protected ProxyFactory<X> proxyFactory;
    protected ProxyFactory<C> proxyServiceFactory;

    private P proxyProvider;

    private final Class<X> proxyInterface;
    private final Class<C> serviceInterface;

    protected AbstractProxyProviderRegistry(Class<X> proxyInterface, Class<C> serviceInterface) {
        this.proxyInterface = proxyInterface;
        this.serviceInterface = serviceInterface;
    }

    /**
     * Returns the proxy serializer.
     *
     * @return the proxy serializer
     */
    protected abstract Serializer getProxySerializer();

    /**
     * Creates a proxy provider service for the given provider.
     *
     * @param provider the provider for which to create the service
     * @return the proxy provider service
     */
    protected abstract S createProxyProviderService(P provider);

    /**
     * Creates a controller provider service for the given provider.
     *
     * @param provider the provider for which to create the service
     * @return the controller provider service
     */
    protected abstract S createControllerProviderService(P provider);

    /**
     * Creates an instance of the controller provider.
     *
     * @return the controller provider
     */
    protected abstract P createControllerProvider();

    /**
     * Creates a proxy implementation.
     *
     * @return a proxy implementation
     */
    protected abstract X createProxy();

    /**
     * Creates a proxy service implementation.
     *
     * @return a proxy service implementation
     */
    protected abstract C createProxyService();

    /**
     * Activates the proxy.
     */
    protected void activateProxy() {
        proxyFactory = proxyService.getProxyFactory(proxyInterface, getProxySerializer());
        proxyServiceFactory = proxyService.getProxyFactory(serviceInterface, getProxySerializer());

        if (proxyRoleService.isProxyEnabled() && proxyRoleService.isControllerNode()) {
            proxyService.registerProxyService(serviceInterface, createProxyService(), getProxySerializer());
            proxyProvider = createControllerProvider();
        } else if (proxyRoleService.isProxyEnabled() && proxyRoleService.isProxyNode()) {
            proxyService.registerProxyService(proxyInterface, createProxy(), getProxySerializer());
        }
    }

    /**
     * Deactivates the proxy.
     */
    protected void deactivateProxy() {
        proxyService.unregisterProxyService(proxyInterface);
        proxyService.unregisterProxyService(serviceInterface);
    }

    @Override
    protected synchronized P getProvider(DeviceId deviceId) {
        return proxyProvider != null ? proxyProvider : super.getProvider(deviceId);
    }

    @Override
    protected synchronized P getProvider(ProviderId providerId) {
        return proxyProvider != null ? proxyProvider : super.getProvider(providerId);
    }

    @Override
    protected S createProviderService(P provider) {
        if (proxyRoleService.isProxyEnabled() && proxyRoleService.isProxyNode()) {
            return createProxyProviderService(provider);
        }
        return createControllerProviderService(provider);
    }
}
