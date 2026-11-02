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

/**
 * <p>The state of a capability on a particular device, as decided by {@link CapabilityResolver}.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ResolvedCapability {

    public static final String SUPPORTED = "supported";
    public static final String REQUIRES = "requires";
    public static final String DEGRADED = "degraded";
    public static final String UNSUPPORTED = "unsupported";
    public static final String UNKNOWN = "unknown";

    /**
     * <p>An ID of the capability.</p>
     */
    private String id;

    /**
     * <p>One of <code>supported</code>, <code>requires</code>, <code>degraded</code>, <code>unsupported</code>,
     * <code>unknown</code>.</p>
     */
    private String state;

    /**
     * <p>An ID of the strategy implementing the capability on the device.</p>
     */
    private String strategy;

    /**
     * <p>The missing prerequisites (e.g. <code>user</code>, <code>adb</code>, <code>platformKey</code>) when the state
     * is <code>requires</code>.</p>
     */
    private List<String> missing = new ArrayList<>();

    /**
     * <p>What decided the state: <code>rule:...</code>, <code>probe:...</code>, <code>vendor:...</code> or
     * <code>result:...</code>.</p>
     */
    private String evidence;

    /**
     * <p>The effect of a state other than <code>supported</code>.</p>
     */
    private String effect;

    public ResolvedCapability() {
    }

    public ResolvedCapability(String id) {
        this.id = id;
    }
}
