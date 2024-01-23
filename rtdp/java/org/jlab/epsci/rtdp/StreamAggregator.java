/*
 * Copyright (c) 2010, Jefferson Science Associates
 *
 * Thomas Jefferson National Accelerator Facility
 * Data Acquisition Group
 *
 * 12000 Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 */

package org.jlab.epsci.rtdp;

import com.lmax.disruptor.*;
import org.jlab.coda.emu.EmuException;
import org.jlab.coda.emu.support.data.*;
import org.jlab.coda.jevio.ByteDataTransformer;
import org.jlab.coda.jevio.EvioException;
import org.jlab.coda.jevio.EvioNode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

/**
 * This class implements an event aggregator for a reconstructed stream for multple VTPs.
 * Pcap files of TCP communication from VTPs were stored and replayed. Now trying to make
 * sense of it.
 */
public class StreamAggregator extends ModuleAdapter {


    /** The number of BuildingThread objects. */
    private int buildingThreadCount = 1;

    /** Container for threads used to build events. */
    private final ArrayList<BuildingThread> buildingThreadList = new ArrayList<>(6);

    /** If <code>true</code>, produce debug print out. */
    private final boolean debug = true;

    // ---------------------------------------------------
    // Configuration parameters
    // ---------------------------------------------------

    /**
     * The maximum difference in ticks for timestamps for a single event before
     * an error condition is flagged.
     */
    private int timestampSlop;


    //-------------------------------------------
    // Disruptor (RingBuffer)
    //-------------------------------------------

    /** Number of items in build thread ring buffers. */
    protected int ringItemCount;

    /** One RingBuffer per input channel (references to channels' rings). */
    private RingBuffer<RingItem>[] ringBuffersIn;

    /** Size of RingBuffer for each input channel. */
    private int[] ringBufferSize;

    // Sorter thread is receiving data from all input channels

    /** For each input channel, 1 sequence for sorter thread. */
    private Sequence[] sorterSequenceIn;

    /** For each input channel, one barrier. */
    private SequenceBarrier[] sorterBarrierIn;

    /** Array of available sequences (largest index of items desired), one per input channel. */
    private long[] availableSequences;

    /** Array of next sequences (index of next item desired), one per input channel. */
    private long[] nextSequences;

    /** One ring buffer for each build thread to hold time slice banks. */
    private final RingBuffer<TimeSliceBankItem>[] sorterRingBuffers;

    /** Thread to sort incoming time slice banks to proper build thread. */
    private TimeSliceSorter timeSliceSorterThread;

    /** Number of elements in each sorter (build thread input) ring. */
    private int sorterRingSize;

    // Each build thread is receiving data from 1 sorterRingBuffer

    /** For each sorter ring, 1 sequence since only 1 build thread uses it. */
    private Sequence[] buildSequenceIn;

    /** For each sorter ring, 1 barrier since only 1 build thread uses it. */
    private SequenceBarrier[] buildBarrierIn;


    /**
     * Constructor creates a new EventBuilding instance.
     *
     * @param name name of module
     */
    public StreamAggregator(String name) {
        super(name);

        // The only way a StreamAggregator gets created in the first place
        // is for streaming="on" to be set in jcedit.
        // So just ignore the "streaming" attribute.
        streamingData = true;

        // default to 2 clock ticks
        timestampSlop = 2;

        //--------------------------------------------------------------------
        // Set parameters for the ByteBufferSupply which provides ByteBuffers
        // to hold built events.
        //--------------------------------------------------------------------

        // Number of items in each build thread ring. We need to limit this
        // since it costs real memory. For big events, 128 x 20MB events = 2.56GB
        // of mem used. Multiply that times the number of build threads.
        ringItemCount = 256;
System.out.println("  Agg mod: internal ring buf count -> " + ringItemCount);

        //--------------------------------------------------------------------
        // Create rings to hold TimeSliceBanks, 1 ring for each build thread.
        // These rings only hold references to objects so no real memory
        // being used. Do this in prestart() but define array here.
        //--------------------------------------------------------------------
        sorterRingSize = 4096;
        sorterRingBuffers = new RingBuffer[buildingThreadCount];
     }


    /** {@inheritDoc} */
    public int getInternalRingCount() {return buildingThreadCount*ringItemCount;};


    /** {@inheritDoc} */
    public boolean isStreamingData() {return true;}


    /** {@inheritDoc} */
    public boolean dataFromVTP() {return true;}



    /**
     * This method is called by a build thread and is used to place
     * a bank onto the ring buffer of an output channel.
     *
     * @param ringNum    the id number of the output channel's ring buffer.
     * @param channelNum the number of the output channel.
     * @param eventCount number of evio events contained in this bank.
     * @param buf        the event to place on output channel ring buffer.
     * @param eventType  what type of data are we sending.
     * @param item       item corresponding to the buffer allowing buffer to be reused.
     * @param bbSupply   supply from which the item (and buf) came.
     * @throws InterruptedException
     */
    private void eventToOutputRing(int ringNum, int channelNum, int eventCount,
                                   ByteBuffer buf, EventType eventType,
                                   ByteBufferItem item, ByteBufferSupply bbSupply)
            throws InterruptedException {

        // Have output channels?
        if (outputChannelCount < 1) {
            bbSupply.release(item);
            return;
        }

        RingBuffer<RingItem> rb = outputChannels.get(channelNum).getRingBuffersOut()[ringNum];

//System.out.println("  Agg mod: wait for out buf, ch" + channelNum + ", ring " + ringNum);
        long nextRingItem = rb.nextIntr(1);
//System.out.println("  Agg mod: Got sequence " + nextRingItem + " for " + channelNum + ":" + ringNum);
        RingItem ri = rb.get(nextRingItem);
        ri.setBuffer(buf);
        ri.setEventType(eventType);
        ri.setControlType(null);
        ri.setSourceName(null);
        ri.setReusableByteBuffer(bbSupply, item);
        ri.setEventCount(eventCount);

//System.out.println("  Agg mod: will publish to ring " + ringNum);
        rb.publish(nextRingItem);
//System.out.println("  Agg mod: published to ring " + ringNum);
    }


