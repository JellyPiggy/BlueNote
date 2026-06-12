package com.bluenote.social.rank.infrastructure.mq;

import com.bluenote.social.rank.application.RankApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RankNoteMqMessageHandler extends AbstractRankMqMessageHandler {

    public RankNoteMqMessageHandler(RankApplicationService rankApplicationService, ObjectMapper objectMapper) {
        super("bluenote-rank-note-consumer", List.of("note-event"), rankApplicationService, objectMapper);
    }
}
