package com.buttermdm.plugins.deviceprofile.check;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.domain.Configuration;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugins.deviceprofile.apk.ApkAnalyzer;
import com.buttermdm.plugins.deviceprofile.catalog.Catalog;
import com.buttermdm.plugins.deviceprofile.catalog.CatalogService;
import com.buttermdm.plugins.deviceprofile.catalog.ResolvedCapability;
import com.buttermdm.plugins.deviceprofile.persistence.domain.AppVersionAnalysis;
import com.buttermdm.plugins.deviceprofile.persistence.domain.DeviceProfileRecord;
import com.buttermdm.plugins.deviceprofile.persistence.mapper.DeviceProfileMapper;
import com.buttermdm.plugins.deviceprofile.service.DeviceProfileService;
import com.buttermdm.plugins.deviceprofile.sync.ApkSelector;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * <p>Checks a configuration against the devices using it: which settings can't be applied, or only partly, on
 * which devices, and which applications they can't install.</p>
 *
 * @author buttermdm
 */
@Singleton
public class ConfigurationChecker {

    private final DeviceProfileMapper mapper;

    private final DeviceProfileService profileService;

    private final CatalogService catalogService;

    private final ApkAnalyzer apkAnalyzer;

    /**
     * <p>Constructs new <code>ConfigurationChecker</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public ConfigurationChecker(DeviceProfileMapper mapper, DeviceProfileService profileService,
                                CatalogService catalogService, ApkAnalyzer apkAnalyzer) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.catalogService = catalogService;
        this.apkAnalyzer = apkAnalyzer;
    }

    /**
     * <p>Gets the IDs of the capabilities a configuration uses.</p>
     *
     * @param configuration a configuration.
     * @return the capability IDs; restrictions unknown to the catalog are prefixed with <code>restriction.</code> too.
     */
    public static List<String> getRequiredCapabilities(Configuration configuration) {
        List<String> result = new ArrayList<>();
        if (configuration.isKioskMode()) {
            result.add("kiosk.lockTask");
            if (isTrue(configuration.getKioskHome()) || isTrue(configuration.getKioskRecents()) ||
                    isTrue(configuration.getKioskNotifications()) || isTrue(configuration.getKioskSystemInfo()) ||
                    isTrue(configuration.getKioskKeyguard()) || isTrue(configuration.getKioskLockButtons())) {
                result.add("kiosk.lockTaskFeatures");
            }
        }
        if (configuration.isBlockStatusBar()) {
            result.add("kiosk.statusBarDisable");
        }
        if (configuration.getRestrictions() != null) {
            for (String restriction : configuration.getRestrictions().split(",")) {
                if (!restriction.trim().isEmpty()) {
                    result.add("restriction." + restriction.trim());
                }
            }
        }
        if (configuration.getGps() != null) {
            result.add("location.toggle");
        }
        if (configuration.getWifi() != null) {
            result.add("wifi.toggle");
        }
        if (configuration.getBluetooth() != null) {
            result.add("bluetooth.toggle");
        }
        if (configuration.getMobileData() != null) {
            result.add("mobileData.toggle");
        }
        if (configuration.getAutoBrightness() != null) {
            result.add("display.brightness");
        }
        if (isTrue(configuration.getManageTimeout())) {
            result.add("display.timeout");
        }
        if (configuration.getTimeZone() != null && !configuration.getTimeZone().isEmpty()) {
            result.add("auto".equals(configuration.getTimeZone()) ? "time.autoTimeZone" : "time.timeZone");
        }
        if (isTrue(configuration.getDisableScreenshots())) {
            result.add("screenCapture.disable");
        }
        if (configuration.getPasswordMode() != null) {
            result.add("password.quality");
        }
        if (configuration.getSystemUpdateType() != 0) {
            result.add("systemUpdate.policy");
        }
        if (configuration.getUsbStorage() != null) {
            result.add("usb.dataSignaling");
        }
        return result;
    }

