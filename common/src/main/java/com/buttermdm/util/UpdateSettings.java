package com.buttermdm.util;

// A variant for free limited version
// Will be copied by opensource-prod.bat
public class UpdateSettings {
    // Set to false for custom web builds
    public static boolean WEB_UPDATE_ENABLED = false;

    // Here CUSTOMER_DOMAIN template can be used, replaced with the customer's domain
    // In the future, this could be used to limit access to updates
    // Null: no update check (ButterMDM's manifest offers ButterMDM's apps, not ButterMDM's)
    public static String MANIFEST_URL = null;

    // Null: no usage statistics are sent
    public static String STAT_URL = null;

    // Set to different values for different build variants
    public static String WEB_SUFFIX = "os";

    public static String WEB_UPDATE_USERNAME = null;
    public static String WEB_UPDATE_PASSWORD = null;
}
