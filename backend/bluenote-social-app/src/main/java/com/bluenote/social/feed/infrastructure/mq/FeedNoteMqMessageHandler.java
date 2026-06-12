package com.bluenote.social.feed.infrastructure.mq;

import com.bluenote.social.feed.application.FeedApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeedNoteMqMessageHandler extends AbstractFeedMqMessageHandler {

    public FeedNoteMqMessageHandler(FeedApplicationService feedApplicationService, ObjectMapper objectMapper) {
        super("bluenote-feed-note-consumer", List.of("note-event"), feedApplicationService, objectMapper);
    }
}
