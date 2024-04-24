/*
 * Copyright (c) 2014, Jefferson Science Associates
 *
 * Thomas Jefferson National Accelerator Facility
 * Data Acquisition Group
 *
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 */

package org.jlab.epsci.rtdp;


import org.jlab.coda.cMsg.*;
import org.jlab.coda.cMsg.common.cMsgMessageFull;
import org.jlab.coda.emu.EmuException;
import org.jlab.coda.emu.EmuUtilities;
import org.jlab.coda.emu.support.data.*;
import org.jlab.coda.jevio.*;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

import java.nio.ByteOrder;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;



/**
 * This class implement a data channel which
 * gets-data-from/sends-data-to an Emu domain client/server.
 *
 * @author timmer
 * (4/23/2014)
 */
public class DataChannelImplEmu extends DataChannelAdapter {
    

    /** Read END event from input ring. */
    private volatile boolean haveInputEndEvent;

    private String expid;

    private boolean debug;
    
    // OUTPUT

    /** Thread used to output data. */
    private DataOutputHelper dataOutputThread;

    /** TCP port of emu domain server. */
    private int sendPort;

    /** TCP send buffer size in bytes. */
    private int tcpSendBuf;

    /** TCP send buffer size in bytes. */
    private String serverIP;

    /** TCP no delay setting. */
    private boolean noDelay;

    /** Connection to emu domain server. */
    private cMsg emuDomain;

    // INPUT
    
    /**
     * Store locally whether this channel's module is an ER or not.
     * If so, don't parse incoming data so deeply - only top bank header.
     */
    private boolean isER = false;

    /** Threads used to read incoming data. */
    private DataInputHelper dataInputThread;

    /** Thread to parse incoming data and merge it into 1 ring if coming from multiple sockets. */
    private ParserMerger parserMergerThread;

    /** Data input streams from TCP sockets. */
    private DataInputStream in;

    /** Input socket. */
    private Socket socket;

    /** TCP receive buffer size in bytes. */
    private int tcpRecvBuf;

    /**
     * Node pools is used to get top-level EvioNode objects.
     * First index is socketCount, second is number of buffers
     * (64 total) in ByteBufferSupplys.
     */
    private EvioNodePool[] nodePools;

    // INPUT & OUTPUT

    /** Coda id of the data source. */
    private int sourceId;

    /**
     * Biggest chunk of data sent by data producer.
     * Allows good initial value of ByteBuffer size.
     */
    private int maxBufferSize;

    /** Use the evio block header's block number as a record id. */
    private int recordId = 1;

    /** Use direct ByteBuffer? */
    private boolean direct;

    /**
     * In order to get a higher throughput for fast networks,
     * this emu channel may use multiple sockets underneath. Defaults to 1.
     */
    private int socketCount;


    // Disruptor (RingBuffer)  stuff

    private long nextRingItem;

    /** Ring buffer holding ByteBuffers when using EvioCompactEvent reader for incoming events.
     *  One per socket (socketCount total). */
    protected ByteBufferSupply bbInSupply;


    /**
     * Constructor to create a new input DataChannelImplEt instance.
     *
     * @param name         the name of this channel
     * @param debug        debug output
     * @param outputIndex  order in which module's events will be sent to this
     *                     output channel (0 for first output channel, 1 for next, etc.).
     */
    DataChannelImplEmu(String name, boolean debug, int outputIndex) {

        // constructor of super class
        super(name, true, debug, outputIndex);

        // Use direct ByteBuffers or not, faster & more stable with non-direct.
        // Turn this off since performance is better.
        direct = false;
        this.debug = debug;
        // How many sockets to use underneath
        socketCount = 1;
        // size of TCP receive buffer (0 means use operating system default)
        tcpRecvBuf = 20000000;
        
        System.out.println("      DataChannel Emu: creating input channel " + name);
    }

    /**
     * Constructor to create a new output DataChannelImplEt instance.
     *
     * @param name         the name of this channel
     * @param debug        debug output
     */
    DataChannelImplEmu(String name, boolean debug,
                       int codaId, String expid, String serverIp,
                       int serverPort, ByteOrder order) {

        // constructor of super class
        super(name, false, debug, 0);

        this.id = codaId;
        this.expid = expid;
        this.serverIP = serverIp;
        this.sendPort = serverPort;
        this.byteOrder = order;

        // Use direct ByteBuffers or not, faster & more stable with non-direct.
        // Turn this off since performance is better.
        direct = false;
        this.debug = debug;

        // How many sockets to use underneath
        socketCount = 1;
        // set TCP_NODELAY option on
        noDelay = true;
        // size of TCP send buffer (0 means use operating system default)
        tcpSendBuf = 5000000;
        // Send port
//        sendPort = cMsgNetworkConstants.emuTcpPort;
        // Size of max buffer
        maxBufferSize = 4000000;

        System.out.println("      DataChannel Emu: creating output channel " + name);
    }


