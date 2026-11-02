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

package com.buttermdm.plugins.melonmaintenance.sync;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugin.service.PluginStatusCache;
import com.buttermdm.plugins.melonmaintenance.persistence.MelonMaintenanceDAO;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceSettings;
import com.buttermdm.rest.json.SyncResponseHook;
import com.buttermdm.rest.json.SyncResponseInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.buttermdm.plugins.melonmaintenance.MelonMaintenancePluginConfigurationImpl.PLUGIN_ID;

/**
 * <p>Hook extending the device configuration sync response with the <code>melonMaintenance</code> policy block.</p>
 *
 * @author buttermdm
 */
@Singleton
public class MelonMaintenanceSyncResponseHook implements SyncResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(MelonMaintenanceSyncResponseHook.class);

    private final MelonMaintenanceDAO melonMaintenanceDAO;
    private final UnsecureDAO unsecureDAO;
    private final PluginStatusCache pluginStatusCache;

    @Inject
    public MelonMaintenanceSyncResponseHook(MelonMaintenanceDAO melonMaintenanceDAO,
                                            UnsecureDAO unsecureDAO,
                                            PluginStatusCache pluginStatusCache) {
        this.melonMaintenanceDAO = melonMaintenanceDAO;
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
            MelonMaintenanceSettings settings = melonMaintenanceDAO.getSettingsByCustomerId(device.getCustomerId());
            return settings != null && settings.isGateEnabled()
                    ? new MelonMaintenanceSyncResponse(original, settings)
                    : original;
        } catch (Exception e) {
            logger.error("Failed to attach Melon maintenance settings to sync response for device #{}", deviceId, e);
            return original;
        }
    }
}
