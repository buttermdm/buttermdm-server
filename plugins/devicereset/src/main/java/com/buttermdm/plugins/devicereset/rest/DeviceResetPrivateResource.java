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

package com.buttermdm.plugins.devicereset.rest;

import com.buttermdm.notification.PushService;
import com.buttermdm.persistence.DeviceDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.plugins.devicereset.persistence.DeviceResetDAO;
import com.buttermdm.plugins.devicereset.persistence.domain.DeviceResetStatus;
import com.buttermdm.plugins.devicereset.rest.json.DeviceLockRequest;
import com.buttermdm.plugins.devicereset.rest.json.DevicePasswordRequest;
import com.buttermdm.rest.json.Response;
import com.buttermdm.security.SecurityContext;
import com.buttermdm.security.SecurityException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * <p>A resource used by administrators to request the factory reset, reboot, lock and password reset of a device.</p>
 *
 * <p>Each request is stored and delivered to the device with the next configuration update which is triggered
 * immediately by a Push message.</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/devicereset/private")
@Api(tags = {"Device reset plugin"})
public class DeviceResetPrivateResource {

    private static final Logger logger = LoggerFactory.getLogger(DeviceResetPrivateResource.class);

    private static final String PERMISSION_RESET = "plugin_devicereset_reset";
    private static final String PERMISSION_LOCK = "plugin_devicereset_lock";
    private static final String PERMISSION_REBOOT = "plugin_devicereset_reboot";
    private static final String PERMISSION_PASSWORD = "plugin_devicereset_password";

    /**
     * <p>An interface to device reset records persistence.</p>
     */
    private DeviceResetDAO deviceResetDAO;

    /**
     * <p>An interface to device records persistence.</p>
     */
    private DeviceDAO deviceDAO;

    /**
     * <p>An interface to notification services.</p>
     */
    private PushService pushService;

    /**
     * <p>An interface to the audit log.</p>
     */
    private DeviceResetAuditLog auditLog;

    /**
     * <p>A constructor required by swagger.</p>
     */
    public DeviceResetPrivateResource() {
    }