    /**
     * Once a client connects to the Emu domain server in the Emu transport object,
     * that socket is passed to this method and a thread is spawned to handle all
     * communications over it. Only used for input channel.<p>
     *
     * This method is called synchronously by a single thread in the
     * EmuDomainTcpServer class.
     *
     * @param channel        data input socket/channel
     * @param sourceId       CODA id # of data source
     * @param maxBufferSize  biggest chunk of data expected to be sent by data producer
     *
     * @throws IOException   if exception dealing with socket or input stream
     */
    final void attachToInput(SocketChannel channel, int sourceId, int maxBufferSize) throws IOException {

        // Create a ring buffer full of empty ByteBuffer objects
        // in which to copy incoming data from client.
        // Using direct buffers works but performance is poor and fluctuates
        // quite a bit in speed.
        //
        // A DC with 13 inputs can quickly consume too much memory if we're not careful.
        // Put a limit on the total amount of memory used for all emu socket input channels.
        // Total limit is 1GB. This is probably the easiest way to figure out how many buffers to use.
        // Number of bufs must be a power of 2 with a minimum of 16 and max of 128.
        //
        // int channelCount = emu.getInputChannelCount();
        // int numBufs = 1024000000 / (maxBufferSize * channelCount);
        // numBufs = numBufs <  16 ?  16 : numBufs;
        // numBufs = numBufs > 128 ? 128 : numBufs;
        //
        // Reducing numBufs to 32 increases barrier.waitfor() time from .02% to .4% of EB time
        int numBufs = 32;
        
        // Initialize things once
        parserMergerThread = new ParserMerger();
        nodePools = new EvioNodePool[numBufs];
        this.sourceId = sourceId;
        this.maxBufferSize = maxBufferSize;

        // Set socket options
        socket = channel.socket();

        // Set TCP receive buffer size
        if (tcpRecvBuf > 0) {
            socket.setPerformancePreferences(0,0,1);
            socket.setReceiveBufferSize(tcpRecvBuf);
        }

        // Use buffered streams for efficiency
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        // EBs release events sequentially if there's only 1 build thread,
        // else the release is NOT sequential.
        boolean sequentialRelease = true;

//logger.info("      DataChannel Emu in: seq release of buffers = " + sequentialRelease);

        // Create the EvioNode pools - each socket gets numBuf number of pools -
        // each of which contain 3500 EvioNodes to begin with. These are used for
        // the nodes of each event.
        for (int i = 0; i < numBufs; i++) {
            nodePools[i] = new EvioNodePool(3500);
        }
//logger.info("      DataChannel Emu in: created " + (numBufs) + " node pools for socket " + index + ", " + name());

        bbInSupply = new ByteBufferSupply(numBufs, 32,
                                          ByteOrder.BIG_ENDIAN, direct,
                                          sequentialRelease, nodePools);

        System.out.println("      DataChannel Emu in: connection made from " + name);

        // Start thread to handle socket input
        dataInputThread = new DataInputHelper();
        dataInputThread.start();

        // If this is the last socket, make sure all threads are started up before proceeding
        parserMergerThread.start();
        dataInputThread.waitUntilStarted();
        
        System.out.println("      DataChannel Emu in: last connection made, parser thd started, input threads running");
    }



    /**
     * Open a client output channel to the EmuSocket server.
     * @throws cMsgException if communication problems with server.
     */
    private void openOutputChannel() throws cMsgException {

        // "name" is name of this channel which also happens to be the
        // destination CODA component we want to connect to.

        StringBuilder builder = new StringBuilder(256);

        builder.append("emu://").append(serverIP).append(':').append(sendPort);
        builder.append('/').append(expid).append('/').append(name);
        builder.append("?codaId=").append(id);

        if (maxBufferSize > 0) {
            builder.append("&bufSize=").append(maxBufferSize);
        }
        else {
            builder.append("&bufSize=4000000");
        }

        if (tcpSendBuf > 0) {
            builder.append("&tcpSend=").append(tcpSendBuf);
        }

        if (noDelay) {
            builder.append("&noDelay");
        }

        // This connection will contain "sockCount" number of sockets
        // which are all used to send data.
        try {
            if (debug) System.out.println("      DataChannel Emu out: will directly connect to server w/ UDL = " + builder.toString());
            emuDomain = new cMsg(builder.toString(), name, "emu domain client");
            emuDomain.connect();
            startOutputThread();
            return;
        }
        catch (cMsgException e) {
            System.out.println("      DataChannel Emu out: could not connect to server at " + serverIP);
            builder.delete(0, builder.length());
        }

        throw new cMsgException("Cannot connect to IP address " + serverIP + " & port " + sendPort);
    }


    private void closeOutputChannel() throws cMsgException {
        if (input) return;
        // flush and close sockets
        emuDomain.disconnect();
    }


    private void closeInputSockets() {
        if (!input) return;

        try {
            in.close();
            // Will close socket, associated channel & stream
            socket.close();
        }
        catch (IOException e) {}
    }



    /** {@inheritDoc} */
    public void prestart() {
        haveInputEndEvent = false;
        if (input) return;

        try {
             openOutputChannel();
        }
        catch (cMsgException e) {
             e.printStackTrace();
        }
    }


    /** {@inheritDoc} */
    public int getInputLevel() {
        int supplyLevel, level = 0;

        if (bbInSupply == null) {
            supplyLevel = 0;
        }
        else {
            supplyLevel = bbInSupply.getFillLevel();
        }
        level = level > supplyLevel ? level : supplyLevel;
        return level;
    }



