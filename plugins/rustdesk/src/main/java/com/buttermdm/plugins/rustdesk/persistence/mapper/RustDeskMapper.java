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

package com.buttermdm.plugins.rustdesk.persistence.mapper;

import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskDeviceStatus;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>MyBatis mapper for <code>plugin_rustdesk_settings</code> and <code>plugin_rustdesk_devices</code>.</p>
 *
 * @author buttermdm
 */
public interface RustDeskMapper {

    @Select("SELECT * FROM plugin_rustdesk_settings WHERE customerId = #{customerId}")
    RustDeskSettings findSettings(@Param("customerId") int customerId);

    @Insert("INSERT INTO plugin_rustdesk_settings (" +
            "customerId, enabled, idServer, relayServer, apiServer, publicKey, " +
            "hbbsDbPath, hbbsKeyPath, apkUrl, apkVersion, autoProvision, " +
            "autoGrantPermissions, enableInputService, allowInKiosk, autoStart, permanentPassword" +
            ") VALUES (" +
            "#{customerId}, #{enabled}, #{idServer}, #{relayServer}, #{apiServer}, #{publicKey}, " +
            "#{hbbsDbPath}, #{hbbsKeyPath}, #{apkUrl}, #{apkVersion}, #{autoProvision}, " +
            "#{autoGrantPermissions}, #{enableInputService}, #{allowInKiosk}, #{autoStart}, #{permanentPassword}" +
            ")")
    void insertSettings(RustDeskSettings settings);

    @Update("UPDATE plugin_rustdesk_settings SET " +
            "enabled = #{enabled}, " +
            "idServer = #{idServer}, " +
            "relayServer = #{relayServer}, " +
            "apiServer = #{apiServer}, " +
            "publicKey = #{publicKey}, " +
            "hbbsDbPath = #{hbbsDbPath}, " +
            "hbbsKeyPath = #{hbbsKeyPath}, " +
            "apkUrl = #{apkUrl}, " +
            "apkVersion = #{apkVersion}, " +
            "autoProvision = #{autoProvision}, " +
            "autoGrantPermissions = #{autoGrantPermissions}, " +
            "enableInputService = #{enableInputService}, " +
            "allowInKiosk = #{allowInKiosk}, " +
            "autoStart = #{autoStart}, " +
            "permanentPassword = #{permanentPassword} " +
            "WHERE customerId = #{customerId}")
    void updateSettings(RustDeskSettings settings);

    @Select("SELECT * FROM plugin_rustdesk_devices WHERE deviceId = #{deviceId}")
    RustDeskDeviceStatus findDeviceStatus(@Param("deviceId") int deviceId);

    @Select("SELECT * FROM plugin_rustdesk_devices WHERE customerId = #{customerId}")
    List<RustDeskDeviceStatus> findAllDeviceStatuses(@Param("customerId") int customerId);

    @Insert("INSERT INTO plugin_rustdesk_devices (" +
            "deviceId, customerId, installed, versionName, versionCode, rustdeskId, peerUuid, " +
            "inputServiceEnabled, overlayGranted, storageGranted, notificationsGranted, " +
            "mediaProjectionGranted, batteryOptimizationIgnored, serverConfigured, hbbsRegistered, lastReportedAt" +
            ") VALUES (" +
            "#{deviceId}, #{customerId}, #{installed}, #{versionName}, #{versionCode}, #{rustdeskId}, #{peerUuid}, " +
            "#{inputServiceEnabled}, #{overlayGranted}, #{storageGranted}, #{notificationsGranted}, " +
            "#{mediaProjectionGranted}, #{batteryOptimizationIgnored}, #{serverConfigured}, #{hbbsRegistered}, #{lastReportedAt}" +
            ") ON CONFLICT (deviceId) DO UPDATE SET " +
            "installed = EXCLUDED.installed, " +
            "versionName = COALESCE(EXCLUDED.versionName, plugin_rustdesk_devices.versionName), " +
            "versionCode = COALESCE(EXCLUDED.versionCode, plugin_rustdesk_devices.versionCode), " +
            "rustdeskId = COALESCE(EXCLUDED.rustdeskId, plugin_rustdesk_devices.rustdeskId), " +
            "peerUuid = COALESCE(EXCLUDED.peerUuid, plugin_rustdesk_devices.peerUuid), " +
            "inputServiceEnabled = EXCLUDED.inputServiceEnabled, " +
            "overlayGranted = EXCLUDED.overlayGranted, " +
            "storageGranted = EXCLUDED.storageGranted, " +
            "notificationsGranted = EXCLUDED.notificationsGranted, " +
            "mediaProjectionGranted = EXCLUDED.mediaProjectionGranted, " +
            "batteryOptimizationIgnored = EXCLUDED.batteryOptimizationIgnored, " +
            "serverConfigured = EXCLUDED.serverConfigured, " +
            "hbbsRegistered = EXCLUDED.hbbsRegistered, " +
            "lastReportedAt = EXCLUDED.lastReportedAt")
    void upsertDeviceStatus(RustDeskDeviceStatus status);
}
