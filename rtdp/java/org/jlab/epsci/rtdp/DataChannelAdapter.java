/*
 * Copyright (c) 2013, Jefferson Science Associates
 *
 * Thomas Jefferson National Accelerator Facility
 * Data Acquisition Group
 *
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 */

package org.jlab.epsci.rtdp;


import com.lmax.disruptor.*;
import org.jlab.coda.emu.EmuUtilities;
import org.jlab.coda.emu.EmuException;
import org.jlab.coda.emu.support.data.RingItem;
import org.jlab.coda.emu.support.data.RingItemFactory;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

/**
 * This class provides boilerplate code for the DataChannel
 * interface (which includes the CODAStateMachine interface).
 * Extending this class implements the DataChannel interface and frees
 * any subclass from having to implement common methods or those that aren't used.<p>
 * This class defines an object that can send and
 * receive banks of data in the CODA evio format. It
 * refers to a particular connection (eg. an et open
 * or cMsg connection id).
 *
 * @author timmer
 *         (Apr 25, 2013)
 */
public  class DataChannelAdapter implements DataChannel {

    /** Channel id (corresponds to sourceId of ROCs for CODA event building). */
    protected int id;

    /** Record id (corresponds to evio events flowing through data channel). */
    protected int recordId;

    /**
     * Channel error message. reset() sets it back to null.
     * Making this an atomically settable String ensures that only 1 thread
     * at a time can change its value. That way it's only set once per error.
     */
    protected AtomicReference<String> errorMsg = new AtomicReference<>();

    /** Channel name */
    protected final String name;

    /** Is this channel an input (true) or output (false) channel? */
    protected final boolean input;

    /** Byte order of output data. */
    protected ByteOrder byteOrder;

    /** Got END command from Run Control. */
    protected volatile boolean gotEndCmd;

    /** Got RESET command from Run Control. */
    protected volatile boolean gotResetCmd;

    //---------------------------------------------------------------------------
    // AUTO GENERATION OF INPUT CHANNELS
    //---------------------------------------------------------------------------

    /**
     * Used only in the case of generating multiple input channels from a single
     * config input channel entry - as in the case of a VTP connected to an aggregator.
     * The aggregator will possible generate multiple channels. This number is used
     * to distinguish between these channels.
     */
    protected int streamNumber;

    //---------------------------------------------------------------------------
    // Used to determine which ring to get event from if multiple output channels
    //---------------------------------------------------------------------------

    /** Total number of module's output channels. */
    protected int outputChannelCount;

    /** Total number of module's event-building threads and therefore output ring buffers. */
    protected int outputRingCount;

    /** This output channel's order in relation to the other output channels
     * for module, starting at 0. First event goes to channel 0, etc. */
    protected int outputIndex;

    /** Ring that the next event will show up on. */
    protected int ringIndex;

    /**
     * Number of the module's buildable event produced
     * which this channel will output next (starting at 0).
     * Depends on the # of output channels as well as the order of this
     * channel (outputIndex).
     */
    protected long nextEvent;

    /** Keep track of output channel thread's state. */
    protected enum ThreadState {RUNNING, DONE, INTERRUPTED};

    //-------------------------------------------
    // Disruptor (RingBuffer)  Stuff
    //-------------------------------------------

    // Input
    /** Ring buffer - one per input channel. */
    protected RingBuffer<RingItem> ringBufferIn;

    /** Number of items in input ring buffer. */
    protected int inputRingItemCount;

    // Output
    /** Array holding all ring buffers for output. */
    protected RingBuffer<RingItem>[] ringBuffersOut;

    /** Number of items in output ring buffers. */
    protected int outputRingItemCount;

    /** One barrier for each output ring. */
    protected SequenceBarrier[] sequenceBarriers;

    /** One sequence for each output ring. */
    protected Sequence[] sequences;

    /** Index of next ring item. */
    protected long[] nextSequences;

    /** Maximum index of available ring items. */
    protected long[] availableSequences;

    /** When releasing in sequence, the last sequence to have been released. */
    private long[] lastSequencesReleased;

    /** When releasing in sequence, the highest sequence to have asked for release. */
    private long[] maxSequences;

    /** When releasing in sequence, the number of sequences between maxSequence &
     * lastSequenceReleased which have called release(), but not been released yet. */
    private int[] betweens;

    /** Total number of slots in all output channel ring buffers. */
    protected int totalRingCapacity;


static int idNum = 0;


