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

import com.buttermdm.event.ConfigurationUpdatedEvent;
import com.buttermdm.event.EventListener;
import com.buttermdm.event.EventType;
import com.buttermdm.persistence.domain.Device;
import com.buttermdm.persistence.mapper.DeviceMapper;
import com.buttermdm.service.DeviceStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * <p>A listener for {@link EventType#CONFIGURATION_UPDATED} events.</p>
 */
public class ConfigurationUpdatedEventListener implements EventListener<ConfigurationUpdatedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationUpdatedEventListener.class);

    private final DeviceMapper deviceMapper;
    private final DeviceStatusService deviceStatusService;

    /**
     * <p>Constructs new <code>ConfigurationUpdatedEventListener</code> instance. This implementation does nothing.</p>
     */
    public ConfigurationUpdatedEventListener(DeviceMapper deviceMapper, DeviceStatusService deviceStatusService) {
        this.deviceMapper = deviceMapper;
        this.deviceStatusService = deviceStatusService;
    }

    /**
     * <p>Handles the event.</p>
     *
     * @param event an event fired from the external source.
     */
    @Override
    public void onEvent(ConfigurationUpdatedEvent event) {
        final List<Device> configurationDevices = this.deviceMapper.getDeviceIdsBySoleConfigurationId(event.getConfigurationId());
        configurationDevices.forEach(device -> {
            try {
                this.deviceStatusService.recalcDeviceStatuses(device.getId());
            } catch (Exception e) {
                logger.warn("Failed to recalculate statuses for device: {}", device.getId(), e);
            }
        });
    }

    /**
     * <p>Gets the type of supported events.</p>
     *
     * @return a type of supported events.
     */
    @Override
    public EventType getSupportedEventType() {
        return EventType.CONFIGURATION_UPDATED;
    }
}
