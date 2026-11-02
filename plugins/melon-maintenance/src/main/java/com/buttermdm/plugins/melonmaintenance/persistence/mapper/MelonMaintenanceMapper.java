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

package com.buttermdm.plugins.melonmaintenance.persistence.mapper;

import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceDeviceStatus;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceForceAudit;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>MyBatis mapper for <code>plugin_melon_maintenance_*</code> tables.</p>
 *
 * @author buttermdm
 */
public interface MelonMaintenanceMapper {

    @Select("SELECT * FROM plugin_melon_maintenance_settings WHERE customerId = #{customerId}")
    MelonMaintenanceSettings findSettings(@Param("customerId") int customerId);

    @Insert("INSERT INTO plugin_melon_maintenance_settings (" +
            "customerId, gateEnabled, gatePackage, gateSignerSha256, askTimeoutSeconds, minRetrySeconds" +
            ") VALUES (" +
            "#{customerId}, #{gateEnabled}, #{gatePackage}, #{gateSignerSha256}, #{askTimeoutSeconds}, #{minRetrySeconds}" +
            ")")
    void insertSettings(MelonMaintenanceSettings settings);

    @Update("UPDATE plugin_melon_maintenance_settings SET " +
            "gateEnabled = #{gateEnabled}, " +
            "gatePackage = #{gatePackage}, " +
            "gateSignerSha256 = #{gateSignerSha256}, " +
            "askTimeoutSeconds = #{askTimeoutSeconds}, " +
            "minRetrySeconds = #{minRetrySeconds} " +
            "WHERE customerId = #{customerId}")
    void updateSettings(MelonMaintenanceSettings settings);

    @Select("SELECT * FROM plugin_melon_maintenance_devices WHERE deviceId = #{deviceId}")
    MelonMaintenanceDeviceStatus findDeviceStatus(@Param("deviceId") int deviceId);

    @Select("SELECT s.*, d.number AS deviceNumber FROM plugin_melon_maintenance_devices s " +
            "LEFT JOIN devices d ON d.id = s.deviceId " +
            "WHERE s.customerId = #{customerId} ORDER BY d.number")
    List<MelonMaintenanceDeviceStatus> findAllDeviceStatuses(@Param("customerId") int customerId);

    @Insert("INSERT INTO plugin_melon_maintenance_devices (" +
            "deviceId, customerId, lastDecision, lastReason, lastLeaseId, lastOutcome, " +
            "deferralCount, threeNightAlertActive, nextRetryAt, lastReportedAt" +
            ") VALUES (" +
            "#{deviceId}, #{customerId}, #{lastDecision}, #{lastReason}, #{lastLeaseId}, #{lastOutcome}, " +
            "#{deferralCount}, #{threeNightAlertActive}, #{nextRetryAt}, #{lastReportedAt}" +
            ") ON CONFLICT (deviceId) DO UPDATE SET " +
            "lastDecision = EXCLUDED.lastDecision, " +
            "lastReason = EXCLUDED.lastReason, " +
            "lastLeaseId = COALESCE(EXCLUDED.lastLeaseId, plugin_melon_maintenance_devices.lastLeaseId), " +
            "lastOutcome = COALESCE(EXCLUDED.lastOutcome, plugin_melon_maintenance_devices.lastOutcome), " +
            "deferralCount = EXCLUDED.deferralCount, " +
            "threeNightAlertActive = EXCLUDED.threeNightAlertActive, " +
            "nextRetryAt = EXCLUDED.nextRetryAt, " +
            "lastReportedAt = EXCLUDED.lastReportedAt")
    void upsertDeviceStatus(MelonMaintenanceDeviceStatus status);

    @Insert("INSERT INTO plugin_melon_maintenance_force_audit (" +
            "deviceId, customerId, userId, username, ticketRef, packageName, reason, createdAt" +
            ") VALUES (" +
            "#{deviceId}, #{customerId}, #{userId}, #{username}, #{ticketRef}, #{packageName}, #{reason}, #{createdAt}" +
            ")")
    void insertForceAudit(MelonMaintenanceForceAudit audit);
}