    /**
     * Copied from Evio class.
     * Check the given payload buffer for correct record id, source id.
     * Store sync and error info in payload buffer.
     *
     * @param pBuf          payload buffer to be examined
     * @param channel       input channel buffer is from
     * @param eventType     type of input event in buffer
     * @param inputNode     EvioNode object representing event
     * @param recordIdError non-fatal record id error.
     */
    public static void checkInput(PayloadBuffer pBuf, DataChannel channel,
                                  EventType eventType, EvioNode inputNode,
                                  boolean recordIdError) {

        int sourceId = pBuf.getSourceId();
        boolean nonFatalError = false;

        // Only worry about record id if event to be built.
        // Initial recordId stored is 0, ignore that.
        if (eventType != null && eventType.isBuildable()) {
            int tag = inputNode.getTag();

            pBuf.setSync(Evio.isTagSyncEvent(tag));
            pBuf.setError(Evio.tagHasError(tag));
        }

        // Check source ID of bank to see if it matches channel id
        if (!pBuf.matchesId()) {
            System.out.println("checkInput: buf source id = " + sourceId +
                    " != input channel id = " + channel.getID());
            nonFatalError = true;
        }

        pBuf.setNonFatalBuildingError(nonFatalError || recordIdError);
    }


    /**
     * Handle a USER event by sending it to the first output channel.
     * If same endian, it may be in EvioNode form, else it'll be
     * written, swapped, into a buffer.
     * 
     * @param buildingBank  bank holding USER event.
     * @param inputChannel
     * @param recordIdError
     * @throws InterruptedException
     */
    private void handleUserEvent(PayloadBuffer buildingBank, DataChannel inputChannel,
                                 boolean recordIdError)
            throws InterruptedException {


        ByteBuffer buffy    = buildingBank.getBuffer();
        EvioNode inputNode  = buildingBank.getNode();
        EventType eventType = buildingBank.getEventType();

        // Check payload buffer for source id.
        // Store sync and error info in payload buffer.
        checkInput(buildingBank, inputChannel,
                        eventType, inputNode, recordIdError);

        // Swap headers, NOT DATA, if necessary
        if (outputOrder != buildingBank.getByteOrder()) {
            try {
                // Check to see if user event is already in its own buffer
                if (buffy != null) {
                    // Takes care of swapping of event in its own separate buffer,
                    // headers not data. This doesn't ever happen.
                    ByteDataTransformer.swapEvent(buffy, buffy, 0, 0, false, null);
                }
                else if (inputNode != null) {
                    // This node may share a backing buffer with other, ROC Raw, events.
                    // Thus we cannot change the order of the entire backing buffer.
                    // For simplicity, let's copy it and swap it in its very
                    // own buffer.

                    // Copy
                    buffy = inputNode.getStructureBuffer(true);
                    // Swap headers but not data
                    ByteDataTransformer.swapEvent(buffy, null, 0, 0, false, null);
                    // Store in ringItem
                    buildingBank.setBuffer(buffy);
                    buildingBank.setNode(null);
                    // Release claim on backing buffer since we are now
                    // using a different buffer.
                    buildingBank.releaseByteBuffer();
                }
            }
            catch (EvioException e) {/* should never happen */ }
        }
        else if (buffy == null) {
            // We could let things "slide" and pass on an EvioNode to the output channel.
            // HOWEVER, since we are now reusing EvioNode objects, this is a bad strategy.
            // We must copy it into a new buffer and pass that along, allowing us to free
            // up the EvioNode.

            // Copy
            buffy = inputNode.getStructureBuffer(true);
            // Store in ringItem
            buildingBank.setBuffer(buffy);
            buildingBank.setNode(null);
            // Release claim on backing buffer since we are now
            // using a different buffer.
            buildingBank.releaseByteBuffer();
        }

        // Do this so we can use fifo as output & get accurate stats
        buildingBank.setEventCount(1);

        // User event is stored in a buffer from here on

        // Send it on.
        // User events are thrown away if no output channels
        // since this event builder does nothing with them.
        // User events go into the first ring of the first channel.
        // Since all user events are dealt with here
        // and since they're now all in their own (non-ring) buffers,
        // the post-build threads can skip over them.
        eventToOutputChannel(buildingBank, 0, 0);
    }


    /** Enum to describe a difference in frame numbers of various time slices. */
    enum FrameNumberDiff {
        /** Same frame. */
        SAME(),
        /** Next frame. */
        NEXT(),
        /** Multiple frames removed. */
        MULTIPLE();
    }




    /**
     * Compare two time frames to see if they're the same,
     * in sequential, or differ by multiple time slices.
     * Frame is just the sequential slice number.
     *
     * @param tf1   first time frame to examine.
     * @param tf2   second time frame to examine.
     * @return {@link FrameNumberDiff#SAME} if in same time frame,
     *         {@link FrameNumberDiff#NEXT} if in sequential time frames, or
     *         {@link FrameNumberDiff#MULTIPLE} if in different and non-sequential time slices.
     */
    private FrameNumberDiff compareTimeFrames(long tf1, long tf2) {
        // Same frame?
        boolean same = (tf1 == tf2);

        if (same) {
            // Same as last time frame
            return FrameNumberDiff.SAME;
        }
        else if (Math.abs(tf1 - tf2) == 1) {
            // Neighboring time frame?
            return FrameNumberDiff.NEXT;
        }

        // Frames differ by multiple slices
        return FrameNumberDiff.MULTIPLE;
    }


    /**
     * The leading consumer of each input channel ring is the sorter thread. This thread
     * sends all events of the same time frame to the ring of the same build thread.
     * Each build thread consumes ring items that the sorter thread fills.
     */
    class TimeSliceSorter extends Thread {

        /** Index of build thread currently receiving TimeSliceBanks. */
        private int currentBT = 0;

        /** Place to store events read off of channels but not immediately needed. */
        private final PayloadBuffer[] storedBank = new PayloadBuffer[inputChannelCount];
        PayloadBuffer bank = null;

