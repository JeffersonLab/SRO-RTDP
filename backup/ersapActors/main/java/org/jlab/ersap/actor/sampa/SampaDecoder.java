package org.jlab.ersap.actor.sampa;

import org.jlab.ersap.engine.Engine;
import org.jlab.ersap.engine.EngineData;
import org.jlab.ersap.engine.EngineDataType;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ERSAP service that decodes raw SAMPA data.
 */
public class SampaDecoder implements Engine {

    private static final String DEBUG_KEY = "debug";
    private boolean debug = false;

    @Override
    public EngineData configure(EngineData input) {
        if (input.getMimeType().equals(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            JSONObject config = new JSONObject(source);
            if (config.has(DEBUG_KEY)) {
                debug = config.getBoolean(DEBUG_KEY);
            }
            if (debug) {
                System.out.println("SampaDecoder: Debug mode enabled");
            }
        }
        return null;
    }

    @Override
    public EngineData execute(EngineData input) {
        byte[] rawData = (byte[]) input.getData();
        
        if (debug) {
            System.out.println("SampaDecoder: Received " + rawData.length + " bytes");
        }

        // Decode the raw data
        SampaDecodedData decodedData = decodeData(rawData);
        
        if (debug) {
            System.out.println("SampaDecoder: Decoded data: " + decodedData);
        }

        // Create output data
        EngineData output = new EngineData();
        output.setData(SampaDataType.SAMPA_DECODED, decodedData);
        output.setDescription("Decoded SAMPA data");
        
        return output;
    }

    private SampaDecodedData decodeData(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Extract header information
        int frameId = buffer.getInt();
        long timestamp = buffer.getLong();
        
        // Extract channel data
        List<SampaDecodedData.SampaChannel> channels = new ArrayList<>();
        
        while (buffer.hasRemaining()) {
            int channelId = buffer.getInt();
            int fee = buffer.getInt();
            int numSamples = buffer.getInt();
            
            // Ensure we have enough data for the samples
            if (buffer.remaining() < numSamples * 4) {
                if (debug) {
                    System.out.println("SampaDecoder: Incomplete data for channel " + channelId);
                }
                break;
            }
            
            int[] samples = new int[numSamples];
            for (int i = 0; i < numSamples; i++) {
                samples[i] = buffer.getInt();
            }
            
            channels.add(new SampaDecodedData.SampaChannel(channelId, fee, samples));
        }
        
        return new SampaDecodedData(frameId, timestamp, channels);
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return Set.of(SampaDataType.SAMPA_STREAM);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return Set.of(SampaDataType.SAMPA_DECODED);
    }

    @Override
    public Set<String> getStates() {
        return Set.of();
    }

    @Override
    public String getDescription() {
        return "SAMPA data decoder service";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "ERSAP Team";
    }

    @Override
    public void reset() {
        // Nothing to reset
    }

    @Override
    public void destroy() {
        // Nothing to clean up
    }
} 