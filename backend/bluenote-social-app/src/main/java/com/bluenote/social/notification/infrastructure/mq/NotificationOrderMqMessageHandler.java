package com.bluenote.social.notification.infrastructure.mq;

import com.bluenote.social.notification.application.NotificationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationOrderMqMessageHandler extends AbstractNotificationMqMessageHandler {

    public NotificationOrderMqMessageHandler(
            NotificationApplicationService notificationApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-notification-order-consumer",
                List.of("order-event"),
                notificationApplicationService,
                objectMapper
        );
    }
}
