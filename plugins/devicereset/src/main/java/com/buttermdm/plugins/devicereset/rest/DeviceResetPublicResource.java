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

import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugins.devicereset.persistence.DeviceResetDAO;
import com.buttermdm.rest.json.DeviceInfo;
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
import java.util.function.Predicate;

/**
 * <p>A resource used by devices to confirm the factory reset, reboot and password reset requests.</p>
 *
 * <p>The launcher performs the action only if the confirmation succeeds (any HTTP 2xx status), so the request is
 * cleared here, before the action. Every failure MUST be reported by a non-2xx HTTP status: the standard
 * {@link com.buttermdm.rest.json.Response#ERROR()} is sent with HTTP 200 and would be treated as a success, which would
 * make the device reboot (or wipe) while the request is still pending.</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/devicereset/public")
@Api(tags = {"Device reset plugin"})
public class DeviceResetPublicResource {

    private static final Logger logger = LoggerFactory.getLogger(DeviceResetPublicResource.class);

    private static final String HEADER_REQUEST_SIGNATURE = "X-Request-Signature";

    /**
     * <p>An interface to persistence without security checks.</p>
     */
    private UnsecureDAO unsecureDAO;

    /**
     * <p>An interface to device reset records persistence.</p>
     */
    private DeviceResetDAO deviceResetDAO;

    /**
     * <p>An interface to the audit log.</p>
     */
    private DeviceResetAuditLog auditLog;

    /**
     * <p>A shared secret used for checking the request signature.</p>
     */
    private String hashSecret;

    /**
     * <p>A flag allowing the confirmations without the request signature.</p>
     */
    private boolean allowUnsigned;

    /**
     * <p>A constructor required by swagger.</p>
     */
    public DeviceResetPublicResource() {
    }

    /**
     * <p>Constructs new <code>DeviceResetPublicResource</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceResetPublicResource(UnsecureDAO unsecureDAO,
                                     DeviceResetDAO deviceResetDAO,
                                     DeviceResetAuditLog auditLog,
                                     @Named("hash.secret") String hashSecret,
                                     @Named("plugin.devicereset.allow.unsigned") boolean allowUnsigned) {
        this.unsecureDAO = unsecureDAO;
        this.deviceResetDAO = deviceResetDAO;
        this.auditLog = auditLog;
        this.hashSecret = hashSecret;
        this.allowUnsigned = allowUnsigned;
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Confirm factory reset",
            notes = "Called by the device before the factory reset. Clears the factory reset request."
    )
    @POST
    @Path("/{number}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmReset(@PathParam("number") @ApiParam("Device number") String number,
                                 DeviceInfo deviceInfo,
                                 @Context HttpServletRequest request) {
        return confirm(number, request, "factory reset",
                device -> this.deviceResetDAO.confirmReset(device.getId()),
                "plugin.audit.action.device.reset.confirmed");
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Confirm reboot",
            notes = "Called by the device before the reboot. Clears the reboot request."
    )
    @POST
    @Path("/reboot/{number}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmReboot(@PathParam("number") @ApiParam("Device number") String number,
                                  DeviceInfo deviceInfo,
                                  @Context HttpServletRequest request) {
        return confirm(number, request, "reboot",
                device -> this.deviceResetDAO.confirmReboot(device.getId()),
                "plugin.audit.action.device.reboot.confirmed");
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Confirm password reset",
            notes = "Called by the device after processing the password reset. Removes the stored password."
    )
    @POST
    @Path("/password/{number}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmPasswordReset(@PathParam("number") @ApiParam("Device number") String number,
                                         DeviceInfo deviceInfo,
                                         @Context HttpServletRequest request) {
        return confirm(number, request, "password reset",
                device -> this.deviceResetDAO.confirmPasswordReset(device.getId()),
                "plugin.audit.action.password.reset.confirmed");
    }

    private Response confirm(String number, HttpServletRequest request, String what,
                             Predicate<Device> confirmation, String auditAction) {
        if (!allowUnsigned &&
                !CryptoUtil.checkRequestSignature(request.getHeader(HEADER_REQUEST_SIGNATURE), hashSecret + number)) {
            logger.warn("Rejecting {} confirmation from device {}: signature mismatch", what, number);
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        try {
            Device dbDevice = this.unsecureDAO.getDeviceByNumber(number);
            if (dbDevice == null) {
                logger.warn("Rejecting {} confirmation: device {} was not found", what, number);
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            if (confirmation.test(dbDevice)) {
                logger.info("Device {} confirmed {}", number, what);
                this.auditLog.log(auditAction, dbDevice, null, null, request);
            } else {
                // A repeated confirmation: nothing was pending, still a success
                logger.info("Device {} confirmed {}, but nothing was pending", number, what);
            }
            return Response.ok(com.buttermdm.rest.json.Response.OK()).build();
        } catch (Exception e) {
            logger.error("Unexpected error when processing {} confirmation from device {}", what, number, e);
            return Response.serverError().build();
        }
    }
}
