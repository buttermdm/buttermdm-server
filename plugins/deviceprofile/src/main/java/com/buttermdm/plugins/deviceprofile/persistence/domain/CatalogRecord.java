package com.buttermdm.plugins.deviceprofile.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>A capability catalog stored on the server. The active one is delivered to the devices.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;

    private int schemaVersion;

    /**
     * <p>The catalog (JSON); not loaded in lists.</p>
     */
    private String content;

    private boolean active;

    private Integer uploadedBy;

    private long uploadTime;
}
