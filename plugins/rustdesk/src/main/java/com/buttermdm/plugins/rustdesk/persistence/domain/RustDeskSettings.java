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

package com.buttermdm.plugins.rustdesk.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.buttermdm.persistence.domain.CustomerData;

import java.io.Serializable;

/**
 * <p>Per-customer configuration for the <code>RustDesk Integration</code> plugin.</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustDeskSettings implements CustomerData, Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_HBBS_DB_PATH = "/opt/rustdesk/db_v2.sqlite3";
    public static final String DEFAULT_HBBS_KEY_PATH = "/opt/rustdesk/id_ed25519.pub";
    public static final String RUSTDESK_PACKAGE = "com.carriez.flutter_hbb";

    private Integer id;
    private int customerId;
    private boolean enabled = true;
    private String idServer = "";
    private String relayServer = "";
    private String apiServer = "";
    private String publicKey = "";
    private String hbbsDbPath = DEFAULT_HBBS_DB_PATH;
    private String hbbsKeyPath = DEFAULT_HBBS_KEY_PATH;
    private String apkUrl = "";
    private String apkVersion = "";
    private boolean autoProvision = true;
    private boolean autoGrantPermissions = true;
    private boolean enableInputService = true;
    private boolean allowInKiosk = true;
    private boolean autoStart = false;
    private String permanentPassword = "";
    private String configString = "";

    public RustDeskSettings() {
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public int getCustomerId() {
        return customerId;
    }

    @Override
    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIdServer() {
        return idServer;
    }

    public void setIdServer(String idServer) {
        this.idServer = idServer != null ? idServer.trim() : "";
    }

    public String getRelayServer() {
        return relayServer;
    }

    public void setRelayServer(String relayServer) {
        this.relayServer = relayServer != null ? relayServer.trim() : "";
    }

    public String getApiServer() {
        return apiServer;
    }

    public void setApiServer(String apiServer) {
        this.apiServer = apiServer != null ? apiServer.trim() : "";
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey != null ? publicKey.trim() : "";
    }

    public String getHbbsDbPath() {
        return hbbsDbPath;
    }

    public void setHbbsDbPath(String hbbsDbPath) {
        this.hbbsDbPath = hbbsDbPath != null && !hbbsDbPath.trim().isEmpty() ? hbbsDbPath.trim() : DEFAULT_HBBS_DB_PATH;
    }

    public String getHbbsKeyPath() {
        return hbbsKeyPath;
    }

    public void setHbbsKeyPath(String hbbsKeyPath) {
        this.hbbsKeyPath = hbbsKeyPath != null && !hbbsKeyPath.trim().isEmpty() ? hbbsKeyPath.trim() : DEFAULT_HBBS_KEY_PATH;
    }

    public String getApkUrl() {
        return apkUrl;
    }

    public void setApkUrl(String apkUrl) {
        this.apkUrl = apkUrl != null ? apkUrl.trim() : "";
    }

    public String getApkVersion() {
        return apkVersion;
    }

    public void setApkVersion(String apkVersion) {
        this.apkVersion = apkVersion != null ? apkVersion.trim() : "";
    }

    public boolean isAutoProvision() {
        return autoProvision;
    }

    public void setAutoProvision(boolean autoProvision) {
        this.autoProvision = autoProvision;
    }

    public boolean isAutoGrantPermissions() {
        return autoGrantPermissions;
    }

    public void setAutoGrantPermissions(boolean autoGrantPermissions) {
        this.autoGrantPermissions = autoGrantPermissions;
    }

    public boolean isEnableInputService() {
        return enableInputService;
    }

    public void setEnableInputService(boolean enableInputService) {
        this.enableInputService = enableInputService;
    }

    public boolean isAllowInKiosk() {
        return allowInKiosk;
    }

    public void setAllowInKiosk(boolean allowInKiosk) {
        this.allowInKiosk = allowInKiosk;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public String getPermanentPassword() {
        return permanentPassword;
    }

    public void setPermanentPassword(String permanentPassword) {
        this.permanentPassword = permanentPassword != null ? permanentPassword : "";
    }

    public String getConfigString() {
        return configString;
    }

    public void setConfigString(String configString) {
        this.configString = configString;
    }
}
