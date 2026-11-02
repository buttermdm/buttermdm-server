package com.buttermdm.plugins.deviceprofile.rest;

import com.buttermdm.notification.PushService;
import com.buttermdm.persistence.ConfigurationDAO;
import com.buttermdm.persistence.DeviceDAO;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Application;
import com.buttermdm.persistence.domain.Configuration;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.plugins.deviceprofile.apk.ApkAnalyzer;
import com.buttermdm.plugins.deviceprofile.catalog.Catalog;
import com.buttermdm.plugins.deviceprofile.catalog.CatalogService;
import com.buttermdm.plugins.deviceprofile.catalog.ResolvedCapability;
import com.buttermdm.plugins.deviceprofile.check.ConfigurationChecker;
import com.buttermdm.plugins.deviceprofile.persistence.domain.AppVersionAnalysis;
import com.buttermdm.plugins.deviceprofile.persistence.domain.CatalogRecord;
import com.buttermdm.plugins.deviceprofile.persistence.domain.DeviceProfileRecord;
import com.buttermdm.plugins.deviceprofile.persistence.domain.FleetDevice;
import com.buttermdm.plugins.deviceprofile.persistence.domain.SetupStepState;
import com.buttermdm.plugins.deviceprofile.persistence.mapper.DeviceProfileMapper;
import com.buttermdm.plugins.deviceprofile.rest.json.SdkOverrideRequest;
import com.buttermdm.plugins.deviceprofile.service.DeviceProfileService;
import com.buttermdm.rest.json.Response;
import com.buttermdm.security.SecurityContext;
import com.buttermdm.security.SecurityException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * <p>A resource used by administrators to view the device profiles, check configurations against the devices,
 * manage the capability catalogs and the Android requirements of applications, request setup checks and create
 * enrollment scripts.</p>
 *
 * @author buttermdm
 */
@Singleton
@Path("/plugins/deviceprofile/private")
@Api(tags = {"Device profile plugin"})
public class DeviceProfilePrivateResource {

    private static final Logger logger = LoggerFactory.getLogger(DeviceProfilePrivateResource.class);

    private static final String PERMISSION_VIEW = "plugin_deviceprofile_view";
    private static final String PERMISSION_MANAGE = "plugin_deviceprofile_manage";

    private static final String BUILT_IN = "builtin";

    private static final int HISTORY_LIMIT = 50;

    private static final List<String> SCRIPT_STEPS = Arrays.asList("writeSecureSettings", "overlay",
            "batteryOptimization", "exactAlarms");

    private DeviceProfileMapper mapper;
    private DeviceProfileService profileService;
    private CatalogService catalogService;
    private ConfigurationChecker configurationChecker;
    private ApkAnalyzer apkAnalyzer;
    private DeviceDAO deviceDAO;
    private ConfigurationDAO configurationDAO;
    private UnsecureDAO unsecureDAO;
    private PushService pushService;
    private DeviceProfileAuditLog auditLog;

    /**
     * <p>A constructor required by swagger.</p>
     */
    public DeviceProfilePrivateResource() {
    }

