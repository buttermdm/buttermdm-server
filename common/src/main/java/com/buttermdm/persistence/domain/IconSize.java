/*
 *
 * ButterMDM: Open Source Android MDM Software
 * https://github.com/ButterMDM
 *
 * Copyright (C) 2019 ButterMDM (https://github.com/ButterMDM)
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

package com.buttermdm.persistence.domain;

/**
 * <p>An enumeration over the supported icon sizes.</p>
 *
 * @author isv
 */
public enum IconSize {
    SMALL("100"),
    MEDIUM("120"),
    LARGE("140");

    /**
     * <p>A string value transmitted between the server and the device to represent this item.</p>
     */
    private String transmittedValue;

    private IconSize(String transmittedValue) {
        this.transmittedValue = transmittedValue;
    }

    public String getTransmittedValue() {
        return transmittedValue;
    }}