    /**
     * Constructor to create a new DataChannel instance.
     * Used only by a transport's createChannel() method
     * which is only called during PRESTART in the Emu.
     *
     * @param name          the name of this channel
     * @param input         true if this is an input data channel, otherwise false
     * @param outputIndex   order in which module's events will be sent to this
     *                      output channel (0 for first output channel, 1 for next, etc.).
    */
    public DataChannelAdapter(String name,
                              boolean input,
                              int outputIndex) {
        this.name = name;
        this.input = input;
        this.outputIndex = outputIndex;


        // Set id number
        id = idNum++;


        if (input) {
            // FIFO code never makes it here.
            // Each FIFO must be listed in config file with output fifo coming first
            // and its input counterpart coming second. Thus, when initially constructed,
            // it is always as an output channel. To get the already-existing fifo as an
            // input channel, a lookup is done.
            // Input Fifo calls setupInputRingBuffers(), but does not construct this channel.

            // Set the number of items for the input ring buffers.
            // These contain evio events parsed from ET, cMsg,
            // or Emu domain buffers. They should not take up much mem.
            // Or it can contain fully built events from fifo.
            inputRingItemCount = 4096;

            // Create RingBuffers
            setupInputRingBuffers();
            System.out.println("      DataChannel Adapter: input ring item count -> " + inputRingItemCount);
        }
        else {
            // Set the number of items for the output chan ring buffers.
            // We don't need any more slots than we have internal buffers.
            // The number returned by getInternalRingCount is for a single build thread
            // times the number of build threads. Since a channel has one ring for each
            // build thread, the # of items in any one ring is
            // getInternalRingCount / buildThreadCount. Should be a power of 2 already
            // but will enforce that.

            outputRingItemCount = 256;

            outputRingItemCount = EmuUtilities.powerOfTwo(outputRingItemCount, false);
            System.out.println("      DataChannel Adapter: output ring item count -> " + outputRingItemCount);

            // Set endianness of output data, default same as this node
            byteOrder = ByteOrder.nativeOrder();
            System.out.println("      DataChannel Adapter: byte order = " + byteOrder);

            // Set number of data output ring buffers (1 for each build thread, only 1 in this case)
            outputRingCount = 1;
            System.out.println("      DataChannel Adapter: output ring buffer count (1/buildthread) = " + outputRingCount);

            // Create RingBuffers
            ringBuffersOut = new RingBuffer[outputRingCount];
            setupOutputRingBuffers();

            // Total capacity of all ring buffers
            totalRingCapacity = outputRingCount * outputRingItemCount;

            // Init arrays
            lastSequencesReleased = new long[outputRingCount];
            maxSequences = new long[outputRingCount];
            betweens = new int[outputRingCount];
            Arrays.fill(lastSequencesReleased, -1L);
            Arrays.fill(maxSequences, -1L);
            Arrays.fill(betweens, 0);

            // Normally do this in prestart

            // Get output channel count from module
            outputChannelCount = 1;

            // Initialize the event number (first buildable event)
            nextEvent = outputIndex;

            // Initialize the ring number (of first buildable event)
            ringIndex = (int) (nextEvent % outputRingCount);

System.out.println("      DataChannel Adapter: prestart, nextEv (" +
                           nextEvent + "), ringIndex (" + ringIndex + ')' +
                    ", output channel count = (" + outputChannelCount + ")");

        }
     }


