/*
 *
 * ButterMDM: Open Source Android MDM Software
 * https://github.com/ButterMDM
 *
 * Copyright (C) 2026 ButterMDM (https://github.com/ButterMDM)
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

package com.buttermdm.plugins.rustdesk.guice.module;

import com.google.inject.servlet.ServletModule;
import com.buttermdm.plugin.rest.PluginAccessFilter;
import com.buttermdm.plugins.rustdesk.rest.RustDeskPrivateResource;
import com.buttermdm.plugins.rustdesk.rest.RustDeskPublicResource;
import com.buttermdm.plugins.rustdesk.sync.RustDeskSyncResponseHook;
import com.buttermdm.rest.filter.AuthFilter;
import com.buttermdm.rest.filter.PrivateIPFilter;
import com.buttermdm.security.jwt.JWTFilter;

import java.util.Arrays;
import java.util.List;

/**
 * <p>Guice servlet and REST module for the <code>RustDesk Integration</code> plugin.</p>
 *
 * @author buttermdm
 */
public class RustDeskRestModule extends ServletModule {

    private static final List<String> protectedResources = Arrays.asList(
            "/rest/plugins/rustdesk/private/*"
    );

    public RustDeskRestModule() {
    }

    @Override
    protected void configureServlets() {
        this.filter(protectedResources).through(JWTFilter.class);
        this.filter(protectedResources).through(AuthFilter.class);
        this.filter(protectedResources).through(PluginAccessFilter.class);
        this.filter(protectedResources).through(PrivateIPFilter.class);
        this.bind(RustDeskPrivateResource.class);
        this.bind(RustDeskPublicResource.class);
        this.bind(RustDeskSyncResponseHook.class);
    }
}
