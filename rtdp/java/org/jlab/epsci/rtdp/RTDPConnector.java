package org.jlab.epsci.rtdp;


import org.jlab.coda.jevio.*;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class RTDPConnector {

    /** Ints representing ascii for "cMsg is cool", used to filter out port-scanning software. */
    public static final int[] magicNumbers = {0x634d7367, 0x20697320, 0x636f6f6c};

    /** The magic number in evio headers. */
    public static int EVIO_HDR_MAGIC_NUMBER = 0xc0da0100;

    /** Version of cMsg being used when communicating. */
    public static final int cMsgVersion = 6;



    /** debug. */
    public boolean verbose = false;

    /** Byte order in which to send our data. */
    public ByteOrder dataOrder = ByteOrder.LITTLE_ENDIAN;

    /** Connected to server? */
    public boolean connected;

    /** Experiment ID. */
    public String expid;

    /** ID of this data-sending CODA component. */
    public int codaId;

    /** Name of this channel which also happens to be the
        destination CODA component we want to connect to.
        Can actually set this to anything. */
    public String name = "DC1";

    /** TCP port of emu domain server. */
    public int sendPort = 46100; // cMsgNetworkConstants.emuTcpPort

    /** TCP send buffer size in bytes (0 = system default). */
    public int tcpSendBuf = 5000000;

    /** TCP server IP address. */
    public String serverIP;

    /** TCP no delay setting. */
    public boolean tcpNoDelay = true;


    /**
     * Biggest chunk of data sent by data producer.
     * Allows good initial value of ByteBuffer size.
     */
    public int maxBufferSize = 4000000;

    /** How many time-slice frames to be sent to server. */
    public int frameCount;
    
    /** Do we send PRESTART, GO, and END? */
    public boolean sendControls;

    /** Socket over which to send messages to the server over TCP. */
    public Socket tcpSocket;

    /** Output TCP data stream from this client to the server. */
    public DataOutputStream domainOut;


    /** Contains CODA PRESTART event + preceding evio v4 block header. **/
    public ByteBuffer prestartBuffer;

    /** Contains CODA GO event + preceding evio v4 block header. **/
    public ByteBuffer goBuffer;

    /** Contains CODA END event + preceding evio v4 block header. **/
    public ByteBuffer endBuffer;

    /** Name of file from which to read evio, stream-formatted events. */
    public String fileName;




    RTDPConnector(String[] args) {
        decodeCommandLine(args);

        // Create a few data buffers:
        // One will have time slice data from a ROC.
        // The others will have PRESTART, GO, and END events (used in CODA to control data flow).
        

        //---------------------------------------------------------
        // 1) Create a PRESTART event
        //---------------------------------------------------------

        // Create buf to hold header + END event
        byte[] prestartArray = new byte[32+20];
        prestartBuffer       = ByteBuffer.wrap(prestartArray);
        // Make sure endian is consistent
        prestartBuffer.order(dataOrder);

        // Write header
        prestartBuffer.putInt(0, 13);       // total length of block in words
        prestartBuffer.putInt(1*4, 0xffffffff);      // block # = -1 for non-built events
        prestartBuffer.putInt(2*4, 8);      // header len in words
        prestartBuffer.putInt(3*4, 1);      // event count
        prestartBuffer.putInt(4*4, 0);      // reserved
        prestartBuffer.putInt(5*4, 0x1604); // version (4), Control event content (5), and last block bit set
        prestartBuffer.putInt(6*4, 0);      // reserved
        prestartBuffer.putInt(7*4, EVIO_HDR_MAGIC_NUMBER);

        // PRESTART
        prestartBuffer.putInt(8*4,  4);  // evio bank length in words (non-inclusive)
        prestartBuffer.putInt(9*4,  (0xFFD1 << 16 | 1 << 8));  // second bank word
        prestartBuffer.putInt(10*4, (int)(System.currentTimeMillis()));    // time
        prestartBuffer.putInt(11*4, 1);  // run #
        prestartBuffer.putInt(12*4, 1);  // run type
        prestartBuffer.limit(prestartBuffer.capacity());

        //---------------------------------------------------------
        // 2) Create a GO event
        //---------------------------------------------------------

        // Create buf to hold header + END event
        byte[] goArray = new byte[32+20];
        goBuffer       = ByteBuffer.wrap(goArray);
        // Make sure endian is consistent
        goBuffer.order(dataOrder);

        // Write header
        goBuffer.putInt(0, 13);       // total length of block in words
        goBuffer.putInt(1*4, 0xffffffff);      // block # = -1 for non-built events
        goBuffer.putInt(2*4, 8);      // header len in words
        goBuffer.putInt(3*4, 1);      // event count
        goBuffer.putInt(4*4, 0);      // reserved
        goBuffer.putInt(5*4, 0x1604); // version (4), Control event content (5), and last block bit set
        goBuffer.putInt(6*4, 0);      // reserved
        goBuffer.putInt(7*4, EVIO_HDR_MAGIC_NUMBER);

        // GO
        goBuffer.putInt(8*4,  4);  // evio bank length in words (non-inclusive)
        goBuffer.putInt(9*4,  (0xFFD2 << 16 | 1 << 8));  // second bank word
        goBuffer.putInt(10*4, (int)(System.currentTimeMillis()));    // time
        goBuffer.putInt(11*4, 0);           // reserved
        goBuffer.putInt(12*4, 1);  // frames sent so far
        goBuffer.limit(goBuffer.capacity());


        //---------------------------------------------------------
        // 3) Create an END event
        //---------------------------------------------------------

        // Create buf to hold header + END event
        byte[] endArray = new byte[32+20];
        endBuffer       = ByteBuffer.wrap(endArray);
        // Make sure endian is consistent
        endBuffer.order(dataOrder);

        // Write header
        endBuffer.putInt(0, 13);       // total length of block in words
        endBuffer.putInt(1*4, 0xffffffff);      // block # = -1 for non-built events
        endBuffer.putInt(2*4, 8);      // header len in words
        endBuffer.putInt(3*4, 1);      // event count
        endBuffer.putInt(4*4, 0);      // reserved
        endBuffer.putInt(5*4, 0x1604); // version (4), Control event content (5), and last block bit set
        endBuffer.putInt(6*4, 0);      // reserved
        endBuffer.putInt(7*4, EVIO_HDR_MAGIC_NUMBER);

        // END
        endBuffer.putInt(8*4,  4);  // evio bank length in words (non-inclusive)
        endBuffer.putInt(9*4,  (0xFFD4 << 16 | 1 << 8));  // second bank word
        endBuffer.putInt(10*4, (int)(System.currentTimeMillis()));    // time
        endBuffer.putInt(11*4, 0);       // reserved
        endBuffer.putInt(12*4, 1);  // frames sent so far
        endBuffer.limit(endBuffer.capacity());
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
                    sendPort = Integer.parseInt(port);
                    if (sendPort < 1024 || sendPort > 65535) {
                        sendPort = 46100;
                    }
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            else if (args[i].equalsIgnoreCase("-id")) {
                String count = args[i + 1];
                i++;
                try {
                    codaId = Integer.parseInt(count);
                    if (codaId < 0) {
                        System.out.println("coda ID must be >= 0\n");
                        usage();
                        System.exit(-1);
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
            else if (args[i].equalsIgnoreCase("-file")) {
                fileName = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = true;
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
                System.out.println("or in an environmental variable");
                System.exit(-1);
            }
        }

        if (name == null) {
            System.out.println("Provide a name of CODA component being connected to");
            System.exit(-1);
        }

    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
                                   "   java ExampleConnector\n" +
                                   "        [-p <port>]      port of TCP server\n" +
                                   "        [-x <expid>]     EXPID of experiment\n" +
                                   "        [-id <CODA id>]  id of this CODA component\n" +
                                   "        [-ip <IP addr>]  IP addr of TCP server)\n" +
                                   "        [-fr <# frames>] number of EVIO events to send (default = no limit)\n" +
                                   "        [-control]       send PRESTART & GO before frames and END event after,\n" +
                                   "                         4 sec delay before sending END\n" +
                                   "        [-big]           send big endian data\n" +
                                   "        [-file]          file from which to read EVIO events to send\n" +
                                   "        [-v]             turn on verbose mode\n" +
                                   "        [-h]             print this help\n");
    }





    /**
         * Run as a stand-alone application.
         * @param args args
         */
    public static void main(String[] args) {

        try {
            
            RTDPConnector connector = new RTDPConnector(args);

            // For reading file containing events to send
            EvioReader reader = new EvioReader(connector.fileName);

            if (connector.frameCount < 1) {
                connector.frameCount = reader.getEventCount();
                System.out.println("Sending " + connector.frameCount + " events");
            }

                // Writing into this buf for sending to aggregator
            ByteBuffer sendingBuf = ByteBuffer.allocate(4000000);
            EventWriterV4 writer = new EventWriterV4(sendingBuf);
            writer.close(); // don't worry about this close

            // Connect to server
            connector.directConnect();

            // Put in a delay here to allow all the channels to be created for the Aggregator
            // and its main building thread to start. Not sure exactly what to do here since
            // in a DAQ system all this timing is controlled by the RunControl program.
            // When replaying packets this shouldn't be an issue either as it reflects the
            // operation of RunControl.
            Thread.sleep(1000);

            if (connector.sendControls) {
                // Send PRESTART and GO events
                if (connector.verbose) {
                    System.out.println("connect: sent prestart and go");
                }
                connector.send(connector.prestartBuffer.array(), 0, connector.prestartBuffer.capacity());
                connector.send(connector.goBuffer.array(), 0, connector.goBuffer.capacity());
            }

            int trackEvents = 0;

            if (connector.frameCount > 0) {

                // Send frame to server
                for (int i=0; i < connector.frameCount; i++) {

                    if (connector.verbose && ((++trackEvents) % 1000 == 0)) {
                        System.out.println("At event " + trackEvents);
                    }

                    // read event from file
                    EvioEvent ev = reader.parseEvent(i+1);

                    // reset buffer in which to place event
                    sendingBuf.clear();
                    // get ready to write into it
                    writer.setBuffer(sendingBuf);
                    // write event
                    writer.writeEvent(ev);
                    // flush data and get ready to read
                    writer.close();

                    // Set limits properly for reading
                    sendingBuf.flip();
                    sendingBuf.limit((int)writer.getBytesWrittenToBuffer());

                    // send to server
                    connector.send(sendingBuf.array(), 0, sendingBuf.limit());
                }
            }

            if (connector.sendControls) {
                // Send END event
                connector.endBuffer.putInt(12*4, connector.frameCount+2);  // # frames sent so far
                // Put in a delay so aggregator can finish building all events, not strictly necessary
                Thread.sleep(4000);
                if (connector.verbose) {
                    System.out.println("connect: sent end");
                }
                connector.send(connector.endBuffer.array(), 0, connector.endBuffer.capacity());
            }

        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }



    /**
     * Method to connect to the TCP server from this client.
     *
     * @throws Exception if there are problems parsing the UDL or
     *                       communication problems with the server(s)
     */
    private void directConnect() throws Exception {

        // Create TCP connection to the Emu Server

        System.out.println("connect: tcp noDelay = " + tcpNoDelay);

            try {
                tcpSocket = new Socket();
                tcpSocket.setReuseAddress(true);
                tcpSocket.setTcpNoDelay(tcpNoDelay);
                tcpSocket.setSendBufferSize(tcpSendBuf);

System.out.println("connect: try making TCP connection to host = " + serverIP + "; port = " + sendPort);
                // Don't waste too much time if a connection can't be made, timeout = 10 sec
                tcpSocket.connect(new InetSocketAddress(serverIP, sendPort), 10000);

                domainOut = new DataOutputStream(new BufferedOutputStream(tcpSocket.getOutputStream()));
                System.out.println("connect: MADE TCP connection to host = " + serverIP +
                                           "; port = " + sendPort);

            }
            catch (SocketTimeoutException e) {
                System.out.println("connect: socket TIMEOUT (20 sec) connecting to " + serverIP);
                // Close any open socket
                try {if (tcpSocket != null) tcpSocket.close();}
                catch (IOException e2) {}
                throw new Exception("Connect error with Emu server", e);
            }
            catch (IOException e) {
                System.out.println("connect: socket failure connecting to " + serverIP);
                throw new Exception("Connect error with Emu server", e);
            }

        try {
            talkToServer();
            System.out.println("connect: done talking to server");
        }
        catch (IOException e) {
            throw new Exception("Communication error with Emu server", e);
        }

        connected = true;
        
    }


    /** Talk to emu server over TCP connection. */
    private void talkToServer() throws IOException {
        try {
            // Send emu server some info
            domainOut.writeInt(magicNumbers[0]);
            domainOut.writeInt(magicNumbers[1]);
            domainOut.writeInt(magicNumbers[2]);

            // Version, coda id, buffer size
            domainOut.writeInt(cMsgVersion);
            domainOut.writeInt(codaId);
            domainOut.writeInt(maxBufferSize);

            // How many sockets, relative position of socket
            domainOut.writeInt(1);
            domainOut.writeInt(1);

            domainOut.flush();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Method to send a message to the Emu domain server.
     *
     * @param data data to send
     * @param offset offset into data array
     * @param length # of bytes to send
     * @throws Exception if there are communication problems with the server;
     *                       subject and/or type is null
     */
    public void send(final byte[] data, int offset, int length) throws Exception {

        if (!connected) {
            throw new Exception("not connected to server");
        }

        try {
            // Type of message is in 1st int.
            // Total length of binary (not including this int) is in 2nd int
            int msgType = 1; // 1 = regular event, 3 = END event
            domainOut.writeLong((long)msgType << 32L | (length & 0xffffffffL));

            // Write byte array
            if (length > 0) {
                domainOut.write(data, offset, length);
            }

            domainOut.flush();
        }
        catch (UnsupportedEncodingException e) {
        }
        catch (IOException e) {
            e.printStackTrace();
            if (verbose) {
                System.out.println("send: " + e.getMessage());
            }
            throw new Exception(e);
        }
    }

    

}