    /**
     * Interrupt all threads.
     */
    private void interruptThreads() {

        if (dataInputThread != null) {
            // The parser merger thread needs to be interrupted first,
            // otherwise the parseToRing method may get stuck waiting
            // on further data in a loop around parkNanos().
//logger.debug("      DataChannel Emu: end/reset(), interrupt parser/merger thread");
            parserMergerThread.interrupt();
            try {Thread.sleep(10);}
            catch (InterruptedException e) {}

            if (dataInputThread != null) {
//logger.debug("      DataChannel Emu: end/reset(), interrupt input thread " + i);
                dataInputThread.interrupt();
            }
        }

        if (dataOutputThread != null) {
//System.out.println("      DataChannel Emu: end/reset(), interrupt main output thread ");
            dataOutputThread.interrupt();

            for (int i=0; i < socketCount; i++) {
//System.out.println("      DataChannel Emu: end/reset(), interrupt output thread " + i);
                dataOutputThread.sender[i].endThread();
            }
        }
    }

    
    /**
     * Try joining all threads, up to 1 sec each.
     */
    private void joinThreads() {
        if (dataInputThread != null) {
            try {parserMergerThread.join(1000);}
            catch (InterruptedException e) {}

//logger.debug("      DataChannel Emu: end/reset(), joined parser/merger thread");
                if (dataInputThread != null) {
                    try {
                        dataInputThread.join(1000);
                    }
                    catch (InterruptedException e) {
                    }
                }
//logger.debug("      DataChannel Emu: end/reset(), joined input thread " + i);
        }


        if (dataOutputThread != null) {

            try {dataOutputThread.join(1000);}
            catch (InterruptedException e) {}

//System.out.println("      DataChannel Emu: end/reset(), joined main output thread ");

            for (int i=0; i < socketCount; i++) {
                try {dataOutputThread.sender[i].join(1000);}
                catch (InterruptedException e) {}
//System.out.println("      DataChannel Emu: end/reset(), joined output thread " + i);
            }
        }
    }
    

    /** {@inheritDoc}. Formerly this code was the close() method. */
    public void end() {
        gotEndCmd   = true;
        gotResetCmd = false;

        // The emu's emu.end() method first waits (up to 60 sec) for the END event to be read
        // by input channels, processed by the module, and finally to be sent by
        // the output channels. Then it calls everyone's end() method including this one.
        // Threads and sockets can be shutdown quickly, since we've already
        // waited for the END event.

        interruptThreads();
        joinThreads();

        // Clean up
        if (dataInputThread != null) {
            dataInputThread = null;
            parserMergerThread = null;
            closeInputSockets();
        }

        if (dataOutputThread != null) {
            for (int i=0; i < socketCount; i++) {
                dataOutputThread.sender[i] = null;
            }
            dataOutputThread = null;

            try {
System.out.println("      DataChannel Emu: end(), close output channel " + name);
                closeOutputChannel();
            }
            catch (cMsgException e) {}
        }

    }


    private void startOutputThread() {
        dataOutputThread = new DataOutputHelper();
        dataOutputThread.start();
        dataOutputThread.waitUntilStarted();
    }





    /**
     * Class used to get data over network and put into ring buffer.
     * There is one of these for each of the "socketCount" number of TCP sockets.
     */
    private final class DataInputHelper extends Thread {
        
        /** Let a single waiter know that the main thread has been started. */
        private final CountDownLatch latch = new CountDownLatch(1);

        /** Data input stream from TCP socket. */
        private final DataInputStream inStream;

        /** Supply of ByteBuffers to use for this socket. */
        private final ByteBufferSupply bbSupply;


        /** Constructor. */
        DataInputHelper() {
            super(name() + "_data_in");
            inStream = in;
            bbSupply = bbInSupply;
        }


        /** A single waiter can call this method which returns when thread was started. */
        private void waitUntilStarted() {
            try {
                latch.await();
            }
            catch (InterruptedException e) {}
        }


        /** {@inheritDoc} */
        @Override
        public void run() {

            // Tell the world I've started
            latch.countDown();

            long word;
            int cmd, size;

            ByteBuffer buf;
            ByteBufferItem item;

            try {

                while (true) {
                    // If I've been told to RESET ...
                    if (gotResetCmd) {
                        return;
                    }

                    // Sets the producer sequence
                    item = bbSupply.get();
//System.out.println("      DataChannel Emu in: GOT item " + item.myIndex + " from ByteBuffer supply");

                    // First read the command & size with one read, into a long.
                    // These 2, 32-bit ints are sent in network byte order, cmd first.
                    // Reading a long assumes big endian so cmd, which is sent
                    // first, should appear in most significant bytes.

//System.out.println("      DataChannel Emu in: Try reading buffer hdr words");
                        word = inStream.readLong();
                        cmd  = (int) ((word >>> 32) & 0xffL);
                        size = (int)   word;   // just truncate for lowest 32 bytes
                        item.ensureCapacity(size);
                        buf = item.getBuffer();
                        buf.limit(size);

                        inStream.readFully(item.getBuffer().array(), 0, size);

//System.out.println("      DataChannel Emu in: " + name + ", incoming buf size = " + size);
//Utilities.printBuffer(item.getBuffer(), 0, size/4, "PRESTART EVENT, buf lim = " + buf.limit());
                    bbSupply.publish(item);

                    // We just received the END event
                    if (cmd == cMsgConstants.emuEvioEndEvent) {
                        System.out.println("      DataChannel Emu in: " + name + ", got END event, exit reading thd");
                        return;
                    }
                }
            }
            catch (InterruptedException e) {
                System.out.println("      DataChannel Emu in: " + name + ", interrupted, exit reading thd");
            }
            catch (AsynchronousCloseException e) {
                System.out.println("      DataChannel Emu in: " + name + ", socket closed, exit reading thd");
            }
            catch (EOFException e) {
                // Assume that if the other end of the socket closes, it's because it has
                // sent the END event and received the end() command.
                System.out.println("      DataChannel Emu in: " + name + ", other end of socket closed, exit reading thd");
            }
            catch (Exception e) {
                if (haveInputEndEvent) {
                    System.out.println("      DataChannel Emu in: " + name +
                                               ", exception but already have END event, so exit reading thd");
                    return;
                }
                e.printStackTrace();
             }
        }
    }


