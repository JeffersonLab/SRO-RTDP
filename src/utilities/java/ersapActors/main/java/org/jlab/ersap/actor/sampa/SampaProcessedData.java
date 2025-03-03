package org.jlab.ersap.actor.sampa;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents processed SAMPA data.
 */
public class SampaProcessedData {

    private final int frameId;
    private final long timestamp;
    private final List<ChannelResult> results;

    /**
     * Creates a new instance of processed SAMPA data.
     *
     * @param frameId   the frame ID
     * @param timestamp the timestamp
     * @param results   the list of channel results
     */
    public SampaProcessedData(int frameId, long timestamp, List<ChannelResult> results) {
        this.frameId = frameId;
        this.timestamp = timestamp;
        this.results = new ArrayList<>(results);
    }

    /**
     * Gets the frame ID.
     *
     * @return the frame ID
     */
    public int getFrameId() {
        return frameId;
    }

    /**
     * Gets the timestamp.
     *
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the list of channel results.
     *
     * @return the list of channel results
     */
    public List<ChannelResult> getResults() {
        return new ArrayList<>(results);
    }

    /**
     * Gets the size of the serialized data.
     *
     * @return the size in bytes
     */
    public int getSerializedSize() {
        int size = 12; // frameId (4) + timestamp (8)
        size += 4; // number of results (4)
        for (ChannelResult result : results) {
            size += result.getSerializedSize();
        }
        return size;
    }

    /**
     * Serializes the data to a byte buffer.
     *
     * @param buffer the buffer to write to
     */
    public void serialize(ByteBuffer buffer) {
        buffer.putInt(frameId);
        buffer.putLong(timestamp);
        buffer.putInt(results.size());
        for (ChannelResult result : results) {
            result.serialize(buffer);
        }
    }

    /**
     * Deserializes the data from a byte buffer.
     *
     * @param buffer the buffer to read from
     * @return the deserialized data
     */
    public static SampaProcessedData deserialize(ByteBuffer buffer) {
        int frameId = buffer.getInt();
        long timestamp = buffer.getLong();
        int numResults = buffer.getInt();
        List<ChannelResult> results = new ArrayList<>(numResults);
        for (int i = 0; i < numResults; i++) {
            results.add(ChannelResult.deserialize(buffer));
        }
        return new SampaProcessedData(frameId, timestamp, results);
    }

    @Override
    public String toString() {
        return "SampaProcessedData{" +
                "frameId=" + frameId +
                ", timestamp=" + timestamp +
                ", results=" + results.size() +
                '}';
    }

    /**
     * Represents the processing result for a SAMPA channel.
     */
    public static class ChannelResult {
        private final int channelId;
        private final int fee;
        private final double baseline;
        private final double amplitude;
        private final double peakTime;
        private final double integral;
        private final double signalToNoise;

        /**
         * Creates a new instance of a channel result.
         *
         * @param channelId     the channel ID
         * @param fee           the front-end electronics ID
         * @param baseline      the baseline value
         * @param amplitude     the amplitude value
         * @param peakTime      the peak time
         * @param integral      the integral value
         * @param signalToNoise the signal-to-noise ratio
         */
        public ChannelResult(int channelId, int fee, double baseline, double amplitude,
                             double peakTime, double integral, double signalToNoise) {
            this.channelId = channelId;
            this.fee = fee;
            this.baseline = baseline;
            this.amplitude = amplitude;
            this.peakTime = peakTime;
            this.integral = integral;
            this.signalToNoise = signalToNoise;
        }

        /**
         * Gets the channel ID.
         *
         * @return the channel ID
         */
        public int getChannelId() {
            return channelId;
        }

        /**
         * Gets the front-end electronics ID.
         *
         * @return the FEE ID
         */
        public int getFee() {
            return fee;
        }

        /**
         * Gets the baseline value.
         *
         * @return the baseline
         */
        public double getBaseline() {
            return baseline;
        }

        /**
         * Gets the amplitude value.
         *
         * @return the amplitude
         */
        public double getAmplitude() {
            return amplitude;
        }

        /**
         * Gets the peak time.
         *
         * @return the peak time
         */
        public double getPeakTime() {
            return peakTime;
        }

        /**
         * Gets the integral value.
         *
         * @return the integral
         */
        public double getIntegral() {
            return integral;
        }

        /**
         * Gets the signal-to-noise ratio.
         *
         * @return the signal-to-noise ratio
         */
        public double getSignalToNoise() {
            return signalToNoise;
        }

        /**
         * Gets the size of the serialized data.
         *
         * @return the size in bytes
         */
        public int getSerializedSize() {
            return 8 + 6 * 8; // channelId (4) + fee (4) + 6 doubles (8 * 6)
        }

        /**
         * Serializes the data to a byte buffer.
         *
         * @param buffer the buffer to write to
         */
        public void serialize(ByteBuffer buffer) {
            buffer.putInt(channelId);
            buffer.putInt(fee);
            buffer.putDouble(baseline);
            buffer.putDouble(amplitude);
            buffer.putDouble(peakTime);
            buffer.putDouble(integral);
            buffer.putDouble(signalToNoise);
        }

        /**
         * Deserializes the data from a byte buffer.
         *
         * @param buffer the buffer to read from
         * @return the deserialized data
         */
        public static ChannelResult deserialize(ByteBuffer buffer) {
            int channelId = buffer.getInt();
            int fee = buffer.getInt();
            double baseline = buffer.getDouble();
            double amplitude = buffer.getDouble();
            double peakTime = buffer.getDouble();
            double integral = buffer.getDouble();
            double signalToNoise = buffer.getDouble();
            return new ChannelResult(channelId, fee, baseline, amplitude,
                    peakTime, integral, signalToNoise);
        }

        @Override
        public String toString() {
            return "ChannelResult{" +
                    "channelId=" + channelId +
                    ", fee=" + fee +
                    ", baseline=" + baseline +
                    ", amplitude=" + amplitude +
                    ", peakTime=" + peakTime +
                    ", integral=" + integral +
                    ", signalToNoise=" + signalToNoise +
                    '}';
        }
    }
} 