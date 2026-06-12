package com.bluenote.social.feed.infrastructure.mq;

import com.bluenote.social.feed.application.FeedApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeedRelationMqMessageHandler extends AbstractFeedMqMessageHandler {

    public FeedRelationMqMessageHandler(FeedApplicationService feedApplicationService, ObjectMapper objectMapper) {
        super("bluenote-feed-relation-consumer", List.of("relation-event"), feedApplicationService, objectMapper);
    }
}