    /** Setup the output channel ring buffers. */
    void setupOutputRingBuffers() {
        sequenceBarriers = new SequenceBarrier[outputRingCount];
        sequences = new Sequence[outputRingCount];

        nextSequences = new long[outputRingCount];
        availableSequences = new long[outputRingCount];
        Arrays.fill(availableSequences, -1L);

        for (int i=0; i < outputRingCount; i++) {
            ringBuffersOut[i] =
                createSingleProducer(new RingItemFactory(),
                                     outputRingItemCount,
          new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));

            // One barrier for each ring
            sequenceBarriers[i] = ringBuffersOut[i].newBarrier();
            sequenceBarriers[i].clearAlert();

            // One sequence for each ring for reading in output channel
            sequences[i] = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
            ringBuffersOut[i].addGatingSequences(sequences[i]);
            nextSequences[i] = sequences[i].get() + 1L;
        }
    }


    /** Setup the input channel ring buffers. */
    void setupInputRingBuffers() {
        ringBufferIn = createSingleProducer(new RingItemFactory(),
                                            inputRingItemCount,
        new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));
    }


    /**
     * Set the index of the next buildable event to get from the module
     * and the ring it will appear on.<p>
     * NOTE: only called IFF outputRingCount &gt; 1.
     *
     * @return index of ring that next event will be placed in
     */
    protected int setNextEventAndRing() {
//        System.out.print("      DataChannel Adapter:next ev (" + nextEvent + ") -> (" +
//                                   nextEvent + " + " + outputChannelCount + ") = ");
        nextEvent += outputChannelCount;
//System.out.println(nextEvent);

        ringIndex = (int) (nextEvent % outputRingCount);
//System.out.println("      DataChannel Adapter: set next ev (" + nextEvent + "), nextEv % bt -> (" +
//                           nextEvent + " % " + outputRingCount + ")");
        return ringIndex;
    }


    /** {@inheritDoc} */
    public int getStreamNumber() {return streamNumber;}

    /** {@inheritDoc} */
    public int getID() {return id;}

    /** {@inheritDoc} */
    public int getRecordId() {return recordId;}

    /** {@inheritDoc} */
    public void setRecordId(int recordId) {this.recordId = recordId;}

    /** {@inheritDoc} */
    public String getError() {return errorMsg.get();}

    /** {@inheritDoc} */
    public String name() {return name;}

    /** {@inheritDoc} */
    public boolean isInput() {return input;}

