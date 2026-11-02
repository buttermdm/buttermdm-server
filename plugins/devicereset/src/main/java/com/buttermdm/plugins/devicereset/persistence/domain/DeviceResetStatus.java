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

package com.buttermdm.plugins.devicereset.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * <p>A domain object representing the pending reset, reboot, lock and password reset requests for a device.</p>
 *
 * @author buttermdm
 */
@ApiModel(description = "Pending reset, reboot, lock and password reset requests for a device")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceResetStatus implements Serializable {

    private static final long serialVersionUID = -6230964425189843177L;

    @ApiModelProperty("Device ID")
    private int deviceId;

    @ApiModelProperty("Factory reset is requested and not yet confirmed by device")
    private boolean factoryReset;

    @ApiModelProperty("Reboot is requested and not yet confirmed by device")
    private boolean reboot;

    @ApiModelProperty("Device is locked")
    private boolean lock;

    @ApiModelProperty("A message displayed on the lock screen")
    private String lockMessage;

    // Plain text password; kept only until the device confirms, never sent to clients except the device itself
    private String passwordReset;

    @ApiModelProperty("Timestamp of the last request (in milliseconds since epoch time)")
    private Long requestedAt;

    @ApiModelProperty("ID of the user who made the last request")
    private Integer requestedBy;

    @ApiModelProperty("Timestamp when the device confirmed the factory reset (in milliseconds since epoch time)")
    private Long resetConfirmedAt;

    @ApiModelProperty("Timestamp when the device confirmed the reboot (in milliseconds since epoch time)")
    private Long rebootConfirmedAt;

    @ApiModelProperty("Timestamp when the device confirmed the password reset (in milliseconds since epoch time)")
    private Long passwordConfirmedAt;

    /**
     * <p>Constructs new <code>DeviceResetStatus</code> instance. This implementation does nothing.</p>
     */
    public DeviceResetStatus() {
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isFactoryReset() {
        return factoryReset;
    }

    public void setFactoryReset(boolean factoryReset) {
        this.factoryReset = factoryReset;
    }

    public boolean isReboot() {
        return reboot;
    }

    public void setReboot(boolean reboot) {
        this.reboot = reboot;
    }

    public boolean isLock() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    public String getLockMessage() {
        return lockMessage;
    }

    public void setLockMessage(String lockMessage) {
        this.lockMessage = lockMessage;
    }

    @JsonIgnore
    public String getPasswordReset() {
        return passwordReset;
    }

    public void setPasswordReset(String passwordReset) {
        this.passwordReset = passwordReset;
    }

    @ApiModelProperty("Password reset is requested and not yet confirmed by device")
    public boolean isPasswordResetPending() {
        return passwordReset != null;
    }

    public Long getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Long requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Integer getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Integer requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Long getResetConfirmedAt() {
        return resetConfirmedAt;
    }

    public void setResetConfirmedAt(Long resetConfirmedAt) {
        this.resetConfirmedAt = resetConfirmedAt;
    }

    public Long getRebootConfirmedAt() {
        return rebootConfirmedAt;
    }

    public void setRebootConfirmedAt(Long rebootConfirmedAt) {
        this.rebootConfirmedAt = rebootConfirmedAt;
    }

    public Long getPasswordConfirmedAt() {
        return passwordConfirmedAt;
    }

    public void setPasswordConfirmedAt(Long passwordConfirmedAt) {
        this.passwordConfirmedAt = passwordConfirmedAt;
    }

    /**
     * <p>Checks if there is anything to be delivered to the device.</p>
     *
     * @return <code>true</code> if any of the requests is pending or the device is locked.
     */
    @JsonIgnore
    public boolean isPending() {
        return factoryReset || reboot || lock || passwordReset != null;
    }

    @Override
    public String toString() {
        return "DeviceResetStatus{" +
                "deviceId=" + deviceId +
                ", factoryReset=" + factoryReset +
                ", reboot=" + reboot +
                ", lock=" + lock +
                ", lockMessage=" + lockMessage +
                ", passwordResetPending=" + isPasswordResetPending() +
                ", requestedAt=" + requestedAt +
                ", requestedBy=" + requestedBy +
                ", resetConfirmedAt=" + resetConfirmedAt +
                ", rebootConfirmedAt=" + rebootConfirmedAt +
                ", passwordConfirmedAt=" + passwordConfirmedAt +
                '}';
    }
}
