package org.jlab.ersap.actor.pcap.engine;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.pcap.source.PcapFileReader;
import org.json.JSONObject;

/**
 * ERSAP source engine that reads network packet data directly from PCAP files.
 * This engine can be used as a data source in ERSAP data processing pipelines.
 */
public class PcapFileSourceEngine extends AbstractEventReaderService<PcapFileReader> {

    private static final Logger LOGGER = Logger.getLogger(PcapFileSourceEngine.class.getName());

    @Override
    protected PcapFileReader createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        try {
            String pcapFilePath = path.toAbsolutePath().toString();
            LOGGER.info("Creating PcapFileReader for file: " + pcapFilePath);

            // Create a new PcapFileReader for the specified file
            return new PcapFileReader(pcapFilePath);
        } catch (IOException | IllegalArgumentException e) {
            throw new EventReaderException("Error creating PcapFileReader: " + e.getMessage(), e);
        }
    }

    @Override
    protected void closeReader() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing PcapFileReader: " + e.getMessage(), e);
            }
        }
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        // Return maximum integer value to indicate an unlimited number of events
        // The actual number of events will be determined by the number of packets in
        // the PCAP file
        return Integer.MAX_VALUE;
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        if (reader == null) {
            throw new EventReaderException("PcapFileReader not initialized");
        }
        return reader.getByteOrder();
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        if (reader == null) {
            throw new EventReaderException("PcapFileReader not initialized");
        }

        try {
            byte[] packetData = reader.readNextPacket();
            if (packetData == null) {
                throw new EventReaderException("No more packets available in PCAP file");
            }
            return packetData;
        } catch (IOException e) {
            throw new EventReaderException("Error reading packet from PCAP file: " + e.getMessage(), e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        // Return byte array data type
        return EngineDataType.BYTES;
    }
}