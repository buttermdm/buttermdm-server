/*
 *
 * ButterMDM: Open Source Android MDM Software
 * https://github.com/ButterMDM
 *
 * Copyright (C) 2019 ButterMDM (https://github.com/ButterMDM)
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

package com.buttermdm.guice.module;

import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;
import com.buttermdm.rest.filter.PrivateIPFilter;
import com.buttermdm.rest.resource.*;
import com.buttermdm.rest.filter.AuthFilter;
import com.buttermdm.security.jwt.JWTFilter;

public class PrivateRestModule extends ServletModule {
    public PrivateRestModule() {
    }

    protected void configureServlets() {
        this.filter("/rest/private/*").through(JWTFilter.class);
        this.filter("/rest/private/*").through(AuthFilter.class);
        this.filter("/rest/private/*").through(PrivateIPFilter.class);
        this.bind(DeviceResource.class);
        this.bind(GroupResource.class);
        this.bind(ConfigurationResource.class);
        this.bind(CustomerResource.class);
        this.bind(SettingsResource.class);
        this.bind(ApplicationResource.class);
        this.bind(IconResource.class);
        this.bind(UserResource.class);
        this.bind(FilesResource.class);
        this.bind(IconFileResource.class);
        this.bind(HintResource.class);
        this.bind(UserRoleResource.class);
    }
}
