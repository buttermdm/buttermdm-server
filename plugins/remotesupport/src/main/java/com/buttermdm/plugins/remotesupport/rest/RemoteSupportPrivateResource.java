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

package com.buttermdm.plugins.remotesupport.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.buttermdm.notification.PushService;
import com.buttermdm.notification.persistence.domain.PushMessage;
import com.buttermdm.persistence.ConfigurationDAO;
import com.buttermdm.persistence.DeviceDAO;
import com.buttermdm.persistence.domain.Configuration;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.plugins.remotesupport.persistence.domain.RemoteSupportSettings;
import com.buttermdm.plugins.remotesupport.persistence.mapper.RemoteSupportMapper;
import com.buttermdm.plugins.remotesupport.rest.json.SessionRequest;
import com.buttermdm.plugins.remotesupport.session.RemoteSession;
import com.buttermdm.plugins.remotesupport.session.RemoteSessionManager;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>A resource used by technicians to start and end remote support sessions, and by administrators to enable
 * remote support for configurations.</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/remotesupport/private")
@Api(tags = {"Remote support plugin"})
public class RemoteSupportPrivateResource {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSupportPrivateResource.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PERMISSION_VIEW = "plugin_remotesupport_view";
    private static final String PERMISSION_CONTROL = "plugin_remotesupport_control";
    private static final String PERMISSION_SETTINGS = "plugin_remotesupport_settings";

    public static final String PUSH_TYPE = "remoteSession";
    public static final String CLIENT_PATH = "/ws/remotesupport/client";
    public static final String DEVICE_PATH = "/ws/remotesupport/device";

    private static final int MAX_MINUTES_LIMIT = 480;
    private static final int RECENT_LIMIT = 50;

    private RemoteSupportMapper mapper;
    private RemoteSessionManager sessionManager;
    private DeviceDAO deviceDAO;
    private ConfigurationDAO configurationDAO;
    private PushService pushService;
    private RemoteSupportAuditLog auditLog;

    /**
     * <p>A constructor required by swagger.</p>
     */
    public RemoteSupportPrivateResource() {
    }

