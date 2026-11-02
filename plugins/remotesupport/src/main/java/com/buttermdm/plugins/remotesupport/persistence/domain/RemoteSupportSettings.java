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

package com.buttermdm.plugins.remotesupport.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>The remote support settings of a configuration. A configuration without settings has remote support off.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteSupportSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_MAX_MINUTES = 60;
    public static final String DEFAULT_TRANSPORT = "auto";
    public static final String DEFAULT_STUN_URL = "stun:stun.l.google.com:19302";

    private int configurationId;

    /**
     * <p>A name of the configuration (filled in lists).</p>
     */
    private String configurationName;

    private boolean enabled;

    /**
     * <p><code>true</code> if a person at the device must accept each session.</p>
     */
    private boolean requireAcceptance = true;

    /**
     * <p>The longest duration of a session, in minutes.</p>
     */
    private int maxMinutes = DEFAULT_MAX_MINUTES;

    /**
     * <p>Preferred transport mode: <code>auto</code> (WebRTC with WebSocket fallback), <code>webrtc</code>, or <code>websocket</code>.</p>
     */
    private String transport = DEFAULT_TRANSPORT;

    private String stunUrl;
    private String turnUrl;
    private String turnUsername;
    private String turnCredential;

    public String getEffectiveTransport() {
        if (transport == null || transport.trim().isEmpty()) {
            return DEFAULT_TRANSPORT;
        }
        String t = transport.trim().toLowerCase();
        if ("webrtc".equals(t) || "websocket".equals(t) || "auto".equals(t)) {
            return t;
        }
        return DEFAULT_TRANSPORT;
    }

    public List<Map<String, Object>> buildIceServers() {
        List<Map<String, Object>> servers = new ArrayList<>();
        String stun = (stunUrl != null && !stunUrl.trim().isEmpty()) ? stunUrl.trim() : DEFAULT_STUN_URL;
        Map<String, Object> stunEntry = new LinkedHashMap<>();
        stunEntry.put("urls", stun);
        servers.add(stunEntry);

        if (turnUrl != null && !turnUrl.trim().isEmpty()) {
            Map<String, Object> turnEntry = new LinkedHashMap<>();
            turnEntry.put("urls", turnUrl.trim());
            if (turnUsername != null && !turnUsername.trim().isEmpty()) {
                turnEntry.put("username", turnUsername.trim());
            }
            if (turnCredential != null && !turnCredential.isEmpty()) {
                turnEntry.put("credential", turnCredential);
            }
            servers.add(turnEntry);
        }
        return servers;
    }
}
