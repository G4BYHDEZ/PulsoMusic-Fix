package com.ut.pulsomusic;

import android.service.notification.NotificationListenerService;
import android.content.ComponentName;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.ut.pulsomusic.shared.Link;

public class ControlListener extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        if (!Link.COMMAND.equals(event.getPath())) return;
        if (MediaAccessService.instance != null) {
            MediaAccessService.instance.receive(event.getSourceNodeId(), Link.json(event.getData()));
        } else {
            NotificationListenerService.requestRebind(new ComponentName(this, MediaAccessService.class));
            Link.send(this, event.getSourceNodeId(), Link.STATE,
                    Link.object("title", "Activa el acceso en el teléfono", "artist", "Abre Pulso y permite acceso a notificaciones", "ready", false));
        }
    }
}
