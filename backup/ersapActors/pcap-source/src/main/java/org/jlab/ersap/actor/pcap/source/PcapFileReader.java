package org.jlab.ersap.actor.pcap.source;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

/**
 * A class for reading network packet data directly from PCAP files.
 * This implementation supports standard PCAP files as well as the modified
 * format
 * found in the CLAS12 data files.
 */
public class PcapFileReader implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(PcapFileReader.class.getName());

    // PCAP file header constants
    private static final int PCAP_HEADER_SIZE = 24;
    private static final int PACKET_HEADER_SIZE = 16;
    private static final int MAGIC_NUMBER = 0xa1b2c3d4;
    private static final int MAGIC_NUMBER_REVERSED = 0xd4c3b2a1;
    private static final int MAGIC_NUMBER_MODIFIED = 0x4d3cb2a1; // Special case for CLAS12 files

    private final File pcapFile;
    private final FileInputStream fileInputStream;
    private final ByteOrder byteOrder;
    private final int versionMajor;
    private final int versionMinor;
    private final int snaplen;
    private final int network;

    private int packetCount = 0;
    private long totalBytes = 0;

    /**
     * Creates a new PcapFileReader for the specified PCAP file.
     * 
     * @param pcapFilePath the path to the PCAP file
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if the file is not a valid PCAP file
     */
    public PcapFileReader(String pcapFilePath) throws IOException {
        this.pcapFile = new File(pcapFilePath);

        if (!pcapFile.exists() || !pcapFile.isFile()) {
            throw new IllegalArgumentException("PCAP file does not exist or is not a regular file: " + pcapFilePath);
        }

        LOGGER.info("Opening PCAP file: " + pcapFilePath);
        LOGGER.info("File size: " + pcapFile.length() + " bytes");

        this.fileInputStream = new FileInputStream(pcapFile);

        // Read and parse the PCAP file header
        byte[] headerBytes = new byte[PCAP_HEADER_SIZE];
        int bytesRead = fileInputStream.read(headerBytes);

        if (bytesRead < PCAP_HEADER_SIZE) {
            throw new IllegalArgumentException("PCAP file is too small to contain a valid header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);
        int magicNumber = buffer.getInt();

        LOGGER.info("Magic number: 0x" + Integer.toHexString(magicNumber));

        // Determine byte order based on magic number
        if (magicNumber == MAGIC_NUMBER) {
            this.byteOrder = ByteOrder.BIG_ENDIAN;
            LOGGER.info("PCAP file byte order: BIG_ENDIAN");
        } else if (magicNumber == MAGIC_NUMBER_REVERSED) {
            this.byteOrder = ByteOrder.LITTLE_ENDIAN;
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            LOGGER.info("PCAP file byte order: LITTLE_ENDIAN");
        } else if (magicNumber == MAGIC_NUMBER_MODIFIED) {
            // Special case for CLAS12 files
            this.byteOrder = ByteOrder.LITTLE_ENDIAN;
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            LOGGER.info("PCAP file byte order: LITTLE_ENDIAN (Modified format)");
        } else {
            LOGGER.warning("Unknown PCAP magic number: 0x" + Integer.toHexString(magicNumber));
            LOGGER.warning("Attempting to continue with LITTLE_ENDIAN byte order...");
            this.byteOrder = ByteOrder.LITTLE_ENDIAN;
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        // Read PCAP file header fields
        this.versionMajor = buffer.getShort(4) & 0xFFFF;
        this.versionMinor = buffer.getShort(6) & 0xFFFF;
        int timezone = buffer.getInt(8);
        int sigfigs = buffer.getInt(12);
        this.snaplen = buffer.getInt(16);
        this.network = buffer.getInt(20);

        LOGGER.info("PCAP version: " + versionMajor + "." + versionMinor);
        LOGGER.info("Snaplen: " + snaplen);
        LOGGER.info("Network: " + network);
    }

    /**
     * Reads the next packet from the PCAP file.
     * 
     * @return a byte array containing the packet data, or null if end of file is
     *         reached
     * @throws IOException if an I/O error occurs
     */
    public byte[] readNextPacket() throws IOException {
        // Read packet header (16 bytes)
        byte[] packetHeaderBytes = new byte[PACKET_HEADER_SIZE];
        int headerBytesRead = fileInputStream.read(packetHeaderBytes);

        if (headerBytesRead < PACKET_HEADER_SIZE) {
            // End of file reached
            return null;
        }

        ByteBuffer packetBuffer = ByteBuffer.wrap(packetHeaderBytes).order(byteOrder);

        // Skip timestamp fields (8 bytes)
        packetBuffer.position(8);

        // Read packet length
        int includedLength = packetBuffer.getInt();
        int originalLength = packetBuffer.getInt();

        // Sanity check on packet length
        if (includedLength < 0 || includedLength > snaplen) {
            LOGGER.warning("Invalid packet length: " + includedLength +
                    " at packet #" + (packetCount + 1) +
                    " (exceeds snaplen: " + snaplen + ")");
            return null;
        }

        // Read packet data
        byte[] packetData = new byte[includedLength];
        int dataBytesRead = fileInputStream.read(packetData);

        if (dataBytesRead < includedLength) {
            LOGGER.warning("Incomplete packet data: expected " + includedLength +
                    " bytes, got " + dataBytesRead + " bytes");
            return null;
        }

        // Update statistics
        packetCount++;
        totalBytes += includedLength;

        // Log progress periodically
        if (packetCount % 10000 == 0) {
            LOGGER.info("Processed " + packetCount + " packets (" + totalBytes + " bytes)");
        }

        return packetData;
    }

    /**
     * Gets the byte order of the PCAP file.
     * 
     * @return the byte order
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    /**
     * Gets the total number of packets read so far.
     * 
     * @return the packet count
     */
    public int getPacketCount() {
        return packetCount;
    }

    /**
     * Gets the total number of bytes read so far.
     * 
     * @return the total bytes
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * Closes the PCAP file.
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        if (fileInputStream != null) {
            fileInputStream.close();
            LOGGER.info("PCAP file closed. Total packets: " + packetCount +
                    ", Total bytes: " + totalBytes);
        }
    }
}