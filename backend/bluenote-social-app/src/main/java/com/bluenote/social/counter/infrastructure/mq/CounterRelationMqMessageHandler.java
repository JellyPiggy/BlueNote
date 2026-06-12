package com.bluenote.social.counter.infrastructure.mq;

import com.bluenote.social.counter.application.CounterApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CounterRelationMqMessageHandler extends AbstractCounterMqMessageHandler {

    public CounterRelationMqMessageHandler(
            CounterApplicationService counterApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-counter-relation-consumer",
                List.of("relation-event"),
                counterApplicationService,
                objectMapper
        );
    }
}
