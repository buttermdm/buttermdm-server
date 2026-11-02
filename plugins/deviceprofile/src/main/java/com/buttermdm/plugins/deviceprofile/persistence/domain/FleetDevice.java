package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>A device with a summary of its profile, setup and results, for the fleet view.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FleetDevice implements Serializable {

    private static final long serialVersionUID = 1L;

    private int deviceId;

    private String number;

    private Integer configurationId;

    private String configurationName;

    private Integer sdkInt;

    private String release;

    private String securityPatch;

    private String manufacturer;

    private String model;

    private Long updateTime;

    private String catalogVersion;

    /**
     * <p>A number of setup steps waiting for a person or ADB, or failed.</p>
     */
    private int setupPending;

    /**
     * <p>A number of capabilities the device failed to apply.</p>
     */
    private int failed;

    private Long retiredAt;
}
