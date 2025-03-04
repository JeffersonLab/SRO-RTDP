package org.jlab.ersap.actor.rtdp.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.jlab.ersap.actor.rtdp.source.StreamParameters;
import org.jlab.ersap.actor.rtdp.source.StreamReceiver;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ERSAP engine for receiving data from a single socket stream.
 * This engine extends AbstractEventReaderService to provide a standardized way to read events.
 */
public class StreamSourceEngine extends AbstractEventReaderService<StreamReceiver> {
    private static final Logger LOGGER = Logger.getLogger(StreamSourceEngine.class.getName());

    @Override
    protected StreamReceiver createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        // Create default parameters
        StreamParameters params = new StreamParameters();
        
        // Get parameters from the ERSAP YAML configuration file
        try {
            if (jsonObject.has("streamHost")) {
                params.setHost(jsonObject.getString("streamHost"));
            }
            
            if (jsonObject.has("streamPort")) {
                params.setPort(jsonObject.getInt("streamPort"));
            }
            
            if (jsonObject.has("sourceId")) {
                params.setSourceId(jsonObject.getInt("sourceId"));
            }
            
            if (jsonObject.has("ringBufferSize")) {
                params.setRingBufferSize(jsonObject.getInt("ringBufferSize"));
            }
            
            if (jsonObject.has("connectionTimeout")) {
                params.setConnectionTimeout(jsonObject.getInt("connectionTimeout"));
            }
            
            if (jsonObject.has("readTimeout")) {
                params.setReadTimeout(jsonObject.getInt("readTimeout"));
            }
            
            LOGGER.info(String.format("Creating StreamReceiver for %s:%d (Source ID: %d)", 
                    params.getHost(), params.getPort(), params.getSourceId()));
            
            return new StreamReceiver(params);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create StreamReceiver: " + e.getMessage(), e);
            throw new EventReaderException("Failed to create StreamReceiver", e);
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