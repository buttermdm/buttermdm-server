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

package com.buttermdm.plugins.deviceprofile.rest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.plugins.audit.persistence.AuditDAO;
import com.buttermdm.plugins.audit.persistence.domain.AuditLogRecord;
import com.buttermdm.rest.filter.BaseIPFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>A service writing the requests and confirmations of the plugin to the audit log (see <code>Audit</code> plugin).</p>
 *
 * @author buttermdm
 */
@Singleton
public class DeviceProfileAuditLog {

    private static final Logger logger = LoggerFactory.getLogger(DeviceProfileAuditLog.class);

    private final AuditDAO auditDAO;

    private final BaseIPFilter remoteAddrResolver;

    /**
     * <p>Constructs new <code>DeviceProfileAuditLog</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceProfileAuditLog(AuditDAO auditDAO,
                               @Named("proxy.addresses") String proxyIps,
                               @Named("proxy.ip.header") String ipHeader) {
        this.auditDAO = auditDAO;
        this.remoteAddrResolver = new BaseIPFilter("", proxyIps, ipHeader);
    }

    /**
     * <p>Adds a record to the audit log. Failures are logged and ignored so they never block the action itself.</p>
     *
     * @param action a key of the action description in the localization resources.
     * @param device a device the action is applied to, or <code>null</code> for an action without a device.
     * @param user a user who requested the action.
     * @param details optional details.
     * @param request a request being processed.
     */
    public void log(String action, Device device, User user, String details, HttpServletRequest request) {
        try {
            AuditLogRecord logRecord = new AuditLogRecord();
            logRecord.setCreateTime(System.currentTimeMillis());
            logRecord.setIpAddress(remoteAddrResolver.getRemoteAddr(request));
            logRecord.setCustomerId(device != null ? device.getCustomerId() : user.getCustomerId());
            if (user != null) {
                logRecord.setUserId(user.getId());
                logRecord.setLogin(user.getLogin());
            }
            String payload = "Method: " + request.getMethod() + "\n" +
                    "URI: " + request.getRequestURI();
            if (device != null) {
                payload += "\nDevice: " + device.getNumber();
            }
            if (details != null) {
                payload += "\n" + details;
            }
            logRecord.setPayload(payload);
            logRecord.setAction(action);
            logRecord.setErrorCode(0);
            this.auditDAO.insertAuditLogRecord(logRecord);
        } catch (Exception e) {
            logger.error("Failed to write the audit log record {}", action, e);
        }
    }
}
