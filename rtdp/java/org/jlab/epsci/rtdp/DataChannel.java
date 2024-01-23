/*
 * Copyright (c) 2008, Jefferson Science Associates
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


/**
 * This interface defines an object that can send and
 * receive data. It refers to a single, particular connection
 * (eg. a single socket or cMsg connection object).
 *
 * @author heyes
 * @author timmer
 * Created on Sep 12, 2008
 */
public interface DataChannel {


    /**
     * Get the CODA ID number of the CODA component connected to this
     * data channel. In event building, this is used, for example, to check
     * the ROC id of incoming event which allows consistency checking.
     * @return the CODA ID number the CODA component connected to this
     *         data channel.
     */
    int getID();

    /**
     * This method returns the streams number.
     * Used only in the case of generating multiple input channels from a single
     * config input channel entry - as in the case of a VTP connected to an aggregator.
     * The aggregator will possible generate multiple channels. This number is used
     * to distinguish between these channels.
     *
     * @return stream number.
     */
    int getStreamNumber();

    /**
     * Get the record ID number of the latest event through this channel.
     * In CODA event building this is used, for example, to track the
     * record ids for input events which allows consistency
     * checks of incoming data.
     * @return the record ID number of the latest event through channel.
     */
    int getRecordId();

    /**
     * Set the record ID number of the latest event through this channel.
     * @param recordId record ID number of the latest event through channel.
     */
    void setRecordId(int recordId);

    /**
     * Get the name of this data channel.
     * @return the name of this data channel.
     */
    String name();

    /**
     * Get whether this channel is an input channel (true),
     * or it is an output channel (false).
     * @return <code>true</code> if input channel, else <code>false</code>
     */
    boolean isInput();

    /**
     * Get the one and only input ring buffer of this data channel.
     * @return the input ring buffer.
     */
    RingBuffer<RingItem> getRingBufferIn();

    /**
     * Get the total number of data-holding output ring buffers.
     * @return total number of data-holding output ring buffers.
     */
    int getOutputRingCount();

    /**
     * Get the array of output ring buffers.
     * @return array of output ring buffers.
     */
    RingBuffer<RingItem>[] getRingBuffersOut();

    /**
     * Get the relative fill level (0-100) of the ring of this input channel.
     * @return relative fill level (0-100) of all the ring.
     */
    int getInputLevel();

    /**
     * Get the relative fill level (0-100) of all the rings of this output channel.
     * Module calls this to report its output channels' levels.
     * (Input levels are associated directly with the module).
     * @return relative fill level (0-100) of all the rings together.
     */
    int getOutputLevel();

}
