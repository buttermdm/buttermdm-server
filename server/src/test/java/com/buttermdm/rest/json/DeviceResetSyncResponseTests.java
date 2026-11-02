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

package com.buttermdm.rest.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.buttermdm.persistence.domain.Application;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugins.devicereset.persistence.domain.DeviceResetStatus;
import com.buttermdm.plugins.devicereset.sync.DeviceResetSyncResponse;
import com.buttermdm.util.CryptoUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * <p>A test suite checking that the device configuration extended by the <code>Device Reset</code> plugin is delivered
 * to the device unchanged and still passes the launcher's response signature check.</p>
 *
 * @author buttermdm
 */
public class DeviceResetSyncResponseTests {

    private static final String HASH_SECRET = "test-secret-1234";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static SyncResponse createSyncResponse() {
        Application app = new Application();
        app.setPkg("com.example.pos");
        app.setName("POS");
        app.setVersion("1.0");
        app.setUrl("http://example.com/pos.apk");
        Device device = new Device();
        device.setImei("123456789012345");

        SyncResponse response = new SyncResponse(null, "12345678", Collections.singletonList(app), device);
        response.setBackgroundColor("#ffffff");
        response.setTitle("Title with spaces");
        response.setKioskMode(true);
        response.setKioskHome(false);
        response.setMainApp("com.example.pos");
        response.setLockStatusBar(true);
        response.setGps(null);
        response.setRestrictions("no_install_unknown_sources,no_debugging_features");
        response.setApplicationSettings(Collections.emptyList());
        return response;
    }

    private static DeviceResetStatus createStatus() {
        DeviceResetStatus status = new DeviceResetStatus();
        status.setDeviceId(1);
        status.setReboot(true);
        status.setLock(true);
        status.setLockMessage("Call support: 555 0100");
        status.setPasswordReset("new pass");
        return status;
    }

    @Test
    public void testWrapperKeepsOriginalFields() throws Exception {
        SyncResponse original = createSyncResponse();
        String originalJson = objectMapper.writeValueAsString(original);
        String wrappedJson = objectMapper.writeValueAsString(new DeviceResetSyncResponse(original, createStatus()));

        // Same text as the original response, the added fields follow
        Assert.assertTrue(wrappedJson.startsWith(originalJson.substring(0, originalJson.length() - 1) + ","));
        ObjectNode withoutAdded = ((ObjectNode) objectMapper.readTree(wrappedJson)).deepCopy();
        withoutAdded.remove(Arrays.asList("factoryReset", "reboot", "lock", "lockMessage", "passwordReset"));
        Assert.assertEquals(objectMapper.readTree(originalJson), withoutAdded);
    }

    /**
     * <p>A response class with a property missing from {@link SyncResponseInt}, as a future version could add.</p>
     */
    public static class ExtendedSyncResponse extends SyncResponse {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getFutureField() {
            return "future value";
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getFutureNullField() {
            return null;
        }
    }

    @Test
    public void testWrapperKeepsFieldsMissingFromInterface() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new DeviceResetSyncResponse(new ExtendedSyncResponse(), createStatus())));
        Assert.assertEquals("future value", json.get("futureField").asText());
        Assert.assertFalse(json.has("futureNullField"));
        Assert.assertTrue(json.get("reboot").asBoolean());
    }

    @Test
    public void testWrapperAddsPendingFields() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new DeviceResetSyncResponse(createSyncResponse(), createStatus())));
        Assert.assertFalse(json.has("factoryReset"));
        Assert.assertTrue(json.get("reboot").asBoolean());
        Assert.assertTrue(json.get("lock").asBoolean());
        Assert.assertEquals("Call support: 555 0100", json.get("lockMessage").asText());
        Assert.assertEquals("new pass", json.get("passwordReset").asText());
    }

    /**
     * <p>Repeats the check done by the launcher (GetServerConfigTask): the signature is the SHA-1 of the shared
     * secret and the "data" part of the response body with all white space removed.</p>
     */
    @Test
    public void testSignatureMatchesLauncherCheck() throws Exception {
        SyncResponseInt[] responses = {
                createSyncResponse(),
                new DeviceResetSyncResponse(createSyncResponse(), createStatus())
        };
        for (SyncResponseInt syncResponse : responses) {
            String signature = CryptoUtil.getDataSignature(HASH_SECRET, syncResponse);

            String body = objectMapper.writeValueAsString(Response.OK(syncResponse));
            final String dataMarker = "\"data\":";
            String serverData = body.substring(body.indexOf(dataMarker) + dataMarker.length(), body.length() - 1);
            String calculated = CryptoUtil.getSHA1String(HASH_SECRET + serverData.replaceAll("\\s", ""));

            Assert.assertTrue(calculated.equalsIgnoreCase(signature));
        }
    }
}
