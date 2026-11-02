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

package com.buttermdm.notification.guice.module;

import com.buttermdm.guice.module.AbstractPersistenceModule;

import javax.servlet.ServletContext;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>A module used for configuring the {@link org.apache.ibatis.session.SqlSessionFactory} to be used by the
 * persistence layer of the <code>Notification</code> sub-system.</p>
 *
 * @author isv
 */
public class NotificationPersistenceModule extends AbstractPersistenceModule {

    /**
     * <p>Constructs new <code>NotificationPersistenceModule</code> instance. This implementation does nothing.</p>
     */
    public NotificationPersistenceModule(ServletContext context) {
        super(context);
    }

    /**
     * <p>Gets the name of Java package containing the MyBatis mapper classes to be used by the persistence layer of the
     * application.</p>
     *
     * @return a fully-qualified name of package with MyBatis mapper classes.
     */
    @Override
    protected String getMapperPackageName() {
        return "com.buttermdm.notification.persistence.mapper";
    }

    /**
     * <p>Gets the name of Java package containing the Domain Object classes to be used by the persistence layer of the
     * application.</p>
     *
     * @return a fully-qualified name of package with Domain Object classes.
     */
    @Override
    protected String getDomainObjectsPackageName() {
        return "com.buttermdm.notification.persistence.domain";
    }
}