    /**
     * <p>Constructs new <code>RemoteSupportPrivateResource</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public RemoteSupportPrivateResource(RemoteSupportMapper mapper,
                                        RemoteSessionManager sessionManager,
                                        DeviceDAO deviceDAO,
                                        ConfigurationDAO configurationDAO,
                                        PushService pushService,
                                        RemoteSupportAuditLog auditLog) {
        this.mapper = mapper;
        this.sessionManager = sessionManager;
        this.deviceDAO = deviceDAO;
        this.configurationDAO = configurationDAO;
        this.pushService = pushService;
        this.auditLog = auditLog;
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Start a session",
            notes = "Creates a remote support session and asks the device to connect. The technician connects to the " +
                    "WebSocket path with the returned session ID and token",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/sessions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startSession(SessionRequest sessionRequest, @Context HttpServletRequest request) {
        if (sessionRequest == null || sessionRequest.getDeviceId() == null) {
            return Response.ERROR("error.notfound.device");
        }
        String permission = sessionRequest.isViewOnly() ? PERMISSION_VIEW : PERMISSION_CONTROL;
        if (!SecurityContext.get().hasPermission(permission)) {
            return denied("start a remote support session on device #" + sessionRequest.getDeviceId());
        }
        try {
            // Returns null if the device doesn't exist and throws SecurityException if it belongs to another customer
            Device device = deviceDAO.getDeviceById(sessionRequest.getDeviceId());
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            RemoteSupportSettings settings = device.getConfigurationId() != null ?
                    mapper.getSettings(device.getConfigurationId()) : null;
            if (settings == null || !settings.isEnabled()) {
                return Response.ERROR("plugin.remotesupport.error.disabled");
            }
            User user = SecurityContext.get().getCurrentUser().get();
            RemoteSession session = sessionManager.create(device, user, sessionRequest.isViewOnly(), settings);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("sessionId", session.getId());
            payload.put("token", sessionManager.getDeviceToken(session));
            payload.put("path", DEVICE_PATH);
            payload.put("viewOnly", session.isViewOnly());
            payload.put("requireAcceptance", settings.isRequireAcceptance());
            payload.put("maxMinutes", settings.getMaxMinutes());
            payload.put("technician", user.getLogin());
            payload.put("transport", session.getTransport());
            payload.set("iceServers", objectMapper.valueToTree(session.getIceServers()));
            PushMessage message = new PushMessage();
            message.setDeviceId(device.getId());
            message.setMessageType(PUSH_TYPE);
            message.setPayload(payload.toString());
            pushService.send(message);

            auditLog.log("plugin.audit.action.remotesupport.session.start", device, user,
                    "Session: " + session.getId() + (session.isViewOnly() ? " (view only)" : ""), request);
            logger.info("User {} started remote support session {} for device {}", user.getLogin(), session.getId(),
                    device.getNumber());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", session.getId());
            result.put("token", sessionManager.getClientToken(session));
            result.put("path", CLIENT_PATH);
            result.put("deviceNumber", device.getNumber());
            result.put("viewOnly", session.isViewOnly());
            result.put("requireAcceptance", settings.isRequireAcceptance());
            result.put("transport", session.getTransport());
            result.put("iceServers", session.getIceServers());
            return Response.OK(result);
        } catch (SecurityException e) {
            return denied("start a remote support session on device #" + sessionRequest.getDeviceId());
        } catch (Exception e) {
            logger.error("Unexpected error when starting a remote support session on device #{}",
                    sessionRequest.getDeviceId(), e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the sessions",
            notes = "Gets the sessions in progress and the recent sessions of the customer",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSessions() {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get the remote support sessions");
        }
        int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
        List<Map<String, Object>> active = new ArrayList<>();
        for (RemoteSession session : sessionManager.getActive(customerId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", session.getId());
            item.put("deviceNumber", session.getDeviceNumber());
            item.put("userLogin", session.getUserLogin());
            item.put("viewOnly", session.isViewOnly());
            item.put("createdAt", session.getCreatedAt());
            item.put("connectedAt", session.getConnectedAt() > 0 ? session.getConnectedAt() : null);
            item.put("deviceConnected", session.isDeviceConnected());
            item.put("clientConnected", session.isClientConnected());
            active.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", active);
        result.put("recent", mapper.getRecentSessions(customerId, RECENT_LIMIT));
        return Response.OK(result);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "End a session",
            notes = "Ends a session in progress",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/sessions/{id}/end")
    @Produces(MediaType.APPLICATION_JSON)
    public Response endSession(@PathParam("id") @ApiParam("Session ID") String id) {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("end remote support session " + id);
        }
        RemoteSession session = sessionManager.find(id);
        int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
        if (session == null || session.getCustomerId() != customerId) {
            return Response.ERROR("plugin.remotesupport.error.session.notfound");
        }
        sessionManager.end(session, "ended by " + SecurityContext.get().getCurrentUserName());
        return Response.OK();
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the settings",
            notes = "Gets the remote support settings of all configurations of the customer",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/settings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSettings() {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get the remote support settings");
        }
        int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
        return Response.OK(mapper.getCustomerSettings(customerId));
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Save the settings of a configuration",
            notes = "Enables or disables remote support for a configuration; the devices are notified",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/settings/{configurationId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveSettings(@PathParam("configurationId") @ApiParam("Configuration ID") Integer configurationId,
                                 RemoteSupportSettings settings,
                                 @Context HttpServletRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return denied("change the remote support settings of configuration #" + configurationId);
        }
        try {
            Configuration configuration = configurationDAO.getConfigurationById(configurationId);
            if (configuration == null || settings == null) {
                return Response.ERROR("plugin.remotesupport.error.configuration.notfound");
            }
            if (settings.getMaxMinutes() < 1 || settings.getMaxMinutes() > MAX_MINUTES_LIMIT) {
                return Response.ERROR("plugin.remotesupport.error.maxMinutes");
            }
            settings.setConfigurationId(configuration.getId());
            mapper.saveSettings(settings);
            auditLog.log("plugin.audit.action.remotesupport.settings", null, SecurityContext.get().getCurrentUser().get(),
                    "Configuration: " + configuration.getName() + ", enabled: " + settings.isEnabled() +
                            ", acceptance: " + settings.isRequireAcceptance() + ", max minutes: " +
                            settings.getMaxMinutes(), request);
            // The devices learn the setting from their configuration (and enable the accessibility service)
            pushService.notifyDevicesOnUpdate(configuration.getId());
            return Response.OK();
        } catch (SecurityException e) {
            return denied("change the remote support settings of configuration #" + configurationId);
        } catch (Exception e) {
            logger.error("Unexpected error when saving the remote support settings of configuration #{}",
                    configurationId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    private static Response denied(String what) {
        logger.error("Unauthorized attempt to {} by user {}", what, SecurityContext.get().getCurrentUserName());
        return Response.PERMISSION_DENIED();
    }
}
