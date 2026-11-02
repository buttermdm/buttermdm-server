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

package com.buttermdm.plugins.melonmaintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceDeviceStatus;
import com.buttermdm.plugins.melonmaintenance.persistence.domain.MelonMaintenanceSettings;
import com.buttermdm.plugins.melonmaintenance.rest.json.MelonMaintenanceForceInstallRequest;
import com.buttermdm.plugins.melonmaintenance.sync.MelonMaintenanceSyncResponse;
import com.buttermdm.rest.json.SyncResponseInt;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * <p>Unit tests for the <code>Melon Maintenance Gate</code> plugin (`BUT-01`).</p>
 */
public class MelonMaintenancePluginTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SAMPLE_SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void testSyncResponseSerializationAcrossAllFourPosPrograms() throws Exception {
        SyncResponseInt original = (SyncResponseInt) Proxy.newProxyInstance(
                SyncResponseInt.class.getClassLoader(),
                new Class<?>[]{SyncResponseInt.class},
                (proxy, method, args) -> {
                    if ("getBackgroundColor".equals(method.getName())) return "#17553F";
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }
        );

        List<String> allPrograms = Arrays.asList(
                MelonMaintenanceSettings.PACKAGE_GROCERY,
                MelonMaintenanceSettings.PACKAGE_RETAIL,
                MelonMaintenanceSettings.PACKAGE_FSR,
                MelonMaintenanceSettings.PACKAGE_QSR
        );

        for (String pkg : allPrograms) {
            assertTrue(MelonMaintenanceSettings.isAllowedGatePackage(pkg));

            MelonMaintenanceSettings settings = new MelonMaintenanceSettings();
            settings.setGateEnabled(true);
            settings.setGatePackage(pkg);
            settings.setGateSignerSha256(SAMPLE_SHA256);
            settings.setAskTimeoutSeconds(10);
            settings.setMinRetrySeconds(300);
            settings.normalize();

            MelonMaintenanceSyncResponse wrapped = new MelonMaintenanceSyncResponse(original, settings);
            String jsonStr = MAPPER.writeValueAsString(wrapped);
            JsonNode root = MAPPER.readTree(jsonStr);

            assertEquals("#17553F", root.get("backgroundColor").asText());
            JsonNode mm = root.get("melonMaintenance");
            assertNotNull(mm);
            assertTrue(mm.get("gateEnabled").asBoolean());
            assertEquals(pkg, mm.get("gatePackage").asText());
            assertEquals(SAMPLE_SHA256, mm.get("gateSignerSha256").asText());
            assertEquals(10, mm.get("askTimeoutSeconds").asInt());
            assertEquals(300, mm.get("minRetrySeconds").asInt());
        }
    }

    @Test
    public void testAllowedPackagesAndSignerValidation() {
        assertFalse(MelonMaintenanceSettings.isAllowedGatePackage("com.unknown.app"));
        assertTrue(MelonMaintenanceSettings.isValidSha256Hex(SAMPLE_SHA256));
        assertFalse(MelonMaintenanceSettings.isValidSha256Hex("short"));
        assertFalse(MelonMaintenanceSettings.isValidSha256Hex(
                "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"));

        MelonMaintenanceSettings settings = new MelonMaintenanceSettings();
        settings.setMinRetrySeconds(60); // below 300s floor
        settings.setAskTimeoutSeconds(0);
        settings.normalize();
        assertEquals(300, settings.getMinRetrySeconds());
        assertEquals(10, settings.getAskTimeoutSeconds());
    }

    @Test
    public void testThreeNightDeferralAlertThreshold() {
        MelonMaintenanceDeviceStatus status = new MelonMaintenanceDeviceStatus();
        status.setDeferralCount(2);
        assertFalse(status.isThreeNightAlertActive());

        status.setDeferralCount(3);
        assertTrue(status.isThreeNightAlertActive());
    }

    @Test
    public void testForceInstallRequiresTicketRefAndReason() {
        MelonMaintenanceForceInstallRequest req = new MelonMaintenanceForceInstallRequest();
        assertFalse(req.isValid());

        req.setTicketRef("INC-2026-4091");
        assertFalse(req.isValid());

        req.setReason("Store terminal stuck after 3 nights of open shift");
        assertTrue(req.isValid());
    }
}
