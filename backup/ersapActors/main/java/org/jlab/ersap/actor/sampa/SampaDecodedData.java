package org.jlab.ersap.actor.sampa;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents decoded SAMPA data.
 */
public class SampaDecodedData {

    private final int frameId;
    private final long timestamp;
    private final List<SampaChannel> channels;

    /**
     * Creates a new instance of decoded SAMPA data.
     *
     * @param frameId   the frame ID
     * @param timestamp the timestamp
     * @param channels  the list of SAMPA channels
     */
    public SampaDecodedData(int frameId, long timestamp, List<SampaChannel> channels) {
        this.frameId = frameId;
        this.timestamp = timestamp;
        this.channels = new ArrayList<>(channels);
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
     * Gets the list of SAMPA channels.
     *
     * @return the list of SAMPA channels
     */
    public List<SampaChannel> getChannels() {
        return new ArrayList<>(channels);
    }

    /**
     * Gets the size of the serialized data.
     *
     * @return the size in bytes
     */
    public int getSerializedSize() {
        int size = 12; // frameId (4) + timestamp (8)
        size += 4; // number of channels (4)
        for (SampaChannel channel : channels) {
            size += channel.getSerializedSize();
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
        buffer.putInt(channels.size());
        for (SampaChannel channel : channels) {
            channel.serialize(buffer);
        }
    }

    /**
     * Deserializes the data from a byte buffer.
     *
     * @param buffer the buffer to read from
     * @return the deserialized data
     */
    public static SampaDecodedData deserialize(ByteBuffer buffer) {
        int frameId = buffer.getInt();
        long timestamp = buffer.getLong();
        int numChannels = buffer.getInt();
        List<SampaChannel> channels = new ArrayList<>(numChannels);
        for (int i = 0; i < numChannels; i++) {
            channels.add(SampaChannel.deserialize(buffer));
        }
        return new SampaDecodedData(frameId, timestamp, channels);
    }

    @Override
    public String toString() {
        return "SampaDecodedData{" +
                "frameId=" + frameId +
                ", timestamp=" + timestamp +
                ", channels=" + channels.size() +
                '}';
    }

    /**
     * Represents a SAMPA channel with samples.
     */
    public static class SampaChannel {
        private final int channelId;
        private final int fee;
        private final int[] samples;

        /**
         * Creates a new instance of a SAMPA channel.
         *
         * @param channelId the channel ID
         * @param fee       the front-end electronics ID
         * @param samples   the samples
         */
        public SampaChannel(int channelId, int fee, int[] samples) {
            this.channelId = channelId;
            this.fee = fee;
            this.samples = samples.clone();
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
         * Gets the samples.
         *
         * @return the samples
         */
        public int[] getSamples() {
            return samples.clone();
        }

        /**
         * Gets the size of the serialized data.
         *
         * @return the size in bytes
         */
        public int getSerializedSize() {
            return 12 + samples.length * 4; // channelId (4) + fee (4) + numSamples (4) + samples (4 * length)
        }

        /**
         * Serializes the data to a byte buffer.
         *
         * @param buffer the buffer to write to
         */
        public void serialize(ByteBuffer buffer) {
            buffer.putInt(channelId);
            buffer.putInt(fee);
            buffer.putInt(samples.length);
            for (int sample : samples) {
                buffer.putInt(sample);
            }
        }

        /**
         * Deserializes the data from a byte buffer.
         *
         * @param buffer the buffer to read from
         * @return the deserialized data
         */
        public static SampaChannel deserialize(ByteBuffer buffer) {
            int channelId = buffer.getInt();
            int fee = buffer.getInt();
            int numSamples = buffer.getInt();
            int[] samples = new int[numSamples];
            for (int i = 0; i < numSamples; i++) {
                samples[i] = buffer.getInt();
            }
            return new SampaChannel(channelId, fee, samples);
        }

        @Override
        public String toString() {
            return "SampaChannel{" +
                    "channelId=" + channelId +
                    ", fee=" + fee +
                    ", samples=" + samples.length +
                    '}';
        }
    }
} 