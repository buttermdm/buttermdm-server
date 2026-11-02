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

import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugins.rustdesk.persistence.RustDeskDAO;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskDeviceStatus;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskDeviceReport;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskPeerInfo;
import com.buttermdm.plugins.rustdesk.service.RustDeskServerBridge;
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
import java.util.List;
import java.util.Map;

/**
 * <p>Public device-facing REST resource for the <code>RustDesk Integration</code> plugin.
 * Requests are authenticated with <code>X-Request-Signature</code> (SHA-1 of <code>hash.secret + deviceNumber</code>).</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/rustdesk/public")
@Api(tags = {"RustDesk plugin (device)"})
public class RustDeskPublicResource {

    private static final Logger logger = LoggerFactory.getLogger(RustDeskPublicResource.class);
    private static final String HEADER_REQUEST_SIGNATURE = "X-Request-Signature";

    private UnsecureDAO unsecureDAO;
    private RustDeskDAO rustDeskDAO;
    private RustDeskServerBridge serverBridge;
    private String hashSecret;

    public RustDeskPublicResource() {
    }

    @Inject
    public RustDeskPublicResource(UnsecureDAO unsecureDAO,
                                  RustDeskDAO rustDeskDAO,
                                  RustDeskServerBridge serverBridge,
                                  @Named("hash.secret") String hashSecret) {
        this.unsecureDAO = unsecureDAO;
        this.rustDeskDAO = rustDeskDAO;
        this.serverBridge = serverBridge;
        this.hashSecret = hashSecret;
    }

    @GET
    @Path("/config/{number}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Fetch RustDesk server and permission configuration for a managed device")
    public Response getDeviceConfig(@PathParam("number") @ApiParam("Device number") String number,
                                    @Context HttpServletRequest request) {
        Device device = authenticate(number, request);
        if (device == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        RustDeskSettings settings = this.rustDeskDAO.getSettingsByCustomerId(device.getCustomerId());
        return Response.ok(com.buttermdm.rest.json.Response.OK(settings)).build();
    }

    @POST
    @Path("/report/{number}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Submit live RustDesk installation, permission, and peer status from a managed device")
    public Response reportDeviceStatus(@PathParam("number") @ApiParam("Device number") String number,
                                       RustDeskDeviceReport report,
                                       @Context HttpServletRequest request) {
        Device device = authenticate(number, request);
        if (device == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (report == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            RustDeskDeviceStatus status = new RustDeskDeviceStatus();
            status.setDeviceId(device.getId());
            status.setCustomerId(device.getCustomerId());
            status.setInstalled(report.isInstalled());
            status.setVersionName(report.getVersionName());
            status.setVersionCode(report.getVersionCode());
            status.setRustdeskId(report.getRustdeskId());
            status.setPeerUuid(report.getPeerUuid());
            status.setInputServiceEnabled(report.isInputServiceEnabled());
            status.setOverlayGranted(report.isOverlayGranted());
            status.setStorageGranted(report.isStorageGranted());
            status.setNotificationsGranted(report.isNotificationsGranted());
            status.setMediaProjectionGranted(report.isMediaProjectionGranted());
            status.setBatteryOptimizationIgnored(report.isBatteryOptimizationIgnored());
            status.setServerConfigured(report.isServerConfigured());
            status.setLastReportedAt(System.currentTimeMillis());

            // Check if the peer is registered in local hbbs SQLite DB
            RustDeskSettings settings = this.rustDeskDAO.getSettingsByCustomerId(device.getCustomerId());
            List<RustDeskPeerInfo> peers = this.serverBridge.readRegisteredPeers(settings.getHbbsDbPath());
            if (peers != null && !peers.isEmpty()) {
                for (RustDeskPeerInfo peer : peers) {
                    if (status.getRustdeskId() != null && status.getRustdeskId().equals(peer.getId())) {
                        status.setHbbsRegistered(true);
                        if (status.getPeerUuid() == null) {
                            status.setPeerUuid(peer.getUuid());
                        }
                        break;
                    } else if ((status.getRustdeskId() == null || status.getRustdeskId().isEmpty()) &&
                            peer.getOs() != null && peer.getOs().toLowerCase().contains("android")) {
                        status.setRustdeskId(peer.getId());
                        status.setPeerUuid(peer.getUuid());
                        status.setHbbsRegistered(true);
                        break;
                    }
                }
            }

            this.rustDeskDAO.saveDeviceStatus(status);

            Map<String, Object> data = new HashMap<>();
            data.put("deviceId", device.getId());
            data.put("rustdeskId", status.getRustdeskId());
            data.put("hbbsRegistered", status.isHbbsRegistered());
            data.put("configString", settings.getConfigString());
            return Response.ok(com.buttermdm.rest.json.Response.OK(data)).build();
        } catch (Exception e) {
            logger.error("Failed to save RustDesk status report from device {}", number, e);
            return Response.serverError().build();
        }
    }

    private Device authenticate(String number, HttpServletRequest request) {
        if (!CryptoUtil.checkRequestSignature(request.getHeader(HEADER_REQUEST_SIGNATURE), hashSecret + number)) {
            logger.warn("Rejecting RustDesk public request for device {}: invalid signature", number);
            return null;
        }
        return unsecureDAO.getDeviceByNumber(number);
    }
}
