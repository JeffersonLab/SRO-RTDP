/*
 * Copyright (c) 2022, Jefferson Science Associates
 *
 * Thomas Jefferson National Accelerator Facility
 * Data Acquisition Group
 *
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 */

package org.jlab.epsci.rtdp;


import org.jlab.coda.cMsg.cMsgConstants;
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
import java.util.concurrent.CountDownLatch;


/**
 * <p>
 * This class is copied from the DataChannelImpEmu class.
 * It main purpose is to read streaming format data over TCP from VTPs.
 * One way that it differs from the original class is that there is no
 * "fat pipe" or multiple socket option for one stream.
 * This is not necessary for the VTP which can send 4 individual streams
 * to get the desired bandwidth.
 * Some old code may be left in place in order to use the EmuDomainTcpServer
 * for convenience.</p>
 *
 *
 * To preferentially use IPv6, give following command line option to JVM:
 * -Djava.net.preferIPv6Addresses=true
 * Java sockets use either IPv4 or 6 automatically depending on what
 * kind of addresses are used. No need (or possibility) for setting
 * this explicitly.
 *
 * @author timmer
 * (8/8/2022)
 */
class DataChannelImplTcpStream extends DataChannelAdapter {

    /** Read END event from input ring. */
    private volatile boolean haveInputEndEvent;

    private final boolean debug;

    // OUTPUT

    // not using this channel for output


    // INPUT

    /** Coda id of the data source. */
    private int sourceId;

    /** Threads used to read incoming data. */
    private DataInputHelper dataInputThread;

    /** Thread to parse incoming data and merge it into 1 ring if coming from multiple sockets. */
    private ParserMerger parserMergerThread;

    /** Data input streams from TCP sockets. */
    private DataInputStream in;

    /** SocketChannels used to receive data. */
    private SocketChannel socketChannel;

    /** Socket used to receive data. */
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

    /**
     * Biggest chunk of data sent by data producer.
     * Allows good initial value of ByteBuffer size.
     */
    private int maxBufferSize;

    /** Use the evio block header's block number as a record id. */
    private int recordId = 1;

    /** Use direct ByteBuffer? */
    private boolean direct;

    // Disruptor (RingBuffer)  stuff

    private long nextRingItem;

    /** Ring buffer holding ByteBuffers when using EvioCompactEvent reader for incoming events. */
    protected ByteBufferSupply bbInSupply;




    /**
     * Constructor to create a new DataChannelImpTcpStream instance.
     *
     * @param name         the name of this channel
     */
    DataChannelImplTcpStream(String name, int streamNumber, boolean debug) {

        // constructor of super class
        super(name, true, debug, 0);

        this.debug = debug;
        this.streamNumber = streamNumber;

        // always INPUT channel
        if (debug) System.out.println("      DataChannel TcpStream: creating input channel " + name);

        // Use direct ByteBuffers or not, faster & more stable with non-direct.
        // Turn this off since performance is better.
        direct = false;

        // size of TCP receive buffer (0 means use operating system default)
        tcpRecvBuf = 20000000;
        if (debug) System.out.println("      DataChannel TcpStream: recvBuf = " + tcpRecvBuf);

//        startInputThread();
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
        //
        // numBufs = numBufs <  16 ?  16 : numBufs;
        // numBufs = numBufs > 128 ? 128 : numBufs;
        //
        // Make power of 2, round up
        // numBufs = EmuUtilities.powerOfTwo(numBufs, true);
        // System.out.println("\n\n      DataChannel TcpStream in: " + numBufs + " buffers in input supply\n\n");

        // Reducing numBufs to 32 increases barrier.waitfor() time from .02% to .4% of EB time
        int numBufs = 32;

        // Initialize things
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
        socketChannel = channel;
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        // EBs release events sequentially if there's only 1 build thread,
        // else the release is NOT sequential.
        boolean sequentialRelease = true;

//System.out.println("      DataChannel TcpStream in: seq release of buffers = " + sequentialRelease);

        // Create the EvioNode pools,
        // each of which contain 3500 EvioNodes to begin with. These are used for
        // the nodes of each event.
        for (int i = 0; i < numBufs; i++) {
            nodePools[i] = new EvioNodePool(3500);
        }
//System.out.println("      DataChannel TcpStream in: created " + (numBufs) + " node pools for socket " + index + ", " + name());

        bbInSupply = new ByteBufferSupply(numBufs, maxBufferSize,
                ByteOrder.BIG_ENDIAN, direct,
                sequentialRelease, nodePools);

        if (debug) System.out.println("      DataChannel TcpStream in: connection made from " + name);

        // Start thread to handle socket input
        dataInputThread = new DataInputHelper(bbInSupply);
        dataInputThread.start();

        // If this is the last socket, make sure all threads are started up before proceeding
        parserMergerThread.start();
        dataInputThread.waitUntilStarted();
        if (debug) System.out.println("      DataChannel TcpStream in: last connection made, parser thd started, input threads running");
    }


