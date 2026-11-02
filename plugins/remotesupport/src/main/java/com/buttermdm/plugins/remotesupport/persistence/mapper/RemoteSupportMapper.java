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

package com.buttermdm.plugins.remotesupport.persistence.mapper;

import com.buttermdm.plugins.remotesupport.persistence.domain.RemoteSessionRecord;
import com.buttermdm.plugins.remotesupport.persistence.domain.RemoteSupportSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>An ORM mapper for the domain objects of <code>Remote Support</code> plugin.</p>
 *
 * @author buttermdm
 */
public interface RemoteSupportMapper {

    @Select("SELECT c.id AS configurationId, c.name AS configurationName, " +
            "COALESCE(s.enabled, FALSE) AS enabled, COALESCE(s.requireAcceptance, TRUE) AS requireAcceptance, " +
            "COALESCE(s.maxMinutes, 60) AS maxMinutes, COALESCE(s.transport, 'auto') AS transport, " +
            "s.stunUrl AS stunUrl, s.turnUrl AS turnUrl, s.turnUsername AS turnUsername, s.turnCredential AS turnCredential " +
            "FROM configurations c LEFT JOIN plugin_remotesupport_settings s ON s.configurationId = c.id " +
            "WHERE c.customerId = #{customerId} ORDER BY c.name")
    List<RemoteSupportSettings> getCustomerSettings(@Param("customerId") int customerId);

    @Select("SELECT * FROM plugin_remotesupport_settings WHERE configurationId = #{configurationId}")
    RemoteSupportSettings getSettings(@Param("configurationId") int configurationId);

    @Insert("INSERT INTO plugin_remotesupport_settings " +
            "(configurationId, enabled, requireAcceptance, maxMinutes, transport, stunUrl, turnUrl, turnUsername, turnCredential) " +
            "VALUES (#{configurationId}, #{enabled}, #{requireAcceptance}, #{maxMinutes}, " +
            "COALESCE(#{transport}, 'auto'), #{stunUrl}, #{turnUrl}, #{turnUsername}, #{turnCredential}) " +
            "ON CONFLICT (configurationId) DO UPDATE SET enabled = EXCLUDED.enabled, " +
            "requireAcceptance = EXCLUDED.requireAcceptance, maxMinutes = EXCLUDED.maxMinutes, " +
            "transport = EXCLUDED.transport, stunUrl = EXCLUDED.stunUrl, turnUrl = EXCLUDED.turnUrl, " +
            "turnUsername = EXCLUDED.turnUsername, turnCredential = EXCLUDED.turnCredential")
    void saveSettings(RemoteSupportSettings settings);

    @Insert("INSERT INTO plugin_remotesupport_sessions (id, deviceId, customerId, userId, userLogin, viewOnly, createdAt) " +
            "VALUES (#{id}, #{deviceId}, #{customerId}, #{userId}, #{userLogin}, #{viewOnly}, #{createdAt})")
    void insertSession(RemoteSessionRecord session);

    @Update("UPDATE plugin_remotesupport_sessions SET connectedAt = #{ts} WHERE id = #{id} AND connectedAt IS NULL")
    void setConnected(@Param("id") String id, @Param("ts") long ts);

    @Update("UPDATE plugin_remotesupport_sessions SET endedAt = #{ts}, endReason = #{reason}, frames = #{frames}, " +
            "bytes = #{bytes} WHERE id = #{id} AND endedAt IS NULL")
    void setEnded(@Param("id") String id, @Param("ts") long ts, @Param("reason") String reason,
                  @Param("frames") long frames, @Param("bytes") long bytes);

    @Update("UPDATE plugin_remotesupport_sessions SET endedAt = #{ts}, endReason = 'server restarted' " +
            "WHERE endedAt IS NULL")
    int endAbandonedSessions(@Param("ts") long ts);

    @Select("SELECT s.*, d.number AS deviceNumber FROM plugin_remotesupport_sessions s " +
            "INNER JOIN devices d ON d.id = s.deviceId WHERE s.customerId = #{customerId} " +
            "ORDER BY s.createdAt DESC LIMIT #{limit}")
    List<RemoteSessionRecord> getRecentSessions(@Param("customerId") int customerId, @Param("limit") int limit);
}
