/*
 * Copyright (c) 2011, Jefferson Science Associates
 *
 * Thomas Jefferson National Accelerator Facility
 * Data Acquisition Group
 *
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 */

package org.jlab.epsci.rtdp;

import com.lmax.disruptor.RingBuffer;
import org.jlab.coda.emu.Emu;
import org.jlab.coda.emu.support.codaComponent.CODAClass;

import org.jlab.coda.emu.support.data.*;
import org.jlab.coda.jevio.*;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.Map;



/**
 * This class simulates a Roc. It is a module which can use multiple threads
 * to create events and send them to a single output channel.<p>
 * Multiple Rocs can be synchronized by running test.RocSynchronizer.
 * @author timmer
 * (2011)
 */
public class RocSimulation extends ModuleAdapter {

    /** Keep track of the number of records built in this ROC. Reset at prestart. */
    private volatile int rocRecordId;

    /** Threads used for generating events. */
    private EventGeneratingThread[] eventGeneratingThreads;

    /** Type of trigger sent from trigger supervisor. */
    private int triggerType;

    /** Number of events in each ROC raw record. */
    private int eventBlockSize;

    /** The id of the detector which produced the data in block banks of the ROC raw records. */
    private int detectorId;

    /** Size of a single generated Roc raw event in 32-bit words (including header). */
    private int eventWordSize;

    /** Size of a single generated event in WORDS (including header). */
    private int eventSize;

    /** Number of computational loops to act as a delay. */
    private int loops;

    /** Number of ByteBuffers in each EventGeneratingThread. */
    private int bufSupplySize = 1024;



    //----------------------------------------------------
    // Members used to synchronize all fake Rocs to each other which allows run to
    // end properly. I.e., they all produce the same number of buildable events.
    //----------------------------------------------------
    /** If true, no physics events are produced.
     *  This is equivalent to having no triggers. */
    private boolean noPhysics;


    /** Set this ROC's sync bit set every syncBitCount events.
     *  Value of 0 means no sync bit. */
    private final int syncBitCount;





    //----------------------------------------------------


    /**
     * Constructor RocSimulation creates a simulated ROC instance.
     *
     * @param name name of module
     */
    public RocSimulation(String name) {

        super(name);

        //outputOrder = ByteOrder.LITTLE_ENDIAN;
        //outputOrder = ByteOrder.BIG_ENDIAN;

        // Set the sync bit every 5000th record
        syncBitCount = 5000;

        // Value for trigger type from trigger supervisor
        triggerType = 15;

        // Id of detector producing data
        detectorId = 111;

        // How many entangled events in one data block?
        eventBlockSize = 40;

        // How many WORDS in a single event?
        eventSize = 75;  // 300 bytes

        // How many loops to constitute a delay?
        loops = 0;

        // Is this ROC to be synced with others? NO


        // Does this ROC produce physics events?
        // Set this to true if you want to test a zero-trigger setup.
        // Just for testing! No physics events are sent.
        // Use this with synced = false, and don't run a TS.
        noPhysics = false;

        // Keep things simple for streaming
        streamingData = true;
        eventProducingThreads = 1;

        // Event generating threads
        eventGeneratingThreads = new EventGeneratingThread[eventProducingThreads];

    }


    /** {@inheritDoc} */
    public void clearChannels() {outputChannels.clear();}

    /** {@inheritDoc} */
    public int getInternalRingCount() {return bufSupplySize;}

    //---------------------------------------
    // Threads
    //---------------------------------------



    /**
     * This method is called by a running EventGeneratingThread.
     * It generates many ROC Raw events in it with simulated data,
     * and places them onto the ring buffer of an output channel.
     *
     * @param ringNum the id number of the output channel ring buffer
     * @param buf     the event to place on output channel ring buffer
     * @param item    item corresponding to the buffer allowing buffer to be reused
     * @param bbSupply supply of ByteBuffers.
     */
    void eventToOutputRing(int ringNum, ByteBuffer buf,
                           ByteBufferItem item, ByteBufferSupply bbSupply) {

        if (outputChannelCount < 1) {
            bbSupply.release(item);
            return;
        }

        // TODO: assumes only one output channel ...
        RingBuffer<RingItem> rb = outputChannels.get(0).getRingBuffersOut()[ringNum];

//System.out.println("  Roc mod: wait for next ring buf for writing");
        long nextRingItem = rb.next();
//System.out.println("  Roc mod: GOT next ring buf");

//System.out.println("  Roc mod: get out sequence " + nextRingItem);
        RingItem ri = rb.get(nextRingItem);
//System.out.println("  Roc mod: GOT out sequence " + nextRingItem);
        ri.setBuffer(buf);
        if (streamingData) {
            ri.setEventType(EventType.ROC_RAW_STREAM);
        }
        else {
            ri.setEventType(EventType.ROC_RAW);
        }
        ri.setControlType(null);
        ri.setSourceName(null);
        ri.setReusableByteBuffer(bbSupply, item);

//System.out.println("  Roc mod: publish ring item #" + nextRingItem + " to ring " + ringNum);
        rb.publish(nextRingItem);
//System.out.println("  Roc mod: published " + nextRingItem);
    }