//    /** {@inheritDoc} */
//    public DataTransport getDataTransport() {return dataTransport;}

    /** {@inheritDoc} */
    public int getOutputRingCount() {return outputRingCount;}

    /** {@inheritDoc} */
    public RingBuffer<RingItem> getRingBufferIn() {return ringBufferIn;}

    /** {@inheritDoc} */
    public RingBuffer<RingItem>[] getRingBuffersOut() {return ringBuffersOut;}


    public long getNextSequence(int ringIndex) {
        if (ringIndex < 0) return -1L;
        return nextSequences[ringIndex];
    }

    /** {@inheritDoc} */
    public int getInputLevel() {return 0;}

    /** {@inheritDoc} */
    public int getOutputLevel() {
        int count=0;

        for (int i=0; i < outputRingCount; i++) {
            // getCursor() does 1 volatile read to get max available sequence.
            // It's important to calculate the output channel level this way,
            // especially for the ET channel since it may get stuck waiting for
            // new ET events to become available and not be able to update ring
            // statistics (more specifically, availableSequences[]).
            //count += (int)(sequenceBarriers[i].getCursor() - nextSequences[i] + 1);

            count += (int)(ringBuffersOut[i].getCursor() - nextSequences[i] + 1);
        }

        // When (cursor(or avail) - next + 1) == ringSize, then the Q is full.
        return count*100/totalRingCapacity;
    }


    /**
     * Gets the next ring buffer item placed there by the last module.
     * Only call from one thread. MUST be followed by call to
     * {@link #releaseOutputRingItem(int)} AFTER the returned item
     * is used or nothing will work right.
     *
     * @param ringIndex ring buffer to take item from
     * @return next ring buffer item
     * @throws InterruptedException if thread interrupted.
     * @throws EmuException problem with the ring buffer.
     */
    protected RingItem getNextOutputRingItem(int ringIndex)
            throws InterruptedException, EmuException {

        RingItem item = null;
//            System.out.println("getNextOutputRingITem: index = " + ringIndex);
//            System.out.println("                     : availableSequences = " + availableSequences[ringIndex]);
//            System.out.println("                     : nextSequences = " + nextSequences[ringIndex]);

        try  {
            // Only wait if necessary ...
            if (availableSequences[ringIndex] < nextSequences[ringIndex]) {
//System.out.println("getNextOutputRingITem: WAITING");
                availableSequences[ringIndex] =
                        sequenceBarriers[ringIndex].waitFor(nextSequences[ringIndex]);
            }
//System.out.println("getNextOutputRingITem: available seq[" + ringIndex + "] = " +
//                           availableSequences[ringIndex] +
//                        ", next seq = " + nextSequences[ringIndex] +
//" delta + 1 = " + (availableSequences[ringIndex] - nextSequences[ringIndex] +1));

            item = ringBuffersOut[ringIndex].get(nextSequences[ringIndex]);
//System.out.println("getNextOutputRingItem: got seq[" + ringIndex + "] = " + nextSequences[ringIndex]);
//System.out.println("Got ring item " + item.getRecordId());
        }
        catch (final TimeoutException ex) {
            // never happen since we don't use timeout wait strategy
        }
        catch (final AlertException ex) {
            throw new EmuException("Channel Adapter: ring buf alert");
        }

        return item;
    }


    /**
     * Releases the item obtained by calling {@link #getNextOutputRingItem(int)},
     * so that it may be reused for writing into by the last module.
     * And it prepares to get the next ring item when that method is called.
     *
     * Must NOT be used in conjunction with {@link #releaseOutputRingItem(int)}
     * and {@link #gotoNextRingItem(int)}.
     *
     * @param ringIndex ring buffer to release item to
     */
    protected void releaseCurrentAndGoToNextOutputRingItem(int ringIndex) {
        sequences[ringIndex].set(nextSequences[ringIndex]);
//        System.out.print("releaseCurrentAndGoToNextOutputRingItem: rel[" + ringIndex +
//                                 "] = " + nextSequences[ringIndex]);
        nextSequences[ringIndex]++;
//        System.out.println("  ->  " + nextSequences[ringIndex]);
    }

    //
    // The next 2 methods are to be used together in place of the above method.
    //

    /**
     * Releases the item obtained by calling {@link #getNextOutputRingItem(int)},
     * so that it may be reused for writing into by the last module.
     * Must NOT be used in conjunction with {@link #releaseCurrentAndGoToNextOutputRingItem(int)}
     * and must be called after {@link #gotoNextRingItem(int)}.
     * @param ringIndex ring buffer to release item to
     */
    protected void releaseOutputRingItem(int ringIndex) {
//System.out.println("releaseOutputRingItem: got seq = " + (nextSequences[ringIndex]-1));
        sequences[ringIndex].set(nextSequences[ringIndex] - 1);
    }

    /**
     * It prepares to get the next ring item after {@link #getNextOutputRingItem(int)}
     * is called.
     * Must NOT be used in conjunction with {@link #releaseCurrentAndGoToNextOutputRingItem(int)}
     * and must be called before {@link #releaseOutputRingItem(int)}.
     * @param ringIndex ring buffer to release item to
     */
    protected void gotoNextRingItem(int ringIndex) {
//System.out.println("gotoNextRingItem: got seq = " + (nextSequences[ringIndex]+1));
        nextSequences[ringIndex]++;
    }


    /**
     * Releases the items obtained by calling {@link #getNextOutputRingItem(int)},
     * so that it may be reused for writing into by the last module.
     * Must NOT be used in conjunction with {@link #releaseCurrentAndGoToNextOutputRingItem(int)}
     * or {@link #releaseOutputRingItem(int)}
     * and must be called after {@link #gotoNextRingItem(int)}.<p>
     *
     * This method <b>ensures</b> that sequences are released in order and is thread-safe.
     * Only works if each ring item is released individually.
     *
     * @param ringIndexes array of ring buffers to release item to
     * @param seqs array of sequences to release.
     * @param len number of array items to release
     */
    synchronized protected void sequentialReleaseOutputRingItem(byte[] ringIndexes, long[] seqs, int len) {
        long seq;
        int ringIndex;

        for (int i=0; i < len; i++) {
            seq = seqs[i];
            ringIndex = ringIndexes[i];

            // If we got a new max ...
            if (seq > maxSequences[ringIndex]) {
                // If the old max was > the last released ...
                if (maxSequences[ringIndex] > lastSequencesReleased[ringIndex]) {
                    // we now have a sequence between last released & new max
                    betweens[ringIndex]++;
                }

                // Set the new max
                maxSequences[ringIndex] = seq;
//System.out.println("    set max seq = " + seq + " for ring " + ringIndex);
            }
            // If we're < max and > last, then we're in between
            else if (seq > lastSequencesReleased[ringIndex]) {
                betweens[ringIndex]++;
//System.out.println("    add in between seq = " + seq + " for ring " + ringIndex);
            }

            // If we now have everything between last & max, release it all.
            // This way higher sequences are never released before lower.
            if ( (maxSequences[ringIndex] - lastSequencesReleased[ringIndex] - 1L) == betweens[ringIndex]) {
//System.out.println("    release seq " + sequences[ringIndex] + " for ring " + ringIndex);
                sequences[ringIndex].set(maxSequences[ringIndex]);
                lastSequencesReleased[ringIndex] = maxSequences[ringIndex];
                betweens[ringIndex] = 0;
            }
        }
    }


}
