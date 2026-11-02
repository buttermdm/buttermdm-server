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

package com.buttermdm.plugins.melonmaintenance.guice.module;

import com.buttermdm.guice.module.AbstractPersistenceModule;

import javax.servlet.ServletContext;

/**
 * <p>MyBatis persistence module for the <code>Melon Maintenance Gate</code> plugin.</p>
 *
 * @author buttermdm
 */
public class MelonMaintenancePersistenceModule extends AbstractPersistenceModule {

    public MelonMaintenancePersistenceModule(ServletContext context) {
        super(context);
    }

    @Override
    protected String getMapperPackageName() {
        return "com.buttermdm.plugins.melonmaintenance.persistence.mapper";
    }

    @Override
    protected String getDomainObjectsPackageName() {
        return "com.buttermdm.plugins.melonmaintenance.persistence.domain";
    }
}
