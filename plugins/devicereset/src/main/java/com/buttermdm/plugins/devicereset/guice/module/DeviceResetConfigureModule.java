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
 */

package com.buttermdm.plugins.devicereset.guice.module;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import javax.servlet.ServletContext;

/**
 * <p>A module binding the context parameters specific to <code>Device Reset</code> plugin.</p>
 *
 * @author buttermdm
 */
public class DeviceResetConfigureModule extends AbstractModule {
    private final ServletContext context;

    public DeviceResetConfigureModule(ServletContext context) {
        this.context = context;
    }

    protected void configure() {
        String allowUnsigned = "plugin.devicereset.allow.unsigned";
        String allowUnsignedTag = this.context.getInitParameter(allowUnsigned);
        this.bindConstant().annotatedWith(Names.named(allowUnsigned)).to(
                allowUnsignedTag != null && (allowUnsignedTag.equals("1") || allowUnsignedTag.equalsIgnoreCase("true"))
        );
    }
}
