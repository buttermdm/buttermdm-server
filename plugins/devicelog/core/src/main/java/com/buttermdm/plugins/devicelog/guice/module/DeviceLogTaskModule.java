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

package com.buttermdm.plugins.devicelog.guice.module;

import com.google.inject.Inject;
import com.buttermdm.event.EventService;
import com.buttermdm.persistence.CustomerDAO;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Customer;
import com.buttermdm.plugin.PluginTaskModule;
import com.buttermdm.plugins.devicelog.persistence.CustomerCreatedEventListener;
import com.buttermdm.plugins.devicelog.persistence.DeviceLogPluginSettingsDAO;

/**
 * <p>A module used for initializing the tasks to be executed in background.</p>
 *
 * @author isv
 */
public class DeviceLogTaskModule implements PluginTaskModule {

    /**
     * <p>An interface to application's events.</p>
     */
    private final EventService eventService;

    /**
     * <p>An interface to the persistence layer.</p>
     */
    private final DeviceLogPluginSettingsDAO settingsDAO;
    private final CustomerDAO customerDAO;
    private final UnsecureDAO unsecureDAO;

    /**
     * <p>Constructs new <code>DeviceLogTaskModule</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceLogTaskModule(EventService eventService,
                               DeviceLogPluginSettingsDAO settingsDAO,
                               CustomerDAO customerDAO,
                               UnsecureDAO unsecureDAO) {
        this.eventService = eventService;
        this.settingsDAO = settingsDAO;
        this.customerDAO = customerDAO;
        this.unsecureDAO = unsecureDAO;
    }

    /**
     * <p>Initializes this module. Sets the new customer event handler.</p>
     */
    @Override
    public void init() {
        this.eventService.addEventListener(new CustomerCreatedEventListener(this.settingsDAO, this.customerDAO, this.unsecureDAO));
    }

}
