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

package com.buttermdm.plugins.devicelog.persistence.postgres.guice.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.buttermdm.plugins.devicelog.persistence.DeviceLogDAO;
import com.buttermdm.plugins.devicelog.persistence.DeviceLogPluginSettingsDAO;
import com.buttermdm.plugins.devicelog.persistence.postgres.dao.PostgresDeviceLogDAO;
import com.buttermdm.plugins.devicelog.persistence.postgres.dao.PostgresDeviceLogPluginSettingsDAO;

/**
 * <p>A module used to bind the service interfaces to specific implementations provided by the <code>Postgres</code>
 * persistence layer for <code>Device Log</code> plugin..</p>
 *
 * @author isv
 */
public class DeviceLogPostgresServiceModule extends AbstractModule {

    /**
     * <p>Constructs new <code>DeviceLogPostgresServiceModule</code> instance. This implementation does nothing.</p>
     */
    public DeviceLogPostgresServiceModule() {
    }

    /**
     * <p>Configures the services exposed by the <code>Postgres</code> persistence layer for <code>Device Log</code>
     * plugin.</p>
     */
    @Override
    protected void configure() {
        bind(DeviceLogPluginSettingsDAO.class).to(PostgresDeviceLogPluginSettingsDAO.class).in(Singleton.class);
        bind(DeviceLogDAO.class).to(PostgresDeviceLogDAO.class).in(Singleton.class);
    }
}
