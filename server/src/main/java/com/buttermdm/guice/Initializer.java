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

package com.buttermdm.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.servlet.GuiceServletContextListener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import com.buttermdm.guice.module.*;
import com.buttermdm.notification.guice.module.*;
import com.buttermdm.plugin.PluginList;
import com.buttermdm.plugin.PluginTaskModule;
import com.buttermdm.plugin.guice.module.PluginLiquibaseModule;
import com.buttermdm.plugin.guice.module.PluginPersistenceModule;
import com.buttermdm.plugin.guice.module.PluginPlatformTaskModule;
import com.buttermdm.plugin.guice.module.PluginRestModule;

public final class Initializer extends GuiceServletContextListener {
    private ServletContext context;
    private Injector injector;

    public Initializer() {
    }

    protected Injector getInjector() {
        boolean success = false;

        final StringWriter errorOut = new StringWriter();
        PrintWriter errorWriter = new PrintWriter(errorOut);
        try {
            this.injector = Guice.createInjector(Stage.PRODUCTION, this.getModules());
            success = true;
        } catch (Exception e){
            System.err.println("[BUTTERMDM-INITIALIZER]: Unexpected error during injector initialization: " + e);
            e.printStackTrace();
            e.printStackTrace(errorWriter);
        }
        if (success) {
            System.out.println("[BUTTERMDM-INITIALIZER]: Application initialization was successful");
            onInitializationCompletion(null);
        } else {
            System.out.println("[BUTTERMDM-INITIALIZER]: Application initialization has failed");
            onInitializationCompletion(errorOut);
        }
        return injector;
    }

    /**
     * <p>Signals on application initialization completion.</p>
     */
    private void onInitializationCompletion(StringWriter errorOut) {

        final String signalFilePath = this.context.getInitParameter("initialization.completion.signal.file");
        if (signalFilePath != null && !signalFilePath.trim().isEmpty()) {
            File signalFile  = new File(signalFilePath);
            if (!signalFile.exists()) {
                try {
                    FileWriter fw = new FileWriter(signalFile);
                    PrintWriter pw = new PrintWriter(fw);
                    if (errorOut == null) {
                        pw.print("OK");
                    } else {
                        pw.print(errorOut.toString());
                    }
                    pw.close();
                    fw.close();
                    System.out.println("[BUTTERMDM-INITIALIZER]: Created a signal file for application " +
                            "initialization completion: " + signalFile.getAbsolutePath());
                } catch (IOException e) {
                    System.err.println("[BUTTERMDM-INITIALIZER]: Failed to create and write to signal file '" + signalFile.getAbsolutePath()
                            + "' for application initialization completion" + e);
                }
            } else {
                System.out.println("[BUTTERMDM-INITIALIZER]: The signal file for application initialization completion " +
                        "already exists: " + signalFile.getAbsolutePath());
            }
        } else {
            System.out.println("Could not find 'initialization.completion.signal.file' parameter in context. " +
                    "Signaling on application initialization completion will be skipped.");
        }
    }

    public void contextInitialized(ServletContextEvent servletContextEvent) {
        this.context = servletContextEvent.getServletContext();

        String log4jConfig = context.getInitParameter("log4j.config");
        if (log4jConfig != null && !log4jConfig.isEmpty()) {
            System.out.println("[BUTTERMDM-LOGGING] : Using log4j configuration from: " + log4jConfig);
            System.setProperty("log4j.configuration", log4jConfig);
            System.setProperty("log4j.ignoreTCL", "true");
        } else {
            System.out.println("[BUTTERMDM-LOGGING] Using log4j configuration from build");
        }

        super.contextInitialized(servletContextEvent);

        initTasks();
    }

    private List<Module> getModules() {
        List<Module> modules = new LinkedList<>();
        modules.add(new PersistenceModule(this.context));
        modules.add(new LiquibaseModule(this.context));
        modules.add(new ConfigureModule(this.context));
        modules.add(new MainRestModule());
        modules.add(new PublicRestModule());
        modules.add(new PrivateRestModule());
        modules.add(new NotificationPersistenceModule(this.context));
        modules.add(new NotificationLiquibaseModule(this.context));
        modules.add(new NotificationRestModule());
        modules.add(new NotificationEngineSelectorModule());
        modules.add(new NotificationMqttConfigModule(this.context));
        modules.add(new PluginPersistenceModule(this.context));
        modules.add(new PluginLiquibaseModule(this.context));
        modules.add(new PluginRestModule());

        PluginList.init(this.context);

        modules.addAll(PluginList.getPluginModules());

        return modules;
    }

    private void initTasks() {
        final NotificationTaskModule notificationTaskModule = this.injector.getInstance(NotificationTaskModule.class);
        notificationTaskModule.init();

        final NotificationMqttTaskModule notificationMqttTaskModule = this.injector.getInstance(NotificationMqttTaskModule.class);
        notificationMqttTaskModule.init();

        final PluginPlatformTaskModule pluginPlatformTaskModule = this.injector.getInstance(PluginPlatformTaskModule.class);
        pluginPlatformTaskModule.init();

        final List<Class<? extends PluginTaskModule>> pluginTaskModules = PluginList.getPluginTaskModules();
        if (pluginTaskModules != null) {
            pluginTaskModules.forEach(clazz -> {
                try {
                    final PluginTaskModule pluginTaskModule = this.injector.getInstance(clazz);
                    pluginTaskModule.init();
                } catch (Exception e) {
                    System.err.println("Failed to instantiate and initialize plugin task module '"
                            + clazz.getName() + "': " + e);
                }
            });
        }

        final EventListenerModule eventListenerModule = this.injector.getInstance(EventListenerModule.class);
        eventListenerModule.init();

        final StartupTaskModule startupTaskModule = this.injector.getInstance(StartupTaskModule.class);
        startupTaskModule.init();
    }
}
