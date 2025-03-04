import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Generates a test PCAP file with dummy packets for testing.
 */
public class GenerateTestPcap {
    // PCAP file format constants
    private static final int PCAP_MAGIC = 0xa1b2c3d4;
    private static final short PCAP_VERSION_MAJOR = 2;
    private static final short PCAP_VERSION_MINOR = 4;
    private static final int PCAP_THISZONE = 0;
    private static final int PCAP_SIGFIGS = 0;
    private static final int PCAP_SNAPLEN = 65535;
    private static final int PCAP_NETWORK = 1; // Ethernet

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java GenerateTestPcap <output_file> <num_packets>");
            System.exit(1);
        }

        String outputFile = args[0];
        int numPackets = Integer.parseInt(args[1]);

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(outputFile))) {
            // Write PCAP file header
            out.writeInt(PCAP_MAGIC);
            out.writeShort(PCAP_VERSION_MAJOR);
            out.writeShort(PCAP_VERSION_MINOR);
            out.writeInt(PCAP_THISZONE);
            out.writeInt(PCAP_SIGFIGS);
            out.writeInt(PCAP_SNAPLEN);
            out.writeInt(PCAP_NETWORK);

            // Generate random packets
            Random random = new Random();
            long timestamp = System.currentTimeMillis() / 1000;

            for (int i = 0; i < numPackets; i++) {
                // Packet header
                out.writeInt((int) timestamp); // Timestamp seconds
                out.writeInt(random.nextInt(1000000)); // Timestamp microseconds

                int packetLength = 64 + random.nextInt(1400); // Random packet size between 64 and 1464 bytes
                out.writeInt(packetLength); // Captured length
                out.writeInt(packetLength); // Original length

                // Packet data (dummy Ethernet frame)
                // Destination MAC
                for (int j = 0; j < 6; j++) {
                    out.writeByte(random.nextInt(256));
                }

                // Source MAC
                for (int j = 0; j < 6; j++) {
                    out.writeByte(random.nextInt(256));
                }

                // EtherType (IPv4)
                out.writeShort(0x0800);

                // Remaining packet data (random bytes)
                for (int j = 0; j < packetLength - 14; j++) {
                    out.writeByte(random.nextInt(256));
                }

                timestamp += random.nextInt(10); // Increment timestamp
            }

            System.out.println("Generated " + numPackets + " packets in " + outputFile);

        } catch (IOException e) {
            System.err.println("Error writing PCAP file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}