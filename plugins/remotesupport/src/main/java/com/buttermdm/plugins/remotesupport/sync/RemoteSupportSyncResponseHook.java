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

package com.buttermdm.plugins.remotesupport.sync;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugin.service.PluginStatusCache;
import com.buttermdm.plugins.remotesupport.persistence.domain.RemoteSupportSettings;
import com.buttermdm.plugins.remotesupport.persistence.mapper.RemoteSupportMapper;
import com.buttermdm.rest.json.SyncResponseHook;
import com.buttermdm.rest.json.SyncResponseInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.buttermdm.plugins.remotesupport.RemoteSupportPluginConfigurationImpl.PLUGIN_ID;

/**
 * <p>A hook telling the device that remote support is enabled for its configuration, so it prepares for it (enables
 * its accessibility service where possible). Without remote support, the configuration is returned unchanged.</p>
 *
 * @author buttermdm
 */
@Singleton
public class RemoteSupportSyncResponseHook implements SyncResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSupportSyncResponseHook.class);

    private final RemoteSupportMapper mapper;

    private final UnsecureDAO unsecureDAO;

    private final PluginStatusCache pluginStatusCache;

    /**
     * <p>Constructs new <code>RemoteSupportSyncResponseHook</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public RemoteSupportSyncResponseHook(RemoteSupportMapper mapper, UnsecureDAO unsecureDAO,
                                         PluginStatusCache pluginStatusCache) {
        this.mapper = mapper;
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
            if (device == null || device.getConfigurationId() == null) {
                return original;
            }
            RemoteSupportSettings settings = mapper.getSettings(device.getConfigurationId());
            return settings != null && settings.isEnabled() ? new RemoteSupportSyncResponse(original, settings) : original;
        } catch (Exception e) {
            // The configuration must reach the device even if this plugin fails
            logger.error("Failed to extend the configuration of device #{}", deviceId, e);
            return original;
        }
    }
}
