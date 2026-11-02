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

package com.buttermdm.plugins.rustdesk.sync;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugin.service.PluginStatusCache;
import com.buttermdm.plugins.rustdesk.persistence.RustDeskDAO;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import com.buttermdm.rest.json.SyncResponseHook;
import com.buttermdm.rest.json.SyncResponseInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.buttermdm.plugins.rustdesk.RustDeskPluginConfigurationImpl.PLUGIN_ID;

/**
 * <p>Hook extending the device configuration sync response with RustDesk server and permission settings.</p>
 *
 * @author buttermdm
 */
@Singleton
public class RustDeskSyncResponseHook implements SyncResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(RustDeskSyncResponseHook.class);

    private final RustDeskDAO rustDeskDAO;
    private final UnsecureDAO unsecureDAO;
    private final PluginStatusCache pluginStatusCache;

    @Inject
    public RustDeskSyncResponseHook(RustDeskDAO rustDeskDAO,
                                    UnsecureDAO unsecureDAO,
                                    PluginStatusCache pluginStatusCache) {
        this.rustDeskDAO = rustDeskDAO;
        this.unsecureDAO = unsecureDAO;
        this.pluginStatusCache = pluginStatusCache;
    }

    @Override
    public SyncResponseInt handle(int deviceId, SyncResponseInt original) {
        if (this.pluginStatusCache.isPluginDisabled(PLUGIN_ID)) {
            return original;
        }
        try {
            Device device = unsecureDAO.getDeviceById(deviceId);
            if (device == null) {
                return original;
            }
            RustDeskSettings settings = rustDeskDAO.getSettingsByCustomerId(device.getCustomerId());
            return settings != null && settings.isEnabled() ? new RustDeskSyncResponse(original, settings) : original;
        } catch (Exception e) {
            logger.error("Failed to attach RustDesk settings to sync response for device #{}", deviceId, e);
            return original;
        }
    }
}
