package com.bluenote.social.notification.infrastructure.mq;

import com.bluenote.social.notification.application.NotificationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationInteractionMqMessageHandler extends AbstractNotificationMqMessageHandler {

    public NotificationInteractionMqMessageHandler(
            NotificationApplicationService notificationApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-notification-interaction-consumer",
                List.of("interaction-event"),
                notificationApplicationService,
                objectMapper
        );
    }
}