    /**
     * <p>Simulates an OS upgrade from <code>fromSdk</code> to <code>toSdk</code> across the customer's fleet and
     * uploaded application versions.</p>
     */
    public RolloutReport checkRollout(int customerId, int fromSdk, int toSdk) {
        Catalog catalog = catalogService.getActive();
        RolloutReport report = new RolloutReport();
        report.setFromSdk(fromSdk);
        report.setToSdk(toSdk);
        report.setCatalogVersion(catalog.getCatalogVersion());

        for (com.buttermdm.plugins.deviceprofile.persistence.domain.FleetDevice device : mapper.getFleet(customerId)) {
            if (device.getSdkInt() != null && device.getSdkInt() == fromSdk) {
                report.getAffectedDevices().add(device.getNumber());
            }
        }

        Map<String, ResolvedCapability> beforeMap = new LinkedHashMap<>();
        for (ResolvedCapability rc : com.buttermdm.plugins.deviceprofile.catalog.CapabilityResolver.resolve(
                syntheticProfile(fromSdk), catalog, java.util.Collections.emptyMap())) {
            beforeMap.put(rc.getId(), rc);
        }
        for (ResolvedCapability after : com.buttermdm.plugins.deviceprofile.catalog.CapabilityResolver.resolve(
                syntheticProfile(toSdk), catalog, java.util.Collections.emptyMap())) {
            ResolvedCapability before = beforeMap.get(after.getId());
            if (before == null) {
                continue;
            }
            boolean stateChanged = !java.util.Objects.equals(before.getState(), after.getState());
            boolean strategyChanged = !java.util.Objects.equals(before.getStrategy(), after.getStrategy());
            if (stateChanged || strategyChanged) {
                RolloutChange change = new RolloutChange();
                change.setCapabilityId(after.getId());
                Catalog.Capability def = catalog.findCapability(after.getId());
                change.setTitle(def != null ? def.getTitle() : after.getId());
                change.setFromState(before.getState());
                change.setToState(after.getState());
                change.setFromStrategy(before.getStrategy());
                change.setToStrategy(after.getStrategy());
                change.setEffect(after.getEffect());
                report.getCapabilityChanges().add(change);
            }
        }

        int installFloor = catalog.getInstallFloor(toSdk);
        for (AppVersionAnalysis version : mapper.getCustomerVersions(customerId)) {
            apkAnalyzer.ensureAnalyzed(version);
            if (version.getUrl() == null && !version.isSplit()) {
                continue;
            }
            String reason = ApkSelector.check(version, toSdk, installFloor);
            if (reason != null) {
                RolloutAppIssue appIssue = new RolloutAppIssue();
                appIssue.setPkg(version.getPkg());
                appIssue.setAppName(version.getAppName());
                appIssue.setVersion(version.getVersion());
                appIssue.setMinSdk(version.getMinSdk());
                appIssue.setTargetSdk(version.getTargetSdk());
                appIssue.setReason(reason);
                report.getBlockedApps().add(appIssue);
            }
        }
        return report;
    }

    private static Map<String, Object> syntheticProfile(int sdkInt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sdkInt", sdkInt);
        map.put("deviceOwner", true);
        map.put("profileOwner", false);
        map.put("deviceAdmin", true);
        map.put("systemApp", false);
        map.put("platformSigned", false);
        map.put("writeSecureSettings", true);
        map.put("accessibilityServiceEnabled", true);
        map.put("notificationsGranted", true);
        map.put("canDrawOverlays", true);
        map.put("manageExternalStorageGranted", true);
        map.put("bluetoothConnectGranted", true);
        map.put("resetPasswordTokenActive", true);
        map.put("screenCaptureDisabled", false);
        return map;
    }

