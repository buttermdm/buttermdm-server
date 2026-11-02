package com.buttermdm.notification;

import com.buttermdm.notification.persistence.domain.PushMessage;

public interface PushSender {
    public void init();
    public int send(PushMessage message);
}
