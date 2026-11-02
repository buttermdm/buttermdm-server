package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>The state of a setup step on a device, as reported by the agent.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetupStepState implements Serializable {

    private static final long serialVersionUID = 1L;

    private int deviceId;

    private String stepId;

    /**
     * <p>One of <code>done</code>, <code>pending-user</code>, <code>pending-adb</code>, <code>not-applicable</code>,
     * <code>failed</code>.</p>
     */
    private String state;

    private String detail;

    private long updateTime;
}
