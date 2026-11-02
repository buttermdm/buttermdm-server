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
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Live health and discovery status of the local or configured RustDesk Server (<code>hbbs</code> / <code>hbbr</code>).</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustDeskServerStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private String detectedHostIp;
    private boolean hbbsInstalled;
    private boolean hbbrInstalled;
    private boolean hbbsNatPortOpen;
    private boolean hbbsIdPortOpen;
    private boolean hbbsWsPortOpen;
    private boolean hbbrRelayPortOpen;
    private boolean hbbrWsPortOpen;
    private String publicKey;
    private String configString;
    private boolean apkProvisionedInMdm;
    private Integer mdmApplicationId;
    private Integer mdmApplicationVersionId;
    private String mdmApkVersion;
    private List<RustDeskPeerInfo> registeredPeers = new ArrayList<>();

    public RustDeskServerStatus() {
    }

    public String getDetectedHostIp() {
        return detectedHostIp;
    }

    public void setDetectedHostIp(String detectedHostIp) {
        this.detectedHostIp = detectedHostIp;
    }

    public boolean isHbbsInstalled() {
        return hbbsInstalled;
    }

    public void setHbbsInstalled(boolean hbbsInstalled) {
        this.hbbsInstalled = hbbsInstalled;
    }

    public boolean isHbbrInstalled() {
        return hbbrInstalled;
    }

    public void setHbbrInstalled(boolean hbbrInstalled) {
        this.hbbrInstalled = hbbrInstalled;
    }

    public boolean isHbbsNatPortOpen() {
        return hbbsNatPortOpen;
    }

    public void setHbbsNatPortOpen(boolean hbbsNatPortOpen) {
        this.hbbsNatPortOpen = hbbsNatPortOpen;
    }

    public boolean isHbbsIdPortOpen() {
        return hbbsIdPortOpen;
    }

    public void setHbbsIdPortOpen(boolean hbbsIdPortOpen) {
        this.hbbsIdPortOpen = hbbsIdPortOpen;
    }

    public boolean isHbbsWsPortOpen() {
        return hbbsWsPortOpen;
    }

    public void setHbbsWsPortOpen(boolean hbbsWsPortOpen) {
        this.hbbsWsPortOpen = hbbsWsPortOpen;
    }

    public boolean isHbbrRelayPortOpen() {
        return hbbrRelayPortOpen;
    }

    public void setHbbrRelayPortOpen(boolean hbbrRelayPortOpen) {
        this.hbbrRelayPortOpen = hbbrRelayPortOpen;
    }

    public boolean isHbbrWsPortOpen() {
        return hbbrWsPortOpen;
    }

    public void setHbbrWsPortOpen(boolean hbbrWsPortOpen) {
        this.hbbrWsPortOpen = hbbrWsPortOpen;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getConfigString() {
        return configString;
    }

    public void setConfigString(String configString) {
        this.configString = configString;
    }

    public boolean isApkProvisionedInMdm() {
        return apkProvisionedInMdm;
    }

    public void setApkProvisionedInMdm(boolean apkProvisionedInMdm) {
        this.apkProvisionedInMdm = apkProvisionedInMdm;
    }

    public Integer getMdmApplicationId() {
        return mdmApplicationId;
    }

    public void setMdmApplicationId(Integer mdmApplicationId) {
        this.mdmApplicationId = mdmApplicationId;
    }

    public Integer getMdmApplicationVersionId() {
        return mdmApplicationVersionId;
    }

    public void setMdmApplicationVersionId(Integer mdmApplicationVersionId) {
        this.mdmApplicationVersionId = mdmApplicationVersionId;
    }

    public String getMdmApkVersion() {
        return mdmApkVersion;
    }

    public void setMdmApkVersion(String mdmApkVersion) {
        this.mdmApkVersion = mdmApkVersion;
    }

    public List<RustDeskPeerInfo> getRegisteredPeers() {
        return registeredPeers;
    }

    public void setRegisteredPeers(List<RustDeskPeerInfo> registeredPeers) {
        this.registeredPeers = registeredPeers;
    }
}
