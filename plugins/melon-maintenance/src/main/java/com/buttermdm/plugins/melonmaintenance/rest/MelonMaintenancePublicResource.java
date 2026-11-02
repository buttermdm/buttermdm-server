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

package com.buttermdm.plugins.melonmaintenance.rest;

import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugins.melonmaintenance.persistence.MelonMaintenanceDAO;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceDeviceStatus;
import com.buttermdm.plugins.melonmaintenance.rest.json.MelonMaintenanceDeviceReport;
import com.buttermdm.util.CryptoUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Public device-facing REST resource for the <code>Melon Maintenance Gate</code> plugin.
 * Requests are authenticated with <code>X-Request-Signature</code> (SHA-1 of <code>hash.secret + deviceNumber</code>).</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/melon-maintenance/public")
@Api(tags = {"Melon Maintenance plugin (device)"})
public class MelonMaintenancePublicResource {

    private static final Logger logger = LoggerFactory.getLogger(MelonMaintenancePublicResource.class);
    public static final String HEADER_REQUEST_SIGNATURE = "X-Request-Signature";

    private UnsecureDAO unsecureDAO;
    private MelonMaintenanceDAO melonMaintenanceDAO;
    private String hashSecret;

    public MelonMaintenancePublicResource() {
    }

    @Inject
    public MelonMaintenancePublicResource(UnsecureDAO unsecureDAO,
                                          MelonMaintenanceDAO melonMaintenanceDAO,
                                          @Named("hash.secret") String hashSecret) {
        this.unsecureDAO = unsecureDAO;
        this.melonMaintenanceDAO = melonMaintenanceDAO;
        this.hashSecret = hashSecret;
    }

    @POST
    @Path("/report/{number}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Submit latest Melon maintenance gate decision, lease outcome, and deferral count from a managed device")
    public Response reportDeviceStatus(@PathParam("number") @ApiParam("Device number") String number,
                                       MelonMaintenanceDeviceReport report,
                                       @Context HttpServletRequest request) {
        Device device = authenticate(number, request);
        if (device == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (report == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            MelonMaintenanceDeviceStatus status = new MelonMaintenanceDeviceStatus();
            status.setDeviceId(device.getId());
            status.setCustomerId(device.getCustomerId());
            status.setLastDecision(report.getLastDecision() != null ? report.getLastDecision() : "UNKNOWN");
            status.setLastReason(report.getLastReason() != null ? report.getLastReason() : "NONE");
            status.setLastLeaseId(report.getLastLeaseId());
            status.setLastOutcome(report.getLastOutcome());
            status.setDeferralCount(report.getDeferralCount());
            status.setNextRetryAt(report.getNextRetryAt());
            status.setLastReportedAt(System.currentTimeMillis());

            this.melonMaintenanceDAO.saveDeviceStatus(status);

            Map<String, Object> data = new HashMap<>();
            data.put("deviceId", device.getId());
            data.put("lastDecision", status.getLastDecision());
            data.put("lastReason", status.getLastReason());
            data.put("deferralCount", status.getDeferralCount());
            data.put("threeNightAlertActive", status.isThreeNightAlertActive());
            return Response.ok(com.buttermdm.rest.json.Response.OK(data)).build();
        } catch (Exception e) {
            logger.error("Failed to save Melon maintenance status report from device {}", number, e);
            return Response.serverError().build();
        }
    }

    private Device authenticate(String number, HttpServletRequest request) {
        String signature = request != null ? request.getHeader(HEADER_REQUEST_SIGNATURE) : null;
        if (!CryptoUtil.checkRequestSignature(signature, hashSecret + number)) {
            logger.warn("Rejecting Melon maintenance public report for device {}: invalid signature", number);
            return null;
        }
        return unsecureDAO.getDeviceByNumber(number);
    }
}
