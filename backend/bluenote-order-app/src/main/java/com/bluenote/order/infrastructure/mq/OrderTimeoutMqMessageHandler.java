package com.bluenote.order.infrastructure.mq;

import com.bluenote.order.application.OrderApplicationService;
import com.bluenote.order.infrastructure.mq.OrderConsumerExecutor.PoolKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutMqMessageHandler extends AbstractOrderMqMessageHandler {

    public OrderTimeoutMqMessageHandler(
            OrderApplicationService orderApplicationService,
            ObjectMapper objectMapper,
            OrderConsumerExecutor consumerExecutor
    ) {
        super(
                "bluenote-order-timeout-consumer",
                List.of("order-timeout-event"),
                orderApplicationService,
                objectMapper,
                consumerExecutor,
                PoolKind.TIMEOUT
        );
    }
}
