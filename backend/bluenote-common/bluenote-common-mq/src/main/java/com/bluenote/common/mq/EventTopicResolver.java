package com.bluenote.common.mq;

import java.util.Map;

public final class EventTopicResolver {

    private static final Map<String, String> DEFAULT_TOPICS = Map.ofEntries(
            Map.entry("UserRegistered", "auth-event"),
            Map.entry("UserLoggedIn", "auth-event"),
            Map.entry("UserLoggedOut", "auth-event"),
            Map.entry("UserProfileUpdated", "user-event"),
            Map.entry("UserStatusChanged", "user-event"),
            Map.entry("FileUploaded", "file-event"),
            Map.entry("FileBound", "file-event"),
            Map.entry("FileDeleted", "file-event"),
            Map.entry("NotePublished", "note-event"),
            Map.entry("NoteUpdated", "note-event"),
            Map.entry("NoteDeleted", "note-event"),
            Map.entry("NoteVisibilityChanged", "note-event"),
            Map.entry("NoteStatusChanged", "note-event"),
            Map.entry("NoteLiked", "interaction-event"),
            Map.entry("NoteUnliked", "interaction-event"),
            Map.entry("NoteCollected", "interaction-event"),
            Map.entry("NoteUncollected", "interaction-event"),
            Map.entry("UserFollowed", "relation-event"),
            Map.entry("UserUnfollowed", "relation-event"),
            Map.entry("CommentCreated", "comment-event"),
            Map.entry("CommentDeleted", "comment-event"),
            Map.entry("CommentLiked", "comment-event"),
            Map.entry("CommentUnliked", "comment-event"),
            Map.entry("CommentStatusChanged", "comment-event"),
            Map.entry("CounterDeltaCreated", "counter-delta-event"),
            Map.entry("CounterChanged", "counter-event"),
            Map.entry("CounterRebuilt", "counter-event"),
            Map.entry("FeedFanoutSubTaskCreated", "feed-fanout-task-event"),
            Map.entry("FeedDelivered", "feed-event"),
            Map.entry("FeedRebuilt", "feed-event"),
            Map.entry("NotificationCreated", "notification-event"),
            Map.entry("NotificationAggregated", "notification-event"),
            Map.entry("NotificationRead", "notification-event"),
            Map.entry("NotificationReadBatch", "notification-event"),
            Map.entry("NotificationDeleted", "notification-event"),
            Map.entry("ImMessageSent", "im-message-event"),
            Map.entry("ImMessageAcked", "im-message-event"),
            Map.entry("ImMessageRead", "im-message-event"),
            Map.entry("CouponSeckillAccepted", "order-seckill-task-event"),
            Map.entry("OrderTimeoutCheck", "order-timeout-event"),
            Map.entry("OrderCreated", "order-event"),
            Map.entry("OrderPaid", "order-event"),
            Map.entry("OrderClosed", "order-event"),
            Map.entry("OrderCancelled", "order-event"),
            Map.entry("CouponIssued", "order-event"),
            Map.entry("PushSendRequested", "push-request-event"),
            Map.entry("PushDelivered", "push-event"),
            Map.entry("PushFiltered", "push-event"),
            Map.entry("PushFailed", "push-event")
    );

    private EventTopicResolver() {
    }

    public static String resolve(String eventType, Map<String, String> overrides) {
        if (overrides != null && overrides.containsKey(eventType)) {
            return overrides.get(eventType);
        }
        return DEFAULT_TOPICS.get(eventType);
    }
}
