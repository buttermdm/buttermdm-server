package com.buttermdm.plugins.deviceprofile.apk;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.domain.Customer;
import com.buttermdm.plugins.deviceprofile.persistence.domain.AppVersionAnalysis;
import com.buttermdm.plugins.deviceprofile.persistence.mapper.DeviceProfileMapper;
import com.buttermdm.util.FileUtil;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.ApkSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.MessageDigest;
import java.util.List;

/**
 * <p>A service reading the Android requirements of the uploaded APK files: the lowest API level
 * (<code>minSdkVersion</code>), the target API level and the signing certificate. The results are stored per
 * application version, so each file is read once.</p>
 *
 * @author buttermdm
 */
@Singleton
public class ApkAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ApkAnalyzer.class);

    private final DeviceProfileMapper mapper;

    private final UnsecureDAO unsecureDAO;

    private final String filesDirectory;

    private final String baseUrl;

    /**
     * <p>Constructs new <code>ApkAnalyzer</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public ApkAnalyzer(DeviceProfileMapper mapper,
                       UnsecureDAO unsecureDAO,
                       @Named("files.directory") String filesDirectory,
                       @Named("base.url") String baseUrl) {
        this.mapper = mapper;
        this.unsecureDAO = unsecureDAO;
        this.filesDirectory = filesDirectory;
        this.baseUrl = baseUrl;
    }

    /**
     * <p>Analyzes an application version if it wasn't analyzed yet.</p>
     *
     * @param version an application version.
     * @return the version with the analysis.
     */
    public AppVersionAnalysis ensureAnalyzed(AppVersionAnalysis version) {
        if (version.getAnalyzed() == null) {
            analyze(version);
        }
        return version;
    }

    /**
     * <p>Analyzes all application versions not analyzed yet.</p>
     *
     * @return a number of analyzed versions.
     */
    public int analyzePending() {
        int count = 0;
        for (AppVersionAnalysis version : mapper.getUnanalyzedVersions()) {
            analyze(version);
            count++;
        }
        return count;
    }

    /**
     * <p>Analyzes the APK file of an application version and stores the result. Failures are stored as a result
     * too (<code>analyzed</code> = <code>false</code> with the reason).</p>
     *
     * @param version an application version.
     */
    public void analyze(AppVersionAnalysis version) {
        version.setAnalyzed(false);
        version.setMinSdk(null);
        version.setTargetSdk(null);
        version.setCertDigest(null);
        version.setAnalyzedAt(System.currentTimeMillis());
        try {
            File file = getLocalFile(version);
            if (file == null) {
                version.setDetail("The APK file is not stored on this server");
            } else if (!file.isFile()) {
                version.setDetail("The APK file was not found: " + file.getName());
            } else {
                try (ApkFile apkFile = new ApkFile(file)) {
                    ApkMeta meta = apkFile.getApkMeta();
                    version.setMinSdk(parseLevel(meta.getMinSdkVersion()));
                    version.setTargetSdk(parseLevel(meta.getTargetSdkVersion()));
                    version.setCertDigest(getCertificateDigest(file, apkFile));
                    version.setAnalyzed(true);
                    version.setDetail(null);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to analyze the APK of application version #{}", version.getApplicationVersionId(), e);
            version.setDetail("The APK file can't be read: " + e.getMessage());
        }
        mapper.saveAnalysis(version);
    }

    private File getLocalFile(AppVersionAnalysis version) {
        String url = version.getUrl();
        if (version.isSplit()) {
            url = version.getUrlArm64() != null ? version.getUrlArm64() : version.getUrlArmeabi();
        }
        if (url == null || url.isEmpty()) {
            return null;
        }
        Customer customer = version.getCustomerId() != null
                ? unsecureDAO.getCustomerByIdUnsecure(version.getCustomerId()) : null;
        if (customer == null) {
            customer = new Customer();
        }
        String path = FileUtil.translateURLToLocalFilePath(customer, url, baseUrl);
        if (path == null) {
            return null;
        }
        File directory = customer.getFilesDir() != null && !customer.getFilesDir().isEmpty()
                ? new File(filesDirectory, customer.getFilesDir()) : new File(filesDirectory);
        return new File(directory, path);
    }

    private static Integer parseLevel(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            // A preview codename instead of a number
            return null;
        }
    }

    /**
     * @return the SHA-256 digest of the first certificate of the v3/v2 signature, else of the v1 signature, or
     *         <code>null</code> if neither can be read.
     */
    private static String getCertificateDigest(File file, ApkFile apkFile) throws Exception {
        byte[] certificate = null;
        try {
            certificate = ApkSigningBlock.getFirstCertificate(file);
        } catch (Exception e) {
            logger.debug("Failed to read the APK Signing Block of {}", file.getName(), e);
        }
        if (certificate == null) {
            try {
                List<ApkSigner> signers = apkFile.getApkSingers();
                if (signers != null && !signers.isEmpty() && !signers.get(0).getCertificateMetas().isEmpty()) {
                    certificate = signers.get(0).getCertificateMetas().get(0).getData();
                }
            } catch (Exception e) {
                logger.debug("Failed to read the v1 signature of {}", file.getName(), e);
            }
        }
        if (certificate == null) {
            return null;
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
