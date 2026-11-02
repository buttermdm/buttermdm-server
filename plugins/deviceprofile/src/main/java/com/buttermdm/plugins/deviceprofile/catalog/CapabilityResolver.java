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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <p>Decides the state of every capability of a catalog on a device.</p>
 *
 * <p>Order of evidence: the device owner state, a vendor rule, the API-level rule, the probes of the prerequisites,
 * and finally the last result reported by the device: a capability the device failed to apply is
 * <code>unsupported</code> there until the profile changes. The Butter agent contains the same algorithm; both are
 * checked against the same test vectors (<code>catalog-test-vectors.json</code>).</p>
 *
 * @author buttermdm
 */
public final class CapabilityResolver {

    private CapabilityResolver() {
    }

    /**
     * <p>Resolves all capabilities of the catalog.</p>
     *
     * @param profile a device profile (JSON object as a map): <code>sdkInt</code>, the vendor fields and the probes.
     * @param catalog a capability catalog.
     * @param lastOutcomes the last outcome reported by the device per capability ID (may be empty).
     * @return the resolved capabilities in catalog order.
     */
    public static List<ResolvedCapability> resolve(Map<String, Object> profile, Catalog catalog,
                                                   Map<String, String> lastOutcomes) {
        int sdkInt = toInt(profile.get("sdkInt"));
        List<String> vendors = matchVendors(profile, catalog);
        List<ResolvedCapability> result = new ArrayList<>();
        for (Catalog.Capability capability : catalog.getCapabilities()) {
            ResolvedCapability resolved = resolve(capability, profile, sdkInt, vendors);
            if (ResolvedCapability.SUPPORTED.equals(resolved.getState()) &&
                    "failed".equals(lastOutcomes.get(capability.getId()))) {
                resolved.setState(ResolvedCapability.UNSUPPORTED);
                resolved.setEvidence("result:failed");
            }
            result.add(resolved);
        }
        return result;
    }

    /**
     * <p>Gets the IDs of the catalog vendors matching the profile.</p>
     */
    public static List<String> matchVendors(Map<String, Object> profile, Catalog catalog) {
        List<String> result = new ArrayList<>();
        for (Catalog.Vendor vendor : catalog.getVendors()) {
            if (vendor.getMatch() == null || vendor.getMatch().isEmpty()) {
                continue;
            }
            boolean matches = true;
            for (Map.Entry<String, String> entry : vendor.getMatch().entrySet()) {
                Object value = profile.get(entry.getKey());
                if (value == null || !Pattern.compile(entry.getValue()).matcher(value.toString()).find()) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                result.add(vendor.getId());
            }
        }
        return result;
    }

