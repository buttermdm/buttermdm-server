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

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.buttermdm.plugins.remotesupport.persistence.domain.RemoteSupportSettings;
import com.buttermdm.rest.json.SyncApplicationInt;
import com.buttermdm.rest.json.SyncApplicationSettingInt;
import com.buttermdm.rest.json.SyncConfigurationFileInt;
import com.buttermdm.rest.json.SyncResponseInt;

import java.util.List;

/**
 * <p>A device configuration synchronization response extended with the <code>remoteSupport</code> block: remote
 * support is enabled for the configuration of the device, with its settings.</p>
 *
 * <p>The JSON sent to the device is the JSON of the original response, exactly as it is produced for the original
 * response class, followed by the block added here (see <code>DeviceResetSyncResponse</code> of the Device Reset
 * plugin, which uses the same design). The {@link SyncResponseInt} getters are delegated to the original response for
 * the other hooks; a getter added to the interface must be delegated here as well.</p>
 *
 * @author buttermdm
 */
public class RemoteSupportSyncResponse implements SyncResponseInt {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SyncResponseInt original;

    private final RemoteSupportSettings settings;

    /**
     * <p>Constructs new <code>RemoteSupportSyncResponse</code> instance.</p>
     *
     * @param original an original device configuration synchronization response.
     * @param settings the remote support settings of the device's configuration (enabled).
     */
    public RemoteSupportSyncResponse(SyncResponseInt original, RemoteSupportSettings settings) {
        this.original = original;
        this.settings = settings;
    }

    @JsonValue
    public ObjectNode toJson() {
        ObjectNode json = objectMapper.valueToTree(original);
        ObjectNode remoteSupport = json.putObject("remoteSupport");
        remoteSupport.put("enabled", true);
        remoteSupport.put("requireAcceptance", settings.isRequireAcceptance());
        remoteSupport.put("maxMinutes", settings.getMaxMinutes());
        return json;
    }

    // Delegation of the SyncResponseInt getters

    @Override
    public String getBackgroundColor() {
        return original.getBackgroundColor();
    }

    @Override
    public String getTextColor() {
        return original.getTextColor();
    }

    @Override
    public String getBackgroundImageUrl() {
        return original.getBackgroundImageUrl();
    }

    @Override
    public List<SyncApplicationInt> getApplications() {
        return original.getApplications();
    }

    @Override
    public String getPassword() {
        return original.getPassword();
    }

    @Override
    public String getImei() {
        return original.getImei();
    }

    @Override
    public String getPhone() {
        return original.getPhone();
    }

    @Override
    public String getIconSize() {
        return original.getIconSize();
    }

    @Override
    public String getTitle() {
        return original.getTitle();
    }

    @Override
    public Boolean getGps() {
        return original.getGps();
    }

    @Override
    public Boolean getBluetooth() {
        return original.getBluetooth();
    }

    @Override
    public Boolean getWifi() {
        return original.getWifi();
    }

    @Override
    public Boolean getMobileData() {
        return original.getMobileData();
    }

    @Override
    public boolean isKioskMode() {
        return original.isKioskMode();
    }

    @Override
    public Boolean getKioskHome() {
        return original.getKioskHome();
    }

    @Override
    public Boolean getKioskRecents() {
        return original.getKioskRecents();
    }

    @Override
    public Boolean getKioskNotifications() {
        return original.getKioskNotifications();
    }

    @Override
    public Boolean getKioskSystemInfo() {
        return original.getKioskSystemInfo();
    }

    @Override
    public Boolean getKioskKeyguard() {
        return original.getKioskKeyguard();
    }

    @Override
    public Boolean getKioskLockButtons() {
        return original.getKioskLockButtons();
    }

    @Override
    public Boolean getKioskScreenOn() {
        return original.getKioskScreenOn();
    }

    @Override
    public String getMainApp() {
        return original.getMainApp();
    }

    @Override
    public boolean isLockStatusBar() {
        return original.isLockStatusBar();
    }