    /**
     * Class to consume all buffers read from all sockets, parse them into evio events,
     * and merge this data from multiple sockets by placing them into this
     * channel's single ring buffer.
     */
    private final class ParserMerger extends Thread {

        /** Object used to read/parse incoming evio data. */
        private EvioCompactReader reader;


        /** Constructor. */
        ParserMerger() {super("parser_merger");}


        public void run() {
            try {
                while (true) {
                    // Sets the consumer sequence
                    ByteBufferItem item = bbInSupply.consumerGet();
                    if (parseToRing(item, bbInSupply)) {
                        System.out.println("      DataChannel Emu in: 1 quit parser/merger thread for END event from " + name);
                        break;
                    }
                }
            }
            catch (InterruptedException e) {
//                System.out.println("      DataChannel Emu in: " + name +
//                            " parserMerger thread interrupted, quitting ####################################");
            }
            catch (EvioException e) {
                // Bad data format or unknown control event.
                e.printStackTrace();
            }
        }


        /**
         * Parse the buffer into evio bits that get put on this channel's ring.
         *
         * @param item        ByteBufferSupply item containing buffer to be parsed.
         * @param bbSupply    ByteBufferSupply item.
         * @return is the last evio event parsed the END event?
         * @throws EvioException
         * @throws InterruptedException
         */
        private boolean parseToRing(ByteBufferItem item, ByteBufferSupply bbSupply)
                throws EvioException, InterruptedException {

            RingItem ri;
            EvioNode node;
            boolean hasFirstEvent, isUser=false;
            ControlType controlType = null;
            EvioNodeSource pool;

            // Get buffer from an item from ByteBufferSupply - one per channel
            ByteBuffer buf = item.getBuffer();

            try {
                // Pool of EvioNodes associated with this buffer
                pool = (EvioNodePool)item.getMyObject();
                // Each pool must be reset only once!
                pool.reset();
                if (reader == null) {
                    reader = new EvioCompactReader(buf, pool, false, false);
                }
                else {
                    reader.setBuffer(buf, pool);
                }
            }
            catch (EvioException e) {
                System.out.println("      DataChannel Emu in: data NOT evio format 1");
                e.printStackTrace();
                Utilities.printBytes(buf, 0, 80, "BAD BUFFER TO PARSE");
                throw e;
            }

            // First block header in buffer
            IBlockHeader blockHeader = reader.getFirstBlockHeader();
            if (blockHeader.getVersion() < 4) {
                throw new EvioException("Data not in evio but in version " +
                                                blockHeader.getVersion());
            }

            hasFirstEvent = blockHeader.hasFirstEvent();

            // The DAQ is NOT streaming data, so there is one ROC Raw Record that is being parsed.

            EventType eventType = EventType.getEventType(blockHeader.getEventType());
            if (eventType == null || !eventType.isEbFriendly()) {
//System.out.println("      DataChannel Emu in: Record's event type int = " + blockHeader.getEventType() +
//                   ", type = " + eventType);
                throw new EvioException("bad evio format or improper event type");
            }

            recordId = blockHeader.getNumber();
            
            // Each PayloadBuffer contains a reference to the buffer it was
            // parsed from (buf).
            // This cannot be released until the module is done with it.
            // Keep track by counting users (# events parsed from same buffer).
            int eventCount = reader.getEventCount();
            item.setUsers(eventCount);

//    System.out.println("      DataChannel Emu in: block header, event type " + eventType +
//                       ", recd id = " + recordId + ", event cnt = " + eventCount);

            for (int i = 1; i < eventCount + 1; i++) {
                // Type may change if there are mixed types in record
                EventType evType = eventType;

                nextRingItem = ringBufferIn.nextIntr(1);
                ri = ringBufferIn.get(nextRingItem);

                if (isER) {
                    // Don't need to parse all bank headers, just top level.
                    node = reader.getEvent(i);
                }
                else {
                    // getScannedEvent will clear child and allNodes lists
                    node = reader.getScannedEvent(i, pool);
                }

                // This should NEVER happen
                if (node == null) {
                    System.out.println("      DataChannel Emu in: WARNING, event count = " + eventCount +
                                               " but get(Scanned)Event(" + i + ") is null - evio parsing bug");
                    continue;
                }

                // Complication: from the ROC, we'll be receiving USER events
                // mixed in with and labeled as ROC Raw events. Check for that
                // and fix it.
                if (evType.isROCRaw()) {
                    if (Evio.isUserEvent(node)) {
                        isUser = true;
                        eventType = EventType.USER;
                        if (hasFirstEvent) {
                            System.out.println("      DataChannel Emu in: " + name + "  FIRST event from ROC RAW");
                        }
                        else {
                            System.out.println("      DataChannel Emu in: " + name + " USER event from ROC RAW");
                        }
                    }
                    else {
                        // Pick this raw data event apart a little
                        if (!node.getDataTypeObj().isBank()) {
                            DataType eventDataType = node.getDataTypeObj();
                            throw new EvioException("ROC raw record contains " + eventDataType +
                                                            " instead of banks (data corruption?)");
                        }
                    }
                }
                else if (evType.isControl()) {
                    // Find out exactly what type of control event it is
                    // (May be null if there is an error).
                    controlType = ControlType.getControlType(node.getTag());
                    System.out.println("      DataChannel Emu in: got " + controlType + " event from " + name);
                    if (controlType == null) {
                        System.out.println("      DataChannel Emu in: found unidentified control event");
                        throw new EvioException("Found unidentified control event");
                    }
                }
                else if (eventType.isUser()) {
                    isUser = true;
                    if (hasFirstEvent) {
                        System.out.println("      DataChannel Emu in: " + name + " got FIRST event");
                    }
                    else {
                        System.out.println("      DataChannel Emu in: " + name + " got USER event");
                    }
                }
                else if (evType.isMixed()) {
                    // Mix of event types.
                    // Can occur for combo of user, ROC RAW and possibly control events.
                    // Only occurs when a user inserts a User event during the End transition.
                    // What happens is that the User Event gets put into a EVIO Record which can
                    // also contain ROC RAW events. The evio record gets labeled as containing
                    // mixed events.
                    //
                    // This will NOT occur in ER, so headers are all parsed at this point.
                    // Look at the very first header, second word.
                    // num = 0  --> it's a control or User event (tag tells which):
                    //          0xffd0 <= tag <= 0xffdf --> control event
                    //          else                    --> User event
                    // num > 0  --> block level for ROC RAW

                    int num = node.getNum();
                    if (num == 0) {
                        int tag = node.getTag();
                        if (ControlType.isControl(tag)) {
                            controlType = ControlType.getControlType(tag);
                            evType = EventType.CONTROL;
                            System.out.println("      DataChannel Emu in: " + name + " mixed type to " + controlType.name());
                        }
                        else {
                            isUser = true;
                            evType = EventType.USER;
                            System.out.println("      DataChannel Emu in: " + name + " mixed type to user type");
                        }
                    }
                    else {
//logger.info("      DataChannel Emu in: " + name + " mixed type to ROC RAW");
                        evType = EventType.ROC_RAW;
                        // Pick this raw data event apart a little
                        if (!node.getDataTypeObj().isBank()) {
                            DataType eventDataType = node.getDataTypeObj();
                            throw new EvioException("ROC raw record contains " + eventDataType +
                                                            " instead of banks (data corruption?)");
                        }
                    }
                }
                else {
                    // Physics or partial physics event must have BANK as data type
                    if (!node.getDataTypeObj().isBank()) {
                        DataType eventDataType = node.getDataTypeObj();
                        throw new EvioException("physics record contains " + eventDataType +
                                                        " instead of banks (data corruption?)");
                    }
                }
                

                // Set & reset all parameters of the ringItem
                if (evType.isBuildable()) {
                    ri.setAll(null, null, node, evType, controlType,
                              isUser, hasFirstEvent, false, id, recordId, sourceId,
                              node.getNum(), name, item, bbSupply);
                }
                else {
                    ri.setAll(null, null, node, evType, controlType,
                              isUser, hasFirstEvent, false, id, recordId, sourceId,
                              1, name, item, bbSupply);
                }

                // Only the first event of first block can be "first event"
                isUser = hasFirstEvent = false;

                ringBufferIn.publish(nextRingItem);

                // Handle end event ...
                if (controlType == ControlType.END) {
                    // There should be no more events coming down the pike so
                    // go ahead write out existing events and then shut this
                    // thread down.
                    haveInputEndEvent = true;
                    break;
                }
            }

            return haveInputEndEvent;
        }


    }