        /** First time through the sorting? */
        boolean firstTimeThru = true;
        /** Time frame currently being written to a build thread ring. */
        long lookingForFrame = 0L;

        // RingBuffer Stuff

        /** Get empty items from each build thread's sorted TSB ring. */
        private final long[] getSequences = new long[buildingThreadCount];


        TimeSliceSorter() {
            Arrays.fill(storedBank, null);
        }


        /**
         * Get the input level (how full is the ring buffer 0-100) of a single input channel
         * @param chanIndex index of channel (starting at 0)
         * @return input level
         */
        int getInputLevel(int chanIndex) {
            // scale from 0% to 100% of ring buffer size
            return ((int)(ringBuffersIn[chanIndex].getCursor() -
                    ringBuffersIn[chanIndex].getMinimumGatingSequence()) + 1)*100/ringBufferSize[chanIndex];
        }



        /**
         * Send a bank, from one of the input channels, to a TimeSliceBank ring buffer
         * that feeds the build thread corresponding to its timestamp.
         *
         * @param bank PayloadBuffer from one of the input channels
         * @param btIndex index indicating which build thread's ring to send bank to.
         * @throws InterruptedException if thread interrupted waiting on ring get().
         */
        private void sendToTimeSliceBankRing(PayloadBuffer bank, int btIndex)
                                    throws InterruptedException {

            getSequences[btIndex] = sorterRingBuffers[btIndex].nextIntr(1);
// System.out.println("  Agg mod: sendToTimeSliceBankRing: got sorter ring seq = " + getSequences[btIndex] + ". type = " + bank.getEventType());
            TimeSliceBankItem item = sorterRingBuffers[btIndex].get(getSequences[btIndex]);
            item.setBuf(bank);
            sorterRingBuffers[btIndex].publish(getSequences[btIndex]);
        }



        /**
         * Copied from Evio class.
         * Check the given payload buffer for correct record id, source id.
         * Store error info in payload buffer.
         *
         * @param pBuf          payload buffer to be examined
         * @param channel       input channel buffer is from
         * @param eventType     type of input event in buffer
         * @param inputNode     EvioNode object representing event
         * @param recordIdError non-fatal record id error.
         */
        public void checkStreamInput(PayloadBuffer pBuf, DataChannel channel,
                                            EventType eventType, EvioNode inputNode,
                                            boolean recordIdError) {

            int sourceId = pBuf.getSourceId();
            boolean nonFatalError = false;

            // Only worry about record id if event to be built.
            // Initial recordId stored is 0, ignore that.
            if (eventType != null && eventType.isROCRawStream()) {
                if (sourceId != inputNode.getTag()) {
                    System.out.println("checkInput: buf source Id (" + sourceId +
                            ") != buf's id from tag (0x" + Integer.toHexString(inputNode.getTag()) + ')');
                    nonFatalError = true;
                }
            }

            // Check source ID of bank to see if it matches channel id
            if (!pBuf.matchesId()) {
                System.out.println("checkInput: buf source id = " + sourceId +
                        " != input channel id = " + channel.getID());
                nonFatalError = true;
            }

            pBuf.setNonFatalBuildingError(nonFatalError || recordIdError);
        }


