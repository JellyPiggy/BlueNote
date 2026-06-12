ALTER TABLE bluenote_order.order_consume_record
    MODIFY COLUMN event_id VARCHAR(128) NOT NULL;

ALTER TABLE bluenote_order.order_outbox_event
    MODIFY COLUMN event_id VARCHAR(128) NOT NULL;
