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

package com.buttermdm.plugins.rustdesk.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * <p>Enriched view combining a ButterMDM device, its reported RustDesk status, and live <code>hbbs</code> peer correlation.</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustDeskDeviceView implements Serializable {

    private static final long serialVersionUID = 1L;

    private int deviceId;
    private String deviceNumber;
    private String description;
    private Integer configurationId;
    private String model;
    private String androidVersion;
    private boolean installed;
    private String versionName;
    private Integer versionCode;
    private String rustdeskId;
    private String peerUuid;
    private boolean inputServiceEnabled;
    private boolean overlayGranted;
    private boolean storageGranted;
    private boolean notificationsGranted;
    private boolean mediaProjectionGranted;
    private boolean batteryOptimizationIgnored;
    private boolean serverConfigured;
    private boolean hbbsRegistered;
    private Long lastReportedAt;
    private String connectUri;

    public RustDeskDeviceView() {
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceNumber() {
        return deviceNumber;
    }

    public void setDeviceNumber(String deviceNumber) {
        this.deviceNumber = deviceNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getConfigurationId() {
        return configurationId;
    }

    public void setConfigurationId(Integer configurationId) {
        this.configurationId = configurationId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public void setAndroidVersion(String androidVersion) {
        this.androidVersion = androidVersion;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public Integer getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(Integer versionCode) {
        this.versionCode = versionCode;
    }

    public String getRustdeskId() {
        return rustdeskId;
    }

    public void setRustdeskId(String rustdeskId) {
        this.rustdeskId = rustdeskId;
    }

    public String getPeerUuid() {
        return peerUuid;
    }

    public void setPeerUuid(String peerUuid) {
        this.peerUuid = peerUuid;
    }

    public boolean isInputServiceEnabled() {
        return inputServiceEnabled;
    }

    public void setInputServiceEnabled(boolean inputServiceEnabled) {
        this.inputServiceEnabled = inputServiceEnabled;
    }

    public boolean isOverlayGranted() {
        return overlayGranted;
    }

    public void setOverlayGranted(boolean overlayGranted) {
        this.overlayGranted = overlayGranted;
    }

    public boolean isStorageGranted() {
        return storageGranted;
    }

    public void setStorageGranted(boolean storageGranted) {
        this.storageGranted = storageGranted;
    }

    public boolean isNotificationsGranted() {
        return notificationsGranted;
    }

    public void setNotificationsGranted(boolean notificationsGranted) {
        this.notificationsGranted = notificationsGranted;
    }

    public boolean isMediaProjectionGranted() {
        return mediaProjectionGranted;
    }

    public void setMediaProjectionGranted(boolean mediaProjectionGranted) {
        this.mediaProjectionGranted = mediaProjectionGranted;
    }

    public boolean isBatteryOptimizationIgnored() {
        return batteryOptimizationIgnored;
    }

    public void setBatteryOptimizationIgnored(boolean batteryOptimizationIgnored) {
        this.batteryOptimizationIgnored = batteryOptimizationIgnored;
    }

    public boolean isServerConfigured() {
        return serverConfigured;
    }

    public void setServerConfigured(boolean serverConfigured) {
        this.serverConfigured = serverConfigured;
    }

    public boolean isHbbsRegistered() {
        return hbbsRegistered;
    }

    public void setHbbsRegistered(boolean hbbsRegistered) {
        this.hbbsRegistered = hbbsRegistered;
    }

    public Long getLastReportedAt() {
        return lastReportedAt;
    }

    public void setLastReportedAt(Long lastReportedAt) {
        this.lastReportedAt = lastReportedAt;
    }

    public String getConnectUri() {
        return connectUri;
    }

    public void setConnectUri(String connectUri) {
        this.connectUri = connectUri;
    }
}