    private static ResolvedCapability resolve(Catalog.Capability capability, Map<String, Object> profile,
                                              int sdkInt, List<String> vendors) {
        ResolvedCapability resolved = new ResolvedCapability(capability.getId());

        if (capability.isRequiresDeviceOwner()) {
            Boolean deviceOwner = toBoolean(profile.get("deviceOwner"));
            if (deviceOwner == null) {
                resolved.setState(ResolvedCapability.UNKNOWN);
                resolved.setEvidence("probe:deviceOwner=unreported");
                return resolved;
            }
            if (!deviceOwner) {
                resolved.setState(ResolvedCapability.UNSUPPORTED);
                resolved.setEvidence("probe:deviceOwner=false");
                resolved.setEffect("Butter is not the device owner");
                return resolved;
            }
        }

        for (Catalog.VendorRule rule : capability.getVendorRules()) {
            if (vendors.contains(rule.getVendor())) {
                resolved.setState(rule.getState() != null ? rule.getState() : ResolvedCapability.SUPPORTED);
                resolved.setStrategy(rule.getStrategy());
                resolved.setEvidence("vendor:" + rule.getVendor());
                resolved.setEffect(rule.getReason());
                return resolved;
            }
        }

        if (sdkInt < capability.getMinSdk() || (capability.getMaxSdk() != null && sdkInt > capability.getMaxSdk())) {
            applyFallback(resolved, capability, "rule:sdk=" + sdkInt);
            return resolved;
        }

        for (Catalog.Strategy strategy : capability.getStrategies()) {
            if (strategy.isApplicable(sdkInt)) {
                resolved.setStrategy(strategy.getId());
                break;
            }
        }
        if (resolved.getStrategy() == null) {
            applyFallback(resolved, capability, "rule:noStrategy");
            return resolved;
        }
        resolved.setState(ResolvedCapability.SUPPORTED);
        resolved.setEvidence("rule:sdk=" + sdkInt);

        for (Catalog.Requirement requirement : capability.getRequires()) {
            if (requirement.getMinSdk() != null && sdkInt < requirement.getMinSdk()) {
                continue;
            }
            Boolean met = isMet(requirement, profile);
            String evidence = "probe:" + describe(requirement) + "=" + describeValue(requirement, profile, met);
            if (met == null) {
                resolved.setState(ResolvedCapability.UNKNOWN);
                resolved.setEvidence(evidence);
                continue;
            }
            if (met) {
                continue;
            }
            if (requirement.getState() != null) {
                resolved.setState(requirement.getState());
                resolved.setEvidence(evidence);
                resolved.setEffect(requirement.getEffect());
            } else if (capability.getFallback() != null) {
                applyFallback(resolved, capability, evidence);
            } else {
                resolved.setState(ResolvedCapability.REQUIRES);
                resolved.getMissing().add(requirement.getMissing());
                resolved.setEvidence(evidence);
                resolved.setEffect(requirement.getEffect());
            }
            return resolved;
        }
        return resolved;
    }

    private static void applyFallback(ResolvedCapability resolved, Catalog.Capability capability, String evidence) {
        Catalog.Fallback fallback = capability.getFallback();
        resolved.setEvidence(evidence);
        if (fallback == null) {
            resolved.setState(ResolvedCapability.UNSUPPORTED);
            resolved.setStrategy(null);
            return;
        }
        resolved.setStrategy(fallback.getStrategy());
        resolved.setState(fallback.getState() != null ? fallback.getState() : ResolvedCapability.DEGRADED);
        if (fallback.getMissing() != null) {
            resolved.setMissing(new ArrayList<>(fallback.getMissing()));
        }
        resolved.setEffect(fallback.getEffect());
    }

    /**
     * @return <code>true</code> if the requirement is met, <code>false</code> if not, <code>null</code> if the
     *         device didn't report the probes.
     */
    private static Boolean isMet(Catalog.Requirement requirement, Map<String, Object> profile) {
        if (requirement.getAnyProbe() != null) {
            boolean unreported = false;
            for (String probe : requirement.getAnyProbe()) {
                Boolean value = toBoolean(profile.get(probe));
                if (Boolean.TRUE.equals(value)) {
                    return true;
                }
                unreported |= value == null;
            }
            return unreported ? null : false;
        }
        Boolean value = toBoolean(profile.get(requirement.getProbe()));
        if (value == null) {
            return null;
        }
        boolean expected = requirement.getExpect() == null || requirement.getExpect();
        return value == expected;
    }

    private static String describe(Catalog.Requirement requirement) {
        return requirement.getAnyProbe() != null ? String.join("|", requirement.getAnyProbe()) : requirement.getProbe();
    }

    /**
     * @return the reported probe value, or for an any-of requirement whether it is met.
     */
    private static String describeValue(Catalog.Requirement requirement, Map<String, Object> profile, Boolean met) {
        if (met == null) {
            return "unreported";
        }
        return requirement.getAnyProbe() != null ? met.toString() : toBoolean(profile.get(requirement.getProbe())).toString();
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null ? Boolean.valueOf(value.toString()) : null;
    }
}
