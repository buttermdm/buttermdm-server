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

package com.buttermdm.plugins.devicelog.persistence.postgres.dao.domain;

import com.buttermdm.persistence.domain.CustomerData;
import com.buttermdm.plugins.devicelog.model.DeviceLogPluginSettings;

import java.util.ArrayList;

/**
 * <p>A device log settings record stored in <code>Postgres</code> database.</p>
 *
 * @author isv
 */
public class PostgresDeviceLogPluginSettings extends DeviceLogPluginSettings implements CustomerData {

    private static final long serialVersionUID = -6329947142252659953L;
    
    /**
     * <p>An ID of a setting record.</p>
     */
    private Integer id;

    /**
     * <p>An ID of a customer account which the record belongs to.</p>
     */
    private int customerId;

    /**
     * <p>Constructs new <code>PostgresDeviceLogPluginSettings</code> instance. This implementation does nothing.</p>
     */
    public PostgresDeviceLogPluginSettings() {
        setRules(new ArrayList<>());
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public int getCustomerId() {
        return customerId;
    }

    @Override
    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    /**
     * <p>Gets the unique identifier for this record within underlying persistence layer.</p>
     *
     * @return an identifier for this record.
     */
    @Override
    public String getIdentifier() {
        return getId() == null ? null : getId().toString();
    }

}