        /**
         * Method to search for END event on each channel when END found on one channel.
         * @param endChannel    channel in which END event first appeared.
         * @param lookedForTF   time frame of slice currently being written to the ring
         *                      of a build thread when END found.
         * @return the total number of END events found in all channels.
         */
        private int findEnds(int endChannel, long lookedForTF) {
            // If the END event is far back on any of the communication channels, in order to be able
            // to read in those events, resources must be released after being read/used.

            long available;
            int millisecWait = 0;
            // One channel already found END event
            int endEventCount = 1;

            try {
                // For each channel ...
                channelLoop:
                for (int ch=0; ch < inputChannelCount; ch++) {

                    if (ch == endChannel) {
                        // We've already found the END event on this channel
                        continue;
                    }

                    int offset = 0;
                    boolean done = false, written;
                    long veryNextSequence = nextSequences[ch];

                    while (true) {
                        // Check to see if there is anything to read so we don't block.
                        // If not, move on to the next ring.
                        if (ringBuffersIn[ch].getCursor() < veryNextSequence) {
//System.out.println("  Agg mod: findEnd, for chan " + ch + ", sequence " + veryNextSequence + " not available yet");
                            // So the question is, when do we quit if no END event is coming?
                            // If the Agg does not end its threads and complete the END transition,
                            // then the whole state machine gets stuck and it cannot go to DOWNLOAD.
                            if (millisecWait >= 5000) {
System.out.println("  Agg mod: findEnd, stop looking for END on chan " + ch + " since no more events available");
                                continue channelLoop;
                            }

                            // Wait for events to arrive
                            Thread.sleep(200);
                            millisecWait += 200;

                            // Try again
                            continue;
                        }

//System.out.println("  Agg mod: findEnd, waiting for next item from chan " + ch + " at sequence " + veryNextSequence);
                        available = sorterBarrierIn[ch].waitFor(veryNextSequence);
//System.out.println("  Agg mod: findEnd, got items from chan " + ch + " up to sequence " + available);

                        while (veryNextSequence <= available) {
                            PayloadBuffer bank = (PayloadBuffer) ringBuffersIn[ch].get(veryNextSequence);
                            String source = bank.getSourceName();
//System.out.println("  Agg mod: findEnd, on chan " + ch + " found event of type " + bank.getEventType() + " from " + source + ", back " + offset +
//                   " places in ring with seq = " + veryNextSequence);
                            EventType eventType = bank.getEventType();
                            written = false;

                            if (eventType == EventType.CONTROL) {
                                if (bank.getControlType() == ControlType.END) {
                                    // Found the END event
System.out.println("  Agg mod: findEnd, chan " + ch + " got END from " + source + ", back " + offset + " places in ring");
                                    // Release buffer back to ByteBufferSupply
                                    bank.releaseByteBuffer();
                                    endEventCount++;
                                    done = true;
                                    break;
                                }
                            }
                            else if (eventType != EventType.USER) {
                                // Check bank for source id & print out if ids don't match with channel's
                                checkStreamInput(bank, inputChannels.get(ch), eventType,
                                                      bank.getNode(), false);

                                // This sequence needs to be released later, by build thread, so store here
                                bank.setChannelSequence(nextSequences[ch]);
                                bank.setChannelSequenceObj(sorterSequenceIn[ch]);

                                // Get TF from bank just read from chan
                                // Compare to what we're looking for
                                FrameNumberDiff diff = compareTimeFrames(bank.getTimeFrame(), lookedForTF);
                                if (diff == FrameNumberDiff.SAME) {
                                    // If it's what we're looking for, write it out
                                    sendToTimeSliceBankRing(bank, currentBT);
                                    written = true;
                                }
                            }

                            // Release buffer back to ByteBufferSupply if not being passed on to build thread
                            if (!written) {
                                bank.releaseByteBuffer();
                            }

                            // Advance sequence
                            sorterSequenceIn[ch].set(veryNextSequence);
                            veryNextSequence++;
                            offset++;
                        }

                        if (done) {
                            break;
                        }
                    }
                } // for each channel
            }
            catch (InterruptedException e) {
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return endEventCount;
        }



        /** Run this thread. */
        public void run() {

            // Initialize
            boolean streaming, gotBank, recordIdError;
            EventType eventType = null;
            EvioNode inputNode;
            PayloadBuffer[] buildingBanks = new PayloadBuffer[inputChannelCount];

            // Channel currently being examined
            int chan = 0;
            int lastWrittenChan = 0;


            // Ring Buffer stuff - define array for convenience
            nextSequences = new long[inputChannelCount];
            availableSequences = new long[inputChannelCount];
            Arrays.fill(availableSequences, -2L);
            for (int i = 0; i < inputChannelCount; i++) {
                // Gating sequence for each of the input channel rings
                nextSequences[i] = sorterSequenceIn[i].get() + 1L;
            }


            try {
                // Now do the sorting
                while (true) {

                    // Here we have what we need to build:
                    // ROC raw events from all ROCs (or partially built events from
                    // each contributing EB) each with sequential time slices.
                    // However, there may also be user and END events in the rings.

                    // Put null into buildingBanks array elements
                    Arrays.fill(buildingBanks, null);

                    // Cycle through the channels over and over.
                    // Grab all identical TSs from the first channel. Go to the next and
                    // and so on until all identical slices are read and placed into the
                    // ring of the same build thread.
                    // Next go to the next slice, copy them to the ring of the next
                    // build thread - round and round.

                    long frame;

                    while (true) {

//System.out.println("  Agg mod: ch" + chan + ",sorter set gotBank = FALSE");
                        gotBank = false;

                        //----------------------------------------------------
                        // Loop until we get event which is NOT a user event
                        //----------------------------------------------------
                        while (!gotBank) {

                            // Make sure there are available data on this channel.
                            // Only wait if necessary ...
                            if (availableSequences[chan] < nextSequences[chan]) {
//System.out.println("  Agg mod: ch" + chan + ", sorter wait for event (seq [" + chan + "] = " + nextSequences[chan] + ")");
                                availableSequences[chan] = sorterBarrierIn[chan].waitFor(nextSequences[chan]);
//System.out.println("  Agg mod: ch" + chan + ", sorter available seq[" + chan + "]  = " + availableSequences[chan]);
                            }

                            // While we have new data to work with ...
                            while ((nextSequences[chan] <= availableSequences[chan]) || (storedBank[chan] != null)) {
                                // The stored bank is never a user or control event, always a time slice
                                if (storedBank[chan] != null) {
//System.out.println("  Agg mod: sorter picking up stored bank at seq = " + nextSequences[chan]);
                                    bank = storedBank[chan];
                                    storedBank[chan] = null;
                                    eventType = bank.getEventType();
                                }
                                else {
                                    bank = (PayloadBuffer) ringBuffersIn[chan].get(nextSequences[chan]);
                                    inputNode = bank.getNode();
                                    eventType = bank.getEventType();
                                    recordIdError = false;
//System.out.println("  Agg mod: ch" + chan + ",sorter event type = " + eventType);

                                    // Deal with user event
                                    if (eventType.isUser()) {
                                        // User events are placed in first output channel's first ring.
                                        // Only the first build thread will deal with them.
System.out.println("  Agg mod: sorter got user event from channel " + inputChannels.get(chan).name());
                                        //System.out.println("  Agg mod: ch" + chan + ", skip user item " + nextSequences[chan]);
                                        //System.out.println("  Agg mod: user event order = " + bank.getByteOrder());
                                        handleUserEvent(bank, inputChannels.get(chan), recordIdError);
                                        sorterSequenceIn[chan].set(nextSequences[chan]);
                                        nextSequences[chan]++;
                                    }
                                    // Found a bank, so do something with it
                                    else {
//System.out.println("  Agg mod: ch" + chan + ", sorter accept item " + nextSequences[chan] + ", set gotBank = true");
                                        // Check payload buffer for source id.
                                        // Store error info in payload buffer.
                                        checkStreamInput(bank, inputChannels.get(chan),
                                                              eventType, inputNode, recordIdError);
                                        gotBank = true;
                                        // This sequence needs to be released later, by build thread, so store here
                                        bank.setChannelSequence(nextSequences[chan]);
                                        bank.setChannelSequenceObj(sorterSequenceIn[chan]);

                                        break;
                                    }
                                }
                            }
                        }

//System.out.println("  Agg mod: ch" + chan + ", sorter out of TOP LOOP");
                        //----------------------------------------------------

                        // If event needs to be built - a real time slice ...
                        if (!eventType.isControl()) {

                            // Get time frame from bank just read from chan
                            frame = bank.getTimeFrame();

                            // If this is the first read from the first channel
                            if (firstTimeThru) {
                                // This is the time frame will be looking for in each channel
                                lookingForFrame = frame;
                                firstTimeThru = false;
                            }

                            // Compare bank's TS to the one we're looking for -
                            // those to be placed into the current build thread's ring.
                            // First time thru this comes back as "SAME".
                            FrameNumberDiff diff = compareTimeFrames(frame, lookingForFrame);

//System.out.println("  Agg mod: ch" + chan + ", sorter NOT CONTROL EVENT, frame = " + frame + ", looking for " + lookingForFrame + ", diff = " + diff);
                            // Bank was has same Time Slice as the one we're looking for.
                            // This means that this bank must be written out to the current
                            // receiving ring buffer. That's because all identical time slices
                            // go to the same ring buffer no matter the input channel.
                            if (diff == FrameNumberDiff.SAME) {
//System.out.println("  Agg mod: ch" + chan + ", sorter send time slice to BT# = " + currentBT +", event type = " + bank.getEventType() + ", frame = " + frame);
                                sendToTimeSliceBankRing(bank, currentBT);
                                // This bank must be released AFTER build thread finishes with it
                                nextSequences[chan]++;
                                lastWrittenChan = chan;

                                // Next read will be on the same channel to see if there are
                                // more identical time slice banks there. Keep at it until all
                                // identical time slices from this channel are in one ring.
                                continue;
                            }
                            // Bank was has DIFFERENT Time Slice than previous bank from same channel that was
                            // written out. This means that this bank must be put on hold for a while - store
                            // it for later use.
                            // Check the other channels to see if they have banks with the same time slices
                            // as the one last written.
                            else  {
//System.out.println("  Agg mod: ch" + chan + ", sorter DIFF timestamp, frame = " + frame);
                                // If the last write was on this channel, then the bank we just
                                // read from that channel is part of the next time slice.
                                // This is our clue to move to the next channel to see if it has
                                // banks with the previous time slice.
                                if (chan == lastWrittenChan) {
                                    // Store what we just read for the next time
                                    // we're getting data from this channel
                                    storedBank[chan] = bank;

                                    // if this is the last input channel ...
                                    if (chan >= inputChannelCount - 1) {
                                        // Go back to the first channel
                                        chan = 0;
                                        // Start looking for the next slice
                                        lookingForFrame = frame;
                                        // Which will go to the next build thread
                                        currentBT = (currentBT + 1) % buildingThreadCount;
                                    }
                                    else {
                                        // Go to the next channel & keep looking for the SAME slice
                                        chan++;
                                    }
                                }
                                // If the last write was on a previous channel ...
                                else {
                                    // This channel should have had an event with an identical timestamp.
                                    // Since it didn't, there's a slice missing!
                                    throw new EmuException("Too big of a jump in timestamp");
                                }
                            }

                            continue;
                        }

                        //-------------------------------------------
                        // If we're here, we've got a CONTROL event
                        //-------------------------------------------

                        // If not END, we got problems
                        if (!bank.getControlType().isEnd()) {
                            if (debug) System.out.println("  Agg mod: " + bank.getControlType() +
                                                          " control events not allowed");
                           return;
                        }

                        //-------------------------------------------
                        // If we're here, we've got 1 END event
                        //-------------------------------------------

                        // We need one from each channel so find them now.
                        int endEventCount = findEnds(chan, lookingForFrame);

System.out.println("  Agg mod: sorter found END event from " + bank.getSourceName() + " at seq " + nextSequences[chan]);

                        if (endEventCount != inputChannelCount) {
                            // We do NOT have all END events
                            throw new EmuException("only " + endEventCount + " ENDs for " +
                                                   inputChannelCount + " channels");
                        }
                        else {
System.out.println("  Agg mod: sorter found END events on all input channels");
                            return;
                        }
                    }
                    // repeat loop endlessly
                }
            }
            catch (EmuException e) {
                e.printStackTrace();
            }
            catch (InterruptedException e) {
                //e.printStackTrace();
            }
            catch (AlertException | TimeoutException e) {
                e.printStackTrace();
            }
        }
    }



