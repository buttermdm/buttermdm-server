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

package com.buttermdm.plugins.devicereset.sync;

import com.buttermdm.plugin.persistence.mapper.PluginMapper;
import com.buttermdm.plugin.service.PluginStatusCache;
import com.buttermdm.plugins.devicereset.persistence.DeviceResetDAO;
import com.buttermdm.plugins.devicereset.persistence.domain.DeviceResetStatus;
import com.buttermdm.plugins.devicereset.persistence.mapper.DeviceResetMapper;
import com.buttermdm.rest.json.SyncResponseInt;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>A test suite for {@link DeviceResetSyncResponseHook} class.</p>
 *
 * @author buttermdm
 */
public class DeviceResetSyncResponseHookTests {

    private final Map<Integer, DeviceResetStatus> statuses = new HashMap<>();

    private boolean pluginDisabled = false;

    private final DeviceResetSyncResponseHook hook;

    private final SyncResponseInt original = stub(SyncResponseInt.class, null);

    public DeviceResetSyncResponseHookTests() {
        DeviceResetMapper mapper = stub(DeviceResetMapper.class, args -> statuses.get((Integer) args[0]));
        PluginStatusCache pluginStatusCache = new PluginStatusCache(stub(PluginMapper.class, args -> Collections.emptyList())) {
            @Override
            public boolean isPluginDisabled(String pluginIdentifier) {
                return pluginDisabled;
            }
        };
        this.hook = new DeviceResetSyncResponseHook(new DeviceResetDAO(mapper), pluginStatusCache);
    }

    @Test
    public void testNoStatusReturnsOriginal() {
        Assert.assertSame(original, hook.handle(1, original));
    }

    @Test
    public void testNothingPendingReturnsOriginal() {
        DeviceResetStatus status = new DeviceResetStatus();
        status.setDeviceId(1);
        status.setLockMessage("stale message");
        status.setResetConfirmedAt(1L);
        status.setRebootConfirmedAt(2L);
        status.setPasswordConfirmedAt(3L);
        statuses.put(1, status);
        Assert.assertSame(original, hook.handle(1, original));
    }

    @Test
    public void testDisabledPluginReturnsOriginal() {
        statuses.put(1, pending(true, true, true, "secret"));
        pluginDisabled = true;
        Assert.assertSame(original, hook.handle(1, original));
    }

    @Test
    public void testPendingRequestsAreAdded() {
        statuses.put(1, pending(true, true, true, "secret"));
        DeviceResetSyncResponse response = (DeviceResetSyncResponse) hook.handle(1, original);
        Assert.assertEquals(Boolean.TRUE, response.getFactoryReset());
        Assert.assertEquals(Boolean.TRUE, response.getReboot());
        Assert.assertEquals(Boolean.TRUE, response.getLock());
        Assert.assertEquals("Locked by admin", response.getLockMessage());
        Assert.assertEquals("secret", response.getPasswordReset());
    }

    @Test
    public void testOnlyPendingRequestsAreAdded() {
        statuses.put(1, pending(false, true, false, null));
        DeviceResetSyncResponse response = (DeviceResetSyncResponse) hook.handle(1, original);
        Assert.assertNull(response.getFactoryReset());
        Assert.assertEquals(Boolean.TRUE, response.getReboot());
        Assert.assertNull(response.getLock());
        Assert.assertNull(response.getLockMessage());
        Assert.assertNull(response.getPasswordReset());
    }

    @Test
    public void testEmptyPasswordIsDelivered() {
        // An empty password removes the device password, so it's a valid request
        statuses.put(1, pending(false, false, false, ""));
        DeviceResetSyncResponse response = (DeviceResetSyncResponse) hook.handle(1, original);
        Assert.assertEquals("", response.getPasswordReset());
    }

    private static DeviceResetStatus pending(boolean factoryReset, boolean reboot, boolean lock, String password) {
        DeviceResetStatus status = new DeviceResetStatus();
        status.setDeviceId(1);
        status.setFactoryReset(factoryReset);
        status.setReboot(reboot);
        status.setLock(lock);
        status.setLockMessage("Locked by admin");
        status.setPasswordReset(password);
        return status;
    }

    private interface Answer {
        Object apply(Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> clazz, Answer answer) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                (proxy, method, args) -> answer != null ? answer.apply(args) : null);
    }
}
