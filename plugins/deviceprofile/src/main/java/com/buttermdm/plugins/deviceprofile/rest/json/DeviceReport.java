package com.buttermdm.plugins.deviceprofile.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>A report of a device: the outcomes of applying the capabilities and the states of the setup steps.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceReport {

    /**
     * <p>A version of the catalog the device resolved the capabilities with.</p>
     */
    private String catalogVersion;

    private String configVersion;

    private List<Result> results = new ArrayList<>();

    private List<Step> setup = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String capabilityId;
        private String outcome;
        private String strategyId;
        private String detail;
        private String configVersion;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Step {
        private String stepId;
        private String state;
        private String detail;
    }
}
