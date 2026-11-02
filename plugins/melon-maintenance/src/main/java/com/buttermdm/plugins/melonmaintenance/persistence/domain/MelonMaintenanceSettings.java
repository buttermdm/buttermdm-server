/*
 *
 * ButterMDM: Open Source Android MDM Software
 * https://github.com/ButterMDM
 *
 * Copyright (C) 2026 ButterMDM (https://github.com/ButterMDM)
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

package com.buttermdm.plugins.melonmaintenance.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.buttermdm.persistence.domain.CustomerData;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * <p>Customer-level configuration for the <code>Melon Maintenance Gate</code> plugin.</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MelonMaintenanceSettings implements CustomerData, Serializable {

    private static final long serialVersionUID = 1L;

    public static final String PACKAGE_GROCERY = "com.melon.pos.grocery";
    public static final String PACKAGE_RETAIL = "com.melon.pos.retail";
    public static final String PACKAGE_FSR = "com.melon.pos.fsr";
    public static final String PACKAGE_QSR = "com.melon.pos.qsr";
    public static final String PACKAGE_GAS_STATION = "com.melon.pos.gas.station";

    public static final Set<String> ALLOWED_GATE_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            PACKAGE_GROCERY,
            PACKAGE_RETAIL,
            PACKAGE_FSR,
            PACKAGE_QSR,
            PACKAGE_GAS_STATION
    )));

    public static final int DEFAULT_ASK_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_MIN_RETRY_SECONDS = 300;

    private Integer id;
    private int customerId;
    private boolean gateEnabled = false;
    private String gatePackage = PACKAGE_GROCERY;
    private String gateSignerSha256 = "";
    private int askTimeoutSeconds = DEFAULT_ASK_TIMEOUT_SECONDS;
    private int minRetrySeconds = DEFAULT_MIN_RETRY_SECONDS;

    public MelonMaintenanceSettings() {
    }

    public static boolean isAllowedGatePackage(String pkg) {
        return pkg != null && ALLOWED_GATE_PACKAGES.contains(pkg.trim());
    }

    public static boolean isValidSha256Hex(String sha256) {
        if (sha256 == null) {
            return false;
        }
        String trimmed = sha256.trim();
        if (trimmed.length() != 64) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    public void normalize() {
        if (this.gatePackage == null || this.gatePackage.trim().isEmpty()) {
            this.gatePackage = PACKAGE_GROCERY;
        } else {
            this.gatePackage = this.gatePackage.trim();
        }
        if (this.gateSignerSha256 == null) {
            this.gateSignerSha256 = "";
        } else {
            this.gateSignerSha256 = this.gateSignerSha256.trim().toLowerCase(Locale.ROOT);
        }
        if (this.askTimeoutSeconds <= 0) {
            this.askTimeoutSeconds = DEFAULT_ASK_TIMEOUT_SECONDS;
        }
        if (this.minRetrySeconds < DEFAULT_MIN_RETRY_SECONDS) {
            this.minRetrySeconds = DEFAULT_MIN_RETRY_SECONDS;
        }
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public int getCustomerId() {
        return customerId;
    }

    @Override
    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public boolean isGateEnabled() {
        return gateEnabled;
    }

    public void setGateEnabled(boolean gateEnabled) {
        this.gateEnabled = gateEnabled;
    }

    public String getGatePackage() {
        return gatePackage;
    }

    public void setGatePackage(String gatePackage) {
        this.gatePackage = gatePackage;
    }

    public String getGateSignerSha256() {
        return gateSignerSha256;
    }

    public void setGateSignerSha256(String gateSignerSha256) {
        this.gateSignerSha256 = gateSignerSha256;
    }

    public int getAskTimeoutSeconds() {
        return askTimeoutSeconds;
    }

    public void setAskTimeoutSeconds(int askTimeoutSeconds) {
        this.askTimeoutSeconds = askTimeoutSeconds;
    }

    public int getMinRetrySeconds() {
        return minRetrySeconds;
    }

    public void setMinRetrySeconds(int minRetrySeconds) {
        this.minRetrySeconds = minRetrySeconds;
    }
}
