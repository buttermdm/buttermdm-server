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

package com.buttermdm.plugins.melonmaintenance;

import com.google.inject.Module;
import com.buttermdm.plugin.PluginConfiguration;
import com.buttermdm.plugins.melonmaintenance.guice.module.MelonMaintenanceLiquibaseModule;
import com.buttermdm.plugins.melonmaintenance.guice.module.MelonMaintenancePersistenceModule;
import com.buttermdm.plugins.melonmaintenance.guice.module.MelonMaintenanceRestModule;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Plugin configuration for the <code>Melon Maintenance Gate</code> plugin.</p>
 *
 * @author buttermdm
 */
public class MelonMaintenancePluginConfigurationImpl implements PluginConfiguration {

    public static final String PLUGIN_ID = "melon-maintenance";

    public MelonMaintenancePluginConfigurationImpl() {
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getRootPackage() {
        return "com.buttermdm.plugins.melonmaintenance";
    }

    @Override
    public List<Module> getPluginModules(ServletContext context) {
        List<Module> modules = new ArrayList<>();
        modules.add(new MelonMaintenanceLiquibaseModule(context));
        modules.add(new MelonMaintenancePersistenceModule(context));
        modules.add(new MelonMaintenanceRestModule());
        return modules;
    }
}
