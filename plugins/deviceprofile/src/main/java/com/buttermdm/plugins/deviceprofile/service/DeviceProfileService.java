package com.buttermdm.plugins.deviceprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.plugins.deviceprofile.catalog.CapabilityResolver;
import com.buttermdm.plugins.deviceprofile.catalog.Catalog;
import com.buttermdm.plugins.deviceprofile.catalog.CatalogService;
import com.buttermdm.plugins.deviceprofile.catalog.ResolvedCapability;
import com.buttermdm.plugins.deviceprofile.persistence.domain.CapabilityResult;
import com.buttermdm.plugins.deviceprofile.persistence.domain.DeviceProfileRecord;
import com.buttermdm.plugins.deviceprofile.persistence.domain.FleetAlertSettings;
import com.buttermdm.plugins.deviceprofile.persistence.domain.HistoryEvent;
import com.buttermdm.plugins.deviceprofile.persistence.domain.SetupStepState;
import com.buttermdm.plugins.deviceprofile.persistence.mapper.DeviceProfileMapper;
import com.buttermdm.plugins.deviceprofile.rest.json.DeviceReport;
import com.buttermdm.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>A service storing the profiles and reports of the devices and resolving their capabilities.</p>
 *
 * <p>No security checks are performed here: the callers authenticate the device (reports) or check that the device
 * belongs to the customer of the current user (console).</p>
 *
 * @author buttermdm
 */
