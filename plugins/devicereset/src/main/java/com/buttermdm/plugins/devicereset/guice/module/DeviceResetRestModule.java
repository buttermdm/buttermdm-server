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

package com.buttermdm.plugins.devicereset.guice.module;

import com.google.inject.servlet.ServletModule;
import com.buttermdm.plugin.rest.PluginAccessFilter;
import com.buttermdm.plugins.devicereset.rest.DeviceResetPrivateResource;
import com.buttermdm.plugins.devicereset.rest.DeviceResetPublicResource;
import com.buttermdm.plugins.devicereset.sync.DeviceResetSyncResponseHook;
import com.buttermdm.rest.filter.AuthFilter;
import com.buttermdm.rest.filter.PrivateIPFilter;
import com.buttermdm.rest.filter.PublicIPFilter;
import com.buttermdm.security.jwt.JWTFilter;

import java.util.Arrays;
import java.util.List;

/**
 * <p>A <code>Guice</code> module for <code>Device Reset Plugin</code> REST resources.</p>
 *
 * @author buttermdm
 */
public class DeviceResetRestModule extends ServletModule {

    /**
     * <p>A list of patterns for URIs for plugin resources which prohibit anonymous access.</p>
     */
    private static final List<String> protectedResources = Arrays.asList(
            "/rest/plugins/devicereset/private/*"
    );

    /**
     * <p> A list of patterns for URIs for plugin resources available to devices</p>
     */
    private static final List<String> publicResources = Arrays.asList(
            "/rest/plugins/devicereset/public/*"
    );

    /**
     * <p>Constructs new <code>DeviceResetRestModule</code> instance. This implementation does nothing.</p>
     */
    public DeviceResetRestModule() {
    }

    /**
     * <p>Configures the <code>Device Reset Plugin</code> REST resources and the device configuration hook.</p>
     */
    protected void configureServlets() {
        this.filter(protectedResources).through(JWTFilter.class);
        this.filter(protectedResources).through(AuthFilter.class);
        this.filter(protectedResources).through(PluginAccessFilter.class);
        this.filter(protectedResources).through(PrivateIPFilter.class);
        this.filter(publicResources).through(PublicIPFilter.class);
        this.bind(DeviceResetPrivateResource.class);
        this.bind(DeviceResetPublicResource.class);
        this.bind(DeviceResetSyncResponseHook.class);
    }
}
