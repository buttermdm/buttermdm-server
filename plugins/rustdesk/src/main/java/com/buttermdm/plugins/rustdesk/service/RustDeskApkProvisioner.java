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

package com.buttermdm.plugins.rustdesk.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.notification.PushService;
import com.buttermdm.notification.persistence.domain.PushMessage;
import com.buttermdm.persistence.CustomerDAO;
import com.buttermdm.persistence.DeviceDAO;
import com.buttermdm.persistence.domain.Application;
import com.buttermdm.persistence.domain.ApplicationType;
import com.buttermdm.persistence.domain.ApplicationVersion;
import com.buttermdm.persistence.domain.Customer;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.mapper.ApplicationMapper;
import com.buttermdm.plugins.rustdesk.persistence.domain.RustDeskSettings;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskProvisionRequest;
import com.buttermdm.plugins.rustdesk.rest.json.RustDeskServerStatus;
import com.buttermdm.rest.json.APKFileDetails;
import com.buttermdm.rest.json.ApplicationVersionConfigurationLink;
import com.buttermdm.security.SecurityContext;
import com.buttermdm.util.APKFileAnalyzer;
import com.buttermdm.util.CryptoUtil;
import com.buttermdm.util.FileUtil;
import org.mybatis.guice.transactional.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Provisions the RustDesk Android APK (<code>com.carriez.flutter_hbb</code>) into ButterMDM's
 * Application &amp; ApplicationVersion repository and attaches it to target Configurations for silent
 * Device Owner installation.</p>
 *
 * @author buttermdm
 */
@Singleton
public class RustDeskApkProvisioner {

    private static final Logger log = LoggerFactory.getLogger(RustDeskApkProvisioner.class);

    public static final String DEFAULT_LOCAL_APK_CACHE = "/tmp/buttermdm-live/rustdesk-1.5.0-aarch64-signed.apk";
    public static final String DEFAULT_GITHUB_APK_URL = "https://github.com/rustdesk/rustdesk/releases/download/1.5.0/rustdesk-1.5.0-aarch64-signed.apk";

    private final ApplicationMapper applicationMapper;
    private final CustomerDAO customerDAO;
    private final DeviceDAO deviceDAO;
    private final PushService pushService;
    private final APKFileAnalyzer apkFileAnalyzer;
    private final String filesDirectory;
    private final String baseUrl;

    @Inject
    public RustDeskApkProvisioner(ApplicationMapper applicationMapper,
                                  CustomerDAO customerDAO,
                                  DeviceDAO deviceDAO,
                                  PushService pushService,
                                  APKFileAnalyzer apkFileAnalyzer,
                                  @Named("files.directory") String filesDirectory,
                                  @Named("base.url") String baseUrl) {
        this.applicationMapper = applicationMapper;
        this.customerDAO = customerDAO;
        this.deviceDAO = deviceDAO;
        this.pushService = pushService;
        this.apkFileAnalyzer = apkFileAnalyzer;
        this.filesDirectory = filesDirectory;
        this.baseUrl = baseUrl;
    }

    public void populateMdmAppStatus(int customerId, RustDeskServerStatus status) {
        List<Application> apps = applicationMapper.findByPackageId(customerId, RustDeskSettings.RUSTDESK_PACKAGE);
        if (apps != null && !apps.isEmpty()) {
            Application app = apps.get(0);
            status.setApkProvisionedInMdm(true);
            status.setMdmApplicationId(app.getId());
            status.setMdmApplicationVersionId(app.getLatestVersion());
            status.setMdmApkVersion(app.getVersion());
        } else {
            status.setApkProvisionedInMdm(false);
        }
    }

