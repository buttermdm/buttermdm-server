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

package com.buttermdm.plugins.rustdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskPeerInfo;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskServerStatus;
import com.buttermdm.plugins.rustdesk.service.RustDeskConfigEncoder;
import com.buttermdm.plugins.rustdesk.service.RustDeskServerBridge;
import com.buttermdm.plugins.rustdesk.sync.RustDeskSyncResponse;
import com.buttermdm.rest.json.SyncResponseInt;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * <p>Unit tests for the <code>RustDesk Integration</code> plugin.</p>
 */
public class RustDeskPluginTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testConfigStringEncodeAndDecodeRoundTrip() throws Exception {
        String idServer = "192.168.7.154:21116";
        String relayServer = "192.168.7.154:21117";
        String apiServer = "http://192.168.7.154:21114";
        String publicKey = "zfF2RQh9DaEa9zco1efF7yNzs6ewqwYmLH80L6IHvjk=";

        String encoded = RustDeskConfigEncoder.encode(idServer, relayServer, apiServer, publicKey);
        assertTrue(!encoded.isEmpty());

        JsonNode decoded = RustDeskConfigEncoder.decode(encoded);
        assertEquals(idServer, decoded.get("host").asText());
        assertEquals(relayServer, decoded.get("relay").asText());
        assertEquals(apiServer, decoded.get("api").asText());
        assertEquals(publicKey, decoded.get("key").asText());
    }

    @Test
    public void testPeerInfoJsonParsing() {
        RustDeskServerBridge bridge = new RustDeskServerBridge();
        RustDeskPeerInfo peer = new RustDeskPeerInfo();
        bridge.parsePeerInfoJson("{\"username\":\"android\",\"device_name\":\"Pixel 6\",\"os\":\"Android 16\",\"version\":\"1.5.0\"}", peer);

        assertEquals("android", peer.getUsername());
        assertEquals("Pixel 6", peer.getDeviceName());
        assertEquals("Android 16", peer.getOs());
        assertEquals("1.5.0", peer.getVersion());
    }

    @Test
    public void testSyncResponseSerializationIncludesRustDeskBlock() throws Exception {
        SyncResponseInt original = (SyncResponseInt) Proxy.newProxyInstance(
                SyncResponseInt.class.getClassLoader(),
                new Class<?>[]{SyncResponseInt.class},
                (proxy, method, args) -> {
                    if ("getBackgroundColor".equals(method.getName())) return "#000000";
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }
        );

        RustDeskSettings settings = new RustDeskSettings();
        settings.setEnabled(true);
        settings.setIdServer("192.168.7.154:21116");
        settings.setRelayServer("192.168.7.154:21117");
        settings.setPublicKey("zfF2RQh9DaEa9zco1efF7yNzs6ewqwYmLH80L6IHvjk=");
        settings.setConfigString(RustDeskConfigEncoder.encode(
                settings.getIdServer(), settings.getRelayServer(), "", settings.getPublicKey()));
        settings.setAutoGrantPermissions(true);
        settings.setEnableInputService(true);
        settings.setAllowInKiosk(true);

        RustDeskSyncResponse wrapped = new RustDeskSyncResponse(original, settings);
        String jsonStr = MAPPER.writeValueAsString(wrapped);
        JsonNode root = MAPPER.readTree(jsonStr);

        assertEquals("#000000", root.get("backgroundColor").asText());
        JsonNode rd = root.get("rustdesk");
        assertNotNull(rd);
        assertTrue(rd.get("enabled").asBoolean());
        assertEquals("com.carriez.flutter_hbb", rd.get("pkg").asText());
        assertEquals("192.168.7.154:21116", rd.get("idServer").asText());
        assertEquals("192.168.7.154:21117", rd.get("relayServer").asText());
        assertEquals("zfF2RQh9DaEa9zco1efF7yNzs6ewqwYmLH80L6IHvjk=", rd.get("publicKey").asText());
        assertEquals("=0nI9sma2hUS2wEM4gETtl1dxdXZ2MneOl3NGZWZx82Y6lTYFFGR5gWUSJjRmpnI6ISeltmIsIyNxETMyoDN1EjL34CO2EjLykTMiojI5FGblJnIsIiNxETMyoDN1EjL34CO2EjLykTMiojI0N3boJye", rd.get("configString").asText());
        assertTrue(rd.get("autoGrantPermissions").asBoolean());
        assertTrue(rd.get("enableInputService").asBoolean());
    }

    @Test
    public void testLocalServerInspection() {
        RustDeskServerBridge bridge = new RustDeskServerBridge();
        RustDeskSettings settings = new RustDeskSettings();
        RustDeskServerStatus status = bridge.inspectServer(settings);
        assertNotNull(status);
        assertNotNull(status.getDetectedHostIp());
    }
}
