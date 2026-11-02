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

package com.buttermdm.plugins.devicereset.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.plugins.devicereset.persistence.domain.DeviceResetStatus;
import com.buttermdm.plugins.devicereset.persistence.mapper.DeviceResetMapper;

/**
 * <p>A DAO for {@link DeviceResetStatus} domain objects.</p>
 *
 * <p>No security checks are performed here: the callers must verify that the device belongs to the customer of
 * the current user (administrator requests) or authenticate the device (confirmations).</p>
 *
 * @author buttermdm
 */
@Singleton
public class DeviceResetDAO {

    /**
     * <p>An interface to persistence layer.</p>
     */
    private final DeviceResetMapper mapper;

    /**
     * <p>Constructs new <code>DeviceResetDAO</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceResetDAO(DeviceResetMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * <p>Gets the pending requests for the device.</p>
     *
     * @param deviceId an ID of a device.
     * @return the status record or <code>null</code> if nothing was ever requested for the device.
     */
    public DeviceResetStatus getStatus(int deviceId) {
        return this.mapper.getStatus(deviceId);
    }

    /**
     * <p>Stores the factory reset request for the device.</p>
     *
     * @param deviceId an ID of a device.
     * @param userId an ID of the user making the request.
     */
    public void requestReset(int deviceId, Integer userId) {
        this.mapper.requestReset(deviceId, System.currentTimeMillis(), userId);
    }

    /**
     * <p>Stores the reboot request for the device.</p>
     *
     * @param deviceId an ID of a device.
     * @param userId an ID of the user making the request.
     */
    public void requestReboot(int deviceId, Integer userId) {
        this.mapper.requestReboot(deviceId, System.currentTimeMillis(), userId);
    }

    /**
     * <p>Locks the device.</p>
     *
     * @param deviceId an ID of a device.
     * @param message a message displayed on the lock screen (optional).
     * @param userId an ID of the user making the request.
     */
    public void lock(int deviceId, String message, Integer userId) {
        this.mapper.setLock(deviceId, true, message, System.currentTimeMillis(), userId);
    }

    /**
     * <p>Unlocks the device.</p>
     *
     * @param deviceId an ID of a device.
     * @param userId an ID of the user making the request.
     */
    public void unlock(int deviceId, Integer userId) {
        this.mapper.setLock(deviceId, false, null, System.currentTimeMillis(), userId);
    }

    /**
     * <p>Stores the password reset request for the device.</p>
     *
     * @param deviceId an ID of a device.
     * @param password a new password of the device.
     * @param userId an ID of the user making the request.
     */
    public void requestPasswordReset(int deviceId, String password, Integer userId) {
        this.mapper.requestPasswordReset(deviceId, password, System.currentTimeMillis(), userId);
    }

    /**
     * <p>Clears the factory reset, reboot and password reset requests which are not yet confirmed by the device.</p>
     *
     * @param deviceId an ID of a device.
     * @param userId an ID of the user making the request.
     * @return <code>true</code> if any pending request was cleared.
     */
    public boolean cancelPending(int deviceId, Integer userId) {
        return this.mapper.cancelPending(deviceId, System.currentTimeMillis(), userId) > 0;
    }

    /**
     * <p>Confirms the pending factory reset request of the device.</p>
     *
     * @param deviceId an ID of a device.
     * @return <code>true</code> if a pending factory reset request was confirmed; <code>false</code> if nothing was
     *         pending.
     */
    public boolean confirmReset(int deviceId) {
        long now = System.currentTimeMillis();
        boolean confirmed = this.mapper.confirmReset(deviceId, now) > 0;
        if (confirmed) {
            try {
                this.mapper.retireProfile(deviceId, now);
            } catch (Exception ignored) {
            }
        }
        return confirmed;
    }

    /**
     * <p>Confirms the pending reboot request of the device.</p>
     *
     * @param deviceId an ID of a device.
     * @return <code>true</code> if a pending reboot request was confirmed; <code>false</code> if nothing was pending.
     */
    public boolean confirmReboot(int deviceId) {
        return this.mapper.confirmReboot(deviceId, System.currentTimeMillis()) > 0;
    }

    /**
     * <p>Confirms the pending password reset request of the device and deletes the stored password.</p>
     *
     * @param deviceId an ID of a device.
     * @return <code>true</code> if a pending password reset request was confirmed; <code>false</code> if nothing was
     *         pending.
     */
    public boolean confirmPasswordReset(int deviceId) {
        return this.mapper.confirmPasswordReset(deviceId, System.currentTimeMillis()) > 0;
    }
}