    /**
     * <p>
     * This thread is started by the PRESTART transition.
     * An empty buffer is obtained from a supply.
     * All time slice banks with the same frame # (from a single input ring buffer
     * containing all slices with the same time stamp) are built into a new aggregated
     * bank in that buffer.
     * If this module has outputs, the built banks are placed on an output channel.
     * If there are multiple output channels, output is selected by round-robin.
     * </p>
     */
    class BuildingThread extends Thread {

        /** The total number of build threads. */
        private final int btCount;

        /** The order of this build thread, relative to the other build threads,
          * starting at zero. */
        private final int btIndex;

        /**
         * This is true if the roc data buffers have a backing byte array,
         * this object's ByteBufferSupply has buffers with a backing byte array,
         * AND they both have the same endian value.
         * This allows for a quick copy of data from one buffer to the other.
         * Useful for efficiency in creating trigger bank.
         */
        private boolean fastCopyReady;

        // Stuff needed to direct built events to proper output channel(s)

        /** Number (index) of the current, sequential-between-all-build-thds,
         * built bank produced from this thread.
         * 1st build thread starts at 0, 2nd starts at 1, etc.
         * 1st build thread's 2nd event is btCount, 2nd thread's 2nd event is btCount + 1, etc.*/
        private long evIndex = 0;

        /** Which channel does this thread currently output to, using round-robin?
         * outputChannelIndex = (int) (evIndex % outputChannelCount). */
        private int outputChannelIndex = 0;

        // RingBuffer Stuff

        /** Largest available input sequence. */
        private long availableSequence = -2;
        /** Next sequence. */
        private long nextSequence;


        private String thdName;



        /**
         * Constructor.
         *
         * @param btIndex place in relation to other build threads (first = 0)
         * @param name    thread name
         */
        BuildingThread(int btIndex, String name) {
            super(name);
            thdName = name;
            this.btIndex = btIndex;
            evIndex = btIndex;
            btCount = buildingThreadCount;
System.out.println("  Agg mod: create Build Thread (" + name + ") with index " + btIndex + ", count = " + btCount);
        }



