/*
 * Copyright (c) 2024, Jefferson Science Associates
 *
 * Thomas Jefferson National Accelerator Facility
 * Data Acquisition Group
 *
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 */

package org.jlab.epsci.rtdp;


import org.jlab.coda.cMsg.cMsgNetworkConstants;
import org.jlab.coda.emu.EmuException;
import org.jlab.coda.emu.support.control.CmdExecException;
import org.jlab.coda.emu.support.data.*;
import org.jlab.coda.hipo.CompressionType;
import org.jlab.coda.jevio.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;


/**
 * This class is designed to be a receiver and aggregator for a module who
 * has emu domain streaming input channels and a file output channel.
 * This is used in the context of Dave Lawrence's experiment to save
 * streamed data from ROCs/VTPs, storing them in pcap files and later replaying them.
 * The data comes as chunks of evio without the preceeding prestart and go event.
 * The first round of data was sent over TCP, so emphasis is on the TCP server/channels.
 *
 * @author timmer
 * (Jan 16, 2024)
 */
public class FakeRoc extends Thread {

    private boolean debug;


    private int tcpPort = cMsgNetworkConstants.emuTcpPort;
    private int codaId = 0;
    private String expid;
    private String name = "RocSim";
    private String serverIP;

    /** How many time-slices to send. */
    private int frameCount = 1;

    /** If true, send PRESTART, GO, and END events. */
    private boolean sendControls = true;

    /** Data byte order. */
    private ByteOrder dataOrder = ByteOrder.LITTLE_ENDIAN;




