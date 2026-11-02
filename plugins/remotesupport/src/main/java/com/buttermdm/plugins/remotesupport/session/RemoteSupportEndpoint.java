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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.util.List;
import java.util.Map;

/**
 * <p>The WebSocket endpoint of remote support sessions: <code>/ws/remotesupport/device</code> for the device and
 * <code>/ws/remotesupport/client</code> for the technician, with the query parameters <code>session</code> and
 * <code>token</code>. The endpoint is registered by the plugin configuration.</p>
 *
 * @author buttermdm
 */
public class RemoteSupportEndpoint extends Endpoint {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSupportEndpoint.class);

    public static final String PATH = "/ws/remotesupport/{side}";

    private static final int MAX_FRAME_SIZE = 4 * 1024 * 1024;
    private static final int MAX_TEXT_SIZE = 64 * 1024;
    private static final String SESSION = "remoteSession";
    private static final String SIDE = "remoteSide";

    @Override
    public void onOpen(Session socket, EndpointConfig config) {
        RemoteSessionManager manager = RemoteSessionManager.get();
        String side = socket.getPathParameters().get("side");
        Map<String, List<String>> parameters = socket.getRequestParameterMap();
        RemoteSession session = manager != null ? manager.attach(side, first(parameters, "session"),
                first(parameters, "token"), socket) : null;
        if (session == null) {
            logger.warn("Rejecting a remote support connection ({}): unknown session, wrong token or already connected", side);
            close(socket, CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid session");
            return;
        }
        socket.setMaxBinaryMessageBufferSize(MAX_FRAME_SIZE);
        socket.setMaxTextMessageBufferSize(MAX_TEXT_SIZE);
        socket.getUserProperties().put(SESSION, session);
        socket.getUserProperties().put(SIDE, side);
        // Handlers with explicit types: the container can't infer the type of a lambda
        socket.addMessageHandler(String.class, text -> manager.onText(session, side, text));
        socket.addMessageHandler(byte[].class, frame -> manager.onFrame(session, side, frame));
    }

    @Override
    public void onClose(Session socket, CloseReason closeReason) {
        RemoteSession session = (RemoteSession) socket.getUserProperties().get(SESSION);
        RemoteSessionManager manager = RemoteSessionManager.get();
        if (session != null && manager != null) {
            manager.onClose(session, (String) socket.getUserProperties().get(SIDE));
        }
    }

    @Override
    public void onError(Session socket, Throwable error) {
        logger.debug("Remote support connection error", error);
    }

    private static String first(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    private static void close(Session socket, CloseReason.CloseCode code, String reason) {
        try {
            socket.close(new CloseReason(code, reason));
        } catch (Exception e) {
            logger.debug("Failed to close a remote support connection", e);
        }
    }
}
