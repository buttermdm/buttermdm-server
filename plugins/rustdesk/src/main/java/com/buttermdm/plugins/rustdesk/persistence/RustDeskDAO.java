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

package com.buttermdm.plugins.rustdesk.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.AbstractDAO;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskDeviceStatus;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import com.buttermdm.plugins.rustdesk.persistence.mapper.RustDeskMapper;
import com.buttermdm.plugins.rustdesk.service.RustDeskConfigEncoder;
import com.buttermdm.plugins.rustdesk.service.RustDeskServerBridge;
import com.buttermdm.security.SecurityContext;
import org.mybatis.guice.transactional.Transactional;

import java.util.List;

/**
 * <p>Data Access Object for the <code>RustDesk Integration</code> plugin.</p>
 *
 * @author buttermdm
 */
@Singleton
public class RustDeskDAO extends AbstractDAO<RustDeskSettings> {

    private final RustDeskMapper mapper;
    private final RustDeskServerBridge serverBridge;

    @Inject
    public RustDeskDAO(RustDeskMapper mapper, RustDeskServerBridge serverBridge) {
        this.mapper = mapper;
        this.serverBridge = serverBridge;
    }

    public RustDeskSettings getSettings() {
        RustDeskSettings settings = getSingleRecord(this.mapper::findSettings);
        if (settings == null) {
            settings = createDefaultSettings(SecurityContext.get().getCurrentUser().get().getCustomerId());
        } else {
            enrichSettings(settings);
        }
        return settings;
    }

    public RustDeskSettings getSettingsByCustomerId(int customerId) {
        RustDeskSettings settings = this.mapper.findSettings(customerId);
        if (settings == null) {
            settings = createDefaultSettings(customerId);
        } else {
            enrichSettings(settings);
        }
        return settings;
    }

    @Transactional
    public RustDeskSettings saveSettings(RustDeskSettings settings) {
        int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
        settings.setCustomerId(customerId);
        enrichSettings(settings);
        RustDeskSettings existing = this.mapper.findSettings(customerId);
        if (existing == null) {
            this.mapper.insertSettings(settings);
        } else {
            this.mapper.updateSettings(settings);
        }
        return getSettingsByCustomerId(customerId);
    }

    public RustDeskDeviceStatus getDeviceStatus(int deviceId) {
        return this.mapper.findDeviceStatus(deviceId);
    }

    public List<RustDeskDeviceStatus> getAllDeviceStatuses(int customerId) {
        return this.mapper.findAllDeviceStatuses(customerId);
    }

    @Transactional
    public void saveDeviceStatus(RustDeskDeviceStatus status) {
        this.mapper.upsertDeviceStatus(status);
    }

    private RustDeskSettings createDefaultSettings(int customerId) {
        RustDeskSettings settings = new RustDeskSettings();
        settings.setCustomerId(customerId);
        settings.setEnabled(true);
        settings.setAutoProvision(true);
        settings.setAutoGrantPermissions(true);
        settings.setEnableInputService(true);
        settings.setAllowInKiosk(true);
        settings.setAutoStart(false);
        enrichSettings(settings);
        return settings;
    }

    private void enrichSettings(RustDeskSettings settings) {
        String detectedHost = serverBridge.detectLocalHostAddress();
        if ((settings.getIdServer() == null || settings.getIdServer().isEmpty()) && detectedHost != null) {
            settings.setIdServer(detectedHost + ":21116");
        }
        if ((settings.getRelayServer() == null || settings.getRelayServer().isEmpty()) && detectedHost != null) {
            settings.setRelayServer(detectedHost + ":21117");
        }
        if (settings.getPublicKey() == null || settings.getPublicKey().isEmpty()) {
            String key = serverBridge.readPublicKey(settings.getHbbsKeyPath());
            if (key != null && !key.isEmpty()) {
                settings.setPublicKey(key);
            }
        }
        settings.setConfigString(RustDeskConfigEncoder.encode(
                settings.getIdServer(),
                settings.getRelayServer(),
                settings.getApiServer(),
                settings.getPublicKey()
        ));
    }
}