    /**
     * Class used to take Evio banks from ring buffer (placed there by a module),
     * and write them over network to an Emu domain input channel using the Emu
     * domain output channel.
     */
    private final class DataOutputHelper extends Thread {

        /** Let a single waiter know that the main thread has been started. */
        private final CountDownLatch startLatch = new CountDownLatch(1);

        /** Object to write (marshall) input buffers into larger, output evio buffer (next member). */
        private EventWriterUnsyncV4 writer;

        /** Buffer to write events into so it can be sent in a cMsg message. */
        private ByteBuffer currentBuffer;

        /** ByteBuffer supply item that currentBuffer comes from. */
        private ByteBufferItem currentBBitem;

        /** Index into sender array to SocketSender currently being used. */
        private int currentSenderIndex;

        /** Entry in evio block header. */
        private final BitSet bitInfo = new BitSet(24);

        /** Type of last event written out. */
        private EventType previousEventType;


        /** Time at which events were sent over socket. */
        private volatile long lastSendTime;

        /** Sender threads to send data over network. */
        private final SocketSender[] sender;

        /** One ByteBufferSupply for each sender/socket. */
        private final ByteBufferSupply[] bbOutSupply;


        /** When regulating output buffer flow, the current
         * number of physics events written to buffer. */
        private int currentEventCount;




        ByteBuffer bufferPrev;

        /**
         * This class is a separate thread used to write filled data
         * buffers over the emu socket.
         */
        private final class SocketSender extends Thread {

            /** Boolean used to kill this thread. */
            private volatile boolean killThd;

            /** The ByteBuffers to send. */
            private final ByteBufferSupply supply;

            /** cMsg message into which out going data is placed in order to be written. */
            private final cMsgMessageFull outGoingMsg;

            private int socketIndex;



            SocketSender(ByteBufferSupply supply, int socketIndex) {
                super(name + "_sender_"+ socketIndex);

                this.supply = supply;
                this.socketIndex = socketIndex;

                // Need do this only once
                outGoingMsg = new cMsgMessageFull();
                // Message format
                outGoingMsg.setUserInt(cMsgConstants.emuEvioFileFormat);
                // Tell cmsg which socket to use
                outGoingMsg.setSysMsgId(socketIndex);
            }

