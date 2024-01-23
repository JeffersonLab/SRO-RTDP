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

import org.jlab.coda.emu.EmuException;
import org.jlab.coda.emu.support.data.ControlType;
import org.jlab.coda.emu.support.data.EventType;
import org.jlab.coda.emu.support.data.RingItem;
import org.jlab.coda.emu.support.transport.TransportType;
import org.jlab.coda.jevio.EventWriterUnsync;
import org.jlab.coda.jevio.EvioException;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Implementation of a DataChannel reading/writing from/to a file in EVIO format.
 *
 * @author heyes
 * @author timmer
 * (Nov 10, 2008)
 */
public class DataChannelImplFile extends DataChannelAdapter {


    /** Thread used to output data. */
    private final DataOutputHelper dataOutputThread;

    /** Name of file being written-to / read-from. */
    private final String fileName;

    //----------------------------------------
    // Output file parameters
    //----------------------------------------

    /** Evio file writer. */
    private final EventWriterUnsync evioFileWriter;


    /**
     * Constructor DataChannelImplFile creates a new DataChannelImplFile instance.
     *
     * @param chName  name of file channel
     * @param fileName  name of file to create & write to
     * @throws DataTransportException if unable to create file.
     */
    DataChannelImplFile(String chName, String fileName) throws DataTransportException {

        // constructor of super class
        super(chName, false, 0);
        this.fileName = fileName;

        System.out.println("      DataChannel File: file name = " + fileName);

        try {
                // Only for output channels here !!!
                evioFileWriter = new EventWriterUnsync(fileName);

System.out.println("      DataChannel File: file = " + evioFileWriter.getCurrentFilePath());

                dataOutputThread = new DataOutputHelper(name() + "_data out");
                dataOutputThread.start();
                dataOutputThread.waitUntilStarted();
        }
        catch (Exception e) {
            e.printStackTrace();
            if (input) {
System.out.println("      DataChannel File in: Cannot open file, " + e.getMessage());
                throw new DataTransportException("      DataChannel File in: Cannot open data file " + e.getMessage(), e);
            }
            else {
System.out.println("      DataChannel File out: Cannot create file, " + e.getMessage());
                throw new DataTransportException("      DataChannel File out: Cannot create data file " + e.getMessage(), e);
            }
        }
    }

    /** {@inheritDoc} */
    public TransportType getTransportType() {return TransportType.FILE;}


    /**
     * Class <b>DataOutputHelper </b>
     * Handles writing evio events (banks) to a file.
     * A lot of the work is done in jevio such as splitting files.
     */
    private class DataOutputHelper extends Thread {

        /** Let a single waiter know that the main thread has been started. */
        private final CountDownLatch latch = new CountDownLatch(1);


        DataOutputHelper(String name) {
            super(name);
        }


        /** A single waiter can call this method which returns when thread was started. */
        private void waitUntilStarted() {
            try {
                latch.await();
            }
            catch (InterruptedException e) {}
        }


        /**
         * Write event to file.
         *
         * @param ri          item to write to disk
         * @param forceToDisk if true, force event to hard disk
         * @param ownRecord   if true, write event in its own record
         *
         * @throws IOException
         * @throws EvioException
         * @throws InterruptedException
         */
        private void writeEvioData(RingItem ri, boolean forceToDisk, boolean ownRecord)
                throws IOException, EvioException, InterruptedException {

                boolean written;
                int repeatLoops = 0;
                boolean sentMsgToRC = false;

                if (ri.getBuffer() != null) {
//System.out.println("      DataChannel File out: write buffer with order = " + ri.getBuffer().order());
                    written = evioFileWriter.writeEventToFile(null, ri.getBuffer(), forceToDisk, ownRecord);
                }
                else {
                    //System.out.println("      DataChannel File out: write node with order = " + ri.getNode().getBuffer().order());
//System.out.println("      DataChannel File out: write node");
                    // Last boolean arg means do (not) duplicate node's buffer when writing.
                    // Setting this to false led to problems since the input channel is using
                    // the buffer at the same time.
                    written = evioFileWriter.writeEventToFile(ri.getNode(), forceToDisk, true, ownRecord);
                }
//System.out.println("      DataChannel File out: written = " + written);

                while (!written) {

                    if (!sentMsgToRC && repeatLoops++ > 1) {
System.out.println("      DataChannel File out: disc is full, waiting ...");
                        sentMsgToRC = true;
                    }


//System.out.println("      DataChannel File out: sleep 1 sec, try write again .....");
                        // Wait 1 sec
                        Thread.sleep(1000);

                        // Try writing again
                        if (ri.getBuffer() != null) {
                            written = evioFileWriter.writeEventToFile(null, ri.getBuffer(), forceToDisk, ownRecord);
                        }
                        else {
                            written = evioFileWriter.writeEventToFile(ri.getNode(), forceToDisk, true, ownRecord);
                        }
                }

                // If msg was sent to RC saying disk if full AND we're here, then space got freed up
                if (sentMsgToRC) {
System.out.println("      DataChannel File out: disc space is now available");
                }


            ri.releaseByteBuffer();
        }


