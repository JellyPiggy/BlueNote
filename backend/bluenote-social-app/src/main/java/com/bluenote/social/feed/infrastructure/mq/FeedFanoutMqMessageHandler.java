package com.bluenote.social.feed.infrastructure.mq;

import com.bluenote.social.feed.application.FeedApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeedFanoutMqMessageHandler extends AbstractFeedMqMessageHandler {

    public FeedFanoutMqMessageHandler(FeedApplicationService feedApplicationService, ObjectMapper objectMapper) {
        super("bluenote-feed-fanout-executor", List.of("feed-fanout-task-event"), feedApplicationService, objectMapper);
    }
}