            /**
             * Kill this thread which is sending messages/data to other end of emu socket.
             */
            final void endThread() {
System.out.println("SocketSender: killThread, set flag, interrupt");
                killThd = true;
                this.interrupt();
            }


            /**
             * Send the events currently marshalled into a single buffer.
             */
            public void run() {
                boolean isEnd;

                while (true) {
                    if (killThd) {
System.out.println("SocketSender thread told to return");
                        return;
                    }

                    try {
//                        Thread.sleep(2000);
                        
                        // Get a buffer filled by the other thread
                        ByteBufferItem item = supply.consumerGet();
                        ByteBuffer buf = item.getBufferAsIs();
//Utilities.printBuffer(buf, 0, 40, "PRESTART EVENT, buf lim = " + buf.limit());

                        // Put data into message
                        if (direct) {
                            outGoingMsg.setByteArray(buf);
                        }
                        else {
                            outGoingMsg.setByteArrayNoCopy(buf.array(), buf.arrayOffset(),
                                                           buf.remaining());
                        }

                        // User boolean is true if this buf contains END event,
                        // so signify that in command (user int).
                        isEnd = item.getUserBoolean();
                        if (isEnd) {
                            outGoingMsg.setUserInt(cMsgConstants.emuEvioEndEvent);
                        }
                        else {
                            outGoingMsg.setUserInt(cMsgConstants.emuEvioFileFormat);
                        }

                        // Send it
                        emuDomain.send(outGoingMsg);

                        // Force things out over socket
                        if (item.getForce()) {
                            try {
                                emuDomain.flush(0);
                            }
                            catch (cMsgException e) {
                            }
                        }

                        // Release this buffer so it can be filled again
//System.out.println("release " + item.getMyId() + ", rec # = " + item.getConsumerSequence());
                        supply.release(item);
//System.out.println("released rec # = " + item.getConsumerSequence());
                    }
                    catch (InterruptedException e) {
System.out.println("SocketSender thread interrupted");
                        return;
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }

                    lastSendTime = System.currentTimeMillis();
                }
            }
        }


        /** Constructor. */
        DataOutputHelper() {
            super(name + "_data_out");

            // All buffers will be released in order in this code.
            // This will improve performance since mutexes can be avoided.
            boolean orderedRelease = true;

            bufferPrev = ByteBuffer.allocate(maxBufferSize).order(byteOrder);

            sender = new SocketSender[socketCount];
            bbOutSupply = new ByteBufferSupply[socketCount];

            for (int i=0; i < socketCount; i++) {
                // A mini ring of buffers, 16 is the best size
                if (debug) System.out.println("DataOutputHelper constr: making BB supply of 8 bufs @ bytes = " + maxBufferSize);
                bbOutSupply[i] = new ByteBufferSupply(16, maxBufferSize, byteOrder,
                                                      direct, orderedRelease);

                // Start up sender thread
                sender[i] = new SocketSender(bbOutSupply[i], i);
                sender[i].start();
            }

            // Create writer to write events into file format
            try {

                // Start out with a single buffer from the first supply just created
                currentSenderIndex = 0;
                currentBBitem = bbOutSupply[currentSenderIndex].get();
                currentBuffer = currentBBitem.getBuffer();
//System.out.println("\nFirst current buf -> rec # = " + currentBuffer.getInt(4) +
//                           ", " + System.identityHashCode(currentBuffer));
                // For debug purposes, limit record to 1 event
                int maxEventCount = 100000;  // 100000   orig
                writer = new EventWriterUnsyncV4(currentBuffer, 0, maxEventCount, null, null, 0, 1);


                writer.close();
            }
            catch (InterruptedException e) {/* never happen */}
            catch (EvioException e) {/* never happen */}
        }


        /** A single waiter can call this method which returns when thread was started. */
        private final void waitUntilStarted() {
            try {
                startLatch.await();
            }
            catch (InterruptedException e) {
            }
        }


        /**
         * Put the current buffer of events back into the bbOutSupply ring for its
         * consumer which is the writing thread.
         *
         * @param force    if true, force data over socket
         * @param userBool user boolean to be set in byte buffer item. In this case,
         *                 if true, event being flushed is single END event.
         * @param isData   if true, current item is data (not control or user event).
         * @throws InterruptedException
         */
        private void flushEvents(boolean force, boolean userBool, boolean isData)
                throws InterruptedException {
            
            // Position the buffer
            writer.close();

            // We must have something to write
            if (writer.getEventsWritten() < 1) {
                return;
            }

            currentEventCount = 0;

            // Store flags for future use
            currentBBitem.setForce(force);
            currentBBitem.setUserBoolean(userBool);

            // Put the written-into buffer back into the supply so the consumer -
            // the thread which writes it over the network - can get it and
            // write it.
//            ByteBuffer bb = writer.getByteBuffer();
            currentBuffer.flip();
            currentBuffer.limit((int)writer.getBytesWrittenToBuffer());

//System.out.println("flushEvents: reading buf limit = " + bb.limit());
//System.out.println("flushEvents: setting current buf lim = " + currentBuffer.limit());

            bbOutSupply[currentSenderIndex].publish(currentBBitem);

            // Get another buffer from the supply so writes can continue.
            // It'll block if none available.

//Thread.sleep(200);
            currentBBitem = bbOutSupply[currentSenderIndex].get();
            currentBuffer = currentBBitem.getBuffer();
//System.out.println("flushEvents: out\n");
        }


