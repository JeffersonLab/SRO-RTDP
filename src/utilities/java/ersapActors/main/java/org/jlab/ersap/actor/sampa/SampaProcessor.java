package org.jlab.ersap.actor.sampa;

import org.jlab.ersap.engine.Engine;
import org.jlab.ersap.engine.EngineData;
import org.jlab.ersap.engine.EngineDataType;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ERSAP service that processes decoded SAMPA data.
 */
public class SampaProcessor implements Engine {

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
                System.out.println("SampaProcessor: Debug mode enabled");
            }
        }
        return null;
    }

    @Override
    public EngineData execute(EngineData input) {
        SampaDecodedData decodedData = (SampaDecodedData) input.getData();
        
        if (debug) {
            System.out.println("SampaProcessor: Processing decoded data: " + decodedData);
        }

        // Process the decoded data
        SampaProcessedData processedData = processData(decodedData);
        
        if (debug) {
            System.out.println("SampaProcessor: Processed data: " + processedData);
        }

        // Create output data
        EngineData output = new EngineData();
        output.setData(SampaDataType.SAMPA_PROCESSED, processedData);
        output.setDescription("Processed SAMPA data");
        
        return output;
    }

    private SampaProcessedData processData(SampaDecodedData decodedData) {
        List<SampaProcessedData.ChannelResult> results = new ArrayList<>();
        
        for (SampaDecodedData.SampaChannel channel : decodedData.getChannels()) {
            int[] samples = channel.getSamples();
            
            // Calculate baseline (average of first 10 samples or all if less than 10)
            double baseline = calculateBaseline(samples);
            
            // Find peak and calculate amplitude
            int peakIndex = findPeakIndex(samples, baseline);
            double amplitude = samples[peakIndex] - baseline;
            
            // Calculate peak time (in sample units)
            double peakTime = peakIndex;
            
            // Calculate integral (sum of all samples above baseline)
            double integral = calculateIntegral(samples, baseline);
            
            // Calculate signal-to-noise ratio
            double noise = calculateNoise(samples, baseline);
            double signalToNoise = (noise > 0) ? amplitude / noise : 0;
            
            results.add(new SampaProcessedData.ChannelResult(
                    channel.getChannelId(),
                    channel.getFee(),
                    baseline,
                    amplitude,
                    peakTime,
                    integral,
                    signalToNoise
            ));
        }
        
        return new SampaProcessedData(decodedData.getFrameId(), decodedData.getTimestamp(), results);
    }

    private double calculateBaseline(int[] samples) {
        int numSamples = Math.min(10, samples.length);
        if (numSamples == 0) {
            return 0;
        }
        
        double sum = 0;
        for (int i = 0; i < numSamples; i++) {
            sum += samples[i];
        }
        return sum / numSamples;
    }

    private int findPeakIndex(int[] samples, double baseline) {
        int peakIndex = 0;
        double maxValue = Double.MIN_VALUE;
        
        for (int i = 0; i < samples.length; i++) {
            double value = samples[i] - baseline;
            if (value > maxValue) {
                maxValue = value;
                peakIndex = i;
            }
        }
        
        return peakIndex;
    }

    private double calculateIntegral(int[] samples, double baseline) {
        double integral = 0;
        
        for (int sample : samples) {
            double value = sample - baseline;
            if (value > 0) {
                integral += value;
            }
        }
        
        return integral;
    }

    private double calculateNoise(int[] samples, double baseline) {
        int numSamples = Math.min(10, samples.length);
        if (numSamples <= 1) {
            return 0;
        }
        
        double sumSquares = 0;
        for (int i = 0; i < numSamples; i++) {
            double diff = samples[i] - baseline;
            sumSquares += diff * diff;
        }
        
        return Math.sqrt(sumSquares / (numSamples - 1));
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return Set.of(SampaDataType.SAMPA_DECODED);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return Set.of(SampaDataType.SAMPA_PROCESSED);
    }

    @Override
    public Set<String> getStates() {
        return Set.of();
    }

    @Override
    public String getDescription() {
        return "SAMPA data processor service";
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