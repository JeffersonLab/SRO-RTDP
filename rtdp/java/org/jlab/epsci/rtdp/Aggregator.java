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

import java.util.ArrayList;
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
public class Aggregator extends Thread {

    private boolean debug;
    private boolean tcp = true;

    private boolean singleVTPinput = false;

    private int tcpPort= cMsgNetworkConstants.emuTcpPort;
    private int clientCount = 1;
    private String expid;
    private String name = "Aggregator";
    private String fileName = "streamingRTD.dat";

    /**
     * Constructor.
     * @param args program args
     */
    Aggregator(String[] args) {
        decodeCommandLine(args);
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
                        tcpPort = cMsgNetworkConstants.emuTcpPort;
                    }
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            else if (args[i].equalsIgnoreCase("-c")) {
                String count = args[i + 1];
                i++;
                try {
                    clientCount = Integer.parseInt(count);
                    if (clientCount < 1 || clientCount > 16) {
                        System.out.println("must have at least 1 client and not more than 16");
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
            else if (args[i].equalsIgnoreCase("-f")) {
                fileName = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-n")) {
                name = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-one")) {
                singleVTPinput = true;
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                debug = true;
            }
            else if (args[i].equalsIgnoreCase("-udp")) {
                tcp = false;
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
                System.out.println("or in an environmental variable");
                System.exit(-1);
            }
        }

    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
                "   java Aggregator\n" +
                "        [-p <port>]   TCP port to listen on for connections or starting UDP port\n" +
                "        [-x <expid>]  EXPID of experiment\n" +
                "        [-n <name>]   name of server's CODA component\n" +
                "        [-c <# of clients>]  number of ROCs sending data (default = 1)\n" +
                "        [-f <output file>]   name of output file\n" +
                "        [-udp]        accept data from udp channels (tcp is default)\n" +
                "        [-v]          turn on printout\n" +
                "        [-one]        all channels are from 1 VTP\n" +
                "        [-h]          print this help\n");

        // TODO: "[-single VTP input] to pass on to module of aggregator !!!
    }


    /**
     * Run as a stand-alone application.
     * @param args args
     */
    public static void main(String[] args) {
        try {
            Aggregator receiver = new Aggregator(args);
            receiver.start();
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
        DataChannelImplFile fileChannel = null;
        try {
            if (debug) System.out.println("Call channel constructor ");
            fileChannel = new DataChannelImplFile("fileChannel", fileName, debug);
            if (debug) System.out.println("Past channel creation ");
        }
        catch (DataTransportException e) {
            e.printStackTrace();
            System.exit(1);
        }
        if (debug) System.out.println("Created a file channel for " + fileName);
        ArrayList<DataChannel> outputChannels = new ArrayList<DataChannel>();
        outputChannels.add(fileChannel);


        // Let us know that the server has the expected # of client connected
        CountDownLatch latch = new CountDownLatch(clientCount);
        if (debug) System.out.println("Created a latch");

        //--------------------------------------------------------------------
        // Start up the TCP server or UDP receivers.
        // The TCP server will create DataChannelImplTcpStream channels as connections are made.
        // (Haven't dealt with UDP yet ...)
        EmuDomainServer server = new EmuDomainServer(tcpPort, clientCount, expid, name, tcp, latch, debug);
        if (debug) System.out.println("Created a TCP server and start it");
        server.start();
        if (debug) System.out.println("TCP server started");

        // Wait until the expected # of client connect
        System.out.println("Waiting for " + clientCount + " clients to connect");

        try {
            latch.await();
        }
        catch (InterruptedException e) {
            System.out.println("Interrupted while waiting clients to connect");
            System.exit(1);
        }

        System.out.println("All clients have connected");
        //--------------------------------------------------------------------

        // Create the Aggregator module
        StreamAggregator agg = new StreamAggregator("Aggregator", debug);
        agg.setSingleVTPInputs(singleVTPinput);
        agg.addInputChannels(server.getTcpServer().getInputChannels());
        agg.addOutputChannels(outputChannels);

        // And get it running
        agg.prestart();

        // This is a daemon thread so if all other threads end, this application will end too.
        try {
            Thread.sleep(1000000); // 11.5 days
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


}



