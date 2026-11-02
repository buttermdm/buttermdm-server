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

package com.buttermdm.plugins.rustdesk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * <p>Encodes and decodes the official RustDesk client configuration string format
 * (<code>===&lt;reversed-base64-json&gt;</code>) used by RustDesk Android and desktop clients.</p>
 *
 * @author buttermdm
 */
public final class RustDeskConfigEncoder {

    public static final String PREFIX = "===";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RustDeskConfigEncoder() {
    }

    /**
     * <p>Encodes <code>idServer</code>, <code>relayServer</code>, <code>apiServer</code>, and <code>publicKey</code>
     * into the official RustDesk <code>===</code> configuration string.</p>
     */
    public static String encode(String idServer, String relayServer, String apiServer, String publicKey) {
        String host = idServer != null ? idServer.trim() : "";
        String relay = relayServer != null ? relayServer.trim() : "";
        String api = apiServer != null ? apiServer.trim() : "";
        String key = publicKey != null ? publicKey.trim() : "";

        if (host.isEmpty() && relay.isEmpty() && api.isEmpty() && key.isEmpty()) {
            return "";
        }

        ObjectNode node = MAPPER.createObjectNode();
        node.put("host", host);
        if (!relay.isEmpty()) {
            node.put("relay", relay);
        }
        if (!api.isEmpty()) {
            node.put("api", api);
        }
        node.put("key", key);

        String json = node.toString();
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return new StringBuilder(b64).reverse().toString();
    }

    /**
     * <p>Decodes an official RustDesk reversed-Base64 configuration string back into a JSON tree.</p>
     */
    public static JsonNode decode(String configString) throws Exception {
        if (configString == null || configString.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid RustDesk config string: empty");
        }
        String b64 = new StringBuilder(configString.trim()).reverse().toString();
        while (b64.length() % 4 != 0 && b64.endsWith("=")) {
            b64 = b64.substring(0, b64.length() - 1);
        }
        byte[] decoded = Base64.getDecoder().decode(b64);
        return MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
    }
}
