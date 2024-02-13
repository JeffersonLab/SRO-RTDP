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
import org.jlab.coda.emu.support.control.CmdExecException;
import org.jlab.coda.emu.support.data.*;
import org.jlab.coda.hipo.CompressionType;
import org.jlab.coda.jevio.*;


import java.io.IOException;
import java.nio.ByteBuffer;

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

    private final String expid;

    private final boolean debug;
    
    // OUTPUT

    /** Thread used to output data. */
    private DataOutputHelper dataOutputThread;

    /** TCP port of emu domain server. */
    private int sendPort;

    /** TCP send buffer size in bytes. */
    private final int tcpSendBuf;

    /** TCP send buffer size in bytes. */
    private final String serverIP;

    /** TCP no delay setting. */
    private final boolean noDelay;

    /** Connection to emu domain server. */
    private cMsg emuDomain;
    

    // INPUT & OUTPUT

    /**
     * Biggest chunk of data sent by data producer.
     * Allows good initial value of ByteBuffer size.
     */
    private final int maxBufferSize;

    /** Use the evio block header's block number as a record id. */
    private int recordId = 1;

    /** Use direct ByteBuffer? */
    private final boolean direct;

    /**
     * In order to get a higher throughput for fast networks,
     * this emu channel may use multiple sockets underneath. Defaults to 1.
     */
    private final int socketCount;




    /**
     * Constructor to create a new DataChannelImplEt instance.
     *
     * @param name         the name of this channel.
     * @param codaId       CODA id of this data source.
     * @param serverIp     IP addr of server to send data to.
     * @param serverPort   port of server to send data to.
     */
    DataChannelImplEmu(String name, int codaId, String expid, String serverIp, int serverPort, boolean debug) {

        // constructor of super class
        super(name, false, debug, 0);

        this.id = codaId;
        this.expid = expid;
        this.debug = debug;
        this.serverIP = serverIp;
        this.sendPort = serverPort;

        if (debug) System.out.println("      DataChannel Emu: creating output channel " + name);

        // Use direct ByteBuffers or not, faster & more stable with non-direct.
        // Turn this off since performance is better.
        direct = false;

        // How many sockets to use underneath
        socketCount = 1;

        // if OUTPUT channel

        // set TCP_NODELAY option on
        noDelay = true;

        // size of TCP send buffer (0 means use operating system default)
        tcpSendBuf = 5000000;

        // Send port
        sendPort = cMsgNetworkConstants.emuTcpPort;

        // Size of max buffer
        maxBufferSize = 4000000;
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



    /** {@inheritDoc} */
    public void prestart() throws CmdExecException {
        haveInputEndEvent = false;

        try {
             openOutputChannel();
        }
        catch (cMsgException e) {
            throw new CmdExecException(e);
        }
    }




    /**
     * Interrupt all threads.
     */
    private void interruptThreads() {

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
     * Class used to take Evio banks from ring buffer (placed there by a module),
     * and write them over network to an Emu domain input channel using the Emu
     * domain output channel.
     */
    private final class DataOutputHelper extends Thread {

        /** Let a single waiter know that the main thread has been started. */
        private final CountDownLatch startLatch = new CountDownLatch(1);

        /** Object to write (marshall) input buffers into larger, output evio buffer (next member). */
        private EventWriterUnsync writer;

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
                writer = new EventWriterUnsync(currentBuffer, 0, maxEventCount, null, 1, CompressionType.RECORD_UNCOMPRESSED);
                // writer = new EventWriterUnsync(currentBuffer, 0, 100000, null, 1, CompressionType.RECORD_UNCOMPRESSED);     // orig


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
            currentBuffer.limit(writer.getBytesWrittenToBuffer());

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
//                    System.out.println("try writing " + buf.limit() + " data bytes into buf of size " +
//                                 writer.getByteBuffer().remaining() + ", has cap = " +
//                                             writer.getByteBuffer().capacity() + ", has pos = " +
//                                             writer.getByteBuffer().position() + ", has room = " +
//                            writer.hasRoom(rItem.getTotalBytes()));

                    EvioNode node = rItem.getNode();
                    if (node != null) {
                        System.out.print("HEY! node is not null!!!");
                    }
                    
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
