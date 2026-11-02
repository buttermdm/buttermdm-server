package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>The last outcome of applying a capability on a device, as reported by the agent.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CapabilityResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private int deviceId;

    private String capabilityId;

    /**
     * <p>One of <code>applied</code>, <code>fallback</code>, <code>skipped-unsupported</code>, <code>failed</code>,
     * <code>needs-user</code>, <code>needs-adb</code>.</p>
     */
    private String outcome;

    private String strategyId;

    private String detail;

    private String configVersion;

    private long updateTime;
}
