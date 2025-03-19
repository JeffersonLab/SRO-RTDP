package org.jlab.epsci.ersap.actor.pcap.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.epsci.ersap.std.services.AbstractService;
import org.jlab.epsci.ersap.actor.pcap.source.MultiSocketSource;
import org.jlab.epsci.ersap.actor.pcap.source.StreamParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MultiSocketSourceEngine extends AbstractService {
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private MultiSocketSource source;
    private List<String> hosts;
    private List<Integer> ports;
    private int connectionTimeout;
    private int readTimeout;
    private int bufferSize;

    @Override
    public EngineData configure(EngineData input) {
        if (input.getMimeType().equals(EngineDataType.JSON.mimeType())) {
            String sourceStr = (String) input.getData();
            try {
                JSONObject config = new JSONObject(sourceStr);

                // Parse connection parameters
                JSONArray connections = config.getJSONArray("connections");
                hosts = new ArrayList<>();
                ports = new ArrayList<>();
                for (int i = 0; i < connections.length(); i++) {
                    JSONObject conn = connections.getJSONObject(i);
                    hosts.add(conn.getString("host"));
                    ports.add(conn.getInt("port"));
                }

                connectionTimeout = config.optInt("connection_timeout", 5000);
                readTimeout = config.optInt("read_timeout", 5000);
                bufferSize = config.optInt("buffer_size", DEFAULT_BUFFER_SIZE);

                // Create StreamParameters for each connection
                List<StreamParameters> parametersList = new ArrayList<>();
                for (int i = 0; i < hosts.size(); i++) {
                    StreamParameters params = new StreamParameters();
                    params.setHost(hosts.get(i));
                    params.setPort(ports.get(i));
                    params.setConnectionTimeout(connectionTimeout);
                    params.setReadTimeout(readTimeout);
                    parametersList.add(params);
                }

                // Initialize the source with the parameters
                source = new MultiSocketSource();
                try {
                    source.open(parametersList);
                } catch (IOException e) {
                    return buildErrorResponse("Failed to open connections: " + e.getMessage());
                }

                return null;
            } catch (JSONException e) {
                return buildErrorResponse("Invalid configuration format: " + e.getMessage());
            }
        }
        return buildErrorResponse("Invalid configuration mime type");
    }

    @Override
    public EngineData execute(EngineData input) {
        if (source == null || !source.isOpen()) {
            return buildErrorResponse("Source is not initialized or not open");
        }

        try {
            byte[] data = source.getNextEvent();
            if (data != null) {
                EngineData output = new EngineData();
                output.setData(EngineDataType.BYTES.mimeType(), ByteBuffer.wrap(data));
                return output;
            } else {
                return buildErrorResponse("No data available");
            }
        } catch (IOException e) {
            return buildErrorResponse("Error reading data: " + e.getMessage());
        }
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
        types.add(EngineDataType.BYTES);
        return types;
    }

    @Override
    public String getDescription() {
        return "PCAP stream source engine that reads from multiple sockets";
    }

    @Override
    public String getName() {
        return "PcapMultiSocketSource";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "JLAB EPSCI Group";
    }

    @Override
    public void reset() {
        if (source != null) {
            try {
                source.close();
            } catch (IOException e) {
                // Log error but continue
                System.err.println("Error closing source: " + e.getMessage());
            }
            source = null;
        }
    }

    @Override
    public void destroy() {
        reset();
    }

    private EngineData buildErrorResponse(String message) {
        EngineData error = new EngineData();
        error.setData(EngineDataType.STRING.mimeType(), message);
        error.setStatus(EngineStatus.ERROR);
        return error;
    }
}