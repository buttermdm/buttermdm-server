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

package com.buttermdm.plugins.rustdesk.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.buttermdm.notification.PushService;
import com.buttermdm.notification.persistence.domain.PushMessage;
import com.buttermdm.persistence.DeviceDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.plugins.rustdesk.persistence.RustDeskDAO;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskDeviceStatus;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskActionRequest;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskDeviceView;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskPeerInfo;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskProvisionRequest;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskServerStatus;
import com.buttermdm.plugins.rustdesk.service.RustDeskApkProvisioner;
import com.buttermdm.plugins.rustdesk.service.RustDeskConfigEncoder;
import com.buttermdm.plugins.rustdesk.service.RustDeskServerBridge;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Administrator REST resource for the <code>RustDesk Integration</code> plugin.</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/rustdesk/private")
@Api(tags = {"RustDesk plugin"}, authorizations = {@Authorization("Bearer")})
public class RustDeskPrivateResource {

    private static final Logger logger = LoggerFactory.getLogger(RustDeskPrivateResource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PERMISSION_ACCESS = "plugin_rustdesk_access";
    private static final String PERMISSION_SETTINGS = "plugin_rustdesk_settings";

    public static final String PUSH_TYPE_CONFIGURE = "rustdeskConfigure";
    public static final String PUSH_TYPE_GRANT_PERMISSIONS = "rustdeskGrantPermissions";
    public static final String PUSH_TYPE_START = "rustdeskStart";

    private RustDeskDAO rustDeskDAO;
    private DeviceDAO deviceDAO;
    private PushService pushService;
    private RustDeskServerBridge serverBridge;
    private RustDeskApkProvisioner apkProvisioner;

    public RustDeskPrivateResource() {
    }

    @Inject
    public RustDeskPrivateResource(RustDeskDAO rustDeskDAO,
                                   DeviceDAO deviceDAO,
                                   PushService pushService,
                                   RustDeskServerBridge serverBridge,
                                   RustDeskApkProvisioner apkProvisioner) {
        this.rustDeskDAO = rustDeskDAO;
        this.deviceDAO = deviceDAO;
        this.pushService = pushService;
        this.serverBridge = serverBridge;
        this.apkProvisioner = apkProvisioner;
    }

    @GET
    @Path("/settings")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get RustDesk plugin settings")
    public Response getSettings() {
        if (!SecurityContext.get().hasPermission(PERMISSION_ACCESS) && !SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            return Response.OK(this.rustDeskDAO.getSettings());
        } catch (Exception e) {
            logger.error("Failed to load RustDesk settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @PUT
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Save RustDesk plugin settings")
    public Response saveSettings(RustDeskSettings settings) {
        if (!SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            RustDeskSettings saved = this.rustDeskDAO.saveSettings(settings);
            return Response.OK(saved);
        } catch (Exception e) {
            logger.error("Failed to save RustDesk settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @POST
    @Path("/detect-local")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Auto-detect local /opt/rustdesk server settings and save")
    public Response detectLocalServer() {
        if (!SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            RustDeskSettings settings = this.rustDeskDAO.getSettings();
            String host = serverBridge.detectLocalHostAddress();
            if (host != null && !host.isEmpty()) {
                settings.setIdServer(host + ":21116");
                settings.setRelayServer(host + ":21117");
            }
            String key = serverBridge.readPublicKey(settings.getHbbsKeyPath());
            if (key != null && !key.isEmpty()) {
                settings.setPublicKey(key);
            }
            settings.setConfigString(RustDeskConfigEncoder.encode(
                    settings.getIdServer(),
                    settings.getRelayServer(),
                    settings.getApiServer(),
                    settings.getPublicKey()
            ));
            RustDeskSettings saved = this.rustDeskDAO.saveSettings(settings);
            return Response.OK(saved);
        } catch (Exception e) {
            logger.error("Failed to auto-detect local RustDesk server", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @GET
    @Path("/server-status")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Check live status of local/configured RustDesk hbbs/hbbr servers and peer database")
    public Response getServerStatus() {
        if (!SecurityContext.get().hasPermission(PERMISSION_ACCESS) && !SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            User user = SecurityContext.get().getCurrentUser().get();
            RustDeskSettings settings = this.rustDeskDAO.getSettings();
            RustDeskServerStatus status = this.serverBridge.inspectServer(settings);
            this.apkProvisioner.populateMdmAppStatus(user.getCustomerId(), status);
            return Response.OK(status);
        } catch (Exception e) {
            logger.error("Failed to inspect RustDesk server status", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @POST
    @Path("/provision-apk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Provision the RustDesk Android APK into ButterMDM and attach to target Configuration")
    public Response provisionApk(RustDeskProvisionRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_SETTINGS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            Map<String, Object> result = this.apkProvisioner.provisionApk(request);
            return Response.OK(result);
        } catch (Exception e) {
            logger.error("Failed to provision RustDesk APK", e);
            return Response.ERROR("plugin.rustdesk.error.provision", e.getMessage());
        }
    }

    @GET
    @Path("/devices")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List all enrolled devices with RustDesk installation, permissions, and hbbs peer status")
    public Response getDevices() {
        if (!SecurityContext.get().hasPermission(PERMISSION_ACCESS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            User user = SecurityContext.get().getCurrentUser().get();
            RustDeskSettings settings = this.rustDeskDAO.getSettings();
            List<RustDeskPeerInfo> peers = this.serverBridge.readRegisteredPeers(settings.getHbbsDbPath());
            List<Device> devices = this.deviceDAO.getAllDevices();

            List<RustDeskDeviceStatus> statuses = this.rustDeskDAO.getAllDeviceStatuses(user.getCustomerId());
            Map<Integer, RustDeskDeviceStatus> statusByDevice = new HashMap<>();
            if (statuses != null) {
                for (RustDeskDeviceStatus st : statuses) {
                    statusByDevice.put(st.getDeviceId(), st);
                }
            }

            List<RustDeskDeviceView> views = new ArrayList<>();
            if (devices != null) {
                for (Device device : devices) {
                    RustDeskDeviceView view = buildDeviceView(device, statusByDevice.get(device.getId()), peers);
                    views.add(view);
                }
            }
            return Response.OK(views);
        } catch (Exception e) {
            logger.error("Failed to list RustDesk device statuses", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @POST
    @Path("/devices/{deviceId}/action")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Trigger a RustDesk action on a managed device (configure, grantPermissions, start, setPeerId)")
    public Response triggerDeviceAction(@PathParam("deviceId") int deviceId, RustDeskActionRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_ACCESS)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            Device device = this.deviceDAO.getDeviceById(deviceId);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            String action = request != null && request.getAction() != null ? request.getAction().trim() : "configure";
            RustDeskSettings settings = this.rustDeskDAO.getSettings();

            if ("setPeerId".equalsIgnoreCase(action) && request.getRustdeskId() != null) {
                RustDeskDeviceStatus status = this.rustDeskDAO.getDeviceStatus(device.getId());
                if (status == null) {
                    status = new RustDeskDeviceStatus();
                    status.setDeviceId(device.getId());
                    status.setCustomerId(device.getCustomerId());
                }
                status.setRustdeskId(request.getRustdeskId().trim());
                status.setLastReportedAt(System.currentTimeMillis());
                this.rustDeskDAO.saveDeviceStatus(status);
                return Response.OK(status);
            }

            PushMessage message = new PushMessage();
            message.setDeviceId(device.getId());

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("idServer", settings.getIdServer());
            payload.put("relayServer", settings.getRelayServer());
            payload.put("apiServer", settings.getApiServer());
            payload.put("publicKey", settings.getPublicKey());
            payload.put("configString", settings.getConfigString());
            payload.put("autoGrantPermissions", settings.isAutoGrantPermissions());
            payload.put("enableInputService", settings.isEnableInputService());
            payload.put("allowInKiosk", settings.isAllowInKiosk());

            if ("grantPermissions".equalsIgnoreCase(action)) {
                message.setMessageType(PUSH_TYPE_GRANT_PERMISSIONS);
            } else if ("start".equalsIgnoreCase(action)) {
                message.setMessageType(PUSH_TYPE_START);
            } else {
                message.setMessageType(PUSH_TYPE_CONFIGURE);
            }
            message.setPayload(payload.toString());
            this.pushService.send(message);

            Map<String, Object> res = new HashMap<>();
            res.put("deviceId", device.getId());
            res.put("deviceNumber", device.getNumber());
            res.put("messageType", message.getMessageType());
            return Response.OK(res);
        } catch (Exception e) {
            logger.error("Failed to trigger RustDesk action on device #{}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    private RustDeskDeviceView buildDeviceView(Device device, RustDeskDeviceStatus status, List<RustDeskPeerInfo> peers) {
        RustDeskDeviceView view = new RustDeskDeviceView();
        view.setDeviceId(device.getId());
        view.setDeviceNumber(device.getNumber());
        view.setDescription(device.getDescription());
        view.setConfigurationId(device.getConfigurationId());

        String model = null;
        boolean installedFromInventory = false;
        String versionFromInventory = null;

        if (device.getInfo() != null && !device.getInfo().isEmpty()) {
            try {
                JsonNode infoNode = objectMapper.readTree(device.getInfo());
                if (infoNode.hasNonNull("model")) {
                    model = infoNode.get("model").asText();
                    view.setModel(model);
                }
                if (infoNode.hasNonNull("androidVersion")) {
                    view.setAndroidVersion(infoNode.get("androidVersion").asText());
                }
                if (infoNode.has("applications") && infoNode.get("applications").isArray()) {
                    for (JsonNode appNode : infoNode.get("applications")) {
                        if (RustDeskSettings.RUSTDESK_PACKAGE.equals(appNode.path("pkg").asText())) {
                            installedFromInventory = true;
                            versionFromInventory = appNode.path("version").asText(null);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (status != null) {
            view.setInstalled(status.isInstalled() || installedFromInventory);
            view.setVersionName(status.getVersionName() != null ? status.getVersionName() : versionFromInventory);
            view.setVersionCode(status.getVersionCode());
            view.setRustdeskId(status.getRustdeskId());
            view.setPeerUuid(status.getPeerUuid());
            view.setInputServiceEnabled(status.isInputServiceEnabled());
            view.setOverlayGranted(status.isOverlayGranted());
            view.setStorageGranted(status.isStorageGranted());
            view.setNotificationsGranted(status.isNotificationsGranted());
            view.setMediaProjectionGranted(status.isMediaProjectionGranted());
            view.setBatteryOptimizationIgnored(status.isBatteryOptimizationIgnored());
            view.setServerConfigured(status.isServerConfigured());
            view.setHbbsRegistered(status.isHbbsRegistered());
            view.setLastReportedAt(status.getLastReportedAt());
        } else {
            view.setInstalled(installedFromInventory);
            view.setVersionName(versionFromInventory);
        }

        // Correlate with live hbbs SQLite peer table
        if (peers != null && !peers.isEmpty()) {
            for (RustDeskPeerInfo peer : peers) {
                boolean matched = false;
                if (view.getRustdeskId() != null && view.getRustdeskId().equals(peer.getId())) {
                    matched = true;
                } else if (model != null && peer.getDeviceName() != null &&
                        peer.getDeviceName().toLowerCase().contains(model.toLowerCase())) {
                    matched = true;
                } else if (peer.getOs() != null && peer.getOs().toLowerCase().contains("android") && peers.size() == 1) {
                    matched = true;
                }
                if (matched) {
                    if (view.getRustdeskId() == null || view.getRustdeskId().isEmpty()) {
                        view.setRustdeskId(peer.getId());
                    }
                    if (view.getPeerUuid() == null || view.getPeerUuid().isEmpty()) {
                        view.setPeerUuid(peer.getUuid());
                    }
                    view.setHbbsRegistered(true);
                    break;
                }
            }
        }

        if (view.getRustdeskId() != null && !view.getRustdeskId().isEmpty()) {
            view.setConnectUri("rustdesk://" + view.getRustdeskId());
        }
        return view;
    }
}