    @Transactional
    public Map<String, Object> provisionApk(RustDeskProvisionRequest request) throws Exception {
        int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
        Customer customer = customerDAO.findById(customerId);

        File sourceApk = resolveSourceApk(request);
        if (sourceApk == null || !sourceApk.exists() || !sourceApk.isFile()) {
            throw new IllegalArgumentException("RustDesk APK file not found or could not be downloaded");
        }

        File customerDir = new File(filesDirectory, customer.getFilesDir() != null ? customer.getFilesDir() : "");
        if (!customerDir.exists()) {
            customerDir.mkdirs();
        }
        File targetApk = new File(customerDir, "rustdesk-android.apk");
        Files.copy(sourceApk.toPath(), targetApk.toPath(), StandardCopyOption.REPLACE_EXISTING);

        String pkg = RustDeskSettings.RUSTDESK_PACKAGE;
        String versionName = request != null && request.getVersionName() != null && !request.getVersionName().isEmpty()
                ? request.getVersionName() : "1.5.0";
        int versionCode = request != null && request.getVersionCode() != null && request.getVersionCode() > 0
                ? request.getVersionCode() : 2068;

        try {
            APKFileDetails details = apkFileAnalyzer.analyzeFile(targetApk.getAbsolutePath());
            if (details != null) {
                if (details.getPkg() != null && !details.getPkg().isEmpty()) {
                    pkg = details.getPkg();
                }
                if (details.getVersion() != null && !details.getVersion().isEmpty()) {
                    versionName = details.getVersion();
                }
                if (details.getVersionCode() > 0) {
                    versionCode = details.getVersionCode();
                }
            }
        } catch (Exception e) {
            log.warn("APKFileAnalyzer fallback to defaults for {}: {}", targetApk.getAbsolutePath(), e.getMessage());
        }

        String apkHash = CryptoUtil.calculateChecksum(Files.newInputStream(targetApk.toPath()));
        String downloadUrl = FileUtil.createFileUrl(this.baseUrl, customer.getFilesDir(), targetApk.getName());

        List<Application> existingApps = applicationMapper.findByPackageId(customerId, pkg);
        Application app;
        ApplicationVersion appVersion;

        if (existingApps == null || existingApps.isEmpty()) {
            app = new Application();
            app.setCustomerId(customerId);
            app.setName("RustDesk");
            app.setPkg(pkg);
            app.setVersion(versionName);
            app.setVersionCode(versionCode);
            app.setShowIcon(request == null || request.isShowIcon());
            app.setUseKiosk(false);
            app.setSystem(false);
            app.setRunAfterInstall(false);
            app.setRunAtBoot(true);
            app.setSkipVersion(false);
            app.setType(ApplicationType.app);
            app.setUrl(downloadUrl);
            applicationMapper.insertApplication(app);

            appVersion = new ApplicationVersion(app);
            appVersion.setApkHash(apkHash);
            applicationMapper.insertApplicationVersion(appVersion);
            applicationMapper.recalculateLatestVersion(app.getId());
        } else {
            app = existingApps.get(0);
            app.setVersion(versionName);
            app.setVersionCode(versionCode);
            app.setUrl(downloadUrl);
            app.setShowIcon(request == null || request.isShowIcon());
            applicationMapper.updateApplication(app);

            appVersion = applicationMapper.findApplicationVersion(customerId, pkg, versionName);
            if (appVersion == null) {
                appVersion = new ApplicationVersion(app);
                appVersion.setApkHash(apkHash);
                applicationMapper.insertApplicationVersion(appVersion);
            } else {
                appVersion.setUrl(downloadUrl);
                appVersion.setVersionCode(versionCode);
                appVersion.setApkHash(apkHash);
                applicationMapper.updateApplicationVersion(appVersion);
            }
            applicationMapper.recalculateLatestVersion(app.getId());
        }

        Integer configId = request != null && request.getConfigurationId() != null ? request.getConfigurationId() : 1;
        ApplicationVersionConfigurationLink link = new ApplicationVersionConfigurationLink();
        link.setConfigurationId(configId);
        link.setApplicationId(app.getId());
        link.setApplicationVersionId(appVersion.getId());
        link.setAction(1); // Install
        link.setShowIcon(request == null || request.isShowIcon());
        link.setRemove(false);

        applicationMapper.removeApplicationVersionConfigurationsById(customerId, appVersion.getId());
        applicationMapper.insertApplicationVersionConfigurations(
                app.getId(),
                appVersion.getId(),
                Collections.singletonList(link)
        );

        // Notify all devices on the configuration to sync and install immediately
        List<Device> devices = deviceDAO.getDeviceIdsByConfigurationId(configId);
        int notifiedCount = 0;
        if (devices != null) {
            for (Device device : devices) {
                PushMessage msg = new PushMessage();
                msg.setDeviceId(device.getId());
                msg.setMessageType(PushMessage.TYPE_CONFIG_UPDATED);
                pushService.send(msg);
                notifiedCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("applicationId", app.getId());
        result.put("applicationVersionId", appVersion.getId());
        result.put("pkg", pkg);
        result.put("versionName", versionName);
        result.put("versionCode", versionCode);
        result.put("url", downloadUrl);
        result.put("apkHash", apkHash);
        result.put("configurationId", configId);
        result.put("notifiedDevices", notifiedCount);
        return result;
    }

    private File resolveSourceApk(RustDeskProvisionRequest request) throws Exception {
        if (request != null && request.getApkPath() != null && !request.getApkPath().trim().isEmpty()) {
            File f = new File(request.getApkPath().trim());
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        File cached = new File(DEFAULT_LOCAL_APK_CACHE);
        if (cached.exists() && cached.isFile() && cached.length() > 1_000_000) {
            return cached;
        }
        String downloadUrl = request != null && request.getApkUrl() != null && !request.getApkUrl().trim().isEmpty()
                ? request.getApkUrl().trim() : DEFAULT_GITHUB_APK_URL;
        cached.getParentFile().mkdirs();
        HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, cached.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return cached;
    }
}
