/*
 *
 * ButterMDM: Open Source Android MDM Software
 * https://github.com/ButterMDM
 *
 * Copyright (C) 2026 ButterMDM (https://github.com/ButterMDM)
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

package com.buttermdm.plugins.rustdesk.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * <p>Request payload for triggering a RustDesk action on a managed device.</p>
 *
 * @author buttermdm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustDeskActionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String action;
    private String rustdeskId;

    public RustDeskActionRequest() {
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getRustdeskId() {
        return rustdeskId;
    }

    public void setRustdeskId(String rustdeskId) {
        this.rustdeskId = rustdeskId;
    }
}
