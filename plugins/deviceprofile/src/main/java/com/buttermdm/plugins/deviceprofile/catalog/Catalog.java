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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>A capability catalog: which management capability works from which Android API level, with which strategy,
 * prerequisites and fallbacks. The same catalog is evaluated by the server and by the Butter agent.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Catalog {

    /**
     * <p>The only schema version supported by this implementation.</p>
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * <p>A version of the catalog format.</p>
     */
    private int schemaVersion;

    /**
     * <p>A version of the catalog content (<code>YYYY.MM.N</code>).</p>
     */
    private String catalogVersion;

    /**
     * <p>The capabilities.</p>
     */
    private List<Capability> capabilities = new ArrayList<>();

    /**
     * <p>The setup steps of a device.</p>
     */
    private List<SetupStep> setupSteps = new ArrayList<>();

    /**
     * <p>The lowest target API level of an app which Android installs, by device API level.</p>
     */
    private List<InstallFloor> installFloor = new ArrayList<>();

    /**
     * <p>The known device vendors.</p>
     */
    private List<Vendor> vendors = new ArrayList<>();

    /**
     * <p>Gets the capability with the specified ID.</p>
     *
     * @param id an ID of a capability.
     * @return the capability or <code>null</code> if the catalog has no such capability.
     */
    public Capability findCapability(String id) {
        for (Capability capability : capabilities) {
            if (capability.getId().equals(id)) {
                return capability;
            }
        }
        return null;
    }

    /**
     * <p>Gets the lowest target API level of an app which the device can install.</p>
     *
     * @param sdkInt an API level of a device.
     * @return a target API level or <code>0</code> if there's no limit.
     */
    public int getInstallFloor(int sdkInt) {
        int floor = 0;
        for (InstallFloor item : installFloor) {
            if (sdkInt >= item.getMinSdk() && item.getTargetSdk() > floor) {
                floor = item.getTargetSdk();
            }
        }
        return floor;
    }

    /**
     * <p>A management capability.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Capability {
        private String id;
        private String area;
        private String title;
        private int minSdk;
        private Integer maxSdk;
        private boolean requiresDeviceOwner;
        private List<Strategy> strategies = new ArrayList<>();
        private List<Requirement> requires = new ArrayList<>();
        private Fallback fallback;
        private List<VendorRule> vendorRules = new ArrayList<>();
        private List<String> configFields = new ArrayList<>();
        private List<String> tests = new ArrayList<>();
        private String confidence;
        private String notes;
    }

    /**
     * <p>An implementation of a capability, used on the API levels from <code>minSdk</code> to <code>maxSdk</code>.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Strategy {
        private String id;
        private int minSdk;
        private Integer maxSdk;

        public boolean isApplicable(int sdkInt) {
            return sdkInt >= minSdk && (maxSdk == null || sdkInt <= maxSdk);
        }
    }

    /**
     * <p>A prerequisite of a capability checked by a device probe.</p>
     *
     * <p>With <code>probe</code>, the probe must have the value <code>expect</code> (default <code>true</code>);
     * with <code>anyProbe</code>, at least one of the probes must be <code>true</code>. The requirement applies from
     * <code>minSdk</code> on. If it isn't met, the capability gets the state <code>state</code> if specified,
     * otherwise the fallback of the capability if there is one, otherwise the state <code>requires</code> with the
     * missing prerequisite <code>missing</code>.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Requirement {
        private String probe;
        private Boolean expect;
        private List<String> anyProbe;
        private Integer minSdk;
        private String missing;
        private String state;
        private String effect;
    }

    /**
     * <p>What happens when a capability isn't available: a replacement strategy, a state and its effect.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fallback {
        private String strategy;
        private String state;
        private List<String> missing;
        private String effect;
    }

    /**
     * <p>A vendor-specific exception to the API-level rules of a capability.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VendorRule {
        private String vendor;
        private String state;
        private String strategy;
        private String reason;
    }

    /**
     * <p>A step of the device setup.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SetupStep {
        private String id;
        private String title;
        private int minSdk;
        private boolean optional;
        private String auto;
        private String otherwise;
        private String adb;
        private String notes;
    }

    /**
     * <p>The lowest target API level of an app which Android installs from the API level <code>minSdk</code> on.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstallFloor {
        private int minSdk;
        private int targetSdk;
    }

    /**
     * <p>A device vendor, recognized by regular expressions on the profile fields (e.g. <code>manufacturer</code>).</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Vendor {
        private String id;
        private Map<String, String> match;
        private List<String> vendorApis = new ArrayList<>();
        private String notes;
    }
}