    /**
     * <p>Checks a configuration against its devices.</p>
     *
     * @param configuration a configuration.
     * @param devices the devices using the configuration.
     * @return the report.
     */
    public Report check(Configuration configuration, List<Device> devices) {
        Catalog catalog = catalogService.getActive();
        Report report = new Report();
        report.setConfigurationId(configuration.getId());
        report.setConfigurationName(configuration.getName());
        report.setDevices(devices.size());
        report.setCatalogVersion(catalog.getCatalogVersion());

        List<String> required = getRequiredCapabilities(configuration);
        List<AppVersionAnalysis> applications = configuration.getId() != null
                ? mapper.getConfigurationVersions(configuration.getId())
                : new ArrayList<>();
        for (AppVersionAnalysis application : applications) {
            apkAnalyzer.ensureAnalyzed(application);
        }
        Map<String, Issue> issues = new LinkedHashMap<>();
        for (String id : required) {
            if (catalog.findCapability(id) == null) {
                Issue issue = issues.computeIfAbsent("unknown:" + id, k -> new Issue("unknownCapability", id));
                issue.setEffect("Not in the capability catalog: probably a misspelled or very new restriction");
            }
        }

        for (Device device : devices) {
            DeviceProfileRecord profile = mapper.getProfile(device.getId());
            if (profile == null || profile.getRetiredAt() != null) {
                report.getDevicesWithoutProfile().add(device.getNumber());
                continue;
            }
            Map<String, ResolvedCapability> resolved = new LinkedHashMap<>();
            for (ResolvedCapability capability : profileService.getCapabilities(profile)) {
                resolved.put(capability.getId(), capability);
            }
            for (String id : required) {
                ResolvedCapability capability = resolved.get(id);
                if (capability == null || ResolvedCapability.SUPPORTED.equals(capability.getState())) {
                    continue;
                }
                Issue issue = issues.computeIfAbsent(id + ":" + capability.getState() + ":" + capability.getMissing(),
                        k -> new Issue("capability", id));
                issue.setState(capability.getState());
                issue.setMissing(capability.getMissing());
                issue.setEffect(capability.getEffect());
                issue.add(device.getNumber(), profile.getSdkInt());
            }
            int installFloor = catalog.getInstallFloor(profile.getSdkInt());
            for (AppVersionAnalysis application : applications) {
                if (application.getUrl() == null && !application.isSplit()) {
                    continue;
                }
                String reason = ApkSelector.check(application, profile.getSdkInt(), installFloor);
                if (reason == null) {
                    continue;
                }
                Issue issue = issues.computeIfAbsent("app:" + application.getPkg() + ":" + application.getVersion(),
                        k -> new Issue("application", application.getPkg()));
                issue.setVersion(application.getVersion());
                issue.setEffect(reason.replaceAll(", the device has API \\d+$", "") +
                        ". A newer-Android-only version is replaced by an older suitable version of the same app, " +
                        "if one exists, or left out.");
                issue.add(device.getNumber(), profile.getSdkInt());
            }
        }
        report.setIssues(new ArrayList<>(issues.values()));
        return report;
    }

    private static boolean isTrue(Boolean value) {
        return value != null && value;
    }

    /**
     * <p>The result of a configuration check.</p>
     */
    @Data
    public static class Report {
        private Integer configurationId;
        private String configurationName;
        private int devices;
        private String catalogVersion;
        private List<String> devicesWithoutProfile = new ArrayList<>();
        private List<Issue> issues = new ArrayList<>();
    }

    /**
     * <p>A setting or application not fully available on some devices.</p>
     */
    @Data
    public static class Issue {
        /**
         * <p><code>capability</code>, <code>application</code> or <code>unknownCapability</code>.</p>
         */
        private String kind;
        /**
         * <p>A capability ID or an application package.</p>
         */
        private String subject;
        private String version;
        private String state;
        private List<String> missing = new ArrayList<>();
        private String effect;
        private List<String> deviceNumbers = new ArrayList<>();
        private TreeSet<Integer> sdkLevels = new TreeSet<>();

        public Issue() {
        }

        public Issue(String kind, String subject) {
            this.kind = kind;
            this.subject = subject;
        }

        void add(String deviceNumber, int sdkInt) {
            deviceNumbers.add(deviceNumber);
            sdkLevels.add(sdkInt);
        }
    }

    @Data
    public static class RolloutReport {
        private int fromSdk;
        private int toSdk;
        private String catalogVersion;
        private List<String> affectedDevices = new ArrayList<>();
        private List<RolloutChange> capabilityChanges = new ArrayList<>();
        private List<RolloutAppIssue> blockedApps = new ArrayList<>();
    }

    @Data
    public static class RolloutChange {
        private String capabilityId;
        private String title;
        private String fromState;
        private String toState;
        private String fromStrategy;
        private String toStrategy;
        private String effect;
    }

    @Data
    public static class RolloutAppIssue {
        private String pkg;
        private String appName;
        private String version;
        private Integer minSdk;
        private Integer targetSdk;
        private String reason;
    }
}
