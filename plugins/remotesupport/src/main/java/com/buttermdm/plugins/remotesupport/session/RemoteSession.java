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

package com.buttermdm.plugins.remotesupport.session;

import javax.websocket.Session;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>A remote support session in progress: the device and the technician (client) connections and the one-time
 * tokens each of them has to present.</p>
 *
 * @author buttermdm
 */
public class RemoteSession {

    public static final String DEVICE = "device";
    public static final String CLIENT = "client";

    final String id;
    final int deviceId;
    final String deviceNumber;
    final int customerId;
    final Integer userId;
    final String userLogin;
    final boolean viewOnly;
    final boolean requireAcceptance;
    final String deviceToken;
    final String clientToken;
    final long createdAt;
    final long maxMillis;
    final String transport;
    final List<Map<String, Object>> iceServers;

    volatile Session device;
    volatile Session client;

    /**
     * <p>The last <code>hello</code>, <code>status</code> and <code>offer</code> messages of the device, sent again
     * to a technician who connects after them.</p>
     */
    volatile String lastHello;
    volatile String lastStatus;
    volatile String lastOffer;
    volatile long connectedAt;
    volatile boolean ended;

    final AtomicLong frames = new AtomicLong();
    final AtomicLong bytes = new AtomicLong();

    /**
     * <p><code>true</code> while a frame is being sent to the client: newer frames are dropped meanwhile, so a slow
     * connection shows the latest screen instead of a growing backlog.</p>
     */
    final AtomicBoolean clientBusy = new AtomicBoolean();

    /**
     * <p>All messages to a connection are sent by one thread: a WebSocket connection can't send two messages at
     * the same time.</p>
     */
    final ExecutorService deviceSender = Executors.newSingleThreadExecutor(r -> daemon(r, "remotesupport-device"));

    final ExecutorService clientSender = Executors.newSingleThreadExecutor(r -> daemon(r, "remotesupport-client"));

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    RemoteSession(String id, int deviceId, String deviceNumber, int customerId, Integer userId, String userLogin,
                  boolean viewOnly, boolean requireAcceptance, String deviceToken, String clientToken, long maxMillis) {
        this(id, deviceId, deviceNumber, customerId, userId, userLogin, viewOnly, requireAcceptance,
                deviceToken, clientToken, maxMillis, "auto", Collections.emptyList());
    }

    RemoteSession(String id, int deviceId, String deviceNumber, int customerId, Integer userId, String userLogin,
                  boolean viewOnly, boolean requireAcceptance, String deviceToken, String clientToken, long maxMillis,
                  String transport, List<Map<String, Object>> iceServers) {
        this.id = id;
        this.deviceId = deviceId;
        this.deviceNumber = deviceNumber;
        this.customerId = customerId;
        this.userId = userId;
        this.userLogin = userLogin;
        this.viewOnly = viewOnly;
        this.requireAcceptance = requireAcceptance;
        this.deviceToken = deviceToken;
        this.clientToken = clientToken;
        this.createdAt = System.currentTimeMillis();
        this.maxMillis = maxMillis;
        this.transport = transport != null ? transport : "auto";
        this.iceServers = iceServers != null ? iceServers : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getDeviceNumber() {
        return deviceNumber;
    }

    public int getCustomerId() {
        return customerId;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public boolean isViewOnly() {
        return viewOnly;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public boolean isDeviceConnected() {
        return device != null;
    }

    public boolean isClientConnected() {
        return client != null;
    }

    public long getFrames() {
        return frames.get();
    }

    public String getTransport() {
        return transport;
    }

    public List<Map<String, Object>> getIceServers() {
        return iceServers;
    }
}
