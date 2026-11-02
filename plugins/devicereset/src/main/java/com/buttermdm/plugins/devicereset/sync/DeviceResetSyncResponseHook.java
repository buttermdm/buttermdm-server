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
 *
 */

package com.buttermdm.plugins.devicereset.sync;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.plugin.service.PluginStatusCache;
import com.buttermdm.plugins.devicereset.persistence.DeviceResetDAO;
import com.buttermdm.plugins.devicereset.persistence.domain.DeviceResetStatus;
import com.buttermdm.rest.json.SyncResponseHook;
import com.buttermdm.rest.json.SyncResponseInt;

import static com.buttermdm.plugins.devicereset.DeviceResetPluginConfigurationImpl.PLUGIN_ID;

/**
 * <p>A hook adding the pending reset, reboot, lock and password reset requests to the device configuration.</p>
 *
 * @author buttermdm
 */
@Singleton
public class DeviceResetSyncResponseHook implements SyncResponseHook {

    private final DeviceResetDAO deviceResetDAO;

    private final PluginStatusCache pluginStatusCache;

    /**
     * <p>Constructs new <code>DeviceResetSyncResponseHook</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceResetSyncResponseHook(DeviceResetDAO deviceResetDAO, PluginStatusCache pluginStatusCache) {
        this.deviceResetDAO = deviceResetDAO;
        this.pluginStatusCache = pluginStatusCache;
    }

    /**
     * <p>Returns the original response if nothing is pending for the device, so the response doesn't change at all
     * for such devices.</p>
     *
     * @param deviceId an ID of a device.
     * @param original an original device configuration synchronization response to be handled by this hook.
     * @return a device configuration synchronization response to be used further in process.
     */
    @Override
    public SyncResponseInt handle(int deviceId, SyncResponseInt original) {
        if (this.pluginStatusCache.isPluginDisabled(PLUGIN_ID)) {
            return original;
        }
        DeviceResetStatus status = this.deviceResetDAO.getStatus(deviceId);
        if (status == null || !status.isPending()) {
            return original;
        }
        return new DeviceResetSyncResponse(original, status);
    }
}
