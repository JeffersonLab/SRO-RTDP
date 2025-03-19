package org.jlab.epsci.ersap.actor.pcap.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.epsci.ersap.std.services.AbstractService;
import org.json.JSONObject;

public class PcapSinkEngine extends AbstractService {
    private FileOutputStream outputStream;
    private Path outputDir;
    private String filePrefix;
    private String fileSuffix;
    private String serviceName;
    private String serviceDescription;

    @Override
    public EngineData configure(EngineData input) {
        String configStr = (String) input.getData();
        try {
            JSONObject config = new JSONObject(configStr);
            outputDir = Paths.get(config.getString("output_dir"));
            filePrefix = config.getString("file_prefix");
            fileSuffix = config.getString("file_suffix");

            // Create output directory if it doesn't exist
            File dir = outputDir.toFile();
            if (!dir.exists() && !dir.mkdirs()) {
                return buildErrorResponse("Failed to create output directory: " + outputDir);
            }

            // Open output file
            File file = outputDir.resolve(filePrefix + fileSuffix).toFile();
            try {
                outputStream = new FileOutputStream(file);
                return null;
            } catch (IOException e) {
                return buildErrorResponse("Failed to open output file: " + e.getMessage());
            }
        } catch (Exception e) {
            return buildErrorResponse("Failed to configure sink: " + e.getMessage());
        }
    }

    @Override
    public EngineData execute(EngineData input) {
        if (outputStream == null) {
            return buildErrorResponse("Sink is not configured");
        }

        ByteBuffer data = (ByteBuffer) input.getData();
        try {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            outputStream.write(bytes);
            outputStream.flush();
            return input;
        } catch (IOException e) {
            return buildErrorResponse("Failed to write data: " + e.getMessage());
        }
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        // Process a group of events
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(EngineDataType.BYTES);
        return types;
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(EngineDataType.STRING);
        return types;
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return serviceDescription != null ? serviceDescription
                : "PCAP stream sink engine that writes PCAP data to a file";
    }

    @Override
    public String getName() {
        return serviceName != null ? serviceName : "PcapSink";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "JLAB EPSCI Group";
    }

    private EngineData buildErrorResponse(String message) {
        EngineData error = new EngineData();
        error.setData(EngineDataType.STRING, message);
        error.setStatus(EngineStatus.ERROR);
        return error;
    }

    @Override
    public void reset() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public void destroy() {
        reset();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: PcapSinkEngine <name> <description>");
            System.exit(1);
        }

        PcapSinkEngine service = new PcapSinkEngine();
        service.serviceName = args[0];
        service.serviceDescription = args[1];
    }
}
