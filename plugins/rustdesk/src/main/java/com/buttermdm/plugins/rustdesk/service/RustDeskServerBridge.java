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
import com.google.inject.Singleton;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskPeerInfo;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>Interacts with the local or remote self-hosted RustDesk Server (<code>hbbs</code> / <code>hbbr</code>),
 * reading <code>/opt/rustdesk/id_ed25519.pub</code>, probing TCP service ports, and querying registered peers
 * from <code>/opt/rustdesk/db_v2.sqlite3</code>.</p>
 *
 * @author buttermdm
 */
@Singleton
public class RustDeskServerBridge {

    private static final Logger log = LoggerFactory.getLogger(RustDeskServerBridge.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String DEFAULT_HBBS_BINARY = "/opt/rustdesk/hbbs";
    public static final String DEFAULT_HBBR_BINARY = "/opt/rustdesk/hbbr";

    public RustDeskServerBridge() {
    }

    public RustDeskServerStatus inspectServer(RustDeskSettings settings) {
        RustDeskServerStatus status = new RustDeskServerStatus();
        String detectedIp = detectLocalHostAddress();
        status.setDetectedHostIp(detectedIp);
        status.setHbbsInstalled(new File(DEFAULT_HBBS_BINARY).exists());
        status.setHbbrInstalled(new File(DEFAULT_HBBR_BINARY).exists());

        String probeHost = extractHost(settings != null ? settings.getIdServer() : null, detectedIp != null ? detectedIp : "127.0.0.1");
        String relayHost = extractHost(settings != null ? settings.getRelayServer() : null, probeHost);

        status.setHbbsNatPortOpen(isTcpPortOpen(probeHost, 21115, 400));
        status.setHbbsIdPortOpen(isTcpPortOpen(probeHost, 21116, 400));
        status.setHbbsWsPortOpen(isTcpPortOpen(probeHost, 21118, 400));
        status.setHbbrRelayPortOpen(isTcpPortOpen(relayHost, 21117, 400));
        status.setHbbrWsPortOpen(isTcpPortOpen(relayHost, 21119, 400));

        String keyPath = settings != null ? settings.getHbbsKeyPath() : RustDeskSettings.DEFAULT_HBBS_KEY_PATH;
        String key = readPublicKey(keyPath);
        if ((key == null || key.isEmpty()) && settings != null) {
            key = settings.getPublicKey();
        }
        status.setPublicKey(key);

        String idServer = settings != null && settings.getIdServer() != null && !settings.getIdServer().isEmpty()
                ? settings.getIdServer() : (detectedIp != null ? detectedIp + ":21116" : "");
        String relayServer = settings != null && settings.getRelayServer() != null && !settings.getRelayServer().isEmpty()
                ? settings.getRelayServer() : (detectedIp != null ? detectedIp + ":21117" : "");
        String apiServer = settings != null ? settings.getApiServer() : "";

        status.setConfigString(RustDeskConfigEncoder.encode(idServer, relayServer, apiServer, key));

        String dbPath = settings != null ? settings.getHbbsDbPath() : RustDeskSettings.DEFAULT_HBBS_DB_PATH;
        status.setRegisteredPeers(readRegisteredPeers(dbPath));
        return status;
    }

    public String readPublicKey(String keyPath) {
        try {
            File file = new File(keyPath != null && !keyPath.isEmpty() ? keyPath : RustDeskSettings.DEFAULT_HBBS_KEY_PATH);
            if (file.exists() && file.isFile() && file.canRead()) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return content.split("\\R")[0].trim();
                }
            }
        } catch (Exception e) {
            log.warn("Could not read RustDesk public key from {}: {}", keyPath, e.getMessage());
        }
        return null;
    }

    public List<RustDeskPeerInfo> readRegisteredPeers(String dbPath) {
        List<RustDeskPeerInfo> peers = new ArrayList<>();
        String targetDb = dbPath != null && !dbPath.isEmpty() ? dbPath : RustDeskSettings.DEFAULT_HBBS_DB_PATH;
        File dbFile = new File(targetDb);
        if (!dbFile.exists() || !dbFile.isFile()) {
            return peers;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "sqlite3", "-separator", "\t", targetDb,
                    "SELECT id, hex(uuid), datetime(created_at), COALESCE(status, 0), COALESCE(info, '{}') FROM peer ORDER BY created_at DESC LIMIT 200;"
            );
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("\t", 5);
                    if (parts.length >= 2) {
                        RustDeskPeerInfo peer = new RustDeskPeerInfo();
                        peer.setId(parts[0].trim());
                        peer.setUuid(parts[1].trim());
                        if (parts.length >= 3) {
                            peer.setCreatedAt(parts[2].trim());
                        }
                        if (parts.length >= 4) {
                            try {
                                peer.setStatus(Integer.parseInt(parts[3].trim()));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        if (parts.length >= 5) {
                            String rawInfo = parts[4].trim();
                            peer.setRawInfo(rawInfo);
                            parsePeerInfoJson(rawInfo, peer);
                        }
                        peers.add(peer);
                    }
                }
            }
            process.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to query RustDesk SQLite database {}: {}", targetDb, e.getMessage());
        }
        return peers;
    }

    public void parsePeerInfoJson(String rawInfo, RustDeskPeerInfo peer) {
        if (rawInfo == null || rawInfo.isEmpty()) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(rawInfo);
            if (node.hasNonNull("username")) {
                peer.setUsername(node.get("username").asText());
            }
            if (node.hasNonNull("device_name")) {
                peer.setDeviceName(node.get("device_name").asText());
            } else if (node.hasNonNull("hostname")) {
                peer.setDeviceName(node.get("hostname").asText());
            }
            if (node.hasNonNull("os")) {
                peer.setOs(node.get("os").asText());
            }
            if (node.hasNonNull("version")) {
                peer.setVersion(node.get("version").asText());
            }
        } catch (Exception ignored) {
        }
    }

    public String detectLocalHostAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                String name = iface.getName().toLowerCase();
                if (name.startsWith("docker") || name.startsWith("br-") || name.startsWith("veth") || name.startsWith("virbr")) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not detect non-loopback IPv4 address: {}", e.getMessage());
        }
        return "127.0.0.1";
    }

    public boolean isTcpPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractHost(String hostPort, String fallback) {
        if (hostPort == null || hostPort.trim().isEmpty()) {
            return fallback;
        }
        String trimmed = hostPort.trim();
        int colon = trimmed.indexOf(':');
        return colon > 0 ? trimmed.substring(0, colon) : trimmed;
    }
}
