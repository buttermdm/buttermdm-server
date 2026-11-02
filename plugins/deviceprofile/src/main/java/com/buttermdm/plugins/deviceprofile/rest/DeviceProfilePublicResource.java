package com.buttermdm.plugins.deviceprofile.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.buttermdm.notification.PushService;
import com.buttermdm.notification.persistence.domain.PushMessage;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugins.deviceprofile.catalog.CatalogService;
import com.buttermdm.plugins.deviceprofile.rest.json.DeviceReport;
import com.buttermdm.plugins.deviceprofile.service.DeviceProfileService;
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
import javax.ws.rs.GET;
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
 * <p>A resource used by devices to send their profile and their report (capability outcomes, setup states), and to
 * download a capability catalog.</p>
 *
 * <p>Every request must be signed like the configuration request (<code>X-Request-Signature</code> =
 * SHA-1 of the shared secret followed by the device number). Failures are reported by HTTP status codes.</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/deviceprofile/public")
@Api(tags = {"Device profile plugin"})
public class DeviceProfilePublicResource {

    private static final Logger logger = LoggerFactory.getLogger(DeviceProfilePublicResource.class);

    private static final String HEADER_REQUEST_SIGNATURE = "X-Request-Signature";

    private static final String HEADER_RESPONSE_SIGNATURE = "X-Response-Signature";

    /**
     * <p>An interface to persistence without security checks.</p>
     */
    private UnsecureDAO unsecureDAO;

    /**
     * <p>An interface to device profiles.</p>
     */
    private DeviceProfileService profileService;

    /**
     * <p>An interface to capability catalogs.</p>
     */
    private CatalogService catalogService;

    /**
     * <p>An interface to notification services.</p>
     */
    private PushService pushService;

    /**
     * <p>A shared secret used for checking the request signature.</p>
     */
    private String hashSecret;

    /**
     * <p>A constructor required by swagger.</p>
     */
    public DeviceProfilePublicResource() {
    }

    /**
     * <p>Constructs new <code>DeviceProfilePublicResource</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceProfilePublicResource(UnsecureDAO unsecureDAO,
                                       DeviceProfileService profileService,
                                       CatalogService catalogService,
                                       PushService pushService,
                                       @Named("hash.secret") String hashSecret) {
        this.unsecureDAO = unsecureDAO;
        this.profileService = profileService;
        this.catalogService = catalogService;
        this.pushService = pushService;
        this.hashSecret = hashSecret;
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Send the device profile",
            notes = "Stores the Android version, vendor and probes of the device and resolves its capabilities"
    )
    @POST
    @Path("/{number}/profile")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveProfile(@PathParam("number") @ApiParam("Device number") String number,
                                JsonNode profile,
                                @Context HttpServletRequest request) {
        Device device = authenticate(number, request, "profile");
        if (device == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        try {
            DeviceProfileService.SaveResult result = profileService.saveProfile(device, profile);
            if (result.sdkChanged) {
                // The application versions may change with the Android version: send the configuration again
                PushMessage message = new PushMessage();
                message.setDeviceId(device.getId());
                message.setMessageType(PushMessage.TYPE_CONFIG_UPDATED);
                pushService.send(message);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("catalogVersion", catalogService.getActive().getCatalogVersion());
            return Response.ok(com.buttermdm.rest.json.Response.OK(data)).build();
        } catch (IllegalArgumentException e) {
            logger.warn("Rejecting the profile of device {}: {}", number, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Unexpected error when saving the profile of device {}", number, e);
            return Response.serverError().build();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Send the device report",
            notes = "Stores the outcomes of applying the capabilities and the states of the setup steps"
    )
    @POST
    @Path("/{number}/report")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveReport(@PathParam("number") @ApiParam("Device number") String number,
                               DeviceReport report,
                               @Context HttpServletRequest request) {
        Device device = authenticate(number, request, "report");
        if (device == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (report == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            profileService.saveReport(device, report);
            return Response.ok(com.buttermdm.rest.json.Response.OK()).build();
        } catch (Exception e) {
            logger.error("Unexpected error when saving the report of device {}", number, e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/{number}/results")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveResults(@PathParam("number") @ApiParam("Device number") String number,
                                DeviceReport report,
                                @Context HttpServletRequest request) {
        return saveReport(number, report, request);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get a capability catalog",
            notes = "The response is signed like the configuration (X-Response-Signature)"
    )
    @GET
    @Path("/{number}/catalog/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCatalog(@PathParam("number") @ApiParam("Device number") String number,
                               @PathParam("version") @ApiParam("Catalog version") String version,
                               @Context HttpServletRequest request) {
        if (authenticate(number, request, "catalog") == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        String content = catalogService.getContent(version);
        if (content == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(content)
                .header(HEADER_RESPONSE_SIGNATURE, CryptoUtil.getSHA1String(hashSecret + content.replaceAll("\\s", "")))
                .build();
    }

    @GET
    @Path("/catalog/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCatalogByVersion(@PathParam("version") @ApiParam("Catalog version") String version,
                                        @Context HttpServletRequest request) {
        String number = request.getHeader("X-Device-Number");
        if (number == null || number.isEmpty()) {
            number = request.getParameter("number");
        }
        if (number != null && !number.isEmpty() && authenticate(number, request, "catalog") == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        String content = catalogService.getContent(version);
        if (content == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(content)
                .header(HEADER_RESPONSE_SIGNATURE, CryptoUtil.getSHA1String(hashSecret + content.replaceAll("\\s", "")))
                .build();
    }

    /**
     * @return the device if the request signature is valid and the device exists, <code>null</code> otherwise.
     */
    private Device authenticate(String number, HttpServletRequest request, String what) {
        if (!CryptoUtil.checkRequestSignature(request.getHeader(HEADER_REQUEST_SIGNATURE), hashSecret + number)) {
            logger.warn("Rejecting the {} request of device {}: signature mismatch", what, number);
            return null;
        }
        Device device = unsecureDAO.getDeviceByNumber(number);
        if (device == null) {
            logger.warn("Rejecting the {} request: device {} was not found", what, number);
        }
        return device;
    }
}
