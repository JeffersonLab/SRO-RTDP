package org.jlab.ersap.actor.pcap.source;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class to monitor the status of a Disruptor ring buffer.
 * Provides metrics such as fill level, throughput, and consumer lag.
 */
public class RingBufferMonitor {

    private final RingBuffer<Event> ringBuffer;
    private final Sequence consumerSequence;

    private final AtomicLong totalEventsPublished = new AtomicLong(0);
    private final AtomicLong totalEventsConsumed = new AtomicLong(0);
    private final AtomicLong totalBytesPublished = new AtomicLong(0);

    private long lastPublishedSequence = 0;
    private long lastConsumedSequence = 0;
    private long lastTimestamp = System.nanoTime();

    /**
     * Constructor.
     * 
     * @param ringBuffer       the ring buffer to monitor
     * @param consumerSequence the consumer sequence to monitor
     */
    public RingBufferMonitor(RingBuffer<Event> ringBuffer, Sequence consumerSequence) {
        this.ringBuffer = ringBuffer;
        this.consumerSequence = consumerSequence;
    }

    /**
     * Updates the monitor with the current state of the ring buffer.
     * Should be called periodically to collect accurate metrics.
     * 
     * @param bytesPublished the number of bytes published since the last update
     */
    public void update(long bytesPublished) {
        long currentPublishedSequence = ringBuffer.getCursor();
        long currentConsumedSequence = consumerSequence.get();

        long eventsPublished = currentPublishedSequence - lastPublishedSequence;
        long eventsConsumed = currentConsumedSequence - lastConsumedSequence;

        totalEventsPublished.addAndGet(eventsPublished);
        totalEventsConsumed.addAndGet(eventsConsumed);
        totalBytesPublished.addAndGet(bytesPublished);

        lastPublishedSequence = currentPublishedSequence;
        lastConsumedSequence = currentConsumedSequence;
        lastTimestamp = System.nanoTime();
    }

    /**
     * Gets the current fill level of the ring buffer as a percentage.
     * 
     * @return the fill level as a percentage (0-100)
     */
    public double getFillLevelPercentage() {
        long capacity = ringBuffer.getBufferSize();
        long used = getUsedSlots();
        return (double) used / capacity * 100.0;
    }

    /**
     * Gets the number of used slots in the ring buffer.
     * 
     * @return the number of used slots
     */
    public long getUsedSlots() {
        long producerSequence = ringBuffer.getCursor();
        long consumerSequence = this.consumerSequence.get();
        return producerSequence - consumerSequence;
    }

    /**
     * Gets the number of available slots in the ring buffer.
     * 
     * @return the number of available slots
     */
    public long getAvailableSlots() {
        return ringBuffer.getBufferSize() - getUsedSlots();
    }

    /**
     * Gets the total number of events published to the ring buffer.
     * 
     * @return the total number of events published
     */
    public long getTotalEventsPublished() {
        return totalEventsPublished.get();
    }

    /**
     * Gets the total number of events consumed from the ring buffer.
     * 
     * @return the total number of events consumed
     */
    public long getTotalEventsConsumed() {
        return totalEventsConsumed.get();
    }

    /**
     * Gets the total number of bytes published to the ring buffer.
     * 
     * @return the total number of bytes published
     */
    public long getTotalBytesPublished() {
        return totalBytesPublished.get();
    }

    /**
     * Gets the consumer lag in number of events.
     * 
     * @return the consumer lag
     */
    public long getConsumerLag() {
        return ringBuffer.getCursor() - consumerSequence.get();
    }

    /**
     * Gets the throughput in events per second.
     * 
     * @param timeUnit the time unit for the throughput
     * @return the throughput in events per second
     */
    public double getPublishThroughput(TimeUnit timeUnit) {
        long now = System.nanoTime();
        long elapsedNanos = now - lastTimestamp;
        if (elapsedNanos <= 0) {
            return 0.0;
        }

        double eventsPerNano = (double) totalEventsPublished.get() / elapsedNanos;
        return eventsPerNano * TimeUnit.NANOSECONDS.convert(1, timeUnit);
    }

    /**
     * Gets the throughput in bytes per second.
     * 
     * @param timeUnit the time unit for the throughput
     * @return the throughput in bytes per second
     */
    public double getBytesThroughput(TimeUnit timeUnit) {
        long now = System.nanoTime();
        long elapsedNanos = now - lastTimestamp;
        if (elapsedNanos <= 0) {
            return 0.0;
        }

        double bytesPerNano = (double) totalBytesPublished.get() / elapsedNanos;
        return bytesPerNano * TimeUnit.NANOSECONDS.convert(1, timeUnit);
    }

    /**
     * Gets a summary of the ring buffer status.
     * 
     * @return a string containing the ring buffer status
     */
    @Override
    public String toString() {
        return String.format(
                "RingBuffer Status: Size=%d, Used=%d (%.2f%%), Available=%d, Lag=%d, " +
                        "Total Published=%d, Total Consumed=%d, " +
                        "Throughput=%.2f events/s (%.2f MB/s)",
                ringBuffer.getBufferSize(),
                getUsedSlots(),
                getFillLevelPercentage(),
                getAvailableSlots(),
                getConsumerLag(),
                getTotalEventsPublished(),
                getTotalEventsConsumed(),
                getPublishThroughput(TimeUnit.SECONDS),
                getBytesThroughput(TimeUnit.SECONDS) / (1024 * 1024));
    }
}