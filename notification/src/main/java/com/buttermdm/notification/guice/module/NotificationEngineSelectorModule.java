package com.buttermdm.notification.guice.module;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.buttermdm.notification.PushSender;
import com.buttermdm.notification.PushSenderMqtt;
import com.buttermdm.notification.PushSenderPolling;

public class NotificationEngineSelectorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PushSender.class)
                .annotatedWith(Names.named("Polling"))
                .to(PushSenderPolling.class);
        bind(PushSender.class)
                .annotatedWith(Names.named("MQTT"))
                .to(PushSenderMqtt.class);
    }
}
