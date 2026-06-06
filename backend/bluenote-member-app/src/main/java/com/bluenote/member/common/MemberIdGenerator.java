package com.bluenote.member.common;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MemberIdGenerator {

    private static final long EPOCH_MILLIS = 1_704_067_200_000L;
    private static final long WORKER_ID = 1L;
    private static final long WORKER_SHIFT = 12L;
    private static final long TIME_SHIFT = 22L;
    private static final long SEQUENCE_MASK = 4095L;

    private final AtomicLong lastMillis = new AtomicLong(-1L);
    private final AtomicLong sequence = new AtomicLong(0L);

    public synchronized long nextId() {
        long currentMillis = Instant.now().toEpochMilli();
        long last = lastMillis.get();
        if (currentMillis == last) {
            long nextSequence = sequence.incrementAndGet() & SEQUENCE_MASK;
            if (nextSequence == 0) {
                currentMillis = waitNextMillis(last);
            }
        } else {
            sequence.set(0L);
            lastMillis.set(currentMillis);
        }
        return ((currentMillis - EPOCH_MILLIS) << TIME_SHIFT) | (WORKER_ID << WORKER_SHIFT) | sequence.get();
    }

    private long waitNextMillis(long last) {
        long currentMillis = Instant.now().toEpochMilli();
        while (currentMillis <= last) {
            currentMillis = Instant.now().toEpochMilli();
        }
        lastMillis.set(currentMillis);
        sequence.set(0L);
        return currentMillis;
    }
}
