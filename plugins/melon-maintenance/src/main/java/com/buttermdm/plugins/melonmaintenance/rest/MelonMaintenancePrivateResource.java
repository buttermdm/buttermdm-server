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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.buttermdm.notification.PushService;
import com.buttermdm.notification.persistence.domain.PushMessage;
import com.buttermdm.persistence.DeviceDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.plugins.melonmaintenance.persistence.MelonMaintenanceDAO;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceDeviceStatus;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceForceAudit;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceSettings;
import com.buttermdm.plugins.melonmaintenance.rest.json.MelonMaintenanceForceInstallRequest;
import com.buttermdm.rest.json.Response;
import com.buttermdm.security.SecurityContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Administrator REST resource for the <code>Melon Maintenance Gate</code> plugin.</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/melon-maintenance/private")
@Api(tags = {"Melon Maintenance plugin"}, authorizations = {@Authorization("Bearer")})
public class MelonMaintenancePrivateResource {

    private static final Logger logger = LoggerFactory.getLogger(MelonMaintenancePrivateResource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static final String PERMISSION_ACCESS = "plugin_melon_maintenance_access";
    public static final String PERMISSION_SETTINGS = "plugin_melon_maintenance_settings";
    public static final String PERMISSION_FORCE_INSTALL = "plugin_melon_maintenance_force_install";

    public static final String PUSH_TYPE_FORCE_INSTALL = "melonForceInstall";

    private MelonMaintenanceDAO melonMaintenanceDAO;
    private DeviceDAO deviceDAO;
    private PushService pushService;

    public MelonMaintenancePrivateResource() {
    }

    @Inject
    public MelonMaintenancePrivateResource(MelonMaintenanceDAO melonMaintenanceDAO,
                                           DeviceDAO deviceDAO,
                                           PushService pushService) {
        this.melonMaintenanceDAO = melonMaintenanceDAO;
        this.deviceDAO = deviceDAO;
        this.pushService = pushService;
    }

    @GET
    @Path("/settings")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get Melon maintenance gate plugin settings")
    public Response getSettings() {
        if (!SecurityContext.get().hasPermission(PERMISSION_ACCESS)
                && !SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            return Response.OK(this.melonMaintenanceDAO.getSettings());
        } catch (Exception e) {
            logger.error("Failed to load Melon maintenance settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @PUT
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Save Melon maintenance gate plugin settings")
    public Response saveSettings(MelonMaintenanceSettings settings) {
        if (!SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return Response.PERMISSION_DENIED();
        }
        if (settings == null) {
            return Response.ERROR("plugin.melon.maintenance.error.invalid", "Settings payload is required");
        }
        settings.normalize();
        if (!MelonMaintenanceSettings.isAllowedGatePackage(settings.getGatePackage())) {
            return Response.ERROR("plugin.melon.maintenance.error.invalid.package",
                    "Unsupported gatePackage: " + settings.getGatePackage());
        }
        if (settings.isGateEnabled() && !MelonMaintenanceSettings.isValidSha256Hex(settings.getGateSignerSha256())) {
            return Response.ERROR("plugin.melon.maintenance.error.invalid.signer",
                    "gateSignerSha256 must be a 64-character hex SHA-256 digest when gateEnabled is true");
        }
        try {
            MelonMaintenanceSettings saved = this.melonMaintenanceDAO.saveSettings(settings);
            return Response.OK(saved);
        } catch (Exception e) {
            logger.error("Failed to save Melon maintenance settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @GET
    @Path("/devices")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List device maintenance gate statuses and 3-night deferral alerts")
    public Response getDevices() {
        if (!SecurityContext.get().hasPermission(PERMISSION_ACCESS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            User user = SecurityContext.get().getCurrentUser().get();
            List<MelonMaintenanceDeviceStatus> statuses = this.melonMaintenanceDAO.getAllDeviceStatuses(user.getCustomerId());
            return Response.OK(statuses);
        } catch (Exception e) {
            logger.error("Failed to list Melon maintenance device statuses", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @POST
    @Path("/devices/{deviceId}/force-install")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Issue an audited technician break-glass force-install with a mandatory ticket reference")
    public Response forceInstall(@PathParam("deviceId") int deviceId,
                                 MelonMaintenanceForceInstallRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_FORCE_INSTALL)) {
            return Response.PERMISSION_DENIED();
        }
        if (request == null || !request.isValid()) {
            return Response.ERROR("plugin.melon.maintenance.error.ticket.required",
                    "Both ticketRef and reason are required for a break-glass force-install");
        }
        try {
            Device device = this.deviceDAO.getDeviceById(deviceId);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            User user = SecurityContext.get().getCurrentUser().get();
            MelonMaintenanceSettings settings = this.melonMaintenanceDAO.getSettingsByCustomerId(user.getCustomerId());
            String pkg = request.getPackageName() != null && !request.getPackageName().trim().isEmpty()
                    ? request.getPackageName().trim()
                    : settings.getGatePackage();

            MelonMaintenanceForceAudit audit = new MelonMaintenanceForceAudit();
            audit.setDeviceId(device.getId());
            audit.setCustomerId(user.getCustomerId());
            audit.setUserId(user.getId());
            audit.setUsername(user.getLogin());
            audit.setTicketRef(request.getTicketRef().trim());
            audit.setPackageName(pkg);
            audit.setReason(request.getReason().trim());
            audit.setCreatedAt(System.currentTimeMillis());
            this.melonMaintenanceDAO.recordForceAudit(audit);

            logger.warn("AUDIT [melon-maintenance]: user={} issued force-install on device={} (number={}) pkg={} ticketRef={} reason={}",
                    user.getLogin(), device.getId(), device.getNumber(), pkg, audit.getTicketRef(), audit.getReason());

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("forceInstall", true);
            payload.put("ticketRef", audit.getTicketRef());
            payload.put("packageName", pkg);
            payload.put("reason", audit.getReason());
            payload.put("authorizedBy", user.getLogin());

            PushMessage message = new PushMessage();
            message.setDeviceId(device.getId());
            message.setMessageType(PUSH_TYPE_FORCE_INSTALL);
            message.setPayload(payload.toString());
            this.pushService.send(message);

            Map<String, Object> res = new HashMap<>();
            res.put("deviceId", device.getId());
            res.put("deviceNumber", device.getNumber());
            res.put("ticketRef", audit.getTicketRef());
            res.put("packageName", pkg);
            res.put("messageType", PUSH_TYPE_FORCE_INSTALL);
            return Response.OK(res);
        } catch (Exception e) {
            logger.error("Failed to execute force-install on device #{}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