@Singleton
public class DeviceProfileService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceProfileService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_PROFILE_LENGTH = 65536;

    private static final int MAX_DETAIL_LENGTH = 1000;

    public static final List<String> OUTCOMES = Arrays.asList("applied", "fallback", "skipped-unsupported", "failed",
            "needs-user", "needs-adb");

    public static final List<String> SETUP_STATES = Arrays.asList("done", "pending-user", "pending-adb",
            "not-applicable", "failed");

    private final DeviceProfileMapper mapper;

    private final CatalogService catalogService;

    @Inject(optional = true)
    private EmailService emailService;

    /**
     * <p>Constructs new <code>DeviceProfileService</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public DeviceProfileService(DeviceProfileMapper mapper, CatalogService catalogService) {
        this.mapper = mapper;
        this.catalogService = catalogService;
    }

    /**
     * <p>The changes caused by a new profile.</p>
     */
    public static class SaveResult {
        public boolean first;
        public boolean sdkChanged;
        public boolean fingerprintChanged;
    }

    /**
     * <p>Stores the profile sent by a device and resolves its capabilities.</p>
     *
     * <p>A profile with <code>firstReport</code> set (the first one after an enrollment) clears the results and setup
     * states of the device: they belong to its previous enrollment.</p>
     *
     * @param device a device.
     * @param profile a profile as sent by the device.
     * @return the changes caused by the profile.
     * @throws IllegalArgumentException if the profile is invalid.
     */
    public SaveResult saveProfile(Device device, JsonNode profile) {
        String json = profile.toString();
        if (!profile.isObject() || !profile.path("sdkInt").canConvertToInt() || profile.path("sdkInt").asInt() <= 0) {
            throw new IllegalArgumentException("The profile has no valid sdkInt");
        }
        if (json.length() > MAX_PROFILE_LENGTH) {
            throw new IllegalArgumentException("The profile is too large");
        }

        DeviceProfileRecord previous = mapper.getProfile(device.getId());
        boolean firstReport = profile.path("firstReport").asBoolean(false);
        if (firstReport || (previous != null && previous.getRetiredAt() != null)) {
            mapper.deleteResults(device.getId());
            mapper.deleteSetup(device.getId());
        }

        DeviceProfileRecord record = new DeviceProfileRecord();
        record.setDeviceId(device.getId());
        record.setSdkInt(profile.get("sdkInt").asInt());
        record.setRelease(text(profile, "release", 32));
        record.setSecurityPatch(text(profile, "securityPatch", 16));
        record.setManufacturer(text(profile, "manufacturer", 128));
        record.setBrand(text(profile, "brand", 128));
        record.setModel(text(profile, "model", 128));
        record.setFingerprint(text(profile, "fingerprint", 512));
        record.setAgentVersionCode(profile.path("agentVersionCode").canConvertToInt()
                ? profile.get("agentVersionCode").asInt() : null);
        record.setProfile(json);
        record.setUpdateTime(System.currentTimeMillis());
        record.setRetiredAt(null);

        Catalog catalog = catalogService.getActive();
        record.setCatalogVersion(catalog.getCatalogVersion());
        record.setCapabilities(toJson(resolve(profile, catalog, lastOutcomes(device.getId()))));
        mapper.saveProfile(record);

        SaveResult result = new SaveResult();
        if (previous == null || firstReport || previous.getRetiredAt() != null) {
            result.first = true;
            result.sdkChanged = true;
            addHistory(device.getId(), HistoryEvent.FIRST_PROFILE, describe(record));
        } else {
            result.sdkChanged = previous.getSdkInt() != record.getSdkInt();
            result.fingerprintChanged = !Objects.equals(previous.getFingerprint(), record.getFingerprint());
            if (result.sdkChanged || result.fingerprintChanged) {
                addHistory(device.getId(), HistoryEvent.OS_UPGRADED, describe(previous) + " -> " + describe(record));
            }
        }
        checkAndSendAlerts(device, record, previous, result);
        return result;
    }

    /**
     * <p>Stores the report of a device: outcomes and setup states. Outcomes contradicting the resolved capabilities
     * are recorded as rule mismatches; the capabilities are resolved again with the new outcomes.</p>
     *
     * @param device a device.
     * @param report a report sent by the device.
     */
    public void saveReport(Device device, DeviceReport report) {
        long now = System.currentTimeMillis();
        DeviceProfileRecord profile = mapper.getProfile(device.getId());
        Map<String, ResolvedCapability> resolved = new HashMap<>();
        if (profile != null) {
            for (ResolvedCapability capability : getCapabilities(profile)) {
                resolved.put(capability.getId(), capability);
            }
        }

        for (DeviceReport.Result item : report.getResults()) {
            if (item.getCapabilityId() == null || !OUTCOMES.contains(item.getOutcome())) {
                continue;
            }
            CapabilityResult result = new CapabilityResult();
            result.setDeviceId(device.getId());
            result.setCapabilityId(truncate(item.getCapabilityId(), 128));
            result.setOutcome(item.getOutcome());
            result.setStrategyId(truncate(item.getStrategyId(), 64));
            result.setDetail(truncate(item.getDetail(), MAX_DETAIL_LENGTH));
            String cfgVer = item.getConfigVersion() != null ? item.getConfigVersion() : report.getConfigVersion();
            result.setConfigVersion(truncate(cfgVer, 64));
            result.setUpdateTime(now);
            mapper.saveResult(result);

            ResolvedCapability expected = resolved.get(item.getCapabilityId());
            if (expected != null && isMismatch(expected, item.getOutcome())) {
                addHistory(device.getId(), HistoryEvent.RULE_MISMATCH, item.getCapabilityId() + ": expected " +
                        expected.getState() + " (" + expected.getEvidence() + "), device reported " +
                        item.getOutcome() + (item.getDetail() != null ? ": " + truncate(item.getDetail(), 200) : ""));
            }
        }

        Map<String, String> previousSetup = new HashMap<>();
        for (SetupStepState step : mapper.getSetup(device.getId())) {
            previousSetup.put(step.getStepId(), step.getState());
        }
        for (DeviceReport.Step item : report.getSetup()) {
            if (item.getStepId() == null || !SETUP_STATES.contains(item.getState())) {
                continue;
            }
            SetupStepState step = new SetupStepState();
            step.setDeviceId(device.getId());
            step.setStepId(truncate(item.getStepId(), 64));
            step.setState(item.getState());
            step.setDetail(truncate(item.getDetail(), MAX_DETAIL_LENGTH));
            step.setUpdateTime(now);
            mapper.saveSetupStep(step);
            String before = previousSetup.get(item.getStepId());
            if (before != null && !before.equals(item.getState())) {
                addHistory(device.getId(), HistoryEvent.SETUP_CHANGED, item.getStepId() + ": " + before + " -> " +
                        item.getState());
            }
        }

        if (profile != null) {
            refreshCapabilities(profile);
        }
    }

    /**
     * <p>Resolves the capabilities of a stored profile again (after new outcomes or a catalog change).</p>
     *
     * @param profile a stored profile.
     */
    public void refreshCapabilities(DeviceProfileRecord profile) {
        try {
            Catalog catalog = catalogService.getActive();
            JsonNode json = objectMapper.readTree(profile.getProfile());
            mapper.updateCapabilities(profile.getDeviceId(),
                    toJson(resolve(json, catalog, lastOutcomes(profile.getDeviceId()))), catalog.getCatalogVersion());
        } catch (Exception e) {
            logger.error("Failed to resolve the capabilities of device #{}", profile.getDeviceId(), e);
        }
    }

    /**
     * <p>Gets the resolved capabilities of a stored profile.</p>
     *
     * @param profile a stored profile.
     * @return the resolved capabilities, or an empty list if they can't be read.
     */
    public List<ResolvedCapability> getCapabilities(DeviceProfileRecord profile) {
        if (profile.getCapabilities() == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(profile.getCapabilities(), new TypeReference<List<ResolvedCapability>>() {});
        } catch (Exception e) {
            logger.error("Invalid stored capabilities of device #{}", profile.getDeviceId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * <p>Gets the profile of a device as a map (for the resolver and the application selection).</p>
     */
    public Map<String, Object> getProfileMap(DeviceProfileRecord profile) {
        try {
            return objectMapper.readValue(profile.getProfile(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<ResolvedCapability> resolve(JsonNode profile, Catalog catalog, Map<String, String> lastOutcomes) {
        Map<String, Object> map = objectMapper.convertValue(profile, new TypeReference<Map<String, Object>>() {});
        return CapabilityResolver.resolve(map, catalog, lastOutcomes);
    }

    private Map<String, String> lastOutcomes(int deviceId) {
        Map<String, String> result = new HashMap<>();
        for (CapabilityResult item : mapper.getResults(deviceId)) {
            result.put(item.getCapabilityId(), item.getOutcome());
        }
        return result;
    }

    /**
     * <p>An outcome contradicts the catalog if a supported capability failed, or a capability the catalog considers
     * unavailable by its rules was applied.</p>
     */
    private static boolean isMismatch(ResolvedCapability expected, String outcome) {
        if (ResolvedCapability.SUPPORTED.equals(expected.getState())) {
            return "failed".equals(outcome);
        }
        return ResolvedCapability.UNSUPPORTED.equals(expected.getState()) && "applied".equals(outcome) &&
                expected.getEvidence() != null && expected.getEvidence().startsWith("rule:");
    }

    private void addHistory(int deviceId, String type, String detail) {
        HistoryEvent event = new HistoryEvent();
        event.setDeviceId(deviceId);
        event.setEventType(type);
        event.setDetail(truncate(detail, MAX_DETAIL_LENGTH));
        event.setEventTime(System.currentTimeMillis());
        mapper.insertHistory(event);
    }

    private static String describe(DeviceProfileRecord profile) {
        List<String> parts = new ArrayList<>();
        parts.add("Android " + (profile.getRelease() != null ? profile.getRelease() : "?") + " (API " +
                profile.getSdkInt() + ")");
        if (profile.getSecurityPatch() != null) {
            parts.add("patch " + profile.getSecurityPatch());
        }
        if (profile.getFingerprint() != null) {
            parts.add(profile.getFingerprint());
        }
        return String.join(", ", parts);
    }

    private static String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? truncate(value.asText(), maxLength) : null;
    }

    private static String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public FleetAlertSettings getAlertSettings(int customerId) {
        FleetAlertSettings settings = mapper.getAlertSettings(customerId);
        return settings != null ? settings : FleetAlertSettings.defaults(customerId);
    }

    public void saveAlertSettings(FleetAlertSettings settings) {
        mapper.saveAlertSettings(settings);
    }

    private void checkAndSendAlerts(Device device, DeviceProfileRecord record, DeviceProfileRecord previous,
                                    SaveResult result) {
        try {
            FleetAlertSettings settings = getAlertSettings(device.getCustomerId());
            List<String> alerts = new ArrayList<>();
            if (settings.getMinSdkAlert() > 0 && record.getSdkInt() < settings.getMinSdkAlert()) {
                alerts.add("Device API level (" + record.getSdkInt() + ") is below threshold (" +
                        settings.getMinSdkAlert() + ")");
            }
            if (settings.getMaxPatchAgeMonths() > 0 && record.getSecurityPatch() != null &&
                    record.getSecurityPatch().matches("\\d{4}-\\d{2}-\\d{2}")) {
                Date patch = new SimpleDateFormat("yyyy-MM-dd").parse(record.getSecurityPatch());
                double months = (System.currentTimeMillis() - patch.getTime()) / (1000.0 * 60 * 60 * 24 * 30.44);
                if (months > settings.getMaxPatchAgeMonths()) {
                    alerts.add("Security patch (" + record.getSecurityPatch() + ") is older than " +
                            settings.getMaxPatchAgeMonths() + " months");
                }
            }
            if (!result.first && settings.isAlertOnOsUpgrade() && (result.sdkChanged || result.fingerprintChanged)) {
                alerts.add("OS upgrade detected: " + (previous != null ? describe(previous) : "?") + " -> " +
                        describe(record));
            }
            if (!alerts.isEmpty() && settings.getAlertEmail() != null && !settings.getAlertEmail().trim().isEmpty() &&
                    emailService != null && emailService.isConfigured()) {
                String subject = "ButterMDM Fleet Alert: Device " + device.getNumber();
                String body = "<p>Device <b>" + device.getNumber() + "</b> (" +
                        (record.getManufacturer() != null ? record.getManufacturer() + " " : "") +
                        (record.getModel() != null ? record.getModel() : "") + ") reported alerts:</p><ul><li>" +
                        String.join("</li><li>", alerts) + "</li></ul>";
                emailService.sendEmail(settings.getAlertEmail().trim(), subject, body);
            }
        } catch (Exception e) {
            logger.warn("Failed to evaluate fleet alerts for device #{}", device.getId(), e);
        }
    }

    private static String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
