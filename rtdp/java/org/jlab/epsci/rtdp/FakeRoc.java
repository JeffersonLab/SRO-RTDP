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
public class FakeRoc extends Thread {

    private boolean debug;


    private int tcpPort = cMsgNetworkConstants.emuTcpPort;
    private int codaId = 0;
    private String expid;
    private String name = "RocSim";
    private String serverIP;

    /**
     * Constructor.
     * @param args program args
     */
    FakeRoc(String[] args) {
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
            else if (args[i].equalsIgnoreCase("-debug")) {
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
                "        [-p <port>]   TCP port to listen on for connections\n" +
                "        [-x <expid>]  EXPID of experiment\n" +
                "        [-n <name>]   name of fake ROC\n" +
                "        [-id <CODA id>]  CODA id of fake ROC\n" +
                "        [-ip <server IP addr>]  IP address of TCP server\n" +
                "        [-debug]      turn on printout\n" +
                "        [-h]          print this help\n");

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
        System.out.println("STARTED Aggregator thread!!");

        // Create output file channel
        System.out.println("Call channel constructor ");
        DataChannelImplEmu emuChannel = new DataChannelImplEmu("emuChannel", codaId, expid,
                                                               serverIP, tcpPort);
        System.out.println("Past channel creation ");

        System.out.println("Created an emu channel for RocSim");
        ArrayList<DataChannel> outputChannels = new ArrayList<DataChannel>();
        outputChannels.add(emuChannel);


        //--------------------------------------------------------------------

        // Create the Aggregator module
        RocSimulation sim = new RocSimulation("RocSim");
        sim.addOutputChannels(outputChannels);

        // And get it running
        sim.prestart();

        // This is a daemon thread so if all other threads end, this application will end too.
        try {
            Thread.sleep(1000000); // 11.5 days
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}



