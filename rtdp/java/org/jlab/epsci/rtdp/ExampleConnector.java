package org.jlab.epsci.rtdp;


import org.jlab.coda.emu.support.data.CODATag;
import org.jlab.coda.jevio.CompactEventBuilder;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EvioException;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class ExampleConnector {

    /** Ints representing ascii for "cMsg is cool", used to filter out port-scanning software. */
    public static final int[] magicNumbers = {0x634d7367, 0x20697320, 0x636f6f6c};

    /** The magic number in evio headers. */
    public static int EVIO_HDR_MAGIC_NUMBER = 0xc0da0100;

    /** Version of cMsg being used when communicating. */
    public static final int cMsgVersion = 6;

    /** For generating fake data. **/
    public static int bytesPerDataBank = 0;




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

    public long frameNumber = 0L;
    public long timestamp = 100;

    /** Do we send PRESTART, GO, and END? */
    public boolean sendControls;

    /** Socket over which to send messages to the server over TCP. */
    public Socket tcpSocket;

    /** Output TCP data stream from this client to the server. */
    public DataOutputStream domainOut;


    /** Contains time-slice data + preceding evio v4 block header. **/
    public ByteBuffer sendBuffer;

    /** Contains CODA PRESTART event + preceding evio v4 block header. **/
    public ByteBuffer prestartBuffer;

    /** Contains CODA GO event + preceding evio v4 block header. **/
    public ByteBuffer goBuffer;

    /** Contains CODA END event + preceding evio v4 block header. **/
    public ByteBuffer endBuffer;




    ExampleConnector(String[] args) {
        decodeCommandLine(args);

        // Create a few data buffers:
        // One will have time slice data from a ROC.
        // The others will have PRESTART, GO, and END events (used in CODA to control data flow).


        //---------------------------------------------------------
        // 1) Data buffer with preceding evio block header
        //---------------------------------------------------------

        // First, generate buffer of properly formatted time-slice data in evio
        // Number of data words in each event
        int generatedDataWords = 5;

        if (verbose) System.out.println("\n  Roc mod: Starting sim ROC frame at " + frameNumber + "\n");
        // Creates a ready-to-read, array-backed ByteBuffer
        ByteBuffer templateBuffer = createSingleTimeSliceBuffer(dataOrder, codaId,
                                                                generatedDataWords, frameNumber, timestamp);

//        int eventWordSize = templateBuffer.remaining()/4;

        // Second place an evio v4 block header in front of the data
        // (as would normally be the case)

        int dataLen    = templateBuffer.limit();
        int totalLen   = dataLen + 32;
        int totalWords = totalLen/4;

        // Create buf to hold header + data
        byte[] sendArray = new byte[totalLen];
        sendBuffer       = ByteBuffer.wrap(sendArray);
        // Make sure endian is consistent
        sendBuffer.order(dataOrder);

        // Copy in data - 32 bytes into destination allowing for header
        System.arraycopy(templateBuffer.array(), 0, sendArray, 32, dataLen);

        // Write header at beginning
        sendBuffer.putInt(0, totalWords); // total length of block in words
        sendBuffer.putInt(1*4, 1);        // block #
        sendBuffer.putInt(2*4, 8);        // header len in words
        sendBuffer.putInt(3*4, 1);        // event count
        sendBuffer.putInt(4*4, 0);        // reserved
        sendBuffer.putInt(5*4, 0x204);    // version (4), RocRaw content (0), and last block bit set
        sendBuffer.putInt(6*4, 0);        // reserved
        sendBuffer.putInt(7*4, EVIO_HDR_MAGIC_NUMBER);

        //---------------------------------------------------------
        // 2) Create a PRESTART event
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
        // 3) Create a GO event
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
        goBuffer.putInt(12*4, frameCount);  // frames sent so far
        goBuffer.limit(goBuffer.capacity());


        //---------------------------------------------------------
        // 4) Create an END event
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
        endBuffer.putInt(12*4, frameCount);  // frames sent so far
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
                                   "        [-fr <# frames>] number of frames to send\n" +
                                   "        [-control]       send PRESTART & GO before frames and END event after,\n" +
                                   "                         4 sec delay before sending END\n" +
                                   "        [-big]           send big endian data\n" +
                                   "        [-v]             turn on verbose mode\n" +
                                   "        [-h]             print this help\n");
    }





    /**
         * Run as a stand-alone application.
         * @param args args
         */
    public static void main(String[] args) {

        try {
            ExampleConnector connector = new ExampleConnector(args);

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

            if (connector.frameCount > 0) {
                // Send frame to server
                for (int i=0; i < connector.frameCount; i++) {
                    if (connector.verbose) {
                        System.out.println("connect: sent event " + i);
                    }
                    connector.send(connector.sendBuffer.array(), 0, connector.sendBuffer.capacity());

                    connector.frameNumber++;
                    connector.timestamp += 10;
                    connector. updateTimeSliceBuffer(connector.sendBuffer, 32,
                                                     connector.frameNumber,
                                                     connector.timestamp);
                }
            }

            if (connector.sendControls) {
                // Send END event
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



    /**
     * Generate data from a streaming ROC.
     *
     * @param outputOrder endianness of generated data
     * @param id CODA id
     * @param generatedDataWords desired amount of total words (not including headers)
     *                           for all data banks (each corresponding to one payload port).
     * @param frameNumber frame number
     * @param timestamp   time stamp
     * @return ByteBuffer with generated single ROC time slice bank inside containing bank.
     */
    static private ByteBuffer createSingleTimeSliceBuffer(ByteOrder outputOrder, int id,
                                                          int generatedDataWords,
                                                          long frameNumber, long timestamp) {

        try {
            // Make generatedDataWords a multiple of 4, round up
            generatedDataWords = 4*((generatedDataWords + 3) / 4);
            int totalLen = 14 + generatedDataWords + 1000; // total of 14 header words + 1K extra

            // Each of 4 data banks has 1/4 of total words so generateDataWords = # bytes for each bank ...
            // Store calculation here
            bytesPerDataBank = generatedDataWords;

            // This creates an array backed ByteBuffer
            CompactEventBuilder builder = new CompactEventBuilder(4*totalLen, outputOrder);

            // ROC Time Slice Bank
            int totalStreams = 2;
            int streamMask = 3;
            int streamStatus = ((totalStreams << 4) & 0x7f) | (streamMask & 0xf);
            builder.openBank(id, streamStatus, DataType.BANK);

            // Stream Info Bank (SIB)
            builder.openBank(CODATag.STREAMING_SIB.getValue(), streamStatus, DataType.SEGMENT);

            // 1st SIB Segment -> TSS or Time Slice Segment
            builder.openSegment(0x31, DataType.UINT32);
            int[] intData = new int[3];
            intData[0] = (int)frameNumber;
            intData[1] = (int)timestamp;
            intData[2] = (int)((timestamp >>> 32) & 0xFFFF);
            builder.addIntData(intData);
            builder.closeStructure();

            // 2nd SIB Segment -> AIS or Aggregation Info Segment
            builder.openSegment(0x41, DataType.USHORT16);
            short[] shortData = new short[4];

            int payloadPort1 = 8;
            int laneId = 0;
            int bond = 0;
            int moduleId = 2;
            short payload1 = (short) (((moduleId << 8) & 0xf) | ((bond << 7) & 0x1) | ((laneId << 5) & 0x3)| (payloadPort1 & 0x1f));
            shortData[0] = payload1;

            int payloadPort2 = 9;
            laneId = 1;
            bond = 0;
            moduleId = 3;
            short payload2 = (short) (((moduleId << 8) & 0xf) | ((bond << 7) & 0x1) | ((laneId << 5) & 0x3)| (payloadPort2 & 0x1f));
            shortData[1] = payload2;

            int payloadPort3 = 10;
            laneId = 2;
            bond = 0;
            moduleId = 5;
            short payload3 = (short) (((moduleId << 8) & 0xf) | ((bond << 7) & 0x1) | ((laneId << 5) & 0x3)| (payloadPort3 & 0x1f));
            shortData[2] = payload3;

            int payloadPort4 = 11;
            laneId = 3;
            bond = 0;
            moduleId = 7;
            short payload4 = (short) (((moduleId << 8) & 0xf) | ((bond << 7) & 0x1) | ((laneId << 5) & 0x3)| (payloadPort4 & 0x1f));
            shortData[3] = payload4;

            builder.addShortData(shortData);
            builder.closeStructure();
            // Close SIB
            builder.closeStructure();

            // Add Data Bank, 1 for each payload (4)
            // TODO: Question: is this stream status different??
            // Assume this stream status is only for the payload in question


            // Fill banks with generated fake data ...
            byte[] iData = new byte[bytesPerDataBank];
            for (int i=0; i < bytesPerDataBank; i++) {
                iData[i] = (byte)i;
            }

            totalStreams = 1;
            streamMask = 1;
            streamStatus = ((totalStreams << 4) & 0x7) | (streamMask & 0xf);
            builder.openBank(payloadPort1, streamStatus, DataType.UCHAR8);
            builder.addByteData(iData);
            builder.closeStructure();

            streamMask = 2;
            streamStatus = ((totalStreams << 4) & 0x7) | (streamMask & 0xf);
            builder.openBank(payloadPort2, streamStatus, DataType.UCHAR8);
            builder.addByteData(iData);
            builder.closeStructure();

            streamMask = 4;
            streamStatus = ((totalStreams << 4) & 0x7) | (streamMask & 0xf);
            builder.openBank(payloadPort3, streamStatus, DataType.UCHAR8);
            builder.addByteData(iData);
            builder.closeStructure();

            streamMask = 8;
            streamStatus = ((totalStreams << 4) & 0x7) | (streamMask & 0xf);
            builder.openBank(payloadPort4, streamStatus, DataType.UCHAR8);
            builder.addByteData(iData);
            builder.closeStructure();


            builder.closeAll();
            // buf is ready to read
            return builder.getBuffer();
        }
        catch (EvioException e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * Instead of rewriting the entire event buffer for each event
     * (with only slightly different data), only update the couple of places
     * in which data changes.
     * The only 2 quantities that need updating are the frame number and time stamp.
     * Both of these are data in the Stream Info Bank.
     *
     * @param buf          buffer with time slice data
     * @param off          offset into buffer
     * @param frameNumber  new frame number to place into buf.
     * @param timestamp    new time stamp to place into buf
     */
    void  updateTimeSliceBuffer(ByteBuffer buf, int off, long frameNumber, long timestamp) {

        // Get buf ready to read for output channel
        buf.putInt(20+off, (int)frameNumber);
        buf.putInt(24+off, (int)timestamp);// low 32 bits
        buf.putInt(28+off, (int)(timestamp >>> 32 & 0xFFFF)); // high 32 bits
    }



}
