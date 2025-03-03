package org.jlab.ersap.actor.rtdp.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.jlab.ersap.actor.rtdp.source.MultiStreamReceiver;
import org.jlab.ersap.actor.rtdp.source.StreamParameters;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ERSAP engine for receiving data from multiple socket streams.
 * This engine extends AbstractEventReaderService to provide a standardized way to read events.
 */
public class MultiStreamSourceEngine extends AbstractEventReaderService<MultiStreamReceiver> {
    private static final Logger LOGGER = Logger.getLogger(MultiStreamSourceEngine.class.getName());

    @Override
    protected MultiStreamReceiver createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        try {
            // Get the aggregation strategy
            MultiStreamReceiver.AggregationStrategy strategy = MultiStreamReceiver.AggregationStrategy.ARRAY;
            if (jsonObject.has("aggregationStrategy")) {
                String strategyName = jsonObject.getString("aggregationStrategy");
                try {
                    strategy = MultiStreamReceiver.AggregationStrategy.valueOf(strategyName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("Invalid aggregation strategy: " + strategyName + 
                            ". Using default: " + strategy);
                }
            }
            
            // Get common parameters that apply to all streams
            int ringBufferSize = jsonObject.has("ringBufferSize") 
                    ? jsonObject.getInt("ringBufferSize") : 1024;
            int connectionTimeout = jsonObject.has("connectionTimeout") 
                    ? jsonObject.getInt("connectionTimeout") : 5000;
            int readTimeout = jsonObject.has("readTimeout") 
                    ? jsonObject.getInt("readTimeout") : 1000;
            
            // Get the array of stream configurations
            JSONArray streamsArray = jsonObject.getJSONArray("streams");
            int numStreams = streamsArray.length();
            
            if (numStreams == 0) {
                throw new EventReaderException("No streams configured");
            }
            
            LOGGER.info("Creating MultiStreamReceiver with " + numStreams + " streams");
            
            // Create parameters for each stream
            StreamParameters[] params = new StreamParameters[numStreams];
            
            for (int i = 0; i < numStreams; i++) {
                JSONObject streamConfig = streamsArray.getJSONObject(i);
                
                String host = streamConfig.getString("host");
                int port = streamConfig.getInt("port");
                int sourceId = streamConfig.has("sourceId") ? streamConfig.getInt("sourceId") : i;
                
                params[i] = new StreamParameters(host, port, sourceId);
                params[i].setRingBufferSize(ringBufferSize);
                params[i].setConnectionTimeout(connectionTimeout);
                params[i].setReadTimeout(readTimeout);
                
                // Override common parameters with stream-specific ones if provided
                if (streamConfig.has("ringBufferSize")) {
                    params[i].setRingBufferSize(streamConfig.getInt("ringBufferSize"));
                }
                if (streamConfig.has("connectionTimeout")) {
                    params[i].setConnectionTimeout(streamConfig.getInt("connectionTimeout"));
                }
                if (streamConfig.has("readTimeout")) {
                    params[i].setReadTimeout(streamConfig.getInt("readTimeout"));
                }
                
                LOGGER.info(String.format("Stream %d/%d: %s:%d (Source ID: %d)", 
                        i + 1, numStreams, params[i].getHost(), params[i].getPort(), params[i].getSourceId()));
            }
            
            // Create and return the MultiStreamReceiver
            return new MultiStreamReceiver(params, strategy);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create MultiStreamReceiver: " + e.getMessage(), e);
            throw new EventReaderException("Failed to create MultiStreamReceiver", e);
        }
    }

    @Override
    protected void closeReader() {
        if (reader != null) {
            reader.close();
        }
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        // For streaming sources, we don't know how many events there will be
        return Integer.MAX_VALUE;
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        return reader.getByteOrder();
    }

    @Override
    protected Object readEvent(int i) throws EventReaderException {
        return reader.nextEvent();
    }

    @Override
    protected EngineDataType getDataType() {
        return JavaObjectType.JOBJ;
    }
} 