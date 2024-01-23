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

import com.lmax.disruptor.RingBuffer;
import org.jlab.coda.emu.support.data.RingItem;

import java.nio.ByteOrder;
import java.util.ArrayList;


/**
 * This class contains boilerplate code for implementing a module.
 *
 * @author timmer
 * Mar 20, 2014
 */
public class ModuleAdapter  {


    /** ID number of this module obtained from config file. */
    protected int id;

    /** Number of event producing threads in operation. Each
     *  must match up with its own output channel ring buffer. */
    protected int eventProducingThreads;

    /** Name of this module. */
    protected final String name;


    /**
     * ArrayList of DataChannel objects for this module that are inputs.
     * It is only modified in the {@link #addInputChannels(ArrayList)} and
     * {@link #clearChannels()} methods and then only by the main EMU thread
     * in prestart. However, other threads (such as the EMU's statistics reporting
     * thread) call methods which use its iterator or getters.
     */
    protected ArrayList<DataChannel> inputChannels = new ArrayList<>(16);

    /** ArrayList of DataChannel objects that are outputs. Only modified in prestart
     *  but used during go when writing module output. */
    protected ArrayList<DataChannel> outputChannels = new ArrayList<>(4);

    /** Number of output channels. */
    protected int inputChannelCount;

    /** Number of output channels. */
    protected int outputChannelCount;

    /** User hit PAUSE button if {@code true}. */
    protected boolean paused;

    /** Do we produce big or little endian output in ByteBuffers? */
    protected ByteOrder outputOrder;

    /** If <code>true</code>, data to be built is from a streaming (not triggered) source. */
    protected boolean streamingData;

    /** If <code>true</code>, and if streamingData is true, the number of streams coming from
     * a single VTP to a single aggregator. */
    protected int streamCount;

    /** If <coda>true</coda>, data processed by this module comes from a VTP. */
    protected boolean dataFromVTP = true;

    /** If <coda>true</coda>, all inputs comes from a single VTP. */
    protected boolean singleVTPInputs;

    //---------------------------
    // For generating statistics
    //---------------------------

    /** Array containing, for each input channel, the percentage (0-100)
     *  of filled ring space. */
    protected int[] inputChanLevels;

    /** Array containing, for each output channel, the percentage (0-100)
     *  of filled ring space. */
    protected int[] outputChanLevels;

    /** Array containing names for each input channel. */
    protected String[] inputChanNames;

    /** Array containing names for each output channel. */
    protected String[] outputChanNames;

    // ---------------------------------------------------



    /**
     * Constructor creates a new EventRecording instance.
     *
     * @param name name of module
     */
    public ModuleAdapter(String name) {
        this.name = name;
        id = 0;

        // Set number of event-producing threads
        eventProducingThreads = 1;

        // Is output written in big or little endian?
        outputOrder = ByteOrder.nativeOrder();
System.out.println("  Module Adapter: output byte order = " + outputOrder);

        // For FPGAs, fake ROCs, or EBs: is data in streaming format?
        streamingData = true;

        // For a single VTP, how many streams are going to the aggregator?
        streamCount = 1;
    }


    /**
     * This method is used to place an item onto a specified ring buffer of a
     * single, specified output channel.
     *
     * @param itemOut    the event to place on output channel
     * @param channelNum index of output channel to place item on
     * @param ringNum    index of output channel ring buffer to place item on
     * @throws InterruptedException it thread interrupted.
     */
    protected void eventToOutputChannel(RingItem itemOut, int channelNum, int ringNum)
                        throws InterruptedException{

        // Have any output channels?
        if (outputChannelCount < 1) {
//System.out.println("  Module Adapter: no output channel so release event w/o publishing");
            itemOut.releaseByteBuffer();
            return;
        }
//System.out.println("  Module Adapter: publishing events in out chan ring");

        RingBuffer rb = outputChannels.get(channelNum).getRingBuffersOut()[ringNum];
        long nextRingItem = rb.nextIntr(1);

        RingItem ri = (RingItem) rb.get(nextRingItem);
        ri.copy(itemOut);
        rb.publish(nextRingItem);
    }


    /** {@inheritDoc} */
    public String[] getInputNames() {
        // The values in this array are set in the EB & ER modules
        return inputChanNames;
    }


    /** {@inheritDoc} */
    public String[] getOutputNames() {
        // The values in this array are set in the EB & ER modules
        return outputChanNames;
    }


    /** {@inheritDoc} */
    public int[] getOutputLevels() {
        // The values in this array need to be obtained from each output channel
        if (outputChanLevels != null) {
            int i=0;
            for (DataChannel chan : outputChannels) {
                outputChanLevels[i++] = chan.getOutputLevel();
            }
        }
        return outputChanLevels;
    }


    /** {@inheritDoc} */
    public int[] getInputLevels() {
        // The values in this array need to be obtained from each input channel
        if (inputChanLevels != null) {
            int i=0;
            for (DataChannel chan : inputChannels) {
                inputChanLevels[i++] = chan.getInputLevel();
            }
        }
        return inputChanLevels;
    }


    //-----------------------------------------------------------
    // For EmuModule interface
    //-----------------------------------------------------------


    /** {@inheritDoc} */
    public String name() {return name;}

    /** {@inheritDoc} */
    public int getInternalRingCount() {return 0;}


    /** {@inheritDoc} */
    public void addInputChannels(ArrayList<DataChannel> input_channels) {
        if (input_channels == null) return;
        inputChannels.addAll(input_channels);
        inputChannelCount  = inputChannels.size();
    }

    /** {@inheritDoc} */
    public void addOutputChannels(ArrayList<DataChannel> output_channels) {
        if (output_channels == null) return;
        outputChannels.addAll(output_channels);
        outputChannelCount = outputChannels.size();
    }

    /** {@inheritDoc} */
    public ArrayList<DataChannel> getInputChannels() {return inputChannels;}

    /** {@inheritDoc} */
    public ArrayList<DataChannel> getOutputChannels() {return outputChannels;}

    /** {@inheritDoc} */
    public void clearChannels() {
        inputChannels.clear();
        outputChannels.clear();
        inputChannelCount = outputChannelCount = 0;

        outputChanLevels = null;
        outputChanNames  = null;
        inputChanLevels  = null;
        inputChanNames   = null;
    }

    /** {@inheritDoc} */
    public int getEventProducingThreadCount() {return eventProducingThreads;}

    /** {@inheritDoc} */
    public ByteOrder getOutputOrder() {return outputOrder;}

    /** {@inheritDoc} */
    public boolean isStreamingData() {return streamingData;}

    /** {@inheritDoc} */
    public boolean dataFromVTP() {return dataFromVTP;}

    /** {@inheritDoc} */
    public boolean singleVTPInputs() {return singleVTPInputs;}

    /**
     * Method to set whether this module has only a single VTP for an input channel.
     * The EMU prestart method calls this to set singleVTPInputs based on its parsing of config file.
     * Called to notify the module that inputs can be combined into a single
     * ROC Time Slice Bank.
     * @param singleVTPInputs true if this module has only a single VTP for an input channel, else false.
     */
    public void setSingleVTPInputs(boolean singleVTPInputs) {
        this.singleVTPInputs = singleVTPInputs;
    }


    //----------------------------------------------------------------


}