    /**
     * <p>Constructs new <code>DeviceResetPrivateResource</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceResetPrivateResource(DeviceResetDAO deviceResetDAO,
                                      DeviceDAO deviceDAO,
                                      PushService pushService,
                                      DeviceResetAuditLog auditLog) {
        this.deviceResetDAO = deviceResetDAO;
        this.deviceDAO = deviceDAO;
        this.pushService = pushService;
        this.auditLog = auditLog;
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get device reset status",
            notes = "Gets the pending requests and the confirmation timestamps for a device",
            response = DeviceResetStatus.class,
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/{deviceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId) {
        if (!hasAnyPermission()) {
            logger.error("Unauthorized attempt to get the reset status of device #{} by user {}",
                    deviceId, SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            Device dbDevice = this.deviceDAO.getDeviceById(deviceId);
            if (dbDevice == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            DeviceResetStatus status = this.deviceResetDAO.getStatus(dbDevice.getId());
            if (status == null) {
                status = new DeviceResetStatus();
                status.setDeviceId(dbDevice.getId());
            }
            return Response.OK(status);
        } catch (SecurityException e) {
            logger.error("Prohibited to get the reset status of device #{} by user {}",
                    deviceId, SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        } catch (Exception e) {
            logger.error("Unexpected error when getting the reset status of device #{}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Factory reset",
            notes = "Requests the factory reset of a device. The device erases all data once it gets the request.",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/{deviceId}/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reset(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId,
                          @Context HttpServletRequest request) {
        return execute(deviceId, PERMISSION_RESET, "factory reset", request,
                (device, user) -> this.deviceResetDAO.requestReset(device.getId(), user.getId()),
                "plugin.audit.action.device.reset", null);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Reboot",
            notes = "Requests the reboot of a device. The device confirms the request before rebooting.",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/{deviceId}/reboot")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reboot(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId,
                           @Context HttpServletRequest request) {
        return execute(deviceId, PERMISSION_REBOOT, "reboot", request,
                (device, user) -> this.deviceResetDAO.requestReboot(device.getId(), user.getId()),
                "plugin.audit.action.device.reboot", null);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Lock",
            notes = "Locks a device: a full-screen lock with an optional message is displayed until the device is unlocked.",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/{deviceId}/lock")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response lock(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId,
                         DeviceLockRequest lockRequest,
                         @Context HttpServletRequest request) {
        String message = lockRequest != null ? lockRequest.getMessage() : null;
        return execute(deviceId, PERMISSION_LOCK, "lock", request,
                (device, user) -> this.deviceResetDAO.lock(device.getId(), message, user.getId()),
                "plugin.audit.action.device.lock", message != null ? "Message: " + message : null);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Unlock",
            notes = "Unlocks a device locked by the lock request.",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/{deviceId}/unlock")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unlock(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId,
                           @Context HttpServletRequest request) {
        return execute(deviceId, PERMISSION_LOCK, "unlock", request,
                (device, user) -> this.deviceResetDAO.unlock(device.getId(), user.getId()),
                "plugin.audit.action.device.unlock", null);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Reset password",
            notes = "Sets a new screen lock password on a device. The password is stored until the device confirms it.",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/{deviceId}/password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetPassword(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId,
                                  DevicePasswordRequest passwordRequest,
                                  @Context HttpServletRequest request) {
        if (passwordRequest == null || passwordRequest.getPassword() == null) {
            return Response.ERROR("Empty password while trying to reset the device password!");
        }
        // Never log or audit the password itself
        return execute(deviceId, PERMISSION_PASSWORD, "password reset", request,
                (device, user) -> this.deviceResetDAO.requestPasswordReset(device.getId(),
                        passwordRequest.getPassword(), user.getId()),
                "plugin.audit.action.password.reset", null);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Cancel pending requests",
            notes = "Cancels the factory reset, reboot and password reset requests not yet confirmed by the device. " +
                    "Doesn't unlock the device.",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/{deviceId}/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancel(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId,
                           @Context HttpServletRequest request) {
        if (!hasAnyPermission()) {
            logger.error("Unauthorized attempt to cancel the pending requests of device #{} by user {}",
                    deviceId, SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        return execute(deviceId, null, "cancel", request,
                (device, user) -> this.deviceResetDAO.cancelPending(device.getId(), user.getId()),
                "plugin.audit.action.device.reset.cancel", null);
    }

    private interface Action {
        void apply(Device device, User user);
    }

    /**
     * <p>Stores the request for the device and notifies the device.</p>
     *
     * @param deviceId an ID of a device.
     * @param permission a permission required for the action or <code>null</code> if already checked by the caller.
     * @param what a name of the action for the log.
     * @param request a request being processed.
     * @param action an action storing the request.
     * @param auditAction a key of the audit log action.
     * @param auditDetails optional details for the audit log (never a password).
     * @return a response to be sent to the client.
     */
    private Response execute(Integer deviceId, String permission, String what, HttpServletRequest request,
                             Action action, String auditAction, String auditDetails) {
        if (permission != null && !SecurityContext.get().hasPermission(permission)) {
            logger.error("Unauthorized attempt to request {} of device #{} by user {}",
                    what, deviceId, SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            // Returns null if the device doesn't exist and throws SecurityException if it belongs to another customer
            Device dbDevice = this.deviceDAO.getDeviceById(deviceId);
            if (dbDevice == null) {
                logger.error("Attempt to request {} of a wrong device #{}", what, deviceId);
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            User user = SecurityContext.get().getCurrentUser().get();

            action.apply(dbDevice, user);
            logger.info("User {} requested {} of device {}", user.getLogin(), what, dbDevice.getNumber());
            this.auditLog.log(auditAction, dbDevice, user, auditDetails, request);

            // Make the device get the configuration now
            this.pushService.sendSimpleMessage(dbDevice.getId(), "configUpdated");

            return Response.OK();
        } catch (SecurityException e) {
            logger.error("Prohibited to request {} of device #{} by user {}",
                    what, deviceId, SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        } catch (Exception e) {
            logger.error("Unexpected error when requesting {} of device #{}", what, deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    private static boolean hasAnyPermission() {
        return SecurityContext.get().hasPermission(PERMISSION_RESET) ||
                SecurityContext.get().hasPermission(PERMISSION_LOCK) ||
                SecurityContext.get().hasPermission(PERMISSION_REBOOT) ||
                SecurityContext.get().hasPermission(PERMISSION_PASSWORD);
    }
}
