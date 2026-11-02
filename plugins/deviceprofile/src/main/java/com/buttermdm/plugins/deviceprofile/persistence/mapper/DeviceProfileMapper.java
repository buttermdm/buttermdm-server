package com.buttermdm.plugins.deviceprofile.persistence.mapper;

import com.buttermdm.plugins.deviceprofile.persistence.domain.AppVersionAnalysis;
import com.buttermdm.plugins.deviceprofile.persistence.domain.CapabilityResult;
import com.buttermdm.plugins.deviceprofile.persistence.domain.CatalogRecord;
import com.buttermdm.plugins.deviceprofile.persistence.domain.DeviceProfileRecord;
import com.buttermdm.plugins.deviceprofile.persistence.domain.FleetAlertSettings;
import com.buttermdm.plugins.deviceprofile.persistence.domain.FleetDevice;
import com.buttermdm.plugins.deviceprofile.persistence.domain.HistoryEvent;
import com.buttermdm.plugins.deviceprofile.persistence.domain.SetupStepState;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>An ORM mapper for the domain objects of <code>Device Profile</code> plugin.</p>
 *
 * @author buttermdm
 */
public interface DeviceProfileMapper {

    // Profiles

    @Select("SELECT * FROM plugin_deviceprofile_profiles WHERE deviceId = #{deviceId}")
    DeviceProfileRecord getProfile(@Param("deviceId") int deviceId);

    @Insert("INSERT INTO plugin_deviceprofile_profiles (deviceId, sdkInt, release, securityPatch, manufacturer, brand, " +
            "model, fingerprint, agentVersionCode, profile, capabilities, catalogVersion, updateTime, retiredAt) " +
            "VALUES (#{deviceId}, #{sdkInt}, #{release}, #{securityPatch}, #{manufacturer}, #{brand}, #{model}, " +
            "#{fingerprint}, #{agentVersionCode}, #{profile}, #{capabilities}, #{catalogVersion}, #{updateTime}, #{retiredAt}) " +
            "ON CONFLICT (deviceId) DO UPDATE SET sdkInt = EXCLUDED.sdkInt, release = EXCLUDED.release, " +
            "securityPatch = EXCLUDED.securityPatch, manufacturer = EXCLUDED.manufacturer, brand = EXCLUDED.brand, " +
            "model = EXCLUDED.model, fingerprint = EXCLUDED.fingerprint, agentVersionCode = EXCLUDED.agentVersionCode, " +
            "profile = EXCLUDED.profile, capabilities = EXCLUDED.capabilities, " +
            "catalogVersion = EXCLUDED.catalogVersion, updateTime = EXCLUDED.updateTime, retiredAt = EXCLUDED.retiredAt")
    void saveProfile(DeviceProfileRecord profile);

    @Update("UPDATE plugin_deviceprofile_profiles SET retiredAt = #{ts} WHERE deviceId = #{deviceId}")
    int retireProfile(@Param("deviceId") int deviceId, @Param("ts") long ts);

    @Update("UPDATE plugin_deviceprofile_profiles SET capabilities = #{capabilities}, catalogVersion = #{catalogVersion} " +
            "WHERE deviceId = #{deviceId}")
    void updateCapabilities(@Param("deviceId") int deviceId, @Param("capabilities") String capabilities,
                            @Param("catalogVersion") String catalogVersion);

    @Select("SELECT p.* FROM plugin_deviceprofile_profiles p INNER JOIN devices d ON d.id = p.deviceId " +
            "WHERE d.customerId = #{customerId}")
    List<DeviceProfileRecord> getCustomerProfiles(@Param("customerId") int customerId);

    @Select("SELECT d.id AS deviceId, d.number, d.configurationId, c.name AS configurationName, p.sdkInt, p.release, " +
            "p.securityPatch, p.manufacturer, p.model, p.updateTime, p.catalogVersion, p.retiredAt, " +
            "(SELECT COUNT(*) FROM plugin_deviceprofile_setup s WHERE s.deviceId = d.id " +
            "  AND s.state IN ('pending-user', 'pending-adb', 'failed')) AS setupPending, " +
            "(SELECT COUNT(*) FROM plugin_deviceprofile_results r WHERE r.deviceId = d.id AND r.outcome = 'failed') AS failed " +
            "FROM devices d " +
            "LEFT JOIN configurations c ON c.id = d.configurationId " +
            "LEFT JOIN plugin_deviceprofile_profiles p ON p.deviceId = d.id " +
            "WHERE d.customerId = #{customerId} ORDER BY d.number")
    List<FleetDevice> getFleet(@Param("customerId") int customerId);

