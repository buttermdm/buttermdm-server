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
import com.buttermdm.persistence.domain.User;
import com.buttermdm.persistence.mapper.DeviceMapper;
import com.buttermdm.security.SecurityContext;
import com.buttermdm.security.SecurityException;

import java.util.List;
import java.util.Optional;

/**
 * @author isv
 */
@Singleton
public class GroupDAO extends AbstractDAO<Group> {

    private final DeviceMapper mapper;

    @Inject
    public GroupDAO(DeviceMapper mapper) {
        this.mapper = mapper;
    }

    public List<Group> getAllGroups() {
        return getListWithCurrentUser(currentUser -> this.mapper.getAllGroups(currentUser.getCustomerId(), currentUser.getId()));
    }

    public List<Group> getAllGroupsByValue(String value) {
        return getListWithCurrentUser(currentUser -> this.mapper.getAllGroupsByValue(currentUser.getCustomerId(), "%" + value + "%", currentUser.getId()));
    }

    public Group getGroupByName(String name) {
        Optional<User> currentUser = SecurityContext.get().getCurrentUser();
        if (currentUser.isPresent()) {
            return getSingleRecord(() -> this.mapper.getGroupByName(currentUser.get().getCustomerId(), name), SecurityException::onGroupAccessViolation);
        } else {
            throw SecurityException.onAnonymousAccess();
        }
    }

    public void insertGroup(Group group) {
        insertRecord(group, this.mapper::insertGroup);
    }

    public void updateGroup(Group group) {
        updateRecord(group, this.mapper::updateGroup, SecurityException::onGroupAccessViolation);
    }

    public void removeGroupById(Integer id) {
        updateById(
                id,
                this.mapper::getGroupById,
                group -> this.mapper.removeGroupById(group.getId()),
                SecurityException::onGroupAccessViolation
        );
    }

    public Long countDevicesByGroupId(Integer id) {
        return this.mapper.countDevicesByGroupId(id);
    }

    public Group getGroupById(Integer id) {
        return getSingleRecord(() -> this.mapper.getGroupById(id), SecurityException::onGroupAccessViolation);
    }

}
