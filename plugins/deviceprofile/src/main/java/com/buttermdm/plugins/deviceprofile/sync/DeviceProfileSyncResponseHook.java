package com.buttermdm.plugins.deviceprofile.sync;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.plugin.service.PluginStatusCache;
import com.buttermdm.plugins.deviceprofile.catalog.Catalog;
import com.buttermdm.plugins.deviceprofile.catalog.CatalogService;
import com.buttermdm.plugins.deviceprofile.persistence.domain.DeviceProfileRecord;
import com.buttermdm.plugins.deviceprofile.persistence.mapper.DeviceProfileMapper;
import com.buttermdm.plugins.deviceprofile.service.DeviceProfileService;
import com.buttermdm.rest.json.SyncResponseHook;
import com.buttermdm.rest.json.SyncResponseInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.buttermdm.plugins.deviceprofile.DeviceProfilePluginConfigurationImpl.PLUGIN_ID;

/**
 * <p>A hook adding the version of the capability catalog to the device configuration, and replacing the application
 * versions which the Android version of the device can't install (see {@link ApkSelector}).</p>
 *
 * @author buttermdm
 */
@Singleton
public class DeviceProfileSyncResponseHook implements SyncResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(DeviceProfileSyncResponseHook.class);

    private final DeviceProfileMapper mapper;

    private final DeviceProfileService profileService;

    private final CatalogService catalogService;

    private final ApkSelector apkSelector;

    private final PluginStatusCache pluginStatusCache;

    /**
     * <p>Constructs new <code>DeviceProfileSyncResponseHook</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceProfileSyncResponseHook(DeviceProfileMapper mapper,
                                         DeviceProfileService profileService,
                                         CatalogService catalogService,
                                         ApkSelector apkSelector,
                                         PluginStatusCache pluginStatusCache) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.catalogService = catalogService;
        this.apkSelector = apkSelector;
        this.pluginStatusCache = pluginStatusCache;
    }

    /**
     * <p>Extends the configuration of a device.</p>
     *
     * @param deviceId an ID of the device.
     * @param original the configuration.
     * @return the extended configuration, or the original one if the plugin is disabled or an error occurs.
     */
    @Override
    public SyncResponseInt handle(int deviceId, SyncResponseInt original) {
        if (this.pluginStatusCache.isPluginDisabled(PLUGIN_ID)) {
            return original;
        }
        try {
            Catalog catalog = catalogService.getActive();
            DeviceProfileRecord profile = mapper.getProfile(deviceId);
            boolean needsProfile = (profile == null || profile.getRetiredAt() != null);
            int sdkInt = (profile != null && profile.getRetiredAt() == null) ? profile.getSdkInt() : 0;
            if (sdkInt <= 0) {
                Integer headerSdk = SyncResponseHook.getRequestAndroidSdk();
                if (headerSdk != null && headerSdk > 0) {
                    sdkInt = headerSdk;
                }
            }
            if (sdkInt <= 0) {
                return new DeviceProfileSyncResponse(original, new ApkSelector.Selection(),
                        catalog.getCatalogVersion(), true);
            }
            List<String> abiList = new ArrayList<>();
            if (profile != null && profile.getRetiredAt() == null) {
                Object abis = profileService.getProfileMap(profile).get("supportedAbis");
                if (abis instanceof Collection) {
                    for (Object abi : (Collection<?>) abis) {
                        abiList.add(String.valueOf(abi));
                    }
                }
            }
            if (abiList.isEmpty()) {
                String reqArch = SyncResponseHook.getRequestCpuArch();
                if (reqArch != null && !reqArch.trim().isEmpty()) {
                    abiList.add(reqArch.trim());
                }
            }
            ApkSelector.Selection selection = apkSelector.select(sdkInt,
                    abiList.isEmpty() ? null : abiList, catalog, original.getApplications());
            return new DeviceProfileSyncResponse(original, selection, catalog.getCatalogVersion(), needsProfile);
        } catch (Exception e) {
            // The configuration must reach the device even if this plugin fails
            logger.error("Failed to extend the configuration of device #{}", deviceId, e);
            return original;
        }
    }
}
