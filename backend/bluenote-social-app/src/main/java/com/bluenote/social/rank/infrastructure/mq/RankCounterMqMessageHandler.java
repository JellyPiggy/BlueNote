package com.bluenote.social.rank.infrastructure.mq;

import com.bluenote.social.rank.application.RankApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RankCounterMqMessageHandler extends AbstractRankMqMessageHandler {

    public RankCounterMqMessageHandler(RankApplicationService rankApplicationService, ObjectMapper objectMapper) {
        super("bluenote-rank-counter-consumer", List.of("counter-event"), rankApplicationService, objectMapper);
    }
}
