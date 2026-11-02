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

package com.buttermdm.plugins.melonmaintenance.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.AbstractDAO;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceDeviceStatus;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceForceAudit;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceSettings;
import com.buttermdm.plugins.melonmaintenance.persistence.mapper.MelonMaintenanceMapper;
import com.buttermdm.security.SecurityContext;
import org.mybatis.guice.transactional.Transactional;

import java.util.List;

/**
 * <p>Data Access Object for the <code>Melon Maintenance Gate</code> plugin.</p>
 *
 * @author buttermdm
 */
@Singleton
public class MelonMaintenanceDAO extends AbstractDAO<MelonMaintenanceSettings> {

    private final MelonMaintenanceMapper mapper;

    @Inject
    public MelonMaintenanceDAO(MelonMaintenanceMapper mapper) {
        this.mapper = mapper;
    }

    public MelonMaintenanceSettings getSettings() {
        MelonMaintenanceSettings settings = getSingleRecord(this.mapper::findSettings);
        if (settings == null) {
            settings = createDefaultSettings(SecurityContext.get().getCurrentUser().get().getCustomerId());
        } else {
            settings.normalize();
        }
        return settings;
    }

    public MelonMaintenanceSettings getSettingsByCustomerId(int customerId) {
        MelonMaintenanceSettings settings = this.mapper.findSettings(customerId);
        if (settings == null) {
            settings = createDefaultSettings(customerId);
        } else {
            settings.normalize();
        }
        return settings;
    }

    @Transactional
    public MelonMaintenanceSettings saveSettings(MelonMaintenanceSettings settings) {
        int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
        settings.setCustomerId(customerId);
        settings.normalize();
        MelonMaintenanceSettings existing = this.mapper.findSettings(customerId);
        if (existing == null) {
            this.mapper.insertSettings(settings);
        } else {
            this.mapper.updateSettings(settings);
        }
        return getSettingsByCustomerId(customerId);
    }

    public MelonMaintenanceDeviceStatus getDeviceStatus(int deviceId) {
        return this.mapper.findDeviceStatus(deviceId);
    }

    public List<MelonMaintenanceDeviceStatus> getAllDeviceStatuses(int customerId) {
        return this.mapper.findAllDeviceStatuses(customerId);
    }

    @Transactional
    public void saveDeviceStatus(MelonMaintenanceDeviceStatus status) {
        status.recomputeAlertFlag();
        this.mapper.upsertDeviceStatus(status);
    }

    @Transactional
    public void recordForceAudit(MelonMaintenanceForceAudit audit) {
        this.mapper.insertForceAudit(audit);
    }

    private MelonMaintenanceSettings createDefaultSettings(int customerId) {
        MelonMaintenanceSettings settings = new MelonMaintenanceSettings();
        settings.setCustomerId(customerId);
        settings.setGateEnabled(false);
        settings.setGatePackage(MelonMaintenanceSettings.PACKAGE_GROCERY);
        settings.setGateSignerSha256("");
        settings.setAskTimeoutSeconds(MelonMaintenanceSettings.DEFAULT_ASK_TIMEOUT_SECONDS);
        settings.setMinRetrySeconds(MelonMaintenanceSettings.DEFAULT_MIN_RETRY_SECONDS);
        return settings;
    }
}
