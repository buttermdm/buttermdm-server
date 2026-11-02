package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>An event in the history of a device profile.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String OS_UPGRADED = "osUpgraded";
    public static final String FIRST_PROFILE = "firstProfile";
    public static final String RULE_MISMATCH = "ruleMismatch";
    public static final String SETUP_CHANGED = "setupChanged";

    private Integer id;

    private int deviceId;

    /**
     * <p>A device number (filled in lists).</p>
     */
    private String deviceNumber;

    private String eventType;

    private String detail;

    private long eventTime;
}
