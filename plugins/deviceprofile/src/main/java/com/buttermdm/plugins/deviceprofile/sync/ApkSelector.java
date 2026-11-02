package com.buttermdm.plugins.deviceprofile.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.domain.ApplicationType;
import com.buttermdm.plugins.deviceprofile.apk.ApkAnalyzer;
import com.buttermdm.plugins.deviceprofile.catalog.Catalog;
import com.buttermdm.plugins.deviceprofile.persistence.domain.AppVersionAnalysis;
import com.buttermdm.plugins.deviceprofile.persistence.mapper.DeviceProfileMapper;
import com.buttermdm.rest.json.SyncApplicationInt;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * <p>Selects, for each application of a device configuration, a version the device can install: the configured
 * version if its lowest API level (read from the APK or set by an administrator) and its target API level suit the
 * Android version of the device, otherwise the newest version of the same application which does.</p>
 *
 * @author buttermdm
 */
@Singleton
public class ApkSelector {

    private static final String ABI_ARM64 = "arm64-v8a";

    private final DeviceProfileMapper mapper;

    private final ApkAnalyzer apkAnalyzer;

    /**
     * <p>Constructs new <code>ApkSelector</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public ApkSelector(DeviceProfileMapper mapper, ApkAnalyzer apkAnalyzer) {
        this.mapper = mapper;
        this.apkAnalyzer = apkAnalyzer;
    }

    /**
     * <p>Selects the application versions for a device.</p>
     *
     * @param sdkInt an API level of the device.
     * @param abis the ABIs supported by the device (may be <code>null</code>).
     * @param catalog the capability catalog (for the install floor of the API level).
     * @param applications the applications of the configuration.
     * @return the replaced and removed versions.
     */
    public Selection select(int sdkInt, Collection<String> abis, Catalog catalog,
                            List<SyncApplicationInt> applications) {
        Selection selection = new Selection();
        if (applications == null) {
            return selection;
        }
        int installFloor = catalog.getInstallFloor(sdkInt);
        for (SyncApplicationInt app : applications) {
            if (app.getId() == null || app.getUrl() == null || app.getUrl().isEmpty() ||
                    (app.getType() != null && app.getType() != ApplicationType.app)) {
                continue;
            }
            List<AppVersionAnalysis> versions = mapper.getApplicationVersions(app.getId());
            AppVersionAnalysis current = null;
            for (AppVersionAnalysis version : versions) {
                if (version.getVersion().equals(app.getVersion())) {
                    current = version;
                    break;
                }
            }
            if (current == null) {
                continue;
            }
            String reason = check(apkAnalyzer.ensureAnalyzed(current), sdkInt, installFloor);
            if (reason == null) {
                continue;
            }

            Override override = new Override();
            override.setPkg(app.getPkg());
            override.setFromVersion(app.getVersion());
            override.setReason(reason);
            for (AppVersionAnalysis candidate : versions) {
                if (candidate == current || getUrl(candidate, abis) == null) {
                    continue;
                }
                if (check(apkAnalyzer.ensureAnalyzed(candidate), sdkInt, installFloor) == null) {
                    override.setVersion(candidate.getVersion());
                    override.setCode(candidate.getVersionCode());
                    override.setUrl(getUrl(candidate, abis));
                    break;
                }
            }
            selection.getOverrides().add(override);
        }
        return selection;
    }

    /**
     * <p>Checks whether a version suits a device.</p>
     *
     * @param version an application version.
     * @param sdkInt an API level of the device.
     * @param installFloor the lowest target API level the device installs.
     * @return <code>null</code> if it does, otherwise the reason why not.
     */
    public static String check(AppVersionAnalysis version, int sdkInt, int installFloor) {
        Integer minSdk = version.getEffectiveMinSdk();
        if (minSdk != null && sdkInt < minSdk) {
            return "Version " + version.getVersion() + " needs API " + minSdk + ", the device has API " + sdkInt;
        }
        if (version.getMaxSdkOverride() != null && sdkInt > version.getMaxSdkOverride()) {
            return "Version " + version.getVersion() + " is limited to API " + version.getMaxSdkOverride() +
                    ", the device has API " + sdkInt;
        }
        if (version.getTargetSdk() != null && version.getTargetSdk() < installFloor) {
            return "Version " + version.getVersion() + " targets API " + version.getTargetSdk() + "; API " + sdkInt +
                    " only installs apps targeting API " + installFloor + " or later";
        }
        return null;
    }

    private static String getUrl(AppVersionAnalysis version, Collection<String> abis) {
        if (!version.isSplit()) {
            return version.getUrl();
        }
        boolean arm64 = abis == null || abis.contains(ABI_ARM64);
        if (arm64 && version.getUrlArm64() != null) {
            return version.getUrlArm64();
        }
        return version.getUrlArmeabi() != null ? version.getUrlArmeabi() : version.getUrlArm64();
    }

    /**
     * <p>An application version replaced (or removed, if <code>version</code> is <code>null</code>).</p>
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Override {
        private String pkg;
        private String fromVersion;
        private String version;
        @JsonIgnore
        private Integer code;
        @JsonIgnore
        private String url;
        private String reason;
    }

    /**
     * <p>The replacements for a device.</p>
     */
    @Data
    public static class Selection {
        private List<Override> overrides = new ArrayList<>();

        /**
         * <p>Applies the replacements to the <code>applications</code> array of a configuration.</p>
         */
        public void apply(ArrayNode applications) {
            Iterator<JsonNode> iterator = applications.iterator();
            while (iterator.hasNext()) {
                JsonNode app = iterator.next();
                for (Override override : overrides) {
                    if (override.getPkg().equals(app.path("pkg").asText()) &&
                            override.getFromVersion().equals(app.path("version").asText())) {
                        if (override.getVersion() == null) {
                            iterator.remove();
                        } else {
                            ObjectNode node = (ObjectNode) app;
                            node.put("version", override.getVersion());
                            node.put("code", override.getCode());
                            node.put("url", override.getUrl());
                        }
                        break;
                    }
                }
            }
        }
    }
}
