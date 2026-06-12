package com.bluenote.order.infrastructure.mq;

import com.bluenote.order.application.OrderApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderSeckillMqMessageHandler extends AbstractOrderMqMessageHandler {

    public OrderSeckillMqMessageHandler(OrderApplicationService orderApplicationService, ObjectMapper objectMapper) {
        super("bluenote-order-seckill-consumer", List.of("order-seckill-task-event"), orderApplicationService, objectMapper);
    }
}
