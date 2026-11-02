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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.plugins.remotesupport.persistence.domain.RemoteSessionRecord;
import com.buttermdm.plugins.remotesupport.persistence.domain.RemoteSupportSettings;
import com.buttermdm.plugins.remotesupport.persistence.mapper.RemoteSupportMapper;
import com.buttermdm.plugins.remotesupport.rest.RemoteSupportAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>Keeps the remote support sessions in progress and relays their messages: screen frames (binary) and status
 * messages (text) from the device to the technician, input and control messages (text) from the technician to the
 * device.</p>
 *
 * <p>Protocol messages are JSON objects with a type <code>t</code>. The server handles <code>end</code> and
 * <code>ping</code>, drops the input messages (<code>tap</code>, <code>swipe</code>, <code>key</code>,
 * <code>text</code>) of view-only sessions, and sends <code>session</code>, <code>peer</code> and <code>end</code>
 * itself. See the README of the plugin for the whole protocol.</p>
 *
 * @author buttermdm
 */
@Singleton
public class RemoteSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSessionManager.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> INPUT_TYPES = Arrays.asList("tap", "swipe", "key", "text");

    // A session whose device or technician doesn't connect within this time is ended
    static final long CONNECT_TIMEOUT_MS = 120_000;

    private static volatile RemoteSessionManager instance;

    private final Map<String, RemoteSession> sessions = new ConcurrentHashMap<>();

    private final SecureRandom random = new SecureRandom();

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "remotesupport-timer");
        thread.setDaemon(true);
        return thread;
    });

    private final RemoteSupportMapper mapper;

    private final RemoteSupportAuditLog auditLog;

    /**
     * <p>Constructs new <code>RemoteSessionManager</code> instance and ends the sessions left open by a previous
     * server run.</p>
     */
    @Inject
    public RemoteSessionManager(RemoteSupportMapper mapper, RemoteSupportAuditLog auditLog) {
        this.mapper = mapper;
        this.auditLog = auditLog;
        try {
            mapper.endAbandonedSessions(System.currentTimeMillis());
        } catch (Exception e) {
            logger.warn("Failed to close the remote support sessions of a previous run", e);
        }
        timer.scheduleAtFixedRate(this::checkTimeouts, 10, 10, TimeUnit.SECONDS);
        instance = this;
    }

    /**
     * <p>Gets the instance used by the WebSocket endpoint, which is created by the container, not by Guice.</p>
     */
    public static RemoteSessionManager get() {
        return instance;
    }

    /**
     * <p>Creates a session for a device.</p>
     *
     * @param device a device.
     * @param user the technician.
     * @param viewOnly <code>true</code> for a session without control.
     * @param settings the remote support settings of the device's configuration.
     * @return the session.
     */
    public RemoteSession create(Device device, User user, boolean viewOnly, RemoteSupportSettings settings) {
        String id = UUID.randomUUID().toString();
        RemoteSession session = new RemoteSession(id, device.getId(), device.getNumber(), device.getCustomerId(),
                user.getId(), user.getLogin(), viewOnly, settings.isRequireAcceptance(), token(), token(),
                settings.getMaxMinutes() * 60_000L, settings.getEffectiveTransport(), settings.buildIceServers());
        sessions.put(id, session);

        RemoteSessionRecord record = new RemoteSessionRecord();
        record.setId(id);
        record.setDeviceId(device.getId());
        record.setCustomerId(device.getCustomerId());
        record.setUserId(user.getId());
        record.setUserLogin(user.getLogin());
        record.setViewOnly(viewOnly);
        record.setCreatedAt(session.createdAt);
        mapper.insertSession(record);
        return session;
    }

    /**
     * <p>Gets the token the device has to present (sent to the device by a Push message).</p>
     */
    public String getDeviceToken(RemoteSession session) {
        return session.deviceToken;
    }

    /**
     * <p>Gets the token the technician has to present (returned to the technician who started the session).</p>
     */
    public String getClientToken(RemoteSession session) {
        return session.clientToken;
    }

    /**
     * <p>Gets a session in progress.</p>
     */
    public RemoteSession find(String id) {
        return id != null ? sessions.get(id) : null;
    }

    /**
     * <p>Gets the sessions in progress of a customer.</p>
     */
    public List<RemoteSession> getActive(int customerId) {
        List<RemoteSession> result = new ArrayList<>();
        for (RemoteSession session : sessions.values()) {
            if (session.customerId == customerId && !session.ended) {
                result.add(session);
            }
        }
        return result;
    }

    /**
     * <p>Attaches a WebSocket connection to a session.</p>
     *
     * @param side <code>device</code> or <code>client</code>.
     * @param id a session ID.
     * @param token the token of that side.
     * @param socket the connection.
     * @return the session, or <code>null</code> if the session, the side or the token is wrong or the side is
     *         already connected.
     */
    public synchronized RemoteSession attach(String side, String id, String token, Session socket) {
        RemoteSession session = find(id);
        if (session == null || session.ended || token == null) {
            return null;
        }
        if (RemoteSession.DEVICE.equals(side) && session.device == null && equal(token, session.deviceToken)) {
            session.device = socket;
        } else if (RemoteSession.CLIENT.equals(side) && session.client == null && equal(token, session.clientToken)) {
            session.client = socket;
        } else {
            return null;
        }
        ObjectNode info = message("session");
        info.put("sessionId", session.id);
        info.put("deviceNumber", session.deviceNumber);
        info.put("viewOnly", session.viewOnly);
        info.put("requireAcceptance", session.requireAcceptance);
        info.put("maxMinutes", session.maxMillis / 60_000);
        info.put("technician", session.userLogin);
        info.put("transport", session.transport);
        info.set("iceServers", objectMapper.valueToTree(session.iceServers));
        sendText(session, side, info.toString());
        if (RemoteSession.CLIENT.equals(side)) {
            if (session.lastHello != null) {
                sendText(session, side, session.lastHello);
            }
            if (session.lastStatus != null) {
                sendText(session, side, session.lastStatus);
            }
            if (session.lastOffer != null) {
                sendText(session, side, session.lastOffer);
            }
        }
        if (session.device != null && session.client != null) {
            session.connectedAt = System.currentTimeMillis();
            mapper.setConnected(session.id, session.connectedAt);
            ObjectNode peer = message("peer");
            peer.put("connected", true);
            sendText(session, RemoteSession.DEVICE, peer.toString());
            sendText(session, RemoteSession.CLIENT, peer.toString());
            logger.info("Remote support session {} for device {} connected", session.id, session.deviceNumber);
        }
        return session;
    }

    /**
     * <p>Handles a text message.</p>
     */
    public void onText(RemoteSession session, String side, String text) {
        if (session.ended) {
            return;
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(text);
        } catch (Exception e) {
            return;
        }
        String type = json.path("t").asText();
        if ("ping".equals(type)) {
            return;
        }
        if ("end".equals(type)) {
            if (RemoteSession.DEVICE.equals(side)) {
                // The device tells why, e.g. declined or screen capture not allowed
                String reason = json.path("reason").asText("");
                end(session, reason.isEmpty() || reason.length() > 200 ? "ended on the device" : reason);
            } else {
                end(session, "ended by the technician");
            }
            return;
        }
        if (RemoteSession.DEVICE.equals(side)) {
            if ("hello".equals(type)) {
                session.lastHello = text;
            } else if ("status".equals(type)) {
                session.lastStatus = text;
            } else if ("offer".equals(type)) {
                session.lastOffer = text;
            }
            sendText(session, RemoteSession.CLIENT, text);
        } else {
            if (session.viewOnly && INPUT_TYPES.contains(type)) {
                return;
            }
            sendText(session, RemoteSession.DEVICE, text);
        }
    }

    /**
     * <p>Handles a screen frame from the device.</p>
     */
    public void onFrame(RemoteSession session, String side, byte[] frame) {
        if (session.ended || !RemoteSession.DEVICE.equals(side)) {
            return;
        }
        session.frames.incrementAndGet();
        session.bytes.addAndGet(frame.length);
        Session client = session.client;
        if (client == null || !client.isOpen() || !session.clientBusy.compareAndSet(false, true)) {
            return;
        }
        try {
            session.clientSender.execute(() -> {
                try {
                    client.getBasicRemote().sendBinary(ByteBuffer.wrap(frame));
                } catch (Exception e) {
                    logger.debug("Failed to send a frame of remote support session {}", session.id, e);
                } finally {
                    session.clientBusy.set(false);
                }
            });
        } catch (Exception e) {
            // The session is ending
            session.clientBusy.set(false);
        }
    }

    /**
     * <p>Handles a closed connection: the session ends.</p>
     */
    public void onClose(RemoteSession session, String side) {
        end(session, RemoteSession.DEVICE.equals(side) ? "device disconnected" : "technician disconnected");
    }

    /**
     * <p>Ends a session: both connections are closed and the session is recorded.</p>
     */
    public void end(RemoteSession session, String reason) {
        synchronized (session) {
            if (session.ended) {
                return;
            }
            session.ended = true;
        }
        sessions.remove(session.id);
        ObjectNode end = message("end");
        end.put("reason", reason);
        String closeReason = reason.length() > 100 ? reason.substring(0, 100) : reason;
        for (String side : new String[] {RemoteSession.DEVICE, RemoteSession.CLIENT}) {
            Session socket = RemoteSession.DEVICE.equals(side) ? session.device : session.client;
            ExecutorService sender = RemoteSession.DEVICE.equals(side) ? session.deviceSender : session.clientSender;
            sender.execute(() -> {
                if (socket != null && socket.isOpen()) {
                    try {
                        socket.getBasicRemote().sendText(end.toString());
                        socket.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, closeReason));
                    } catch (Exception e) {
                        logger.debug("Failed to close a remote support connection", e);
                    }
                }
            });
            sender.shutdown();
        }
        try {
            mapper.setEnded(session.id, System.currentTimeMillis(), reason, session.frames.get(), session.bytes.get());
        } catch (Exception e) {
            logger.error("Failed to record the end of remote support session {}", session.id, e);
        }
        long seconds = session.connectedAt > 0 ? (System.currentTimeMillis() - session.connectedAt) / 1000 : 0;
        auditLog.logSessionEnd(session, reason, seconds);
        logger.info("Remote support session {} for device {} ended: {}", session.id, session.deviceNumber, reason);
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (RemoteSession session : sessions.values()) {
            try {
                if (session.connectedAt == 0 && now - session.createdAt > CONNECT_TIMEOUT_MS) {
                    end(session, session.device == null ? "the device didn't connect" : "the technician didn't connect");
                } else if (session.connectedAt > 0 && now - session.connectedAt > session.maxMillis) {
                    end(session, "time limit reached");
                }
            } catch (Exception e) {
                logger.error("Failed to check remote support session {}", session.id, e);
            }
        }
    }

    private static void sendText(RemoteSession session, String side, String text) {
        Session socket = RemoteSession.DEVICE.equals(side) ? session.device : session.client;
        if (socket == null) {
            return;
        }
        try {
            (RemoteSession.DEVICE.equals(side) ? session.deviceSender : session.clientSender).execute(() -> {
                try {
                    if (socket.isOpen()) {
                        socket.getBasicRemote().sendText(text);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to send a remote support message", e);
                }
            });
        } catch (Exception e) {
            // The session is ending
        }
    }

    private static ObjectNode message(String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("t", type);
        return node;
    }

    private String token() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
