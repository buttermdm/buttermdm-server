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

package com.buttermdm.plugins.deviceprofile.rest;

import com.buttermdm.plugins.deviceprofile.catalog.Catalog;

import java.util.List;

/**
 * <p>Creates the scripts enrolling a device over ADB: install Butter, make it the device owner, run the selected
 * setup steps which need ADB (the commands of the catalog's setup steps) and start it.</p>
 *
 * @author buttermdm
 */
final class EnrollmentScript {

    private static final String ADMIN_RECEIVER = "/com.buttermdm.agent.AdminReceiver";

    private static final String MAIN_ACTIVITY = "/com.buttermdm.agent.ui.MainActivity";

    private EnrollmentScript() {
    }

    /**
     * <p>Creates a script.</p>
     *
     * @param bat <code>true</code> for a Windows batch file, <code>false</code> for a POSIX shell script.
     * @param configurationName a name of the configuration (for the comment).
     * @param apkUrl a download URL of the Butter APK, or <code>null</code> to use a local file.
     * @param pkg the application ID of Butter.
     * @param catalog the catalog with the setup step commands.
     * @param steps the IDs of the setup steps to run.
     * @param date a creation date (for the comment).
     * @return the script.
     */
    static String create(boolean bat, String configurationName, String apkUrl, String pkg, Catalog catalog,
                         List<String> steps, String date) {
        String nl = bat ? "\r\n" : "\n";
        String rem = bat ? "REM " : "# ";
        StringBuilder s = new StringBuilder();
        if (bat) {
            s.append("@echo off").append(nl);
        } else {
            s.append("#!/bin/sh").append(nl);
        }
        s.append(rem).append("ButterMDM: enroll a device over ADB (configuration \"").append(configurationName)
                .append("\"), created ").append(date).append(nl);
        s.append(rem).append("The device must be new or factory-reset, without accounts, with USB debugging on;")
                .append(" adb must be in the PATH.").append(nl);
        if (!bat) {
            s.append("set -e").append(nl);
        }
        s.append(nl);

        if (apkUrl != null) {
            s.append(rem).append("Download Butter from the server").append(nl);
            s.append("curl -fL -o butter.apk \"").append(apkUrl).append("\"").append(nl);
            s.append(check(bat));
        } else {
            s.append(rem).append("No Butter APK is uploaded for this configuration: put it next to this script as")
                    .append(" butter.apk").append(nl);
        }
        s.append("adb install -r butter.apk").append(nl).append(check(bat));
        s.append("adb shell dpm set-device-owner ").append(pkg).append(ADMIN_RECEIVER).append(nl).append(check(bat));

        for (String stepId : steps) {
            for (Catalog.SetupStep step : catalog.getSetupSteps()) {
                if (step.getId().equals(stepId) && step.getAdb() != null) {
                    s.append(nl).append(rem).append(step.getTitle());
                    if (step.getMinSdk() > 21) {
                        s.append(" (Android API ").append(step.getMinSdk()).append(" and later; ignored before)");
                    }
                    s.append(nl);
                    // Optional steps must not stop the enrollment on older Android versions
                    s.append("adb shell ").append(step.getAdb().replace("com.buttermdm.agent", pkg))
                            .append(bat ? "" : " || true").append(nl);
                }
            }
        }
        s.append(nl).append("adb shell am start -n ").append(pkg).append(MAIN_ACTIVITY).append(nl);
        s.append("echo Done: Butter is the device owner and has started.").append(nl);
        return s.toString();
    }

    private static String check(boolean bat) {
        return bat ? "if errorlevel 1 exit /b 1\r\n" : "";
    }
}
