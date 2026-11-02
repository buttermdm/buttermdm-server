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

package com.buttermdm.plugin.guice.module;

import com.google.inject.Inject;
import com.buttermdm.event.EventService;
import com.buttermdm.plugin.PluginTaskModule;
import com.buttermdm.plugin.persistence.CustomerCreatedEventListener;
import com.buttermdm.plugin.persistence.PluginDAO;
import com.buttermdm.util.BackgroundTaskRunnerService;

import java.util.concurrent.TimeUnit;

/**
 * <p>A module used for initializing the tasks to be executed in background.</p>
 *
 * @author isv
 */
public class PluginPlatformTaskModule implements PluginTaskModule {
    /**
     * <p>An interface to application's events.</p>
     */
    private final EventService eventService;

    /**
     * <p>An interface to persistence layer.</p>
     */
    private final PluginDAO pluginDAO;

    /**
     * <p>Constructs new <code>DeviceInfoTaskModule</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public PluginPlatformTaskModule(EventService eventService,
                                    PluginDAO pluginDAO) {
        this.eventService = eventService;
        this.pluginDAO = pluginDAO;
    }

    /**
     * <p>Initializes this module. Schedules the task for purging the outdated device info records from DB on a daily
     * basis.</p>
     */
    @Override
    public void init() {
        this.eventService.addEventListener(new CustomerCreatedEventListener(this.pluginDAO));
    }

}