    @Override
    public int getSystemUpdateType() {
        return original.getSystemUpdateType();
    }

    @Override
    public String getSystemUpdateFrom() {
        return original.getSystemUpdateFrom();
    }

    @Override
    public String getSystemUpdateTo() {
        return original.getSystemUpdateTo();
    }

    @Override
    public Boolean getScheduleAppUpdate() {
        return original.getScheduleAppUpdate();
    }

    @Override
    public String getAppUpdateFrom() {
        return original.getAppUpdateFrom();
    }

    @Override
    public String getAppUpdateTo() {
        return original.getAppUpdateTo();
    }

    @Override
    public String getDownloadUpdates() {
        return original.getDownloadUpdates();
    }

    @Override
    public List<SyncApplicationSettingInt> getApplicationSettings() {
        return original.getApplicationSettings();
    }

    @Override
    public Boolean getUsbStorage() {
        return original.getUsbStorage();
    }

    @Override
    public String getRequestUpdates() {
        return original.getRequestUpdates();
    }

    @Override
    public Boolean getDisableLocation() {
        return original.getDisableLocation();
    }

    @Override
    public String getAppPermissions() {
        return original.getAppPermissions();
    }

    @Override
    public String getPushOptions() {
        return original.getPushOptions();
    }

    @Override
    public Integer getKeepaliveTime() {
        return original.getKeepaliveTime();
    }

    @Override
    public Boolean getAutoBrightness() {
        return original.getAutoBrightness();
    }

    @Override
    public Integer getBrightness() {
        return original.getBrightness();
    }

    @Override
    public Boolean getManageTimeout() {
        return original.getManageTimeout();
    }

    @Override
    public Integer getTimeout() {
        return original.getTimeout();
    }

    @Override
    public Boolean getLockVolume() {
        return original.getLockVolume();
    }

    @Override
    public Boolean getManageVolume() {
        return original.getManageVolume();
    }

    @Override
    public Integer getVolume() {
        return original.getVolume();
    }

    @Override
    public String getPasswordMode() {
        return original.getPasswordMode();
    }

    @Override
    public Integer getOrientation() {
        return original.getOrientation();
    }

    @Override
    public Boolean getDisplayStatus() {
        return original.getDisplayStatus();
    }

    @Override
    public Boolean getRunDefaultLauncher() {
        return original.getRunDefaultLauncher();
    }

    @Override
    public Boolean getDisableScreenshots() {
        return original.getDisableScreenshots();
    }

    @Override
    public Boolean getAutostartForeground() {
        return original.getAutostartForeground();
    }

    @Override
    public String getTimeZone() {
        return original.getTimeZone();
    }

    @Override
    public String getAllowedClasses() {
        return original.getAllowedClasses();
    }

    @Override
    public String getNewServerUrl() {
        return original.getNewServerUrl();
    }

    @Override
    public Boolean getLockSafeSettings() {
        return original.getLockSafeSettings();
    }

    @Override
    public Boolean getPermissive() {
        return original.getPermissive();
    }

    @Override
    public Boolean getKioskExit() {
        return original.getKioskExit();
    }

    @Override
    public Boolean getShowWifi() {
        return original.getShowWifi();
    }

    @Override
    public List<SyncConfigurationFileInt> getFiles() {
        return original.getFiles();
    }

    @Override
    public String getNewNumber() {
        return original.getNewNumber();
    }

    @Override
    public String getRestrictions() {
        return original.getRestrictions();
    }

    @Override
    public String getCustom1() {
        return original.getCustom1();
    }

    @Override
    public String getCustom2() {
        return original.getCustom2();
    }

    @Override
    public String getCustom3() {
        return original.getCustom3();
    }

    @Override
    public String getAppName() {
        return original.getAppName();
    }

    @Override
    public String getVendor() {
        return original.getVendor();
    }

    @Override
    public String getDescription() {
        return original.getDescription();
    }
}
