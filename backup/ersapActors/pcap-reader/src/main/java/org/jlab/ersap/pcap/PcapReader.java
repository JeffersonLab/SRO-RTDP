package org.jlab.ersap.pcap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A simple utility to read and analyze PCAP files.
 */
public class PcapReader {

    // PCAP file header constants
    private static final int PCAP_HEADER_SIZE = 24;
    private static final int MAGIC_NUMBER = 0xa1b2c3d4;
    private static final int MAGIC_NUMBER_REVERSED = 0xd4c3b2a1;
    private static final int MAGIC_NUMBER_MODIFIED = 0x4d3cb2a1; // Special case for this file

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: PcapReader <pcap_file_path>");
            System.exit(1);
        }

        String pcapFilePath = args[0];
        File pcapFile = new File(pcapFilePath);

        if (!pcapFile.exists() || !pcapFile.isFile()) {
            System.err.println("Error: PCAP file does not exist or is not a regular file: " + pcapFilePath);
            System.exit(1);
        }

        System.out.println("Reading PCAP file: " + pcapFilePath);
        System.out.println("File size: " + pcapFile.length() + " bytes");

        try (FileInputStream fis = new FileInputStream(pcapFile)) {
            // Read PCAP file header
            byte[] headerBytes = new byte[PCAP_HEADER_SIZE];
            int bytesRead = fis.read(headerBytes);

            if (bytesRead < PCAP_HEADER_SIZE) {
                System.err.println("Error: PCAP file is too small to contain a valid header");
                System.exit(1);
            }

            ByteBuffer buffer = ByteBuffer.wrap(headerBytes);
            int magicNumber = buffer.getInt();

            System.out.println("Magic number: 0x" + Integer.toHexString(magicNumber));

            // Determine byte order based on magic number
            ByteOrder byteOrder;
            if (magicNumber == MAGIC_NUMBER) {
                byteOrder = ByteOrder.BIG_ENDIAN;
                System.out.println("PCAP file byte order: BIG_ENDIAN");
            } else if (magicNumber == MAGIC_NUMBER_REVERSED) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                System.out.println("PCAP file byte order: LITTLE_ENDIAN");
            } else if (magicNumber == MAGIC_NUMBER_MODIFIED) {
                // Special case for this file
                byteOrder = ByteOrder.LITTLE_ENDIAN;
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                System.out.println("PCAP file byte order: LITTLE_ENDIAN (Modified format)");
            } else {
                System.err.println("Warning: Unknown PCAP magic number: 0x" +
                        Integer.toHexString(magicNumber));
                System.out.println("Attempting to continue with LITTLE_ENDIAN byte order...");
                byteOrder = ByteOrder.LITTLE_ENDIAN;
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }

            // Read PCAP file header fields
            int versionMajor = buffer.getShort(4) & 0xFFFF;
            int versionMinor = buffer.getShort(6) & 0xFFFF;
            int timezone = buffer.getInt(8);
            int sigfigs = buffer.getInt(12);
            int snaplen = buffer.getInt(16);
            int network = buffer.getInt(20);

            System.out.println("PCAP version: " + versionMajor + "." + versionMinor);
            System.out.println("Timezone: " + timezone);
            System.out.println("Sigfigs: " + sigfigs);
            System.out.println("Snaplen: " + snaplen);
            System.out.println("Network: " + network);

            // Count packets
            int packetCount = 0;
            long totalPacketBytes = 0;

            // Read packet headers (16 bytes each) and count packets
            byte[] packetHeaderBytes = new byte[16];
            while (fis.read(packetHeaderBytes) == 16) {
                ByteBuffer packetBuffer = ByteBuffer.wrap(packetHeaderBytes).order(byteOrder);

                // Skip timestamp fields (8 bytes)
                packetBuffer.position(8);

                // Read packet length
                int includedLength = packetBuffer.getInt();
                int originalLength = packetBuffer.getInt();

                // Sanity check on packet length
                if (includedLength < 0 || includedLength > 65535) {
                    System.err.println("Warning: Invalid packet length: " + includedLength +
                            " at packet #" + (packetCount + 1));
                    break;
                }

                totalPacketBytes += includedLength;
                packetCount++;

                // Skip packet data
                fis.skip(includedLength);

                // Print progress every 10000 packets
                if (packetCount % 10000 == 0) {
                    System.out.println("Processed " + packetCount + " packets...");
                }
            }

            System.out.println("\nPCAP file summary:");
            System.out.println("Total packets: " + packetCount);
            System.out.println("Total packet data: " + totalPacketBytes + " bytes");
            System.out.println(
                    "Average packet size: " + (packetCount > 0 ? (totalPacketBytes / packetCount) : 0) + " bytes");

        } catch (IOException e) {
            System.err.println("Error reading PCAP file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}