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
 * This class is designed to be a receiver and an event builder for a module who
 * has emu domain (regular) input channels and a file output channel.
 * This is used in the context of Dave Lawrence's experiment to reproduce
 * NON-streamed data from ROCs/VTPs, presumably reading events from data files.
 * The data comes as chunks of evio without the preceding prestart and go event.
 * The first round of data was sent over TCP, so emphasis is on the TCP server/channels.
 *
 * @author timmer
 * (Apr 19, 2024)
 */
public class Builder extends Thread {

    private boolean debug;
    private boolean tcp = true;  // always
    private boolean useEt = false;

    private int tcpPort     = cMsgNetworkConstants.emuTcpPort;
    private int clientCount = 1;
    private String expid;
    private String name     = "Builder";
    private String fileName = "/tmp/rtdpTest.data";
    private String etName   = "";

    /**
     * Constructor.
     * @param args program args
     */
    Builder(String[] args) {
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
                    System.out.println("-p needs integer arg");
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
                    System.out.println("-c needs integer arg");
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
            else if (args[i].equalsIgnoreCase("-et")) {
                etName = args[i + 1];
                useEt = true;
                i++;
            }
            else if (args[i].equalsIgnoreCase("-n")) {
                name = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                debug = true;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

        if (expid == null) {
            expid = System.getenv("EXPID");
            if (expid == null) {
                expid = "myExpid";
            }
        }

        if (fileName.length() > 0 && etName.length() > 0) {
            System.out.println("Choose either a file or ET output, but not both");
            System.exit(-1);
        }

    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
                "   java Builder\n" +
                "        [-p <port>]          TCP port to listen on (default 46100)\n" +
                "        [-x <expid>]         EXPID of experiment (can ignore)\n" +
                "        [-n <name>]          name of server's CODA component (can ignore, default Builder)\n" +
                "        [-c <# of clients>]  number of ROCs sending data (default 1)\n" +
                "        [-f <output file>]   name of output file (default /tmp/rtdpTest.dat)\n" +
                "        [-et <ET name>]      name of output ET system file\n" +
                "        [-v]                 turn on printout\n" +
                "        [-h]                 print this help\n");

    }


    /**
     * Run as a stand-alone application.
     * @param args args
     */
    public static void main(String[] args) {
        try {
            Builder receiver = new Builder(args);
            receiver.start();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /** This method is executed as a thread. */
    public void run() {
        if (debug) System.out.println("STARTED Builder!");

        // Create output channel
        DataChannel outChannel = null;

        if (useEt) {
            // Create output ET channel
            try {
                outChannel = new DataChannelImplEt("etChannel", etName, debug);
            }
            catch (DataTransportException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        else {
            // Create output file channel
            try {
                outChannel = new DataChannelImplFile("fileChannel", fileName, debug);
            }
            catch (DataTransportException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        if (debug) System.out.println("Created a file channel for " + fileName);
        ArrayList<DataChannel> outputChannels = new ArrayList<DataChannel>();
        outputChannels.add(outChannel);

        // Create input regular emu channels and store all channels created
        ArrayList<DataChannel> inputChannels = new ArrayList<>(clientCount);
        for (int i=0; i < clientCount; i++) {
            DataChannelImplEmu tcpInChannel =
                    new DataChannelImplEmu("emuChan_" + i, debug, i);
            inputChannels.add(tcpInChannel);
        }

        //--------------------------------------------------------------------

        // Create the EventBuilder module
        TriggeredBuilder eb = new TriggeredBuilder(name, debug);
        eb.addInputChannels(inputChannels);
        eb.addOutputChannels(outputChannels);

        // And get it ready to run
        eb.prestart();

        // Let us know that the server has the expected # of client connected
        CountDownLatch latch = new CountDownLatch(clientCount);
        if (debug) System.out.println("Created a latch");

        //--------------------------------------------------------------------
        // Start up the TCP server or UDP receivers.
        // The TCP server will create DataChannelImplTcpStream channels as connections are made.
        // (Haven't dealt with UDP yet ...)
        // False means not-streaming channel
        EmuDomainServer server = new EmuDomainServer(tcpPort, inputChannels, false,
                                                     expid, name, tcp, latch, debug);
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

        // This is a daemon thread so if all other threads end, this application will end too.
        try {
            Thread.sleep(1000000); // 11.5 days
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


}



