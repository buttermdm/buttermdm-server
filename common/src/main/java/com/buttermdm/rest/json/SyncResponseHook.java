/*
 *
 * ButterMDM: Open Source Android MDM Software
 * https://github.com/ButterMDM
 *
 * Copyright (C) 2019 ButterMDM (https://github.com/ButterMDM)
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

package com.buttermdm.rest.json;

/**
 * <p>An interface for the hooks to be executed when a response to device configuration synchronization request is going
 * to be sent to device. The hooks may extend the response with additional properties or perform any other desired
 * actions based on provided response.</p>
 *
 * @author isv
 */
public interface SyncResponseHook {

    ThreadLocal<Integer> REQUEST_ANDROID_SDK = new ThreadLocal<>();
    ThreadLocal<String> REQUEST_CPU_ARCH = new ThreadLocal<>();

    static void setRequestContext(Integer sdkInt, String cpuArch) {
        REQUEST_ANDROID_SDK.set(sdkInt);
        REQUEST_CPU_ARCH.set(cpuArch);
    }

    static Integer getRequestAndroidSdk() {
        return REQUEST_ANDROID_SDK.get();
    }

    static String getRequestCpuArch() {
        return REQUEST_CPU_ARCH.get();
    }

    static void clearRequestContext() {
        REQUEST_ANDROID_SDK.remove();
        REQUEST_CPU_ARCH.remove();
    }

    /**
     * <p>Performs the logic specific to this hook.</p>
     *
     * @param deviceId an ID of a device.
     * @param original an original device configuration synchronization response to be handled by this hook.
     * @return a device configuration synchronization response to be used further in process.
     */
    SyncResponseInt handle(int deviceId, SyncResponseInt original);
}
