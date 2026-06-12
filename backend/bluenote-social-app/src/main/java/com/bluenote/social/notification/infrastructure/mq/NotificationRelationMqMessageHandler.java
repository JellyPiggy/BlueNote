package com.bluenote.social.notification.infrastructure.mq;

import com.bluenote.social.notification.application.NotificationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationRelationMqMessageHandler extends AbstractNotificationMqMessageHandler {

    public NotificationRelationMqMessageHandler(
            NotificationApplicationService notificationApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-notification-relation-consumer",
                List.of("relation-event"),
                notificationApplicationService,
                objectMapper
        );
    }
}
