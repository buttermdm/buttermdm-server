package com.buttermdm.plugins.deviceprofile.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.plugins.deviceprofile.persistence.domain.CatalogRecord;
import com.buttermdm.plugins.deviceprofile.persistence.mapper.DeviceProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p>A service providing the capability catalogs: the catalog built into the plugin and the catalogs uploaded by
 * administrators, one of which may be active instead of the built-in one.</p>
 *
 * @author buttermdm
 */
@Singleton
public class CatalogService {

    private static final Logger logger = LoggerFactory.getLogger(CatalogService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern VERSION = Pattern.compile("\\d{4}\\.\\d{2}\\.\\d+");

    private static final List<String> STATES = Arrays.asList(ResolvedCapability.SUPPORTED, ResolvedCapability.REQUIRES,
            ResolvedCapability.DEGRADED, ResolvedCapability.UNSUPPORTED, ResolvedCapability.UNKNOWN);

    private final DeviceProfileMapper mapper;

    private final String builtInContent;

    private final Catalog builtIn;

    private String cachedVersion;

    private Catalog cachedCatalog;

    private String cachedContent;

    /**
     * <p>Constructs new <code>CatalogService</code> instance and loads the built-in catalog.</p>
     */
    @Inject
    public CatalogService(DeviceProfileMapper mapper) {
        this.mapper = mapper;
        try (InputStream in = CatalogService.class.getResourceAsStream("/catalog/capabilities.json")) {
            byte[] bytes = new byte[0];
            if (in != null) {
                bytes = readAll(in);
            }
            this.builtInContent = new String(bytes, StandardCharsets.UTF_8);
            this.builtIn = parse(this.builtInContent);
        } catch (IOException | CatalogException e) {
            throw new IllegalStateException("The built-in capability catalog can't be loaded", e);
        }
    }

    /**
     * <p>Gets the catalog built into the plugin.</p>
     *
     * @return the built-in catalog.
     */
    public Catalog getBuiltIn() {
        return builtIn;
    }

    /**
     * <p>Gets the catalog delivered to the devices: the active uploaded catalog, or the built-in one.</p>
     *
     * @return the active catalog.
     */
    public synchronized Catalog getActive() {
        loadActive();
        return cachedCatalog;
    }

    /**
     * <p>Gets the content of the active catalog.</p>
     *
     * @return the catalog as JSON.
     */
    public synchronized String getActiveContent() {
        loadActive();
        return cachedContent;
    }

    /**
     * <p>Gets the content of a catalog by its version.</p>
     *
     * @param version a catalog version.
     * @return the catalog as JSON or <code>null</code> if there is no such catalog.
     */
    public String getContent(String version) {
        if (builtIn.getCatalogVersion().equals(version)) {
            return builtInContent;
        }
        CatalogRecord record = mapper.getCatalog(version);
        return record != null ? record.getContent() : null;
    }

    /**
     * <p>Forgets the cached active catalog; called after a catalog was activated.</p>
     */
    public synchronized void reset() {
        cachedVersion = null;
        cachedCatalog = null;
        cachedContent = null;
    }

    private void loadActive() {
        CatalogRecord active = mapper.getActiveCatalog();
        if (active == null) {
            cachedVersion = builtIn.getCatalogVersion();
            cachedCatalog = builtIn;
            cachedContent = builtInContent;
            return;
        }
        if (active.getVersion().equals(cachedVersion)) {
            return;
        }
        try {
            cachedCatalog = parse(active.getContent());
            cachedContent = active.getContent();
            cachedVersion = active.getVersion();
        } catch (CatalogException e) {
            logger.error("The active capability catalog {} is invalid, using the built-in catalog", active.getVersion(), e);
            cachedVersion = builtIn.getCatalogVersion();
            cachedCatalog = builtIn;
            cachedContent = builtInContent;
        }
    }

    /**
     * <p>Parses and validates a catalog.</p>
     *
     * @param json a catalog as JSON.
     * @return the catalog.
     * @throws CatalogException if the catalog can't be parsed or is invalid.
     */
    public static Catalog parse(String json) throws CatalogException {
        Catalog catalog;
        try {
            catalog = objectMapper.readValue(json, Catalog.class);
        } catch (IOException e) {
            throw new CatalogException("Not a valid catalog JSON: " + e.getMessage());
        }
        if (catalog.getSchemaVersion() != Catalog.SCHEMA_VERSION) {
            throw new CatalogException("Unsupported schema version " + catalog.getSchemaVersion());
        }
        if (catalog.getCatalogVersion() == null || !VERSION.matcher(catalog.getCatalogVersion()).matches()) {
            throw new CatalogException("The catalog version must have the format YYYY.MM.N");
        }
        if (catalog.getCapabilities().isEmpty()) {
            throw new CatalogException("The catalog has no capabilities");
        }
        Set<String> ids = new HashSet<>();
        for (Catalog.Capability capability : catalog.getCapabilities()) {
            String id = capability.getId();
            if (id == null || id.isEmpty() || !ids.add(id)) {
                throw new CatalogException("Missing or duplicate capability ID: " + id);
            }
            if (capability.getMinSdk() <= 0 || capability.getStrategies().isEmpty()) {
                throw new CatalogException(id + ": minSdk and at least one strategy are required");
            }
            for (Catalog.Requirement requirement : capability.getRequires()) {
                if ((requirement.getProbe() == null) == (requirement.getAnyProbe() == null)) {
                    throw new CatalogException(id + ": a requirement needs either probe or anyProbe");
                }
                checkState(id, requirement.getState());
            }
            if (capability.getFallback() != null) {
                checkState(id, capability.getFallback().getState());
            }
            for (Catalog.VendorRule rule : capability.getVendorRules()) {
                checkState(id, rule.getState());
            }
        }
        Set<String> steps = new HashSet<>();
        for (Catalog.SetupStep step : catalog.getSetupSteps()) {
            if (step.getId() == null || !steps.add(step.getId())) {
                throw new CatalogException("Missing or duplicate setup step ID: " + step.getId());
            }
        }
        return catalog;
    }

    private static void checkState(String id, String state) throws CatalogException {
        if (state != null && !STATES.contains(state)) {
            throw new CatalogException(id + ": unknown state " + state);
        }
    }

    /**
     * <p>Compares two catalog versions (<code>YYYY.MM.N</code>).</p>
     *
     * @return a negative number, zero or a positive number if the first version is lower, equal or higher.
     */
    public static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * <p>An exception reporting an invalid catalog.</p>
     */
    public static class CatalogException extends Exception {
        public CatalogException(String message) {
            super(message);
        }
    }
}