        /**
         * Flush already written events over sockets.
         * This is only called when data rate is slow and data must
         * be forced over the network.
         * @throws InterruptedException
         */
        private void flushExistingEvioData() throws InterruptedException {
            // Don't write nothin'
            if (currentEventCount == 0) {
                return;
            }

            if (previousEventType.isBuildable()) {
                flushEvents(true, false, true);
            }
            else {
                flushEvents(true, false, false);
            }
        }


        /**
         * Write events into internal buffer and, if need be, flush
         * them over socket. Force all non-buildable events, like control
         * and user events, to be sent immediately.
         *
         * @param rItem event to write
         * @throws IOException if error writing evio data to buf
         * @throws EvioException if error writing evio data to buf (bad format)
         * @throws EmuException if no data to write or buffer is too small to hold 1 event.
         * @throws InterruptedException if thread interrupted.
         */
        private void writeEvioData(RingItem rItem)
                throws IOException, EvioException, EmuException, InterruptedException {

            int blockNum;
            EventType eType = rItem.getEventType();
            boolean isBuildable = eType.isBuildable();
            int eventsWritten = writer.getEventsWritten();

            // If we're sending out 1 event by itself ...
            if (!isBuildable) {
                currentEventCount = 0;

                // If we already have something stored-up to write, send it out first
                if (eventsWritten > 0 && !writer.isClosed()) {
                    if (previousEventType.isBuildable()) {
                        flushEvents(false, false, true);
                    }
                    else {
                        flushEvents(true, false, false);
                    }
                    // This will close the writer and reset currentBBitem & currentBuffer
                }

                blockNum = -1;

                recordId++;
//System.out.println("      DataChannel Emu out: writeEvioData: record Id set to " + blockNum +
//                  ", then incremented to " + recordId);

                // Make sure there's enough room for that one event
                if (rItem.getTotalBytes() > currentBuffer.capacity()) {
                    currentBBitem.ensureCapacity(rItem.getTotalBytes() + 1024);
                    currentBuffer = currentBBitem.getBuffer();
//System.out.println("\n  &&&&&  DataChannel Emu out: writeEvioData:  expand 1 current buf -> rec # = " + currentBuffer.getInt(4));
                }

                // Write the event ..
                EmuUtilities.setEventType(bitInfo, eType);
                if (rItem.isFirstEvent()) {
                    EmuUtilities.setFirstEvent(bitInfo);
                }
//System.out.println("      DataChannel Emu out: writeEvioData: single write into buffer");
                writer.setBuffer(currentBuffer, bitInfo, blockNum);

                // Unset first event for next round
                EmuUtilities.unsetFirstEvent(bitInfo);

                ByteBuffer buf = rItem.getBuffer();
                if (buf != null) {
                    try {
//System.out.println("      DataChannel Emu out: writeEvioData: single ev buf, pos = " + buf.position() +
//", lim = " + buf.limit() + ", cap = " + buf.capacity());
                        boolean fit = writer.writeEvent(buf);
                        if (!fit) {
                            // Our buffer is too small to fit even 1 event!
                            throw new EmuException("emu socket's buffer size must be increased in jcedit");
                        }
//                        Utilities.printBufferBytes(buf, 0, 20, "control?");
                    }
                    catch (Exception e) {
System.out.println("      c: single ev buf, pos = " + buf.position() +
                   ", lim = " + buf.limit() + ", cap = " + buf.capacity());
                        Utilities.printBytes(buf, 0, 20, "bad END?");
                        throw e;
                    }
                }
                else {
                    EvioNode node = rItem.getNode();
                    if (node != null) {
                        boolean fit = writer.writeEvent(node, false);
                        if (!fit) {
                            // Our buffer is too small to fit even 1 event!
                            throw new EmuException("emu socket's buffer size must be increased in jcedit");
                        }
                    }
                    else {
                        throw new EmuException("no data to write");
                    }
                }
                rItem.releaseByteBuffer();


//System.out.println("      DataChannel Emu out: writeEvioData: flush " + eType + " type event, FORCE");
                    if (rItem.getControlType() == ControlType.END) {
//System.out.println("      DataChannel Emu out: writeEvioData: call flushEvents for END");
                        flushEvents(true, true, false);
                    }
                    else {
//System.out.println("      DataChannel Emu out: writeEvioData: call flushEvents for non-END");
                        flushEvents(true, false, false);
                    }
            }
            // If we're marshalling events into a single buffer before sending ...
            else {
//System.out.println("      DataChannel Emu out: writeEvioData: events into buf, written = " + eventsWritten +
//", closed = " + writer.isClosed());
                // If we've already written at least 1 event AND
                // (we have no more room in buffer OR we're changing event types),
                // write what we have.
                if ((eventsWritten > 0 && !writer.isClosed())) {
                    // If previous type not data ...
                    if (previousEventType != eType) {
//System.out.println("      DataChannel Emu out: writeEvioData *** switch types, call flush at current event count = " + currentEventCount);
                        flushEvents(false, false, false);
                    }
                    // Else if there's no more room or have exceeded event count limit ...
                    else if (!writer.hasRoom(rItem.getTotalBytes())) {
//System.out.println("      DataChannel Emu out: writeEvioData *** no room so call flush at current event count = " + currentEventCount);
                        flushEvents(false, false, true);
                    }
//                    else {
//System.out.println("      DataChannel Emu out: writeEvioData *** PLENTY OF ROOM, has room = " +
//                           writer.hasRoom(rItem.getTotalBytes()));
//                    }
                    // Flush closes the writer so that the next "if" is true,
                    // and currentBBitem & currentBuffer are reset
                }

                boolean writerClosed = writer.isClosed();

                // Initialize writer if nothing written into buffer yet
                if (eventsWritten < 1 || writerClosed) {
                    // If we're here, we're writing the first event into the buffer.
                    // Make sure there's enough room for at least that one event.
                    if (rItem.getTotalBytes() > currentBuffer.capacity()) {
                        currentBBitem.ensureCapacity(rItem.getTotalBytes() + 1024);
                        currentBuffer = currentBBitem.getBuffer();
//System.out.println("      DataChannel Emu out: nothing written, but not enough room, ensuring cap bytes = " +
//                           (rItem.getTotalBytes() + 1024));
                    }
//                    else {
//System.out.println("      DataChannel Emu out: nothing written, but should already be enough room");
//                    }

                    // Reinitialize writer
                    EmuUtilities.setEventType(bitInfo, eType);
//System.out.println("\nwriteEvioData: setBuffer, eventsWritten = " + eventsWritten + ", writer -> " +
//                           writer.getEventsWritten());
                    writer.setBuffer(currentBuffer, bitInfo, recordId++);
//System.out.println("\nwriteEvioData: after setBuffer, eventsWritten = " + writer.getEventsWritten());
                }

//System.out.println("      DataChannel Emu write: write ev into buf");
                // Write the new event ..
                ByteBuffer buf = rItem.getBuffer();
                if (buf != null) {
                    boolean fit = writer.writeEvent(buf);
                    if (!fit) {
                        // Our buffer is too small to fit even 1 event!
                        throw new EmuException("emu socket's buffer size must be increased in jcedit");
                    }
                }
                else {
                    EvioNode node = rItem.getNode();
                    if (node != null) {
                        // Since this is an emu-socket output channel,
                        // it is getting events from either the building thread of an event builder
                        // or the event generating thread of a simulated ROC. In both cases,
                        // any node passed into the following function has a backing buffer only
                        // used by that single node. (This is NOT like an input channel when an
                        // incoming buffer has several nodes all parsed from that one buffer).
                        // In this case, we do NOT need to "duplicate" the buffer to avoid interfering
                        // with other threads using the backing buffer's limit & position because there
                        // are no such threads. Thus, we set the duplicate arg to false which should
                        // generate fewer objects and save computing power/time.
                        //
                        // In reality, however, all data coming from EB or ROC will be in buffers and
                        // not in node form, so this method will never be called. This is just here
                        // for completeness.
                        boolean fit = writer.writeEvent(node, false, false);
                        if (!fit) {
                            // Our buffer is too small to fit even 1 event!
                            throw new EmuException("emu socket's buffer size must be increased in jcedit");
                        }
                    }
                    else {
                        throw new EmuException("no data to write");
                    }
                }
                currentEventCount++;
                rItem.releaseByteBuffer();
            }

            previousEventType = eType;
        }


