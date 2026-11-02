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

package com.buttermdm.plugins.remotesupport;

import com.google.inject.Module;
import com.buttermdm.plugin.PluginConfiguration;
import com.buttermdm.plugins.remotesupport.guice.module.RemoteSupportLiquibaseModule;
import com.buttermdm.plugins.remotesupport.guice.module.RemoteSupportPersistenceModule;
import com.buttermdm.plugins.remotesupport.guice.module.RemoteSupportRestModule;

import com.buttermdm.plugins.remotesupport.session.RemoteSupportEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>A configuration for <code>Device Profile</code> plugin: Android versions, capabilities and setup status of
 * the devices, and the adaptation of the configurations to them.</p>
 *
 * @author buttermdm
 */
public class RemoteSupportPluginConfigurationImpl implements PluginConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSupportPluginConfigurationImpl.class);

    public static final String PLUGIN_ID = "remotesupport";

    private static boolean endpointRegistered;

    /**
     * <p>Constructs new <code>RemoteSupportPluginConfigurationImpl</code> instance. This implementation does nothing.</p>
     */
    public RemoteSupportPluginConfigurationImpl() {
    }

    /**
     * <p>Gets the unique identifier for this plugin. This is a sort of logical name for the plugin which is used widely
     * by <code>Plugin Platform</code>.</p>
     *
     * @return a plugin identifier.
     */
    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    /**
     * <p>Gets the root package for the classes comprising the plugin.</p>
     *
     * @return a fully-qualified name of the root package for plugin code.
     */
    @Override
    public String getRootPackage() {
        return "com.buttermdm.plugins.remotesupport";
    }

    /**
     * <p>Gets the list of modules to be used for initializing the plugin.</p>
     *
     * @param context a context for plugin usage.
     * @return a list of modules to be used for plugin initialization.
     */
    @Override
    public List<Module> getPluginModules(ServletContext context) {
        List<Module> modules = new ArrayList<>();

        modules.add(new RemoteSupportLiquibaseModule(context));
        modules.add(new RemoteSupportPersistenceModule(context));
        modules.add(new RemoteSupportRestModule());

        registerEndpoint(context);
        return modules;
    }

    /**
     * <p>Registers the WebSocket endpoint of the sessions. The web application descriptor disables annotation
     * scanning, so the endpoint is added programmatically while the application starts.</p>
     *
     * @param context a context for plugin usage.
     */
    private static synchronized void registerEndpoint(ServletContext context) {
        if (endpointRegistered) {
            return;
        }
        ServerContainer container = (ServerContainer) context.getAttribute(ServerContainer.class.getName());
        if (container == null) {
            logger.error("The servlet container has no WebSocket support: remote support sessions are not available");
            return;
        }
        try {
            container.addEndpoint(ServerEndpointConfig.Builder.create(RemoteSupportEndpoint.class,
                    RemoteSupportEndpoint.PATH).build());
            endpointRegistered = true;
        } catch (Exception e) {
            logger.error("Failed to register the remote support WebSocket endpoint", e);
        }
    }

}
