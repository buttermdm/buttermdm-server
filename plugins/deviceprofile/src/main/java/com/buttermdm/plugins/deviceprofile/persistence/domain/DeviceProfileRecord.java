package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>The last profile reported by a device: Android version, vendor and the probes of the agent, with the
 * capabilities resolved for it.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceProfileRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * <p>An ID of the device.</p>
     */
    private int deviceId;

    /**
     * <p>An API level (<code>Build.VERSION.SDK_INT</code>).</p>
     */
    private int sdkInt;

    /**
     * <p>An Android version name (<code>Build.VERSION.RELEASE</code>).</p>
     */
    private String release;

    /**
     * <p>A security patch level, <code>YYYY-MM-DD</code>.</p>
     */
    private String securityPatch;

    private String manufacturer;

    private String brand;

    private String model;

    /**
     * <p>A build fingerprint; changes with every system update.</p>
     */
    private String fingerprint;

    /**
     * <p>A version code of the agent which sent the profile.</p>
     */
    private Integer agentVersionCode;

    /**
     * <p>The whole profile as sent by the device (JSON).</p>
     */
    private String profile;

    /**
     * <p>The capabilities resolved for the profile (JSON array of resolved capabilities).</p>
     */
    private String capabilities;

    /**
     * <p>A version of the catalog used to resolve the capabilities.</p>
     */
    private String catalogVersion;

    private long updateTime;

    private Long retiredAt;
}
