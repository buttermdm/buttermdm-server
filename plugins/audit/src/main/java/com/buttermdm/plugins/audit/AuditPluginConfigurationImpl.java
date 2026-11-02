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

package com.buttermdm.plugins.audit;

import com.google.inject.Module;
import com.buttermdm.plugin.PluginConfiguration;
import com.buttermdm.plugins.audit.guice.module.AuditConfigureModule;
import com.buttermdm.plugins.audit.guice.module.AuditLiquibaseModule;
import com.buttermdm.plugins.audit.guice.module.AuditPersistenceModule;
import com.buttermdm.plugins.audit.guice.module.AuditRestModule;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>A configuration for <code>Audit</code> plugin.</p>
 *
 * @author isv
 */
public class AuditPluginConfigurationImpl implements PluginConfiguration {

    public static final String PLUGIN_ID = "audit";

    /**
     * <p>Constructs new <code>AuditPluginConfigurationImpl</code> instance. This implementation does nothing.</p>
     */
    public AuditPluginConfigurationImpl() {
    }

    /**
     * <p>Gets the unique identifier for this plugin. This is a sort of logical name for the plugin which is used widely
     * by <code>Plugin Platform</code>.</p>
     *
     * @return a plugin identifier.
     */
    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    /**
     * <p>Gets the root package for the classes comprising the plugin.</p>
     *
     * @return a fully-qualified name of the root package for plugin code.
     */
    @Override
    public String getRootPackage() {
        return "com.buttermdm.plugins.audit";
    }

    /**
     * <p>Gets the list of modules to be used for initializing the plugin.</p>
     *
     * @param context a context for plugin usage.
     * @return a list of modules to be used for plugin initialization.
     */
    @Override
    public List<Module> getPluginModules(ServletContext context) {
        List<Module> modules = new ArrayList<>();

        modules.add(new AuditLiquibaseModule(context));
        modules.add(new AuditPersistenceModule(context));
        modules.add(new AuditConfigureModule(context));
        modules.add(new AuditRestModule());

        return modules;
    }

}