    /**
     * <p>Constructs new <code>DeviceProfilePrivateResource</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceProfilePrivateResource(DeviceProfileMapper mapper,
                                        DeviceProfileService profileService,
                                        CatalogService catalogService,
                                        ConfigurationChecker configurationChecker,
                                        ApkAnalyzer apkAnalyzer,
                                        DeviceDAO deviceDAO,
                                        ConfigurationDAO configurationDAO,
                                        UnsecureDAO unsecureDAO,
                                        PushService pushService,
                                        DeviceProfileAuditLog auditLog) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.catalogService = catalogService;
        this.configurationChecker = configurationChecker;
        this.apkAnalyzer = apkAnalyzer;
        this.deviceDAO = deviceDAO;
        this.configurationDAO = configurationDAO;
        this.unsecureDAO = unsecureDAO;
        this.pushService = pushService;
        this.auditLog = auditLog;
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the device profile",
            notes = "Gets the profile, resolved capabilities, outcomes, setup states and history of a device",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/devices/{deviceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDevice(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId) {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get the profile of device #" + deviceId);
        }
        try {
            Device device = deviceDAO.getDeviceById(deviceId);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            Catalog catalog = catalogService.getActive();
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> deviceInfo = new LinkedHashMap<>();
            deviceInfo.put("id", device.getId());
            deviceInfo.put("number", device.getNumber());
            deviceInfo.put("configurationId", device.getConfigurationId());
            result.put("device", deviceInfo);
            result.put("catalogVersion", catalog.getCatalogVersion());

            DeviceProfileRecord profile = mapper.getProfile(device.getId());
            if (profile != null) {
                Map<String, Object> profileInfo = new LinkedHashMap<>();
                profileInfo.put("sdkInt", profile.getSdkInt());
                profileInfo.put("release", profile.getRelease());
                profileInfo.put("securityPatch", profile.getSecurityPatch());
                profileInfo.put("manufacturer", profile.getManufacturer());
                profileInfo.put("brand", profile.getBrand());
                profileInfo.put("model", profile.getModel());
                profileInfo.put("fingerprint", profile.getFingerprint());
                profileInfo.put("agentVersionCode", profile.getAgentVersionCode());
                profileInfo.put("catalogVersion", profile.getCatalogVersion());
                profileInfo.put("updateTime", profile.getUpdateTime());
                profileInfo.put("retiredAt", profile.getRetiredAt());
                profileInfo.put("reported", profileService.getProfileMap(profile));
                result.put("profile", profileInfo);

                List<Map<String, Object>> capabilities = new ArrayList<>();
                for (ResolvedCapability resolved : profileService.getCapabilities(profile)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("resolved", resolved);
                    item.put("definition", catalog.findCapability(resolved.getId()));
                    capabilities.add(item);
                }
                result.put("capabilities", capabilities);
            }
            result.put("results", mapper.getResults(device.getId()));

            Map<String, SetupStepState> states = new HashMap<>();
            for (SetupStepState state : mapper.getSetup(device.getId())) {
                states.put(state.getStepId(), state);
            }
            List<Map<String, Object>> setup = new ArrayList<>();
            for (Catalog.SetupStep step : catalog.getSetupSteps()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("definition", step);
                item.put("state", states.get(step.getId()));
                setup.add(item);
            }
            result.put("setup", setup);
            result.put("history", mapper.getDeviceHistory(device.getId(), HISTORY_LIMIT));
            return Response.OK(result);
        } catch (SecurityException e) {
            return denied("get the profile of device #" + deviceId);
        } catch (Exception e) {
            logger.error("Unexpected error when getting the profile of device #{}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the fleet overview",
            notes = "Gets the devices of the customer with their Android versions, setup and outcome summaries",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/fleet")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFleet() {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get the fleet overview");
        }
        try {
            int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
            List<FleetDevice> devices = mapper.getFleet(customerId);
            Map<String, Integer> bySdk = new TreeMap<>();
            Map<String, Integer> byModel = new TreeMap<>();
            Map<String, Integer> byPatchAge = new LinkedHashMap<>();
            for (String bucket : Arrays.asList("0-3", "3-6", "6-12", "12+", "unknown")) {
                byPatchAge.put(bucket, 0);
            }
            int withoutProfile = 0;
            for (FleetDevice device : devices) {
                if (device.getSdkInt() == null || device.getRetiredAt() != null) {
                    withoutProfile++;
                    continue;
                }
                String sdkKey = "API " + device.getSdkInt() + (device.getRelease() != null ?
                        " (Android " + device.getRelease() + ")" : "");
                bySdk.merge(sdkKey, 1, Integer::sum);
                String modelKey = (device.getManufacturer() != null ? device.getManufacturer() + " " : "") +
                        (device.getModel() != null ? device.getModel() : "?");
                byModel.merge(modelKey, 1, Integer::sum);
                byPatchAge.merge(patchAgeBucket(device.getSecurityPatch()), 1, Integer::sum);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("catalogVersion", catalogService.getActive().getCatalogVersion());
            result.put("devices", devices);
            result.put("withoutProfile", withoutProfile);
            result.put("bySdk", bySdk);
            result.put("byModel", byModel);
            result.put("byPatchAgeMonths", byPatchAge);
            result.put("history", mapper.getCustomerHistory(customerId, HISTORY_LIMIT));
            result.put("alerts", profileService.getAlertSettings(customerId));
            return Response.OK(result);
        } catch (Exception e) {
            logger.error("Unexpected error when getting the fleet overview", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the configurations",
            notes = "Gets the IDs and names of the configurations available to the current user",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/configurations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfigurations() {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get the configurations");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Configuration configuration : configurationDAO.getAllConfigurations()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", configuration.getId());
            item.put("name", configuration.getName());
            result.add(item);
        }
        return Response.OK(result);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Check a configuration",
            notes = "Checks which settings and applications of a configuration its devices can't apply or install",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/configurations/{id}/check")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkConfiguration(@PathParam("id") @ApiParam("Configuration ID") Integer id) {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("check configuration #" + id);
        }
        try {
            Configuration configuration = configurationDAO.getConfigurationById(id);
            if (configuration == null) {
                return Response.ERROR("plugin.deviceprofile.error.configuration.notfound");
            }
            List<Device> devices = unsecureDAO.getAllConfigurationDevices(configuration.getId(),
                    configuration.getCustomerId());
            return Response.OK(configurationChecker.check(configuration, devices));
        } catch (SecurityException e) {
            return denied("check configuration #" + id);
        } catch (Exception e) {
            logger.error("Unexpected error when checking configuration #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    @POST
    @Path("/configurations/{id}/check")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkDraftConfiguration(@PathParam("id") @ApiParam("Configuration ID") Integer id,
                                            Configuration draft) {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("check configuration #" + id);
        }
        try {
            Configuration saved = configurationDAO.getConfigurationById(id);
            if (saved == null) {
                return Response.ERROR("plugin.deviceprofile.error.configuration.notfound");
            }
            Configuration target = draft != null ? draft : saved;
            target.setId(saved.getId());
            target.setCustomerId(saved.getCustomerId());
            if (target.getName() == null) {
                target.setName(saved.getName());
            }
            List<Device> devices = unsecureDAO.getAllConfigurationDevices(saved.getId(),
                    saved.getCustomerId());
            return Response.OK(configurationChecker.check(target, devices));
        } catch (SecurityException e) {
            return denied("check configuration #" + id);
        } catch (Exception e) {
            logger.error("Unexpected error when checking configuration #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Simulate OS upgrade rollout",
            notes = "Checks capability state changes and APK targetSdk blocks when upgrading from fromSdk to toSdk",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/rollout")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkRollout(@QueryParam("fromSdk") Integer fromSdk,
                                 @QueryParam("toSdk") Integer toSdk) {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("check OS rollout");
        }
        try {
            int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
            int from = fromSdk != null && fromSdk > 0 ? fromSdk : 29;
            int to = toSdk != null && toSdk > 0 ? toSdk : 35;
            return Response.OK(configurationChecker.checkRollout(customerId, from, to));
        } catch (Exception e) {
            logger.error("Unexpected error when checking rollout impact", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get fleet alert settings",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/alerts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAlerts() {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get fleet alert settings");
        }
        int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
        return Response.OK(profileService.getAlertSettings(customerId));
    }

    @POST
    @Path("/alerts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveAlerts(com.buttermdm.plugins.deviceprofile.persistence.domain.FleetAlertSettings settings,
                               @Context HttpServletRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_MANAGE)) {
            return denied("save fleet alert settings");
        }
        try {
            User user = SecurityContext.get().getCurrentUser().get();
            settings.setCustomerId(user.getCustomerId());
            profileService.saveAlertSettings(settings);
            auditLog.log("plugin.audit.action.deviceprofile.alerts", null, user,
                    "minSdk=" + settings.getMinSdkAlert() + ", maxPatchAgeMonths=" + settings.getMaxPatchAgeMonths(),
                    request);
            return Response.OK(profileService.getAlertSettings(user.getCustomerId()));
        } catch (Exception e) {
            logger.error("Unexpected error when saving fleet alert settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the capability catalogs",
            notes = "Gets the built-in catalog version, the active catalog version and the uploaded catalogs",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/catalogs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCatalogs() {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get the capability catalogs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("builtIn", catalogService.getBuiltIn().getCatalogVersion());
        result.put("active", catalogService.getActive().getCatalogVersion());
        result.put("uploaded", mapper.getCatalogs());
        result.put("canManage", canManageCatalogs());
        return Response.OK(result);
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get a capability catalog",
            notes = "Gets a catalog by its version (use the version of the built-in or the active catalog)",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/catalogs/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCatalog(@PathParam("version") @ApiParam("Catalog version") String version) {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get capability catalog " + version);
        }
        String content = catalogService.getContent(version);
        if (content == null) {
            return Response.ERROR("plugin.deviceprofile.error.catalog.notfound");
        }
        try {
            return Response.OK(CatalogService.parse(content));
        } catch (CatalogService.CatalogException e) {
            return Response.ERROR(e.getMessage());
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Upload a capability catalog",
            notes = "Validates and stores a catalog. It is used after it is activated. Super-admin (or single customer) only",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/catalogs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadCatalog(String content, @Context HttpServletRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_MANAGE) || !canManageCatalogs()) {
            return denied("upload a capability catalog");
        }
        try {
            Catalog catalog = CatalogService.parse(content);
            if (catalogService.getContent(catalog.getCatalogVersion()) != null) {
                return Response.ERROR("plugin.deviceprofile.error.catalog.exists");
            }
            User user = SecurityContext.get().getCurrentUser().get();
            CatalogRecord record = new CatalogRecord();
            record.setVersion(catalog.getCatalogVersion());
            record.setSchemaVersion(catalog.getSchemaVersion());
            record.setContent(content);
            record.setUploadedBy(user.getId());
            record.setUploadTime(System.currentTimeMillis());
            mapper.insertCatalog(record);
            auditLog.log("plugin.audit.action.deviceprofile.catalog.upload", null, user,
                    "Catalog: " + catalog.getCatalogVersion(), request);
            return Response.OK(Collections.singletonMap("version", catalog.getCatalogVersion()));
        } catch (CatalogService.CatalogException e) {
            return Response.ERROR(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when uploading a capability catalog", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Activate a capability catalog",
            notes = "Makes an uploaded catalog (or 'builtin' for the built-in one) the catalog used for all devices",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/catalogs/{version}/activate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response activateCatalog(@PathParam("version") @ApiParam("Catalog version or 'builtin'") String version,
                                    @Context HttpServletRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_MANAGE) || !canManageCatalogs()) {
            return denied("activate capability catalog " + version);
        }
        try {
            if (BUILT_IN.equals(version)) {
                mapper.deactivateCatalogs();
            } else {
                CatalogRecord record = mapper.getCatalog(version);
                if (record == null) {
                    return Response.ERROR("plugin.deviceprofile.error.catalog.notfound");
                }
                if (CatalogService.compareVersions(version, catalogService.getBuiltIn().getCatalogVersion()) < 0) {
                    return Response.ERROR("plugin.deviceprofile.error.catalog.older");
                }
                mapper.activateCatalog(version);
            }
            catalogService.reset();
            // Resolve the capabilities of all devices with the catalog now in use
            for (Integer customerId : customerIds()) {
                for (DeviceProfileRecord profile : mapper.getCustomerProfiles(customerId)) {
                    profileService.refreshCapabilities(profile);
                }
            }
            auditLog.log("plugin.audit.action.deviceprofile.catalog.activate", null,
                    SecurityContext.get().getCurrentUser().get(), "Catalog: " + version, request);
            return Response.OK(Collections.singletonMap("version", catalogService.getActive().getCatalogVersion()));
        } catch (Exception e) {
            logger.error("Unexpected error when activating capability catalog {}", version, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get the Android requirements of the applications",
            notes = "Gets the application versions of the customer with the API levels read from their APK files",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/applications")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApplications() {
        if (!SecurityContext.get().hasPermission(PERMISSION_VIEW)) {
            return denied("get the application requirements");
        }
        try {
            int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
            return Response.OK(mapper.getCustomerVersions(customerId));
        } catch (Exception e) {
            logger.error("Unexpected error when getting the application requirements", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Analyze the APK files",
            notes = "Reads the API levels and signing certificates of the APK files not analyzed yet (all with all=true)",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/applications/analyze")
    @Produces(MediaType.APPLICATION_JSON)
    public Response analyzeApplications(@QueryParam("all") @ApiParam("Analyze all versions again") Boolean all) {
        if (!SecurityContext.get().hasPermission(PERMISSION_MANAGE)) {
            return denied("analyze the APK files");
        }
        try {
            int count = 0;
            if (all != null && all) {
                int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
                for (AppVersionAnalysis version : mapper.getCustomerVersions(customerId)) {
                    apkAnalyzer.analyze(version);
                    count++;
                }
            } else {
                count = apkAnalyzer.analyzePending();
            }
            return Response.OK(count);
        } catch (Exception e) {
            logger.error("Unexpected error when analyzing the APK files", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Set the API levels of an application version",
            notes = "Overrides the lowest API level read from the APK and sets the highest one (null removes a value)",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/applications/{versionId}/overrides")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setOverrides(@PathParam("versionId") @ApiParam("Application version ID") Integer versionId,
                                 SdkOverrideRequest overrides,
                                 @Context HttpServletRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_MANAGE)) {
            return denied("set the API levels of application version #" + versionId);
        }
        try {
            AppVersionAnalysis version = mapper.getApplicationVersion(versionId);
            int customerId = SecurityContext.get().getCurrentUser().get().getCustomerId();
            if (version == null || (version.getCustomerId() != null && version.getCustomerId() != customerId)) {
                return Response.ERROR("plugin.deviceprofile.error.application.notfound");
            }
            apkAnalyzer.ensureAnalyzed(version);
            mapper.setOverrides(versionId, overrides.getMinSdk(), overrides.getMaxSdk());
            auditLog.log("plugin.audit.action.deviceprofile.app.overrides", null,
                    SecurityContext.get().getCurrentUser().get(), version.getPkg() + " " + version.getVersion() +
                            ": minSdk=" + overrides.getMinSdk() + ", maxSdk=" + overrides.getMaxSdk(), request);
            return Response.OK();
        } catch (Exception e) {
            logger.error("Unexpected error when setting the API levels of application version #{}", versionId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Request a setup check",
            notes = "Makes the device check its setup and report it (Push message setupCheck)",
            authorizations = {@Authorization("Bearer Token")}
    )
    @POST
    @Path("/devices/{deviceId}/setupCheck")
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestSetupCheck(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId,
                                      @Context HttpServletRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_MANAGE)) {
            return denied("request the setup check of device #" + deviceId);
        }
        try {
            Device device = deviceDAO.getDeviceById(deviceId);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            pushService.sendSimpleMessage(device.getId(), "setupCheck");
            auditLog.log("plugin.audit.action.deviceprofile.setupcheck", device,
                    SecurityContext.get().getCurrentUser().get(), null, request);
            return Response.OK();
        } catch (SecurityException e) {
            return denied("request the setup check of device #" + deviceId);
        } catch (Exception e) {
            logger.error("Unexpected error when requesting the setup check of device #{}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Create an enrollment script",
            notes = "Creates a script (sh or bat) installing Butter over ADB, making it the device owner and running " +
                    "the selected setup steps which need ADB",
            authorizations = {@Authorization("Bearer Token")}
    )
    @GET
    @Path("/enrollment/script")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEnrollmentScript(@QueryParam("configurationId") Integer configurationId,
                                        @QueryParam("format") String format,
                                        @QueryParam("steps") String steps,
                                        @Context HttpServletRequest request) {
        if (!SecurityContext.get().hasPermission(PERMISSION_MANAGE)) {
            return denied("create an enrollment script");
        }
        try {
            Configuration configuration = configurationDAO.getConfigurationById(configurationId);
            if (configuration == null) {
                return Response.ERROR("plugin.deviceprofile.error.configuration.notfound");
            }
            String url = null;
            for (AppVersionAnalysis version : mapper.getConfigurationVersions(configuration.getId())) {
                if (Application.DEFAULT_LAUNCHER_PACKAGE.equals(version.getPkg())) {
                    url = version.isSplit() ? (version.getUrlArm64() != null ? version.getUrlArm64()
                            : version.getUrlArmeabi()) : version.getUrl();
                }
            }
            List<String> selected = new ArrayList<>();
            if (steps != null) {
                for (String step : steps.split(",")) {
                    if (SCRIPT_STEPS.contains(step.trim())) {
                        selected.add(step.trim());
                    }
                }
            }
            boolean bat = "bat".equals(format);
            String script = EnrollmentScript.create(bat, configuration.getName(), url,
                    Application.DEFAULT_LAUNCHER_PACKAGE, catalogService.getActive(), selected,
                    new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", "butter-enroll-" + configuration.getId() + (bat ? ".bat" : ".sh"));
            result.put("content", script);
            result.put("apkAvailable", url != null);
            auditLog.log("plugin.audit.action.deviceprofile.enrollment.script", null,
                    SecurityContext.get().getCurrentUser().get(), "Configuration: " + configuration.getName() +
                            ", steps: " + selected, request);
            return Response.OK(result);
        } catch (SecurityException e) {
            return denied("create an enrollment script for configuration #" + configurationId);
        } catch (Exception e) {
            logger.error("Unexpected error when creating an enrollment script", e);
            return Response.INTERNAL_ERROR();
        }
    }

    /**
     * <p>The catalog is common to all customers: only a super-admin (or the administrator of a single-customer
     * installation) may change it.</p>
     */
    private boolean canManageCatalogs() {
        return SecurityContext.get().isSuperAdmin() || unsecureDAO.isSingleCustomer();
    }

    private List<Integer> customerIds() {
        List<Integer> result = new ArrayList<>();
        if (unsecureDAO.isSingleCustomer()) {
            result.add(SecurityContext.get().getCurrentUser().get().getCustomerId());
        } else {
            unsecureDAO.getAllCustomersUnsecure().forEach(customer -> result.add(customer.getId()));
        }
        return result;
    }

    static String patchAgeBucket(String securityPatch) {
        if (securityPatch == null || !securityPatch.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "unknown";
        }
        try {
            Date patch = new SimpleDateFormat("yyyy-MM-dd").parse(securityPatch);
            double months = (System.currentTimeMillis() - patch.getTime()) / (1000.0 * 60 * 60 * 24 * 30.44);
            if (months < 3) {
                return "0-3";
            } else if (months < 6) {
                return "3-6";
            } else if (months < 12) {
                return "6-12";
            }
            return "12+";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static Response denied(String what) {
        logger.error("Unauthorized attempt to {} by user {}", what, SecurityContext.get().getCurrentUserName());
        return Response.PERMISSION_DENIED();
    }
}
