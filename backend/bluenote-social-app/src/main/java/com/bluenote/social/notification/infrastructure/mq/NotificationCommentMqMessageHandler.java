package com.bluenote.social.notification.infrastructure.mq;

import com.bluenote.social.notification.application.NotificationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationCommentMqMessageHandler extends AbstractNotificationMqMessageHandler {

    public NotificationCommentMqMessageHandler(
            NotificationApplicationService notificationApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-notification-comment-consumer",
                List.of("comment-event"),
                notificationApplicationService,
                objectMapper
        );
    }
}
