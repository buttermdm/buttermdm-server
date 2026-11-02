package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>An application version with its Android requirements: read from the APK file, or set by an administrator.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppVersionAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    private int applicationVersionId;

    private int applicationId;

    private String pkg;

    private String appName;

    private String version;

    private int versionCode;

    private String url;

    private boolean split;

    private String urlArmeabi;

    private String urlArm64;

    private Integer customerId;

    /**
     * <p><code>true</code> if the APK file was analyzed, <code>false</code> if the analysis failed or the file isn't
     * stored on this server, <code>null</code> if not analyzed yet.</p>
     */
    private Boolean analyzed;

    private Integer minSdk;

    private Integer targetSdk;

    /**
     * <p>A SHA-256 digest of the first signing certificate (hex).</p>
     */
    private String certDigest;

    private Integer minSdkOverride;

    private Integer maxSdkOverride;

    private String detail;

    private Long analyzedAt;

    /**
     * <p>Gets the lowest API level the version runs on: the administrator's value if set, else the APK's.</p>
     */
    public Integer getEffectiveMinSdk() {
        return minSdkOverride != null ? minSdkOverride : minSdk;
    }
}
