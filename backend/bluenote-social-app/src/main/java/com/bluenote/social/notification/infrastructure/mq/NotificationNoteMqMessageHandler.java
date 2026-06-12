package com.bluenote.social.notification.infrastructure.mq;

import com.bluenote.social.notification.application.NotificationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationNoteMqMessageHandler extends AbstractNotificationMqMessageHandler {

    public NotificationNoteMqMessageHandler(
            NotificationApplicationService notificationApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-notification-note-consumer",
                List.of("note-event"),
                notificationApplicationService,
                objectMapper
        );
    }
}
