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

package com.buttermdm.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.buttermdm.persistence.domain.Customer;
import com.buttermdm.persistence.domain.Settings;
import com.buttermdm.persistence.domain.UsageStats;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.persistence.mapper.CommonMapper;
import com.buttermdm.persistence.mapper.CustomerMapper;
import com.buttermdm.persistence.mapper.DeviceMapper;
import com.buttermdm.persistence.mapper.UsageStatsMapper;
import com.buttermdm.security.SecurityContext;
import com.buttermdm.security.SecurityException;

@Singleton
public class UsageStatsDAO {

    private final UsageStatsMapper mapper;

    @Inject
    public UsageStatsDAO(UsageStatsMapper mapper) {
        this.mapper = mapper;
    }

    public void insertUsageStats(UsageStats usageStats) {
        mapper.insert(usageStats);
    }
}
