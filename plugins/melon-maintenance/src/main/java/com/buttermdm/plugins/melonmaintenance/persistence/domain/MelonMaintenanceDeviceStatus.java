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

import java.io.Serializable;

/**
 * <p>Per-device maintenance gate status and 3-night deferral counter.</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MelonMaintenanceDeviceStatus implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int THREE_NIGHT_DEFERRAL_THRESHOLD = 3;

    private Integer id;
    private int deviceId;
    private int customerId;
    private String lastDecision = "UNKNOWN";
    private String lastReason = "NONE";
    private String lastLeaseId;
    private String lastOutcome;
    private int deferralCount = 0;
    private boolean threeNightAlertActive = false;
    private long nextRetryAt = 0L;
    private long lastReportedAt = 0L;
    // Read only: filled by the device list query for the console
    private String deviceNumber;

    public MelonMaintenanceDeviceStatus() {
    }

    public String getDeviceNumber() {
        return deviceNumber;
    }

    public void setDeviceNumber(String deviceNumber) {
        this.deviceNumber = deviceNumber;
    }

    public void recomputeAlertFlag() {
        this.threeNightAlertActive = this.deferralCount >= THREE_NIGHT_DEFERRAL_THRESHOLD;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getLastDecision() {
        return lastDecision;
    }

    public void setLastDecision(String lastDecision) {
        this.lastDecision = lastDecision;
    }

    public String getLastReason() {
        return lastReason;
    }

    public void setLastReason(String lastReason) {
        this.lastReason = lastReason;
    }

    public String getLastLeaseId() {
        return lastLeaseId;
    }

    public void setLastLeaseId(String lastLeaseId) {
        this.lastLeaseId = lastLeaseId;
    }

    public String getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(String lastOutcome) {
        this.lastOutcome = lastOutcome;
    }

    public int getDeferralCount() {
        return deferralCount;
    }

    public void setDeferralCount(int deferralCount) {
        this.deferralCount = Math.max(0, deferralCount);
        recomputeAlertFlag();
    }

    public boolean isThreeNightAlertActive() {
        return threeNightAlertActive;
    }

    public void setThreeNightAlertActive(boolean threeNightAlertActive) {
        this.threeNightAlertActive = threeNightAlertActive;
    }

    public long getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(long nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public long getLastReportedAt() {
        return lastReportedAt;
    }

    public void setLastReportedAt(long lastReportedAt) {
        this.lastReportedAt = lastReportedAt;
    }
}
