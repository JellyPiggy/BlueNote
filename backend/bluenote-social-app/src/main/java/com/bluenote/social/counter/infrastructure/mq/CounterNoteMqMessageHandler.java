package com.bluenote.social.counter.infrastructure.mq;

import com.bluenote.social.counter.application.CounterApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CounterNoteMqMessageHandler extends AbstractCounterMqMessageHandler {

    public CounterNoteMqMessageHandler(
            CounterApplicationService counterApplicationService,
            ObjectMapper objectMapper
    ) {
        super(
                "bluenote-counter-note-consumer",
                List.of("note-event", "interaction-event"),
                counterApplicationService,
                objectMapper
        );
    }
}
