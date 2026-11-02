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

package com.buttermdm.plugins.devicereset.persistence.mapper;

import com.buttermdm.plugins.devicereset.persistence.domain.DeviceResetStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * <p>An ORM mapper for {@link DeviceResetStatus} domain objects.</p>
 *
 * @author buttermdm
 */
public interface DeviceResetMapper {

    @Select("SELECT * FROM plugin_devicereset_status WHERE deviceId = #{deviceId}")
    DeviceResetStatus getStatus(@Param("deviceId") int deviceId);

    // Requests (by administrator)

    @Insert("INSERT INTO plugin_devicereset_status (deviceId, factoryReset, requestedAt, requestedBy) " +
            "VALUES (#{deviceId}, TRUE, #{ts}, #{userId}) " +
            "ON CONFLICT (deviceId) DO UPDATE SET factoryReset = TRUE, " +
            "requestedAt = EXCLUDED.requestedAt, requestedBy = EXCLUDED.requestedBy")
    void requestReset(@Param("deviceId") int deviceId, @Param("ts") long ts, @Param("userId") Integer userId);

    @Insert("INSERT INTO plugin_devicereset_status (deviceId, reboot, requestedAt, requestedBy) " +
            "VALUES (#{deviceId}, TRUE, #{ts}, #{userId}) " +
            "ON CONFLICT (deviceId) DO UPDATE SET reboot = TRUE, " +
            "requestedAt = EXCLUDED.requestedAt, requestedBy = EXCLUDED.requestedBy")
    void requestReboot(@Param("deviceId") int deviceId, @Param("ts") long ts, @Param("userId") Integer userId);

    @Insert("INSERT INTO plugin_devicereset_status (deviceId, lock, lockMessage, requestedAt, requestedBy) " +
            "VALUES (#{deviceId}, #{lock}, #{message}, #{ts}, #{userId}) " +
            "ON CONFLICT (deviceId) DO UPDATE SET lock = EXCLUDED.lock, lockMessage = EXCLUDED.lockMessage, " +
            "requestedAt = EXCLUDED.requestedAt, requestedBy = EXCLUDED.requestedBy")
    void setLock(@Param("deviceId") int deviceId, @Param("lock") boolean lock, @Param("message") String message,
                 @Param("ts") long ts, @Param("userId") Integer userId);

    @Insert("INSERT INTO plugin_devicereset_status (deviceId, passwordReset, requestedAt, requestedBy) " +
            "VALUES (#{deviceId}, #{password}, #{ts}, #{userId}) " +
            "ON CONFLICT (deviceId) DO UPDATE SET passwordReset = EXCLUDED.passwordReset, " +
            "requestedAt = EXCLUDED.requestedAt, requestedBy = EXCLUDED.requestedBy")
    void requestPasswordReset(@Param("deviceId") int deviceId, @Param("password") String password,
                              @Param("ts") long ts, @Param("userId") Integer userId);

    @Update("UPDATE plugin_devicereset_status SET factoryReset = FALSE, reboot = FALSE, passwordReset = NULL, " +
            "requestedAt = #{ts}, requestedBy = #{userId} " +
            "WHERE deviceId = #{deviceId} AND (factoryReset OR reboot OR passwordReset IS NOT NULL)")
    int cancelPending(@Param("deviceId") int deviceId, @Param("ts") long ts, @Param("userId") Integer userId);

    // Confirmations (by device). Only a pending request is cleared, so a repeated confirmation changes nothing.

    @Update("UPDATE plugin_devicereset_status SET factoryReset = FALSE, resetConfirmedAt = #{ts} " +
            "WHERE deviceId = #{deviceId} AND factoryReset")
    int confirmReset(@Param("deviceId") int deviceId, @Param("ts") long ts);

    @Update("UPDATE plugin_devicereset_status SET reboot = FALSE, rebootConfirmedAt = #{ts} " +
            "WHERE deviceId = #{deviceId} AND reboot")
    int confirmReboot(@Param("deviceId") int deviceId, @Param("ts") long ts);

    @Update("UPDATE plugin_devicereset_status SET passwordReset = NULL, passwordConfirmedAt = #{ts} " +
            "WHERE deviceId = #{deviceId} AND passwordReset IS NOT NULL")
    int confirmPasswordReset(@Param("deviceId") int deviceId, @Param("ts") long ts);

    @Update("UPDATE plugin_deviceprofile_profiles SET retiredAt = #{ts} WHERE deviceId = #{deviceId}")
    int retireProfile(@Param("deviceId") int deviceId, @Param("ts") long ts);
}
