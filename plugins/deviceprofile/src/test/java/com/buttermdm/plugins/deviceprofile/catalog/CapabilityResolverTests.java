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

package com.buttermdm.plugins.deviceprofile.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Tests of {@link CapabilityResolver} with the built-in catalog and the test vectors shared with the Butter
 * agent.</p>
 *
 * @author buttermdm
 */
public class CapabilityResolverTests {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Catalog loadCatalog() throws Exception {
        try (InputStream in = CapabilityResolverTests.class.getResourceAsStream("/catalog/capabilities.json")) {
            return objectMapper.readValue(in, Catalog.class);
        }
    }

    @Test
    public void testSharedVectors() throws Exception {
        JsonNode vectors;
        try (InputStream in = getClass().getResourceAsStream("/catalog-test-vectors.json")) {
            vectors = objectMapper.readTree(in);
        }
        int checked = 0;
        for (JsonNode testCase : vectors.get("cases")) {
            String name = testCase.get("name").asText();
            Catalog catalog = loadCatalog();
            if (testCase.has("addVendorRules")) {
                for (JsonNode rule : testCase.get("addVendorRules")) {
                    Catalog.VendorRule vendorRule = new Catalog.VendorRule();
                    vendorRule.setVendor(rule.get("vendor").asText());
                    vendorRule.setState(rule.get("state").asText());
                    vendorRule.setReason(rule.path("reason").asText(null));
                    catalog.findCapability(rule.get("capability").asText()).getVendorRules().add(vendorRule);
                }
            }
            Map<String, Object> profile = objectMapper.convertValue(testCase.get("profile"),
                    new TypeReference<Map<String, Object>>() {});
            Map<String, String> lastOutcomes = testCase.has("lastOutcomes")
                    ? objectMapper.convertValue(testCase.get("lastOutcomes"), new TypeReference<Map<String, String>>() {})
                    : new HashMap<>();

            Map<String, ResolvedCapability> resolved = new HashMap<>();
            for (ResolvedCapability capability : CapabilityResolver.resolve(profile, catalog, lastOutcomes)) {
                resolved.put(capability.getId(), capability);
            }

            testCase.get("expect").fields().forEachRemaining(entry -> {
                ResolvedCapability actual = resolved.get(entry.getKey());
                Assert.assertNotNull(name + ": capability " + entry.getKey() + " missing", actual);
                JsonNode expected = entry.getValue();
                String prefix = name + " / " + entry.getKey() + ": ";
                if (expected.has("state")) {
                    Assert.assertEquals(prefix + "state", expected.get("state").asText(), actual.getState());
                }
                if (expected.has("strategy")) {
                    Assert.assertEquals(prefix + "strategy", expected.get("strategy").asText(), actual.getStrategy());
                }
                if (expected.has("evidence")) {
                    Assert.assertEquals(prefix + "evidence", expected.get("evidence").asText(), actual.getEvidence());
                }
                if (expected.has("missing")) {
                    List<String> missing = new ArrayList<>();
                    expected.get("missing").forEach(m -> missing.add(m.asText()));
                    Assert.assertEquals(prefix + "missing", missing, actual.getMissing());
                }
            });
            checked += testCase.get("expect").size();
        }
        Assert.assertTrue("No expectation was checked", checked > 30);
    }

    @Test
    public void testCatalogIsConsistent() throws Exception {
        Catalog catalog = loadCatalog();
        Assert.assertEquals(Catalog.SCHEMA_VERSION, catalog.getSchemaVersion());
        Assert.assertEquals("2026.10.1", catalog.getCatalogVersion());
        Assert.assertEquals(120, catalog.getCapabilities().size());
        List<String> ids = new ArrayList<>();
        for (Catalog.Capability capability : catalog.getCapabilities()) {
            Assert.assertFalse("Duplicate capability " + capability.getId(), ids.contains(capability.getId()));
            ids.add(capability.getId());
            Assert.assertFalse("No strategy for " + capability.getId(), capability.getStrategies().isEmpty());
            Assert.assertNotNull("No confidence for " + capability.getId(), capability.getConfidence());
            for (Catalog.Strategy strategy : capability.getStrategies()) {
                Assert.assertTrue("Strategy below the capability's API level: " + capability.getId(),
                        strategy.getMinSdk() >= capability.getMinSdk());
            }
        }
        Assert.assertEquals(23, catalog.getInstallFloor(34));
        Assert.assertEquals(24, catalog.getInstallFloor(36));
        Assert.assertEquals(0, catalog.getInstallFloor(29));
    }
}
