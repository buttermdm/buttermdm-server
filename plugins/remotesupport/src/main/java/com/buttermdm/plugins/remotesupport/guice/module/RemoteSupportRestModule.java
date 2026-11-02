/*
 *
 * ButterMDM: Open Source Android MDM Software
 * ButterMDM (https://github.com/ButterMDM)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.buttermdm.plugins.remotesupport.guice.module;

import com.google.inject.servlet.ServletModule;
import com.buttermdm.plugin.rest.PluginAccessFilter;
import com.buttermdm.plugins.remotesupport.rest.RemoteSupportPrivateResource;
import com.buttermdm.plugins.remotesupport.session.RemoteSessionManager;
import com.buttermdm.plugins.remotesupport.sync.RemoteSupportSyncResponseHook;
import com.buttermdm.rest.filter.AuthFilter;
import com.buttermdm.rest.filter.PrivateIPFilter;
import com.buttermdm.security.jwt.JWTFilter;

import java.util.Arrays;
import java.util.List;

/**
 * <p>A <code>Guice</code> module for <code>Remote Support Plugin</code> REST resources.</p>
 *
 * @author buttermdm
 */
public class RemoteSupportRestModule extends ServletModule {

    /**
     * <p>A list of patterns for URIs for plugin resources which prohibit anonymous access.</p>
     */
    private static final List<String> protectedResources = Arrays.asList(
            "/rest/plugins/remotesupport/private/*"
    );

    /**
     * <p>Constructs new <code>RemoteSupportRestModule</code> instance. This implementation does nothing.</p>
     */
    public RemoteSupportRestModule() {
    }

    /**
     * <p>Configures the <code>Remote Support Plugin</code> REST resources and the device configuration hook.</p>
     */
    protected void configureServlets() {
        this.filter(protectedResources).through(JWTFilter.class);
        this.filter(protectedResources).through(AuthFilter.class);
        this.filter(protectedResources).through(PluginAccessFilter.class);
        this.filter(protectedResources).through(PrivateIPFilter.class);
        this.bind(RemoteSupportPrivateResource.class);
        this.bind(RemoteSupportSyncResponseHook.class);
        // Created at start: it ends the sessions left open by a previous run and serves the WebSocket endpoint
        this.bind(RemoteSessionManager.class).asEagerSingleton();
    }
}