    private void closeInputSockets() {
        if (!input) return;
//        System.out.println("      DataChannel TcpStream in: close input sockets from " + name);

        try {
            in.close();
            // Will close socket, associated channel & streams
            socket.close();
        }
        catch (IOException e) {}
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
//System.out.println("      DataChannel TcpStream: end/reset(), interrupt parser/merger thread");
            parserMergerThread.interrupt();
            try {Thread.sleep(10);}
            catch (InterruptedException e) {}

            if (dataInputThread != null) {
                dataInputThread.interrupt();
//System.out.println("      DataChannel TcpStream: end/reset(), interrupt input thread " + i);
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
//System.out.println("      DataChannel TcpStream: end/reset(), joined parser/merger thread");

            if (dataInputThread != null) {
                try {dataInputThread.join(1000);}
                catch (InterruptedException e) {}
            }
//System.out.println("      DataChannel TcpStream: end/reset(), joined input thread " + i);
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
    }




//    /**
//     * For input channel, start the DataInputHelper thread which takes Evio
//     * file-format data, parses it, puts the parsed Evio banks into the ring buffer.
//     */
//    private void startInputThread() {
//        dataInputThread = new DataInputHelper();
//        dataInputThread.start();
//        dataInputThread.waitUntilStarted();
//    }


    /**
     * Class used to get data over network and put into ring buffer.
     * There is one of these for each of the "socketCount" number of TCP sockets.
     * Currently only one.
     */
    private final class DataInputHelper extends Thread {

        /** Let a single waiter know that the main thread has been started. */
        private final CountDownLatch latch = new CountDownLatch(1);

        /** Data input stream from TCP socket. */
        private final DataInputStream inStream;

        /** Supply of ByteBuffers to use for this socket. */
        private final ByteBufferSupply bbSupply;


        /** Constructor. */
        DataInputHelper(ByteBufferSupply bbSupply) {
            super("data_in");
            inStream = in;
            this.bbSupply = bbSupply;
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
if (debug) System.out.println("      DataChannel TcpStream in: GOT item " + item.myIndex + " from ByteBuffer supply");

                    // First read the command & size with one read, into a long.
                    // These 2, 32-bit ints are sent in network byte order, cmd first.
                    // Reading a long assumes big endian so cmd, which is sent
                    // first, should appear in most significant bytes.

                    //System.out.println("      DataChannel TcpStream in: Try reading buffer hdr words");
                    word = inStream.readLong();
                    cmd  = (int) ((word >>> 32) & 0xffL);
                    size = (int)   word;   // just truncate for lowest 32 bytes
                    item.ensureCapacity(size);
                    buf = item.getBuffer();
                    buf.limit(size);

if (debug) System.out.println("      DataChannel TcpStream in: got cmd = " + cmd + ", size = " + size + ", now read in data ...");
                    inStream.readFully(item.getBuffer().array(), 0, size);
//System.out.println("      DataChannel TcpStream in: done reading in data");

if (debug) System.out.println("      DataChannel TcpStream in: " + name + ", incoming buf size = " + size);

if (debug) {
    ByteOrder origOrder = buf.order();
    buf.order(ByteOrder.LITTLE_ENDIAN);
    Utilities.printBuffer(item.getBuffer(), 0, buf.limit() / 4, "BUFFER, lim = " + buf.limit());
    buf.order(origOrder);
}

                    bbSupply.publish(item);

                    // We just received the END event
                    if (cmd == cMsgConstants.emuEvioEndEvent) {
                        System.out.println("      DataChannel TcpStream in: " + name + ", got END event, exit reading thd");
                        return;
                    }
                }
            }
            catch (InterruptedException e) {
                System.out.println("      DataChannel TcpStream in: " + name + ", interrupted, exit reading thd");
            }
            catch (AsynchronousCloseException e) {
                System.out.println("      DataChannel TcpStream in: " + name + ", socket closed, exit reading thd");
            }
            catch (EOFException e) {
                // Assume that if the other end of the socket closes, it's because it has
                // sent the END event and received the end() command.
                System.out.println("      DataChannel TcpStream in: " + name + ", other end of socket closed, exit reading thd");
            }
            catch (Exception e) {
                if (haveInputEndEvent) {
                    System.out.println("      DataChannel TcpStream in: " + name +
                            ", exception but already have END event, so exit reading thd");
                    return;
                }
                e.printStackTrace();
            }
        }
    }


    /**
     * Class to consume all buffers read from the socket, parse them into evio events,
     * and place events into this channel's single ring buffer. It no longer "merges"
     * data from multiple inputs as it did in the EMU channel, but it does allow
     * separation of the socket reading code from the parsing code.
     */
    private final class ParserMerger extends Thread {

        /** Object used to read/parse incoming evio data. */
        private EvioCompactReader reader;


        /** Constructor. */
        ParserMerger() {
            super("parser_merger");
        }


        public void run() {
            try {
                // Simplify things when there's only 1 socket for better performance
                ByteBufferSupply bbSupply = bbInSupply;
                while (true) {
                    // Sets the consumer sequence
                    ByteBufferItem item = bbSupply.consumerGet();
                    if (parseStreamingToRing(item, bbSupply)) {
                        if (debug) System.out.println("      DataChannel TcpStream in: 1 quit streaming parser/merger thread for END event from " + name);
                        break;
                    }
                }
            }
            catch (InterruptedException e) {
//                System.out.println("      DataChannel TcpStream in: " + name +
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
        private boolean parseStreamingToRing(ByteBufferItem item, ByteBufferSupply bbSupply)
                throws EvioException, InterruptedException {

            RingItem ri;
            boolean hasFirstEvent, isUser=false;
            ControlType controlType = null;
            EvioNodeSource pool;

            // Get buffer from an item from ByteBufferSupply - one per channel
            ByteBuffer buf = item.getBuffer();
//Utilities.printBytes(buf, 0, 500, "Incoming buf");

            try {
                // Pool of EvioNodes associated with this buffer which grows as needed
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
                System.out.println("      DataChannel TcpStream in: data NOT evio format 1");
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

            int evtType = blockHeader.getEventType();
            EventType eventType = EventType.getEventType(blockHeader.getEventType());
            if (eventType == null || !eventType.isEbFriendly()) {
                System.out.println("bad evio format or improper event type (" + evtType + ")\n");
                Utilities.printBytes(buf, 0, 200, "Incoming (bad format bytes");
                throw new EvioException("bad evio format or improper event type (" + evtType + ")");
            }

            recordId = blockHeader.getNumber();

            // Check record for sequential record id (SKIP THIS)

            int eventCount = reader.getEventCount();
            boolean gotRocRaw  = eventType.isFromROC();
            boolean gotPhysics = eventType.isAnyPhysics();

            // For streaming ROC Raw, there is a ROC bank with at least 2 children -
            // one of which is a stream info bank (SIB) and the others which are
            // data banks, each of which must be parsed.
            if (gotRocRaw) {
                EvioNode topNode = reader.getScannedEvent(1, pool);
                if (topNode == null) {
                    throw new EvioException("Empty buffer arriving into input channel ???");
                }

                if (topNode.getChildCount() < 2) {
                    throw new EvioException("ROC Raw bank should have at least 2 children, not " + topNode.getChildCount());
                }
            }
            else if (gotPhysics) {
                EvioNode topNode = reader.getScannedEvent(1, pool);

                if (topNode == null) {
                    throw new EvioException("Empty buffer arriving into input channel ???");
                }

                if (!CODATag.isStreamingPhysics(topNode.getTag()))  {
                    throw new EvioException("Wrong tag for streaming Physics bank, got " +
                            CODATag.getName(topNode.getTag()));
                }
            }

            // Each PayloadBuffer contains a reference to the buffer it was
            // parsed from (buf).
            // This cannot be released until the module is done with it.
            // Keep track by counting users (# time slice banks parsed from same buffer).
            item.setUsers(eventCount);

            for (int i = 1; i < eventCount+1; i++) {

                int frame = 0;
                long timestamp = 0L;
                EvioNode topNode;


                // getScannedEvent will clear child and allNodes lists
                topNode = reader.getScannedEvent(i, pool);

                // This should NEVER happen
                if (topNode == null) {
                    System.out.println("      DataChannel TcpStream in: WARNING, event count = " + eventCount +
                            " but get(Scanned)Event(" + i + ") is null - evio parsing bug");
                    continue;
                }

                // RocRaw's, Time Slice Bank
                EvioNode node = topNode;

                if (gotRocRaw) {
                    // Complication: from the ROC, we'll be receiving USER events mixed
                    // in with and labeled as ROC Raw events. Check for that & fix it.
                    if (Evio.isUserEvent(node)) {
                        isUser = true;
                        eventType = EventType.USER;
                        if (hasFirstEvent) {
                            if (debug) System.out.println("      DataChannel TcpStream in: " + name + "  FIRST event from ROC RAW");
                        } else {
                            if (debug) System.out.println("      DataChannel TcpStream in: " + name + " USER event from ROC RAW");
                        }
                    }
                    else {
                        // Pick this raw data event apart a little
                        if (!node.getDataTypeObj().isBank()) {
                            DataType eventDataType = node.getDataTypeObj();
                            throw new EvioException("ROC raw record contains " + eventDataType +
                                    " instead of banks (data corruption?)");
                        }

                        // Find the frame and timestamp now for later ease of use (skip over 5 ints)
                        int pos = node.getPosition();
                        ByteBuffer buff = node.getBuffer();
                        frame = buff.getInt(20 + pos);
                        timestamp = EmuUtilities.intsToLong(buff.getInt(24 + pos), buff.getInt(28 + pos));
//System.out.println("      DataChannel TcpStream in: roc raw has frame = " + frame + ", timestamp = " + timestamp + ", pos = " + pos);
                    }
                }
                else if (eventType.isBuildable()) {
                    // If time slices coming from DCAG, SAG, or PAG
                    // Physics or partial physics event must have BANK as data type
                    if (!node.getDataTypeObj().isBank()) {
                        DataType eventDataType = node.getDataTypeObj();
                        throw new EvioException("physics record contains " + eventDataType +
                                " instead of banks (data corruption?)");
                    }

                    int pos = node.getPosition();
                    // Find the frame and timestamp now for later ease of use (skip over 4 ints)
                    ByteBuffer buff = node.getBuffer();
                    frame = buff.getInt(20 + pos);
                    timestamp = EmuUtilities.intsToLong(buff.getInt(24 + pos), buff.getInt(28 + pos));
//System.out.println("      DataChannel TcpStream in: buildable has frame = " + frame + ", timestamp = " + timestamp + ", pos = " + pos);
                }
                else if (eventType == EventType.CONTROL) {
                    // Find out exactly what type of control event it is
                    // (May be null if there is an error).
                    controlType = ControlType.getControlType(node.getTag());
                    System.out.println("      DataChannel TcpStream in: got " + controlType + " event from " + name);
                    if (controlType == null) {
                        System.out.println("      DataChannel TcpStream in: found unidentified control event");
                        throw new EvioException("Found unidentified control event");
                    }
                }
                else if (eventType == EventType.USER) {
                    isUser = true;
                    if (hasFirstEvent) {
                        System.out.println("      DataChannel TcpStream in: " + name + " got FIRST event");
                    } else {
                        System.out.println("      DataChannel TcpStream in: " + name + " got USER event");
                    }
//                } else if (evType == EventType.MIXED) {
//                        // Mix of event types.
//                        // Can occur for combo of user, ROC RAW and possibly control events.
//                        // Only occurs when a user inserts a User event during the End transition.
//                        // What happens is that the User Event gets put into a EVIO Record which can
//                        // also contain ROC RAW events. The evio record gets labeled as containing
//                        // mixed events.
//                        //
//                        // This will NOT occur in ER, so headers are all parsed at this point.
//                        // Look at the very first header, second word.
//                        // num = 0  --> it's a control or User event (tag tells which):
//                        //          0xffd0 <= tag <= 0xffdf --> control event
//                        //          else                    --> User event
//                        // num > 0  --> block level for ROC RAW
//
//                        node = topNode;
//                        int num = node.getNum();
//                        if (num == 0) {
//                            int tag = node.getTag();
//                            if (ControlType.isControl(tag)) {
//                                controlType = ControlType.getControlType(tag);
//                                evType = EventType.CONTROL;
//                                System.out.println("      DataChannel TcpStream in: " + name + " mixed type to " + controlType.name());
//                            }
//                            else {
//                                isUser = true;
//                                evType = EventType.USER;
//                                System.out.println("      DataChannel TcpStream in: " + name + " mixed type to user type");
//                            }
//                        }
//                        else {
//                            System.out.println("      DataChannel TcpStream in: " + name + " mixed type to ROC RAW");
//                            evType = EventType.ROC_RAW;
//                            // Pick this raw data event apart a little
//                            if (!node.getDataTypeObj().isBank()) {
//                                DataType eventDataType = node.getDataTypeObj();
//                                throw new EvioException("ROC raw record contains " + eventDataType +
//                                        " instead of banks (data corruption?)");
//                            }
//                        }
                }

                nextRingItem = ringBufferIn.nextIntr(1);
                ri = ringBufferIn.get(nextRingItem);
                boolean isStreamingData = true;

                // Set & reset all parameters of the ringItem
                if (eventType.isBuildable()) {
//System.out.println("      DataChannel TcpStream in: put buildable event into channel ring, event from " + name);
                    ri.setAll(null, null, node, eventType, controlType,
                            isUser, hasFirstEvent, isStreamingData, id, recordId, sourceId,
                            node.getNum(), name, item, bbSupply);
                    ri.setTimeFrame(frame);
                    ri.setTimestamp(timestamp);
                }
                else {
                    if (debug) System.out.println("      DataChannel TcpStream in: put CONTROL (user?) event into channel ring, event from " + name);
                    ri.setAll(null, null, node, eventType, controlType,
                            isUser, hasFirstEvent, isStreamingData, id, recordId, sourceId,
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
                    // Run callback saying we got end event
                    if (debug) System.out.println("      DataChannel TcpStream in: BREAK from loop, got END event");
                    break;
                }
            }

            return haveInputEndEvent;
        }
    }
}
