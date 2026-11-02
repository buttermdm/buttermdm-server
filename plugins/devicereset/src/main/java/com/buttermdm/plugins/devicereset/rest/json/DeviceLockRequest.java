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

package com.buttermdm.plugins.devicereset.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;

/**
 * <p>A DTO object for the device lock command.</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceLockRequest {

    @ApiModelProperty("A message displayed on the lock screen (optional)")
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
