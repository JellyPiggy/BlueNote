package com.bluenote.social.counter.infrastructure.mq;

import com.bluenote.social.counter.application.CounterApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CounterCommentMqMessageHandler extends AbstractCounterMqMessageHandler {

    public CounterCommentMqMessageHandler(
            CounterApplicationService counterApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-counter-comment-consumer",
                List.of("comment-event"),
                counterApplicationService,
                objectMapper
        );
    }
}