        /** {@inheritDoc} */
        @Override
        public void run() {

            // Tell the world I've started
            startLatch.countDown();

            try {
                RingItem ringItem;
                EventType pBankType;
                ControlType pBankControlType;

                // Time in milliseconds for writing if time expired
                long timeout = 2000L;
                lastSendTime = System.currentTimeMillis();


                ringIndex = 0;

                while (true) {

                    try {
                        ringItem = getNextOutputRingItem(ringIndex);
                    }
                    catch (InterruptedException e) {
                        return;
                    }

                    pBankType = ringItem.getEventType();
                    pBankControlType = ringItem.getControlType();

                    try {
                        writeEvioData(ringItem);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        errorMsg.compareAndSet(null, "Cannot write data: " + e.getMessage());
                        throw e;
                    }

//System.out.println("      DataChannel Emu out: send seq " + nextSequences[ringIndex] + ", release ring item");
                    releaseCurrentAndGoToNextOutputRingItem(ringIndex);

                    // Do not go to the next ring if we got a control or user event.
                    // All prestart, go, & users go to the first ring. Just keep reading
                    // until we get to a built event. Then start keeping count so
                    // we know when to switch to the next ring.
                    if (outputRingCount > 1 && pBankControlType == null && !pBankType.isUser()) {
                        setNextEventAndRing();
//System.out.println("      DataChannel Emu out, " + name + ": for seq " + nextSequences[ringIndex] + " SWITCH TO ring = " + ringIndex);
                    }

                    if (pBankControlType == ControlType.END) {
                        // END event automatically flushed in writeEvioData()
System.out.println("      DataChannel Emu out: " + name + " got END event, quitting 2");
                        return;
                    }

                    // If I've been told to RESET ...
                    if (gotResetCmd) {
System.out.println("      DataChannel Emu out: " + name + " got RESET cmd, quitting");
                        return;
                    }

                    // Time expired so send out events we have
//System.out.println("time = " + emu.getTime() + ", lastSendTime = " + lastSendTime);
                    long t = System.currentTimeMillis();
                    if (t - lastSendTime > timeout) {
//System.out.println("TIME /FLUSH ******************, time = " + t + ", last time = " + lastSendTime +
//        ", delta = " + (t - lastSendTime));
                        flushExistingEvioData();
                    }
                }

            }
            catch (InterruptedException e) {
                System.out.println("      DataChannel Emu out: " + name + "  interrupted thd, quitting");
            }
            catch (Exception e) {
                e.printStackTrace();
System.out.println("      DataChannel Emu out:" + e.getMessage());
            }
        }

    }

}