    //////////////////////////////
    // For Streaming Data
    //////////////////////////////

    /** Store intermediate calculation here. */
    private int bytesPerDataBank = 0;


    /**
     * Generate data from a streaming ROC.
     *
     * @param generatedDataWords desired amount of total words (not including headers)
     *                           for all data banks (each corresponding to one payload port).
     * @param frameNumber frame number
     * @param timestamp   time stamp
     * @return ByteBuffer with generated single ROC time slice bank inside containing bank.
     */
    private ByteBuffer createSingleTimeSliceBuffer(int generatedDataWords, long frameNumber, long timestamp) {

        try {
            // Make generatedDataWords a multiple of 4, round up
            generatedDataWords = 4*((generatedDataWords + 3) / 4);
            int totalLen = 14 + generatedDataWords + 1000; // total of 14 header words + 1K extra

            // Each of 4 data banks has 1/4 of total words so generateDataWords = # bytes for each bank ...
            // Store calculation here
            bytesPerDataBank = generatedDataWords;

            CompactEventBuilder builder = new CompactEventBuilder(4*totalLen, outputOrder, false);

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
     * in which data changes. Save time, memory and garbage collection time.
     * After the first round of writing the entire event, once for each buffer
     * in the ByteBufferSupply, just do updates.
     * The only 2 quantities that need updating are the frame number and time stamp.
     * Both of these are data in the Stream Info Bank.
     *
     * @param buf          buffer from supply.
     * @param templateBuf  buffer with time slice data
     * @param frameNumber  new frame number to place into buf.
     * @param timestamp    new time stamp to place into buf
     * @param copy         ss templateBuf to be copied into buf or not.
     * @param generatedDataBytes number of bytes generated as data for each payload data bank.
     */
    void  writeTimeSliceBuffer(ByteBuffer buf, ByteBuffer templateBuf,
                               long frameNumber, long timestamp,
                               boolean copy, int generatedDataBytes) {

        // Since we're using recirculating buffers, we do NOT need to copy everything
        // into the buffer each time. Once each of the buffers in the BufferSupply object
        // have been copied into, we only need to change the few places that need updating
        // with frame number and timestamp!
        if (copy) {
            // This will be the case if buf is direct
            if (!buf.hasArray()) {
                templateBuf.position(0);
                buf.put(templateBuf);
            }
            else {
                System.arraycopy(templateBuf.array(), 0, buf.array(), 0, templateBuf.limit());
            }
        }

        // Get buf ready to read for output channel
        buf.limit(templateBuf.limit()).position(0);
//System.out.println("  Roc mod: setting frame = " + frameNumber);
        buf.putInt(20, (int)frameNumber);
        buf.putInt(24, (int)timestamp);// low 32 bits
        buf.putInt(28, (int)(timestamp >>> 32 & 0xFFFF)); // high 32 bits

        // For testing compression, need to have real data that changes,
        // endianness does not matter.
        // Only copy data into each of the "bufSupplySize" number of events once.
        // Doing this for each event produced every time slows things down too much.
        // Each event has eventBlockSize * eventSize (40*75 = 3000) data words.
        // 4 * 3k bytes * 1024 events = 12.3MB. This works out nicely since we have
        // retrieved 16MB from a single Hall D data file.
        // However, each Roc has the same data which will lend itself to more compression.
        // So the best thing is for each ROC to have different data.

        // Move to data input position
        int writeIndex = 4*13;
    }


    //////////////////////////////
    //////////////////////////////


    /**
     * <p>This thread generates events with junk data in it (all zeros except first word which
     * is the event number).
     * It is started by the GO transition and runs while the state of the module is ACTIVE.
     * </p>
     * <p>When the state is ACTIVE and the list of output DataChannels is not empty, this thread
     * selects an output by taking the next one from a simple iterator. This thread then creates
     * data transport records with payload banks containing ROC raw records and places them on the
     * output DataChannel.
     * </p>
     */
    class EventGeneratingThread extends Thread {

        private final int myId;
        private long timestamp = 100;
        /** Ring buffer containing ByteBuffers - used to hold events for writing. */
        private ByteBufferSupply bbSupply;
        // Number of data words in each event
        private int generatedDataWords;
        private int generatedDataBytes;
        private ByteBuffer templateBuffer;

        /** Boolean used to kill this thread. */
        private volatile boolean killThd;

        // Streaming stuff
        private long frameNumber = 0L;


        EventGeneratingThread(int id, String name) {
            super(name);
            this.myId = id;

            // Is we streamin'?

                //generatedDataWords = 40*75;
                generatedDataWords = 5;
                //ByteBuffer singleTSB = createSingleTimeSliceBuffer(generatedDataWords, frameNumber, timestamp);
                //singleTSBsize = singleTSB.limit() - 8;
//System.out.println("  Roc mod: single TSB bytes size = " + singleTSBsize + ", words = " + (singleTSBsize/4));
                //templateBuffer = createDualTimeSliceBuffer(generatedDataWords, frameNumber, timestamp);
System.out.println("\n  Roc mod: Starting sim ROC frame at " + frameNumber + "\n");
                templateBuffer = createSingleTimeSliceBuffer(generatedDataWords, frameNumber, timestamp);
//Utilities.printBuffer(templateBuffer, 0, 56, "TEMPLATE BUFFER");
                eventWordSize = templateBuffer.remaining()/4;
                frameNumber++;
                timestamp += 10;

        }


        /**
         * Kill this thread which is sending messages/data to other end of emu socket.
         */
        final void endThread() {
            killThd = true;
            this.interrupt();
        }


        public void run() {

            int  skip=3;
            int loopCount = loops;


            // Stat time
            long totalT=0L, t1, deltaT, t2;

            // Event stats
            long bufCounter=0L;

            // Slice stats
            long oldFrameVal=0L, totalFrameCount=0L;
            double frameRate = 0., avgFrameRate = 0.;

            ByteBuffer buf;
            ByteBufferItem bufItem;
            boolean copyWholeBuf = true;



            // We need for the # of buffers in our bbSupply object to be >=
            // the # of ring buffer slots in the output channel or we can get
            // a deadlock. Although we get the value from the first channel's
            // first ring, it's the same for all output channels.
            if (outputChannelCount > 0) {
                bufSupplySize = outputChannels.get(0).getRingBuffersOut()[0].getBufferSize();
            }
            else {
                bufSupplySize = 1024;
            }

            // Now create our own buffer supply to match
            bbSupply = new ByteBufferSupply(bufSupplySize, 4*eventWordSize, ByteOrder.BIG_ENDIAN, false);

            try {
                t1 = System.currentTimeMillis();
                System.out.println("SET loops to " + loops);

                while (true) {

                    if (killThd) return;

                    // Add ROC Raw Records as PayloadBuffer objects
                    // Get buffer from recirculating supply.
                    bufItem = bbSupply.get();
                    buf = bufItem.getBuffer();

                    // Some logic to allow us to copy everything into buffer
                    // only once. After that, just update it.
                    if (copyWholeBuf) {
                        // Only need to do this once too
                        buf.order(outputOrder);

                        if (++bufCounter > bufSupplySize) {
                            copyWholeBuf = false;
                        }
                    }
//System.out.println("  Roc mod: write event");

                    writeTimeSliceBuffer(buf, templateBuffer, frameNumber,
                                         timestamp, copyWholeBuf, bytesPerDataBank);



                   //     Thread.sleep(1);

                        if (loops != 0 && loopCount++ % loops == 0) {
                            Thread.sleep(1);
                        }

                        if (killThd) return;

                        // Put generated events into output channel
                        eventToOutputRing(myId, buf, bufItem, bbSupply);
//Utilities.printBuffer(buf, 0, 56, "EVENT BUFFER");

                    // Switch frame and timestamp, every  send
                    frameNumber++;
                    timestamp += 10;


                    t2 = System.currentTimeMillis();
                    deltaT = t2 - t1;

                    if (myId == 0 && deltaT > 2000) {
                            if (skip-- < 1) {
                                totalT += deltaT;
                                totalFrameCount += frameNumber - oldFrameVal;
                                frameRate = ((frameNumber - oldFrameVal) * 1000. / deltaT);
                                avgFrameRate = (totalFrameCount * 1000.) / totalT;
                            }
                            System.out.println("  Roc mod: frame rate = " + String.format("%.3g", frameRate) +
                                    " Hz,  avg = " + String.format("%.3g", avgFrameRate));
                            System.out.println("  Roc mod: slice rate = " + String.format("%.3g", 2.*frameRate) +
                                    " Hz,  avg = " + String.format("%.3g", 2.*avgFrameRate));
                            t1 = t2;
                            oldFrameVal = frameNumber;
                    }

                }
            }
            catch (InterruptedException e) {
                // End or Reset most likely
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    //---------------------------------------
    // State machine
    //---------------------------------------



    /** {@inheritDoc} */
    public void prestart() {

System.out.println("  Roc mod: PRESTART");

        // Reset some variables
        rocRecordId = 1;


        rocRecordId++;
    }


    /** {@inheritDoc} */
    public void go() {

        rocRecordId++;


            for (int i = 0; i < eventProducingThreads; i++) {
System.out.println("  Roc mod: create new event generating thread ");
                    eventGeneratingThreads[i] = new EventGeneratingThread(i, name + ":generator");
System.out.println("  Roc mod: starting event generating thread");
                    eventGeneratingThreads[i].start();
            }

    }


}