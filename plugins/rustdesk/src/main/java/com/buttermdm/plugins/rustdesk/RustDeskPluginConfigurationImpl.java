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

package com.buttermdm.plugins.rustdesk;

import com.google.inject.Module;
import com.buttermdm.plugin.PluginConfiguration;
import com.buttermdm.plugins.rustdesk.guice.module.RustDeskLiquibaseModule;
import com.buttermdm.plugins.rustdesk.guice.module.RustDeskPersistenceModule;
import com.buttermdm.plugins.rustdesk.guice.module.RustDeskRestModule;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Plugin configuration for the <code>RustDesk Integration</code> plugin.</p>
 *
 * @author buttermdm
 */
public class RustDeskPluginConfigurationImpl implements PluginConfiguration {

    public static final String PLUGIN_ID = "rustdesk";

    public RustDeskPluginConfigurationImpl() {
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getRootPackage() {
        return "com.buttermdm.plugins.rustdesk";
    }

    @Override
    public List<Module> getPluginModules(ServletContext context) {
        List<Module> modules = new ArrayList<>();
        modules.add(new RustDeskLiquibaseModule(context));
        modules.add(new RustDeskPersistenceModule(context));
        modules.add(new RustDeskRestModule());
        return modules;
    }
}
