package org.jlab.ersap.actor.pcap.engine;

import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.pcap.source.PcapStreamReceiver;
import org.jlab.ersap.actor.pcap.source.StreamParameters;
import org.json.JSONObject;

/**
 * ERSAP engine for reading PCAP data from a socket.
 * This engine implements the Engine interface and provides
 * a way to read PCAP data from a socket connection.
 */
public class PcapSourceEngine implements Engine {

    private static final Logger LOGGER = Logger.getLogger(PcapSourceEngine.class.getName());

    // Define custom EngineDataType for text and bytes
    private static final EngineDataType TEXT = new EngineDataType("text/plain", null);
    private static final EngineDataType BYTES = new EngineDataType("binary/bytes", null);

    private PcapStreamReceiver reader;
    private boolean initialized = false;

    @Override
    public EngineData configure(EngineData engineData) {
        if (engineData.getMimeType().equals("application/json")) {
            String jsonString = (String) engineData.getData();
            JSONObject jsonObject = new JSONObject(jsonString);

            try {
                StreamParameters params = new StreamParameters();

                // Set default values
                params.setHost("localhost");
                params.setPort(9001);
                params.setByteOrder(ByteOrder.BIG_ENDIAN);
                params.setConnectionTimeout(5000);
                params.setReadTimeout(2000);
                params.setRingBufferSize(1024);
                params.setSocketBufferSize(16384);

                // Override with values from the configuration
                if (jsonObject.has("streamHost")) {
                    params.setHost(jsonObject.getString("streamHost"));
                }

                if (jsonObject.has("streamPort")) {
                    params.setPort(jsonObject.getInt("streamPort"));
                }

                if (jsonObject.has("connectionTimeout")) {
                    params.setConnectionTimeout(jsonObject.getInt("connectionTimeout"));
                }

                if (jsonObject.has("readTimeout")) {
                    params.setReadTimeout(jsonObject.getInt("readTimeout"));
                }

                if (jsonObject.has("ringBufferSize")) {
                    params.setRingBufferSize(jsonObject.getInt("ringBufferSize"));
                }

                if (jsonObject.has("socketBufferSize")) {
                    params.setSocketBufferSize(jsonObject.getInt("socketBufferSize"));
                }

                LOGGER.log(Level.INFO,
                        "Creating PcapStreamReceiver with host={0}, port={1}, ringBufferSize={2}, socketBufferSize={3}",
                        new Object[] { params.getHost(), params.getPort(), params.getRingBufferSize(),
                                params.getSocketBufferSize() });

                reader = new PcapStreamReceiver(params);
                initialized = true;

                EngineData result = new EngineData();
                result.setData(TEXT, "PcapSourceEngine configured successfully");
                return result;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error configuring PcapSourceEngine", e);
                EngineData result = new EngineData();
                result.setData(TEXT, "Error: " + e.getMessage());
                return result;
            }
        } else {
            LOGGER.warning("Invalid configuration data type: " + engineData.getMimeType());
            EngineData result = new EngineData();
            result.setData(TEXT, "Error: Invalid configuration data type");
            return result;
        }
    }

    @Override
    public EngineData execute(EngineData engineData) {
        if (!initialized) {
            EngineData result = new EngineData();
            result.setData(TEXT, "Error: Engine not initialized");
            return result;
        }

        try {
            Object event = reader.nextEvent();
            if (event == null) {
                EngineData result = new EngineData();
                result.setData(TEXT, "No more events available");
                return result;
            }

            EngineData result = new EngineData();
            result.setData(BYTES, event);
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error reading event", e);
            EngineData result = new EngineData();
            result.setData(TEXT, "Error: " + e.getMessage());
            return result;
        }
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        // Not implemented for this engine
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(EngineDataType.JSON);
        return types;
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(BYTES);
        return types;
    }

    @Override
    public Set<String> getStates() {
        return new HashSet<>();
    }

    @Override
    public String getDescription() {
        return "PCAP Source Engine: Reads PCAP data from a socket connection";
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
        if (reader != null) {
            reader.close();
        }
    }
}