        /** Run this thread. */
        public void run() {

            try {
                // Create a reusable supply of ByteBuffer objects
                // for writing built physics events into.
                //--------------------------------------------
                // Direct buffers give better performance ??
                //--------------------------------------------
                // If there's only one output channel, release should be sequential
// TODO: Double check to make sure output channels don't do something weird
                boolean releaseSequentially = true;
                if (outputChannelCount > 1)  {
                    releaseSequentially = false;
                }
                boolean useDirectBB = false;
                ByteBufferSupply bbSupply = new ByteBufferSupply(ringItemCount, 2000, outputOrder,
                                                                 useDirectBB, releaseSequentially);
System.out.println("  Agg mod: bbSupply -> " + ringItemCount + " # of bufs, direct = " + false +
                   ", seq = " + releaseSequentially);


                // Initialize
                int     tag;
                long    storedSequence = 0L;
                long    endSequence = -1;
                boolean havePhysicsEvents;
                boolean nonFatalError;
                boolean generalInitDone = false;
                EventType eventType;

                int[] bankData  = new int[3];
                int[] returnLen = new int[1];

                long[] timeStamps = new long[4];

                if (outputChannelCount > 1) {
                    outputChannelIndex = -1;
                }

                long frame, prevFrame=0;
                // The time frame we're currently looking for
                long lookingForTF = 0;
                // Current number of banks that have the same stamp
                int sliceCount;
                // Places to store banks, input nodes, and backing buffers with the same stamp.
                // The array should be sliceCount size, but we don't know
                // what that is yet. If it needs to be increased, do it later.
                PayloadBuffer[] sameStampBanks = new PayloadBuffer[200];
                EvioNode[] inputNodes = new EvioNode[200];
                ByteBuffer[] backingBufs = new ByteBuffer[200];

                PayloadBuffer bank, storedBank = null;
                TimeSliceBankItem timeSliceItem;


                // Ring Buffer stuff
                nextSequence = buildSequenceIn[btIndex].get() + 1L;

                // All banks on the ring are ROC raw / physics slices with the single possibility
                // that there is an END event. User and other controls have already
                // been dealt with in the sorter thread and been sent to output channels.

                // Now do the event building
                while (true) {

                    nonFatalError = false;

                    // Here we have what we need to build:
                    // ROC raw events from all ROCs (or partially built events from
                    // each contributing Aggregator) each with sequential time stamps.
                    // There may be several contiguous banks with the same time stamp.
                    // However, there are also END events in the rings.

                    // Set variables/flags
                    sliceCount = 0;

                    // Loop through all events placed into ring by sorter thread
                    while (true) {

                        // Only wait if necessary ...
                        if (availableSequence < nextSequence) {
                            // Can BLOCK here waiting for item if none available, but can be interrupted
                            // Available sequence may be larger than what we asked for.
//System.out.println("  Agg mod: bt" + btIndex + " ***** wait for event seq = " + nextSequence + ")");
                            availableSequence = buildBarrierIn[btIndex].waitFor(nextSequence);
//System.out.println("  Agg mod: bt" + btIndex + " ***** available seq  = " + availableSequence);
                        }

                        if (storedBank != null) {
                            bank = storedBank;
                            nextSequence = storedSequence;
                            storedBank = null;
//System.out.println("  Agg mod: bt" + btIndex + " ***** picking up stored bank at seq = " + nextSequence);
                        }
                        else {
                            // Next bank to work with ...
                            timeSliceItem = sorterRingBuffers[btIndex].get(nextSequence);
                            bank = timeSliceItem.getBuf();
                        }

                        frame = bank.getTimeFrame();
                        eventType = bank.getEventType();
//                        ControlType controlType = bank.getControlType();
//System.out.println("  Agg mod: bt" + btIndex + " ***** event order = " + bank.getByteOrder());
//System.out.println("  Agg mod: bt" + btIndex + " ***** frame = " + frame + ", event type = " + eventType + ", control type = " + controlType);

                        // Found a bank, so do something with it
//System.out.println("  Agg mod: bt" + btIndex + " ***** accept item " + thisSequence);

                        // If event needs to be built ...
                        if (!eventType.isControl()) {

                            // Do this once per build thread on first buildable event
                            if (!generalInitDone) {

                                lookingForTF = frame;

                                // Find out if the event's buffer has a backing byte array,
                                // if this object's ByteBufferSupply has buffers with backing byte arrays,
                                // and if the 2 buffers have same endianness.
                                // We do this so that when constructing the trigger bank, we can do
                                // efficient copying of data from ROC to trigger bank if possible.
                                try {
                                    ByteBuffer backingBuf = bank.getNode().getBuffer();
                                    if (backingBuf.hasArray() && !useDirectBB &&
                                            backingBuf.order() == outputOrder) {
                                        fastCopyReady = true;
                                        System.out.println("\nEFFICIENT copying is possible!!!\n");
                                    }
                                    else {
                                        fastCopyReady = false;
                                        System.out.println("\nEFFICIENT copying is NOT possible:\n" +
                                                "     backingBuf.hasArray = " + backingBuf.hasArray() +
                                                "\n     supplyBuf is direct = " + useDirectBB +
                                                "\n     backingBuf end = " + backingBuf.order() +
                                                "\n     outputORder = " + outputOrder);
                                    }
                                }
                                catch (Exception e) {
                                    fastCopyReady = false;
                                    System.out.println("\nEFFICIENT copying is NOT possible\n");
                                }
                                generalInitDone = true;
                            }

                            FrameNumberDiff diff = compareTimeFrames(frame, lookingForTF);

                            if (diff == FrameNumberDiff.SAME) {

                                // First check to make sure we have room to store this bank
                                if (sameStampBanks.length < (sliceCount + 1)) {
                                    // If not enough room, double the relevant arrays
                                    int newLength = 2*sameStampBanks.length;
//System.out.println("\n  Agg mod: bt" + btIndex + " ***** EXPAND arrays from " + sameStampBanks.length + " to " + newLength);
                                    PayloadBuffer[] sameStampBanksNew = new PayloadBuffer[newLength];
                                    inputNodes  = new EvioNode[newLength];
                                    backingBufs = new ByteBuffer[newLength];

                                    // Copy over array elements
                                    System.arraycopy(sameStampBanks, 0, sameStampBanksNew, 0, sameStampBanks.length);
                                    sameStampBanks = sameStampBanksNew;
                                }

                                sameStampBanks[sliceCount] = bank;
                                sliceCount++;
                                nextSequence++;
                                prevFrame = frame;
//System.out.println("\n  Agg mod: bt" + btIndex + " ***** found bank, look for another ---> continue, next seq = " + nextSequence +
//        ", frame (prevFrame) = " + frame + ", lookingForTF = " +lookingForTF);
                                continue;
                            }
                            else {
                                // If here, we're at the next time stamp,
                                // so go ahead and build what we've collected
                                // and use this bank in the next round
                                storedBank = bank;
                                storedSequence = nextSequence;
                                // Start looking for the next frame
                                lookingForTF = frame;
//System.out.println("\n  Agg mod: bt" + btIndex + " ***** at next timestamp, got to next seq = " + nextSequence + ", frame = " + frame +
//        ", prevFrame = " + prevFrame + ", lookingForTF = " + lookingForTF);
                            }

                            break;
                        }

                        // If we're here, we've got a CONTROL event and it's guaranteed to be an END
                        // since the only control events placed into the ring buffer is such.
                        // This will be the only build thread with the END event.

                        endSequence = nextSequence;
System.out.println("  Agg mod: bt" + btIndex + " ***** found END event at seq " + endSequence);
                        return;
                    }


                    // At this point there are only physics or ROC raw events, which do we have?
                    havePhysicsEvents = sameStampBanks[0].getEventType().isAnyPhysics();


                    //--------------------------------------------------------------------
                    // Build Stream Info Bank (SIB)
                    //--------------------------------------------------------------------
                    // The tag will be finally set when this bank is fully created

                    // Get an estimate on the buffer memory needed.
                    // Start with 10K and add roughly the amount of trigger bank data + data wrapper
                    int memSize = 10000; // Start with a little extra room
                    for (int i=0; i < sliceCount; i++) {
                        inputNodes[i] = sameStampBanks[i].getNode();
                        memSize += inputNodes[i].getTotalBytes();
                        // Get the backing buffer
                        backingBufs[i] = inputNodes[i].getBuffer();
                    }

                    // Grab a stored ByteBuffer
                    ByteBufferItem bufItem = bbSupply.get();
                    bufItem.ensureCapacity(memSize);
//System.out.println("  Agg mod: bt" + btIndex + " ***** ensure buf has size " + memSize + ", frame = " + frame + ", prevFrame = " + prevFrame);
                    ByteBuffer evBuf = bufItem.getBuffer();
//                    int builtEventHeaderWord2;

                    // Create a (top-level) physics event from payload banks
                    // and the combined SIB bank.
                    eventType = EventType.PHYSICS_STREAM;
                    tag = CODATag.STREAMING_PHYSICS.getValue();

                    // We assume that this is a first-level stream aggregator (input from ROCs/VTPs)
                    //CODAClass myClass = CODAClass.PAGG;
                    //if (myClass == CODAClass.PAGG) {
                        // Check input roc banks for non-fatal errors
                        for (int i=0; i < sliceCount; i++) {
                            // sorting thread checks to see if coda id matches tag, stored in payload bank
                            nonFatalError |= sameStampBanks[i].hasNonFatalBuildingError();
                        }
                    //}

                    int writeIndex=0;

                    // Building with ROC raw records ...

                        // If all inputs are from 1 VTP and can be combined into one ROC Time Slice Bank, do it
                        if (singleVTPInputs()) {
                            nonFatalError = Evio.combineSingleVtpStreamsToPhysics(
                                    sliceCount,
                                    evBuf,
                                    timeStamps,
                                    returnLen,
                                    backingBufs,
                                    inputNodes,
                                    fastCopyReady,
                                    nonFatalError);
                        }
                        else {
//System.out.println("  Agg mod: bt" + btIndex + " ***** Building frame " + prevFrame + " with " + sliceCount + " ROC RAW time slices");
                            nonFatalError = Evio.combineRocStreams(
                                    sliceCount,
                                    sameStampBanks,
                                    evBuf,
                                    tag,
                                    timestampSlop,
                                    prevFrame,
                                    bankData,
                                    returnLen,
                                    backingBufs,
                                    inputNodes,
                                    fastCopyReady,
                                    nonFatalError);
                        }

                        writeIndex = returnLen[0];

                    // Write the length of top bank
//                    System.out.println("writeIndex = " + writeIndex + ", %4 = " + (writeIndex % 4));
                    evBuf.limit(writeIndex).position(0);

                    //-------------------------
                    // Stats
                    //-------------------------


                    // Which output channel do we use?  Round-robin.
                    if (outputChannelCount > 1) {
                        outputChannelIndex = (int) (evIndex % outputChannelCount);
                    }

                    // Put event in the correct output channel.
//System.out.println("  Agg mod: bt#" + btIndex + " write event " + evIndex + " on ch" + outputChannelIndex + ", ring " + btIndex);
                    eventToOutputRing(btIndex, outputChannelIndex, sliceCount,
                                      evBuf, eventType, bufItem, bbSupply);

                    evIndex += btCount;

                    for (int i=0; i < sliceCount; i++) {
                        // The ByteBufferSupply takes care of releasing buffers in proper order.
                        sameStampBanks[i].releaseByteBuffer();

                        // Since we're done building with sameStampBanks,
                        // we can release them back to input channel rings.
                        long seq = sameStampBanks[i].getChannelSequence();
                        Sequence seqObj = sameStampBanks[i].getChannelSequenceObj();
                        // The ring will now have access to this sequence
                        seqObj.set(seq);
                    }

                    // Each build thread must release the "slots" in the build thread ring
                    // buffer of the components it uses to build the physics event.
//System.out.println("  Agg mod: bt#" + btIndex + " release build seq " + (nextSequence - 1));
                    buildSequenceIn[btIndex].set(nextSequence - 1);
                }
            }
            catch (InterruptedException e) {
                System.out.println("  Agg mod: INTERRUPTED build thread " + Thread.currentThread().getName());
            }
            catch (final TimeoutException e) {
                System.out.println("  Agg mod: timeout in ring buffer");
            }
            catch (final AlertException e) {
                System.out.println("  Agg mod: alert in ring buffer");
            }
            catch (Exception e) {
                e.printStackTrace();
                System.out.println("  Agg mod: MAJOR ERROR building event: " + e.getMessage());
            }

        }

    } // BuildingThread



