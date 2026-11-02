/*
 *
 * ButterMDM: Open Source Android MDM Software
 * https://github.com/ButterMDM
 *
 * Copyright (C) 2019 ButterMDM (https://github.com/ButterMDM)
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

package com.buttermdm.plugins.audit.persistence.mapper;

import com.buttermdm.plugins.audit.persistence.domain.AuditLogRecord;
import com.buttermdm.plugins.audit.rest.json.AuditLogFilter;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

/**
 * <p>An ORM mapper for {@link com.buttermdm.plugins.audit.persistence.domain.AuditLogRecord} domain object.</p>
 *
 * @author isv
 */
public interface AuditMapper {

    @Insert({"INSERT INTO plugin_audit_log (" +
            "    createTime," +
            "    customerId," +
            "    userId," +
            "    login," +
            "    action," +
            "    payload," +
            "    ipAddress," +
            "    errorCode" +
            ") " +
            "VALUES (" +
            "    #{createTime}," +
            "    #{customerId}," +
            "    #{userId}," +
            "    #{login}," +
            "    #{action}," +
            "    #{payload}," +
            "    #{ipAddress}," +
            "    #{errorCode}" +
            ")"})
    int insertAuditLogRecord(AuditLogRecord logRecord);

    List<AuditLogRecord> findAllLogRecordsByCustomerId(AuditLogFilter filter);

    long countAll(AuditLogFilter filter);
}
