package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>Customer-level alert thresholds for fleet OS health, patch age, and setup completion.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FleetAlertSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    private int customerId;

    private int minSdkAlert = 28;

    private int maxPatchAgeMonths = 6;

    private int setupIncompleteDays = 7;

    private boolean alertOnOsUpgrade = true;

    private String alertEmail;

    public static FleetAlertSettings defaults(int customerId) {
        FleetAlertSettings settings = new FleetAlertSettings();
        settings.setCustomerId(customerId);
        return settings;
    }
}