    /**
     * Interrupt all EB threads because an END cmd/event or RESET cmd came through.
     * @param end if <code>true</code> called from end(), else called from reset()
     */
    private void interruptThreads(boolean end) {
        // Interrupt the sorter thread
        if (timeSliceSorterThread != null) {
            timeSliceSorterThread.interrupt();
        }

        // Interrupt all Building threads
        for (Thread thd : buildingThreadList) {
            // Try to end thread nicely but it could block on rb.next()
            // when writing to output channel ring if no available space
            thd.interrupt();
        }
    }

    /**
     * Try joining all EB threads, up to 1 sec each.
     */
    private void joinThreads() {

        // Join sorter thread
        if (timeSliceSorterThread != null) {
            try {
                timeSliceSorterThread.join(1000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Join all Building threads
        for (Thread thd : buildingThreadList) {
            try {
                thd.join(1000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Start threads for stats, processing incoming events, and building events.
     * It creates these threads if they don't exist yet.
     */
    private void startThreads() {

        // Time slice sorting thread
        if (timeSliceSorterThread != null) {
            timeSliceSorterThread.interrupt();
            try {
                timeSliceSorterThread.join(1000);
            }
            catch (Exception e) {}
        }
        timeSliceSorterThread = new TimeSliceSorter();
        if (timeSliceSorterThread.getState() == Thread.State.NEW) {
System.out.println("  Aggregator.startThreads(): start time slice sorter thread");
            timeSliceSorterThread.start();
        }

        // Build threads
        buildingThreadList.clear();
        for (int i = 0; i < buildingThreadCount; i++) {
            BuildingThread thd1 = new BuildingThread(i, name + ":builder" + i);
            buildingThreadList.add(thd1);
System.out.println("  Aggregator.startThreads(): start build thread " + i);
            thd1.start();
        }
    }


    //---------------------------------------
    // State machine
    //---------------------------------------


    /** {@inheritDoc} */
    public void reset() {

        // EB threads must be immediately ended
        interruptThreads(false);
        joinThreads();

        timeSliceSorterThread = null;
        buildingThreadList.clear();
        Arrays.fill(sorterRingBuffers, null);

        paused = false;
    }


    /** {@inheritDoc} */
    public void end() {

        // Build & pre-processing threads should already be ended by END event
        interruptThreads(true);
        joinThreads();

        timeSliceSorterThread = null;
        buildingThreadList.clear();
        Arrays.fill(sorterRingBuffers, null);

        paused = false;
    }


    /** {@inheritDoc} */
    public void prestart()  {

        //------------------------------------------------
        // Disruptor (RingBuffer) stuff for input channels
        //------------------------------------------------

        // For each input channel, 1 ring buffer
        ringBuffersIn = new RingBuffer[inputChannelCount];
        // For each input channel, 1 sequence (used by sorter thread)
        sorterSequenceIn = new Sequence[inputChannelCount];
        // For each input channel, 1 barrier
        sorterBarrierIn = new SequenceBarrier[inputChannelCount];

        // Place to put ring level stats
        inputChanLevels  = new int[inputChannelCount];
        outputChanLevels = new int[outputChannelCount];

        // Collect channel names for easy gathering of stats
        int indx=0;
        inputChanNames = new String[inputChannelCount];
        for (DataChannel ch : inputChannels) {
            inputChanNames[indx++] = ch.name();
        }
        indx = 0;
        outputChanNames  = new String[outputChannelCount];
        for (DataChannel ch : outputChannels) {
            outputChanNames[indx++] = ch.name();
        }

        // Have ring sizes handy for calculations
        ringBufferSize = new int[inputChannelCount];

        // For each channel ...
        for (int i=0; i < inputChannelCount; i++) {
            // Get channel's ring buffer
            RingBuffer<RingItem> rb = inputChannels.get(i).getRingBufferIn();
            ringBuffersIn[i]  = rb;
            ringBufferSize[i] = rb.getBufferSize();

            // For sorter thread

            // We have 1 sequence for each input channel
            sorterSequenceIn[i] = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
            // This sequence will be the last consumer before producer comes along
            rb.addGatingSequences(sorterSequenceIn[i]);

            // We have 1 barrier for each channel
            sorterBarrierIn[i] = rb.newBarrier();
        }

        //------------------------------------------------
        // Disruptor (RingBuffer) stuff for build threads
        //------------------------------------------------

        // 1 sequence for each build thread
        buildSequenceIn = new Sequence[buildingThreadCount];
        // 1 barrier for each build thread
        buildBarrierIn = new SequenceBarrier[buildingThreadCount];

        // For each build thread ...
        for (int j = 0; j < buildingThreadCount; j++) {
            // Create 1 ring to hold TimeSliceBanks.
            // This ring only holds references to objects so no real memory being used.
            sorterRingBuffers[j] = createSingleProducer(new TimeSliceBankItemFactory(), sorterRingSize,
                    new SpinCountBackoffWaitStrategy(10000, new LiteBlockingWaitStrategy()));
            // We have 1 sequence
            buildSequenceIn[j] = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
            // This sequence will be the last consumer before sorter produces more
            sorterRingBuffers[j].addGatingSequences(buildSequenceIn[j]);
            // We have 1 barrier
            buildBarrierIn[j] = sorterRingBuffers[j].newBarrier();
        }


        startThreads();
    }





 }