package org.jlab.ersap.actor.pcap.source;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A simple test class to verify that the PcapFileReader works correctly.
 */
public class PcapSourceTest {
    private static final Logger LOGGER = Logger.getLogger(PcapSourceTest.class.getName());

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: PcapSourceTest <pcap-file>");
            System.exit(1);
        }

        String pcapFilePath = args[0];
        int maxPackets = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        try (PcapFileReader reader = new PcapFileReader(pcapFilePath)) {
            System.out.println("PCAP file opened successfully.");
            System.out.println("Reading packets...");

            int packetCount = 0;
            long startTime = System.currentTimeMillis();
            byte[] packetData;

            while ((packetData = reader.readNextPacket()) != null) {
                packetCount++;

                if (packetCount <= maxPackets) {
                    System.out.printf("Packet #%d: %d bytes%n", packetCount, packetData.length);

                    // Print first 16 bytes of the packet (Ethernet header)
                    if (packetData.length >= 14) {
                        System.out.print("Ethernet Header: ");
                        for (int i = 0; i < 14; i++) {
                            System.out.printf("%02X ", packetData[i] & 0xFF);
                        }
                        System.out.println();

                        // Extract EtherType (bytes 12-13)
                        int etherType = ((packetData[12] & 0xFF) << 8) | (packetData[13] & 0xFF);
                        System.out.printf("EtherType: 0x%04X%n", etherType);
                    }

                    System.out.println();
                }

                if (maxPackets > 0 && packetCount >= maxPackets) {
                    break;
                }
            }

            long endTime = System.currentTimeMillis();
            double elapsedSeconds = (endTime - startTime) / 1000.0;

            System.out.println("Test completed.");
            System.out.printf("Processed %d packets in %.2f seconds%n", packetCount, elapsedSeconds);
            System.out.printf("Processing rate: %.2f packets/second%n", packetCount / elapsedSeconds);
            System.out.printf("Total bytes: %d%n", reader.getTotalBytes());

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}