    /**
     * Constructor.
     * @param args program args
     */
    FakeRoc(String[] args) {

        decodeCommandLine(args);

        if (false) {
            ByteBuffer prestart = ByteBuffer.allocate(136);
            prestart.putInt(0x14);     // 20 words?
            prestart.putInt(0x1);
            prestart.putInt(0xe);
            prestart.putInt(0x1);
            prestart.putInt(0x4);
            prestart.putInt(0x00002806);  // 1010 00  06    should be 0x1406 = 01 01 00 06
            prestart.putInt(0x0);
            prestart.putInt(0xc0da0100);
            prestart.putInt(0x14);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
            prestart.putInt(0x0);

            prestart.putInt(0x14); // 20 bytes?

            prestart.putInt(0x4);
            prestart.putInt(0xffd10100);
            prestart.putInt(0x65cbd087);
            prestart.putInt(0x1);
            prestart.putInt(0x1);

            prestart.putInt(0xe);     // 16 words of trailer
            prestart.putInt(0xffffffff);
            prestart.putInt(0xe);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
//            prestart.putInt(0x8);
            prestart.putInt(0x30000206);
            prestart.putInt(0x0);
            prestart.putInt(0xc0da0100);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
            prestart.putInt(0x0);
            prestart.putInt(0x0);

//            prestart.putInt(0x50); // bytes to write
//            prestart.putInt(0x1);  // event count

            prestart.flip();

            try {
                // Try writing control event

                int evType = 5; // control event

                // For some reason this has to be over-sized by 6 bytes
                ByteBuffer prestart2 = ByteBuffer.allocate(150);
                EventWriterUnsync writer = new EventWriterUnsync(prestart2, 0, 1, null, 1,
                                                                 CompressionType.RECORD_UNCOMPRESSED,
                                                                 evType);

                PayloadBuffer pBuf = Evio.createControlBuffer(ControlType.PRESTART, 1,
                                                              1, 0, 0, 0,
                                                              dataOrder, name, false, true);
                ByteBuffer bb = pBuf.getBuffer();

                Utilities.printBuffer(bb, 0, 10, "PRESTART");

                boolean fit = writer.writeEvent(bb);
                if (!fit) {
                    // Our buffer is too small to fit even 1 event!
                    System.out.println("emu socket's buffer size must be increased");
                }

                writer.close();

                prestart2.flip();
                prestart2.limit(writer.getBytesWrittenToBuffer());


            ByteBuffer pp = writer.getByteBuffer();

                System.out.println("flushEvents: writer's bb buf limit = " + pp.limit() + ", pos = " + pp.position());
                System.out.println("flushEvents: writer's prestart2 buf limit = " + prestart2.limit() + ", pos = " + prestart2.position());


                Utilities.printBuffer(pp, 0, pp.limit()/4, "PRESTART");
                Utilities.printBuffer(prestart2, 0, prestart2.limit()/4, "PRESTART");


                EvioCompactReader reader = new EvioCompactReader(prestart2, null, false, false);

                // First block header in buffer
                IBlockHeader blockHeader = reader.getFirstBlockHeader();
                if (blockHeader.getVersion() < 4) {
                    throw new EvioException("Data not in evio but in version " +
                                                    blockHeader.getVersion());
                }

                System.out.println("First record hdr: \n" + blockHeader.toString());

                int evtType = blockHeader.getEventType();
                EventType eventType = EventType.getEventType(blockHeader.getEventType());
                System.out.println("event type: " + evtType + ", " + eventType);
                if (eventType == null || !eventType.isEbFriendly()) {
                    System.out.println("bad evio format or improper event type (" + evtType + ")\n");
                    Utilities.printBytes(prestart, 0, 200, "Incoming (bad format bytes");
                    throw new EvioException("bad evio format or improper event type (" + evtType + ")");
                }

                int recordId = blockHeader.getNumber();


                int eventCount = reader.getEventCount();
                boolean gotRocRaw = eventType.isFromROC();
                boolean gotPhysics = eventType.isAnyPhysics();


                for (int i = 1; i < eventCount + 1; i++) {

                    // getScannedEvent will clear child and allNodes lists
                    EvioNode topNode = reader.getScannedEvent(i, null);
                }

            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * Method to decode the command line used to start this application.
     * @param args command line arguments
     */
    private void decodeCommandLine(String[] args) {

        // loop over all args
        for (int i = 0; i < args.length; i++) {

            if (args[i].equalsIgnoreCase("-h")) {
                usage();
                System.exit(-1);
            }
            else if (args[i].equalsIgnoreCase("-p")) {
                String port = args[i + 1];
                i++;
                try {
                    tcpPort = Integer.parseInt(port);
                    if (tcpPort < 1024 || tcpPort > 65535) {
                        tcpPort = 46100;
                    }
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            else if (args[i].equalsIgnoreCase("-id")) {
                String id = args[i + 1];
                i++;
                try {
                    codaId = Integer.parseInt(id);
                    if (codaId < 0) {
                        codaId = 0;
                    }
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            else if (args[i].equalsIgnoreCase("-fr")) {
                String count = args[i + 1];
                i++;
                try {
                    frameCount = Integer.parseInt(count);
                    if (frameCount < 0) {
                        System.out.println("frame count must be >= 0\n");
                        usage();
                        System.exit(-1);
                    }
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            else if (args[i].equalsIgnoreCase("-x")) {
                expid = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-ip")) {
                serverIP = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-n")) {
                name = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                debug = true;
            }
            else if (args[i].equalsIgnoreCase("-control")) {
                sendControls = true;
            }
            else if (args[i].equalsIgnoreCase("-big")) {
                dataOrder = ByteOrder.BIG_ENDIAN;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

        if (expid == null) {
            expid = System.getenv("EXPID");
            if (expid == null) {
                System.out.println("Provide an EXPID either on the cmd line");
                System.out.println("or in the EXPID environmental variable");
                System.exit(-1);
            }
        }

        if (serverIP == null) {
            System.out.println("Provide a server IP address on the cmd line");
            System.exit(-1);
        }
    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
                "   java FakeRoc\n" +
                "        [-p <port>]      TCP port to listen on for connections\n" +
                "        [-x <expid>]     EXPID of experiment\n" +
                "        [-n <name>]      name of fake ROC\n" +
                "        [-id <CODA id>]  CODA id of fake ROC\n" +
                "        [-ip <IP addr>]  IP address of TCP server\n" +
                "        [-fr <# frames>] number of frames to send\n" +
                "        [-control]       send PRESTART & GO before frames and END event after\n" +
                "        [-big]           send big endian data\n" +
                "        [-v]             turn on printout\n" +
                "        [-h]             print this help\n");

        // TODO: "[-single VTP input] to pass on to module of aggregator !!!
    }


    /**
     * Run as a stand-alone application.
     * @param args args
     */
    public static void main(String[] args) {
        try {
            FakeRoc sender = new FakeRoc(args);
            sender.start();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /** This method is executed as a thread. */
    public void run() {
        if (debug) System.out.println("STARTED Aggregator thread!!");

        // Create output file channel
        DataChannelImplEmu emuChannel = new DataChannelImplEmu("emuChannel", codaId, expid,
                                                               serverIP, tcpPort, debug, dataOrder);

        ArrayList<DataChannel> outputChannels = new ArrayList<DataChannel>();
        outputChannels.add(emuChannel);


        //--------------------------------------------------------------------

        // Create the Simulation ROC module
        RocSimulation sim = new RocSimulation("RocSim", debug, sendControls, frameCount, dataOrder);
        sim.addOutputChannels(outputChannels);

        try {
            emuChannel.prestart();
        }
        catch (CmdExecException e) {
            e.printStackTrace();
        }

        // And get it running, sending PRESTART & GO if desired
        sim.prestart();
        sim.go();

        // This is a daemon thread so if all other threads end, this application will end too.
        try {
            Thread.sleep(1000000); // 11.5 days
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}