    // Results and setup

    @Select("SELECT * FROM plugin_deviceprofile_results WHERE deviceId = #{deviceId} ORDER BY capabilityId")
    List<CapabilityResult> getResults(@Param("deviceId") int deviceId);

    @Insert("INSERT INTO plugin_deviceprofile_results (deviceId, capabilityId, outcome, strategyId, detail, configVersion, updateTime) " +
            "VALUES (#{deviceId}, #{capabilityId}, #{outcome}, #{strategyId}, #{detail}, #{configVersion}, #{updateTime}) " +
            "ON CONFLICT (deviceId, capabilityId) DO UPDATE SET outcome = EXCLUDED.outcome, " +
            "strategyId = EXCLUDED.strategyId, detail = EXCLUDED.detail, configVersion = EXCLUDED.configVersion, " +
            "updateTime = EXCLUDED.updateTime")
    void saveResult(CapabilityResult result);

    @Delete("DELETE FROM plugin_deviceprofile_results WHERE deviceId = #{deviceId}")
    void deleteResults(@Param("deviceId") int deviceId);

    @Select("SELECT * FROM plugin_deviceprofile_setup WHERE deviceId = #{deviceId} ORDER BY stepId")
    List<SetupStepState> getSetup(@Param("deviceId") int deviceId);

    @Insert("INSERT INTO plugin_deviceprofile_setup (deviceId, stepId, state, detail, updateTime) " +
            "VALUES (#{deviceId}, #{stepId}, #{state}, #{detail}, #{updateTime}) " +
            "ON CONFLICT (deviceId, stepId) DO UPDATE SET state = EXCLUDED.state, detail = EXCLUDED.detail, " +
            "updateTime = EXCLUDED.updateTime")
    void saveSetupStep(SetupStepState step);

    @Delete("DELETE FROM plugin_deviceprofile_setup WHERE deviceId = #{deviceId}")
    void deleteSetup(@Param("deviceId") int deviceId);

    // Catalogs

    @Select("SELECT * FROM plugin_deviceprofile_catalog WHERE active")
    CatalogRecord getActiveCatalog();

    @Select("SELECT * FROM plugin_deviceprofile_catalog WHERE version = #{version}")
    CatalogRecord getCatalog(@Param("version") String version);

    @Select("SELECT version, schemaVersion, active, uploadedBy, uploadTime FROM plugin_deviceprofile_catalog " +
            "ORDER BY uploadTime DESC")
    List<CatalogRecord> getCatalogs();

    @Insert("INSERT INTO plugin_deviceprofile_catalog (version, schemaVersion, content, active, uploadedBy, uploadTime) " +
            "VALUES (#{version}, #{schemaVersion}, #{content}, FALSE, #{uploadedBy}, #{uploadTime})")
    void insertCatalog(CatalogRecord catalog);

    @Update("UPDATE plugin_deviceprofile_catalog SET active = (version = #{version})")
    void activateCatalog(@Param("version") String version);

    @Update("UPDATE plugin_deviceprofile_catalog SET active = FALSE")
    void deactivateCatalogs();

    // History

    @Insert("INSERT INTO plugin_deviceprofile_history (deviceId, eventType, detail, eventTime) " +
            "VALUES (#{deviceId}, #{eventType}, #{detail}, #{eventTime})")
    void insertHistory(HistoryEvent event);

    @Select("SELECT h.*, d.number AS deviceNumber FROM plugin_deviceprofile_history h " +
            "INNER JOIN devices d ON d.id = h.deviceId WHERE h.deviceId = #{deviceId} " +
            "ORDER BY h.eventTime DESC LIMIT #{limit}")
    List<HistoryEvent> getDeviceHistory(@Param("deviceId") int deviceId, @Param("limit") int limit);

    @Select("SELECT h.*, d.number AS deviceNumber FROM plugin_deviceprofile_history h " +
            "INNER JOIN devices d ON d.id = h.deviceId WHERE d.customerId = #{customerId} " +
            "ORDER BY h.eventTime DESC LIMIT #{limit}")
    List<HistoryEvent> getCustomerHistory(@Param("customerId") int customerId, @Param("limit") int limit);

