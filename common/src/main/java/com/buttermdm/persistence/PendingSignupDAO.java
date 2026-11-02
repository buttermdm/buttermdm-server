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
import com.buttermdm.persistence.domain.Group;
import com.buttermdm.persistence.domain.PendingSignup;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.persistence.mapper.DeviceMapper;
import com.buttermdm.persistence.mapper.PendingSignupMapper;
import com.buttermdm.security.SecurityContext;
import com.buttermdm.security.SecurityException;

import java.util.List;
import java.util.Optional;

/**
 * @author seva
 */
@Singleton
public class PendingSignupDAO {

    private final PendingSignupMapper mapper;

    @Inject
    public PendingSignupDAO(PendingSignupMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(PendingSignup data) {
        mapper.insert(data);
    }

    public PendingSignup getByEmail(String email) {
        return mapper.getByEmail(email);
    }

    public PendingSignup getByToken(String token) {
        return mapper.getByToken(token);
    }

    public void remove(String email) {
        mapper.remove(email);
    }
}
