/*
 *
 * ButterMDM: Open Source Android MDM Software
 * ButterMDM (https://github.com/ButterMDM)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.buttermdm.plugins.remotesupport.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>A remote support session, kept for the history and the audit.</p>
 *
 * @author buttermdm
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteSessionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private int deviceId;

    /**
     * <p>A device number (filled in lists).</p>
     */
    private String deviceNumber;

    private int customerId;

    private Integer userId;

    private String userLogin;

    private boolean viewOnly;

    private long createdAt;

    private Long connectedAt;

    private Long endedAt;

    private String endReason;

    private long frames;

    private long bytes;
}