    // Application versions

    String APP_VERSION_COLUMNS = "v.id AS applicationVersionId, v.applicationId, app.pkg, app.name AS appName, " +
            "v.version, v.versionCode, v.url, v.split, v.urlArmeabi, v.urlArm64, app.customerId, a.analyzed, a.minSdk, " +
            "a.targetSdk, a.certDigest, a.minSdkOverride, a.maxSdkOverride, a.detail, a.analyzedAt " +
            "FROM applicationVersions v INNER JOIN applications app ON app.id = v.applicationId " +
            "LEFT JOIN plugin_deviceprofile_appversions a ON a.applicationVersionId = v.id ";

    @Select("SELECT " + APP_VERSION_COLUMNS + "WHERE v.applicationId = #{applicationId} ORDER BY v.versionCode DESC, v.id DESC")
    List<AppVersionAnalysis> getApplicationVersions(@Param("applicationId") int applicationId);

    @Select("SELECT " + APP_VERSION_COLUMNS + "WHERE v.id = #{id}")
    AppVersionAnalysis getApplicationVersion(@Param("id") int id);

    @Select("SELECT " + APP_VERSION_COLUMNS + "WHERE app.type = 'app' AND a.applicationVersionId IS NULL")
    List<AppVersionAnalysis> getUnanalyzedVersions();

    @Select("SELECT " + APP_VERSION_COLUMNS + "WHERE app.type = 'app' AND " +
            "(app.customerId = #{customerId} OR app.customerId IS NULL) ORDER BY app.pkg, v.versionCode DESC")
    List<AppVersionAnalysis> getCustomerVersions(@Param("customerId") int customerId);

    @Select("SELECT " + APP_VERSION_COLUMNS +
            "INNER JOIN configurationApplications ca ON ca.applicationVersionId = v.id " +
            "WHERE ca.configurationId = #{configurationId} AND ca.action = 1 AND app.type = 'app'")
    List<AppVersionAnalysis> getConfigurationVersions(@Param("configurationId") int configurationId);

    @Insert("INSERT INTO plugin_deviceprofile_appversions (applicationVersionId, analyzed, minSdk, targetSdk, " +
            "certDigest, detail, analyzedAt) VALUES (#{applicationVersionId}, #{analyzed}, #{minSdk}, #{targetSdk}, " +
            "#{certDigest}, #{detail}, #{analyzedAt}) " +
            "ON CONFLICT (applicationVersionId) DO UPDATE SET analyzed = EXCLUDED.analyzed, minSdk = EXCLUDED.minSdk, " +
            "targetSdk = EXCLUDED.targetSdk, certDigest = EXCLUDED.certDigest, detail = EXCLUDED.detail, " +
            "analyzedAt = EXCLUDED.analyzedAt")
    void saveAnalysis(AppVersionAnalysis analysis);

    @Update("UPDATE plugin_deviceprofile_appversions SET minSdkOverride = #{minSdk}, maxSdkOverride = #{maxSdk} " +
            "WHERE applicationVersionId = #{id}")
    int setOverrides(@Param("id") int applicationVersionId, @Param("minSdk") Integer minSdk,
                     @Param("maxSdk") Integer maxSdk);

    // Fleet alert settings

    @Select("SELECT * FROM plugin_deviceprofile_alerts WHERE customerId = #{customerId}")
    FleetAlertSettings getAlertSettings(@Param("customerId") int customerId);

    @Insert("INSERT INTO plugin_deviceprofile_alerts (customerId, minSdkAlert, maxPatchAgeMonths, setupIncompleteDays, alertOnOsUpgrade, alertEmail) " +
            "VALUES (#{customerId}, #{minSdkAlert}, #{maxPatchAgeMonths}, #{setupIncompleteDays}, #{alertOnOsUpgrade}, #{alertEmail}) " +
            "ON CONFLICT (customerId) DO UPDATE SET minSdkAlert = EXCLUDED.minSdkAlert, " +
            "maxPatchAgeMonths = EXCLUDED.maxPatchAgeMonths, setupIncompleteDays = EXCLUDED.setupIncompleteDays, " +
            "alertOnOsUpgrade = EXCLUDED.alertOnOsUpgrade, alertEmail = EXCLUDED.alertEmail")
    void saveAlertSettings(FleetAlertSettings settings);
}
