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

package com.buttermdm.plugins.melonmaintenance.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * <p>Payload posted by the ButterMDM Android agent to <code>/rest/plugins/melon-maintenance/public/report/{number}</code>.</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MelonMaintenanceDeviceReport implements Serializable {

    private static final long serialVersionUID = 1L;

    private String lastDecision;
    private String lastReason;
    private String lastLeaseId;
    private String lastOutcome;
    private int deferralCount;
    private long nextRetryAt;

    public MelonMaintenanceDeviceReport() {
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
        this.deferralCount = deferralCount;
    }

    public long getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(long nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }
}