        /** {@inheritDoc} */
        public void run() {

            // Tell the world I've started
            latch.countDown();

            try {
                RingItem ringItem;
                EventType pBankType;
                ControlType pBankControlType;

                // The non-END control events are placed on ring 0 of all output channels.
                // The END event is placed in the ring in which the next data event would
                // have gone. Initial user events (before physics) are placed on ring 0 of
                // only the first output channel.


                // Start here, we only have 1 output channel and therefore 1 output ring
                ringIndex = 0;

                while ( true ) {

                    try {
//System.out.println("      DataChannel File out " + outputIndex + ": try getting next buffer from ring");
                        ringItem = getNextOutputRingItem(ringIndex);
//System.out.println("      DataChannel File out " + outputIndex + ": got next buffer");
//Utilities.printBuffer(ringItem.getBuffer(), 0, 6, name+": ev" + nextEvent + ", ring " + ringIndex);
                    }
                    catch (InterruptedException e) {
                        return;
                    }

                    pBankType = ringItem.getEventType();
                    pBankControlType = ringItem.getControlType();

                    try {

                        if (pBankType == EventType.CONTROL) {
                            // if END event ...
                            if (pBankControlType == ControlType.END) {
                                System.out.println("      DataChannel File out: write END event");
                                try {
                                    writeEvioData(ringItem, true, true);
                                    releaseCurrentAndGoToNextOutputRingItem(ringIndex);
                                    evioFileWriter.close();
                                }
                                catch (Exception e) {
                                    errorMsg.compareAndSet(null, "Cannot write to file");
                                    throw e;
                                }
                                return;
                            }
                            // for prestart or go
                            else if (pBankControlType == ControlType.PRESTART) {
                                System.out.println("      DataChannel File out: write PRESTART event");
                                writeEvioData(ringItem, true, true);
                            }
                            // for prestart or go
                            else if (pBankControlType == ControlType.GO) {
                                // Do NOT force GO to hard disk as it will slow things down
                                System.out.println("      DataChannel File out: write GO event");
                                writeEvioData(ringItem, false, true);
                            }
                            else {
                                throw new EmuException("unexpected control event, " + pBankControlType);
                            }
                        }
                        // If user event ...
                        else if (pBankType == EventType.USER) {
//System.out.println("      DataChannel File out " + outputIndex + ": found user event");
                            if (ringItem.isFirstEvent()) {
                                try {
//System.out.println("      DataChannel File out: try writing first event");
                                    // Buffer always gets first priority
                                    if (ringItem.getBuffer() != null) {
                                        evioFileWriter.setFirstEvent(ringItem.getBuffer());
                                    }
                                    else {
                                        evioFileWriter.setFirstEvent(ringItem.getNode());
                                    }
//System.out.println("      DataChannel File out: wrote first event");
                                }
                                catch (EvioException e) {
                                    // Probably here due to bad evio format
                                    System.out.println("      DataChannel File out " + outputIndex + ": failed writing \"first\" user event -> " + e.getMessage());
                                }
                                // The writer will handle the first event from here
                                ringItem.releaseByteBuffer();
                            }
                            else {
                                try {
//System.out.println("      DataChannel File out: try writing user event");
                                    writeEvioData(ringItem, false, true);
//System.out.println("      DataChannel File out: wrote user event");
                                }
                                catch (EvioException e) {
                                    // Probably here due to bad evio format
                                    System.out.println("      DataChannel File out: failed writing user event -> " + e.getMessage());
                                }
                            }
                        }
                        // If not control/user event, don't force to disk
                        else {
//System.out.println("      DataChannel File out: write!");
                            writeEvioData(ringItem, false, false);
                        }
                    }
                    catch (Exception e) {
                        errorMsg.compareAndSet(null, "Cannot write to file");
                        throw e;
                    }

//System.out.println("      DataChannel File out: release ring item");
                    releaseCurrentAndGoToNextOutputRingItem(ringIndex);

                    // Do not go to the next ring if we got a user event.
                    // Just keep reading until we get to a built event.
                    // Then start keeping count so we know when to switch to the next ring.
                    //
                    // Prestart & go events go to the first ring.
                    // End event will stop this thread so don't worry about not switching rings.
                    if (outputRingCount > 1 && !pBankType.isUser() && !pBankType.isControl()) {
                        setNextEventAndRing();
//System.out.println("      DataChannel File out, " + name + ": for next ev " + nextEvent + " SWITCH TO ring = " + ringIndex);
                    }
                }

            } catch (InterruptedException e) {
System.out.println("      DataChannel File out, " + outputIndex + ": interrupted thd, exiting");
            } catch (Exception e) {
                e.printStackTrace();
System.out.println("      DataChannel File out, " + outputIndex + " : exit thd: " + e.getMessage());
            }

        }

    }  /* DataOutputHelper internal class */

}