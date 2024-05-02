/*
 * Copyright (c) 2009, Jefferson Science Associates
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

import org.jlab.coda.emu.EmuException;
import org.jlab.coda.emu.support.data.*;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.exception.*;
import org.jlab.coda.hipo.RecordHeader;
import org.jlab.coda.jevio.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;


/**
 * This class implement a data channel which gets data from
 * or sends data to an ET system.
 *
 * @author timmer
 * (Dec 2, 2009)
 */
public class DataChannelImplEt extends DataChannelAdapter {


    /** Read END event from input ring. */
    private volatile boolean haveInputEndEvent;

    /** Got END or RESET command from Run Control and must stop thread getting events. */
    private volatile boolean stopGetterThread;

    // OUTPUT ONLY

    /** Thread used to output data. */
    private DataOutputHelper dataOutputThread;

    /** Is the EMU using this ET output channel as an event builder? */
    private final boolean isEB;

    /** Is the EMU using this ET output channel as the last level event builder? */
    private final boolean isFinalEB;


    //-------------------------------------------
    // ET Stuff
    //-------------------------------------------

    /** Number of events to ask for in an array. */
    private final int chunk;

    /** If true, there will be a deadlock at prestart since putEvents is blocked
     * due to newEvents not returning in sleep mode due to too few events. */
    private boolean deadLockAtPrestart;

    /** Control words of each ET event written to output. */
    private final int[] control;

    /** ET system connected to. */
    private EtSystem etSystem;

    /** ET station attached to. */
    private final EtStation station;

    /** Name of ET station attached to. */
    private final String stationName;

    /** Attachment to ET station. */
    private final EtAttachment attachment;



    /**
     * Constructor to create a new DataChannelImplEt instance.
     * Output channel only!
     *
     * @param name          the name of this channel.
     * @param etName        ET system file.
     * @param debug         if true, printout debug statements.
     *
     * @throws DataTransportException if unable to open ET system.
     */
    DataChannelImplEt(String name, String etName, boolean debug)
        throws DataTransportException {

        super(name, false, debug, 0);

        System.out.println("DataChannel Et: creating output channel to " + name);


        try {
            // Open given ET system, only if on local host
            EtSystemOpenConfig openConfig = new EtSystemOpenConfig(etName, EtConstants.hostLocal);

            // Set TCP send buffer in bytes
            openConfig.setTcpSendBufSize(25000000);
            // Set TCP no delay on
            openConfig.setNoDelay(true);
            // Direct connect (open an ET system by specifying host & port,
            // do not broadcast or multicast to find ET system)
            openConfig.setNetworkContactMethod(EtConstants.direct);

            // debug is set to error in constructor
            // create ET system object with verbose debugging output
            etSystem = new EtSystem(openConfig);
            if (debug) {
                etSystem.setDebug(EtConstants.debugInfo);
            }
            etSystem.open();

            // How may buffers do we grab at a time?
            chunk = 4;
            System.out.println("      DataChannel Et: chunk = " + chunk);

            // Set station name. Use "GRAND_CENTRAL" for output.
            stationName = "GRAND_CENTRAL";

            // get GRAND_CENTRAL station object
            station = etSystem.stationNameToObject(stationName);

            // attach to GRAND_CENTRAL
            attachment = etSystem.attach(station);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new DataTransportException(e);
        }

        // If this is an event building EMU, set the control array
        // for each outgoing ET buffer.
        isEB = true;
        // The control array needs to be the right size.
        control = new int[EtConstants.stationSelectInts];
        // The first control word is this EB's coda id
        control[0] = id;
        isFinalEB = true;// ??

    }


    /**
     * Get the size of the ET system's events in bytes.
     * @return size of the ET system's events in bytes.
     */
    private long getEtEventSize() {
        return etSystem.getEventSize();
    }


    /**
     * Get the number of the ET system's events.
     * @return number of the ET system's events.
     */
    private int getEtEventCount() {
        return etSystem.getNumEvents();
    }


    private void closeEtSystem() {
        try {
            etSystem.detach(attachment);
        }
        catch (Exception e) {
            // Might be detached already or cannot communicate with ET
        }

        try {
            if (!stationName.equals("GRAND_CENTRAL")) {
                etSystem.removeStation(station);
            }
        }
        catch (Exception e) {
            // Station may not exist, may still have attachments, or
            // cannot communicate with ET
        }

        etSystem.close();
        etSystem = null;
    }


    /** {@inheritDoc} */
    public void prestart() {
        // Find out how many total events in ET system.
        // Useful for avoiding bad situation in output channel in which
        // putEvents() blocks due to newEvents() stuck in sleep mode.
        if (getEtEventCount() < 4*chunk) {
            deadLockAtPrestart = true;
            System.out.println("      DataChannel Et: newEvents() using timed mode to avoid deadlock");
        }

        // Start up threads for I/O
        startHelper();
    }


    /**
     * Interrupt all threads.
     */
    private void interruptThreads() {
        // Don't close ET system until helper threads are done
        if (dataOutputThread != null) {
            dataOutputThread.shutdown();
        }
    }


    /**
     * Try joining all threads, up to 1 sec each.
     */
    private void joinThreads() {
        // Don't close ET system until helper threads are done
        if (dataOutputThread != null) {
            boolean ended = dataOutputThread.waitForThreadsToEnd(1000);
System.out.println("          DataChannel Et joinThreads: " + name + " channel, past joining output threads, ended = " + ended);
        }
    }

    
    /** {@inheritDoc}. Formerly this code was the close() method. */
    public void end() {
System.out.println("      DataChannel Et: " + name + " - end threads & close ET system");
        gotEndCmd = true;
        stopGetterThread = true;

        // Do NOT interrupt threads which are communicating with the ET server.
        // This will mess up future communications !!!

        interruptThreads();
        joinThreads();

        // At this point all threads should be done
        closeEtSystem();

System.out.println("      DataChannel Et: end() done");
    }


    /**
     * For output channel, start the DataOutputHelper thread which takes a bank from
     * the ring, puts it into a new ET event and puts that into the ET system.
     */
    private void startHelper() {
            dataOutputThread = new DataOutputHelper(name() + "et_out");
            dataOutputThread.start();
            dataOutputThread.waitUntilStarted();
    }


    /**
      * Class used by the this input channel's internal RingBuffer
      * to populate itself with etContainer objects.
      */
     private final class ContainerFactory implements EventFactory<EtContainer> {
         public EtContainer newInstance() {
             try {
                 // This object holds an EtEvent array & more
                 return new EtContainer(chunk, (int)getEtEventSize());
             }
             catch (EtException e) {/* never happen */}
             return null;
         }
     }


    /**
     * The DataChannelImplEt class has 1 ring buffer (RB) to accept output from each
     * event-processing thread of a module.
     * It takes Evio banks from these, writes them into ET events and puts them into an
     * ET system. It uses its another RB internally. Sequentially, this is what happens:
     *
     * The getter thread gets new ET buffers and puts each into a holder of the internal RB.
     *
     * The main, DataOutputHelper, thread gets a single holder from the internal RB.
     * It also takes evio events from the other RBs which contain module output. Each
     * evio event is immediately written into the ET buffer. Once the ET buffer is full or
     * some other limiting condition is met, it places the holder back into the internal RB.
     *
     * Finally, the putter thread takes holder and their data-filled ET buffers and puts
     * them back into the ET system.
     */
    private class DataOutputHelper extends Thread {

        /** Thread for getting new ET events. */
        private final EvGetter getter;

        /** Thread for putting filled ET events back into ET system. */
        private final EvPutter putter;

        /** Let a single waiter know that the main threads have been started. */
        private final CountDownLatch startLatch = new CountDownLatch(2);

 
        /** Place to store a bank off the ring for the next event out. */
        private RingItem unusedRingItem;
        
        /** Internal ring buffer. */
        private final RingBuffer<EtContainer> rb;

        /** Used by first consumer (DataOutputHelper) to get ring buffer items. */
        private final Sequence etFillSequence;

        /** Used by first consumer (main thread) to get ring buffer items. */
        private final SequenceBarrier etFillBarrier;

        /** Used by second consumer (putter) to get ring buffer items. */
        private final SequenceBarrier etPutBarrier;

        /** Maximum allowed number of evio ring items per ET event. */
        private static final int maxEvioItemsPerEtBuf = 10000;



        /** Constructor. */
        DataOutputHelper(String name) {
            super(name);

            // Ring will have 4 slots. Each is an EtContainer object containing
            // "chunk" number of ET events.
            int ringSize = 4;

            // Create ring buffer used by 2 threads -
            //   1 to get new events from ET system and place into ring (producer of ring items)
            //   1 to get evio events, parse them into these ET events and
            //        put them back into ET system                  (consumer of ring items)
            rb = createSingleProducer(new ContainerFactory(), ringSize,
                                      new SpinCountBackoffWaitStrategy(10000, new LiteBlockingWaitStrategy()));
                                      //new YieldingWaitStrategy());

            // 1st consumer barrier of ring buffer, which gets evio
            // and writes it, depends on producer.
            etFillBarrier = rb.newBarrier();
            // 1st consumer sequence of ring buffer
            etFillSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

            // 2nd consumer to take filled ET buffers and put back into ET system,
            // depends on filling consumer.
            etPutBarrier = rb.newBarrier(etFillSequence);
            Sequence etPutSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
            // Last sequence before producer can grab ring item
            rb.addGatingSequences(etPutSequence);

            // Start consumer thread to put ET events back into ET system
            putter = new EvPutter(name+"_EvPutter", etPutSequence, etPutBarrier);
            putter.start();

            // Start producer thread for getting new ET events
            getter = new EvGetter(name+"_EvGetter");
            getter.start();
        }


        /** A single waiter can call this method which returns when thread was started. */
        private void waitUntilStarted() {
            try {
                startLatch.await();
            }
            catch (InterruptedException e) {}
        }


        /**
         * Wait for all this object's threads to end, for the given time.
         * @param milliseconds
         * @return true if all threads ended, else false
         */
        private boolean waitForThreadsToEnd(int milliseconds) {
            int oneThreadWaitTime = milliseconds/(3);
            if (oneThreadWaitTime < 0) {
                oneThreadWaitTime = 0;
            }

            try {getter.join(oneThreadWaitTime);}
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (getter.isAlive()) {
                System.out.println("            waitForThreadsToEnd: " + name + " channel, getter is still alive!!");
            }

            try {putter.join(oneThreadWaitTime);}
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (putter.isAlive()) {
                System.out.println("            waitForThreadsToEnd: " + name + " channel, putter is still alive!!");
            }

            try {this.join(oneThreadWaitTime);}
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (this.isAlive()) {
                System.out.println("            waitForThreadsToEnd: " + name + " channel, main thd is still alive!!");
            }

            return !(this.isAlive() || putter.isAlive() || getter.isAlive());
        }


        /** Stop all this object's threads from an external thread. */
        private void shutdown() {
            if (etSystem == null || attachment == null) return;

            // If any EvGetter thread is stuck on etSystem.newEvents(), unstuck it
            try {
                // Wake up getter thread
System.out.println("          DataChannel Et shutdown: " + name + " channel, try to wake up attachment");
                etSystem.wakeUpAttachment(attachment);
                Thread.sleep(100);
                getter.interrupt();
                this.interrupt();
                Thread.sleep(400);
System.out.println("          DataChannel Et shutdown: " + name + " channel, raise alert for put barrier");
                etPutBarrier.alert();
System.out.println("          DataChannel Et shutdown: " + name + " channel, woke up attachments and interrupted threads");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }


        /**
         * Main thread of ET output channel. This thread gets a "new" ET event,
         * gathers the appropriate number of evio events from a module's output
         * and writes them into that single ET buffer.
         */
        @Override
        public void run() {
            
            // Tell the world I've started
            startLatch.countDown();

            try {
                RingItem ringItem;

                EtEvent event;
                EventType pBankType = null;
                ControlType pBankControlType = null;

                // Time in milliseconds for writing if time expired
                long startTime;
                final long TIMEOUT = 2000L;

                // Always start out reading prestart & go events from ring 0
                int outputRingIndex = 0;

                // Create writer with some args that get overwritten later.
                // Make the block size bigger than the Roc's 4.2MB ET buffer
                // size so no additional block headers must be written.
                // It should contain less than 100 ROC Raw records,
                // but we'll allow 10000 such banks per block header.
                ByteBuffer etBuffer = ByteBuffer.allocate(128);
                etBuffer.order(byteOrder);

                // If wanting evio V6 output, use this writer
//                EventWriterUnsync writer = new EventWriterUnsync(etBuffer, 4*1100000, maxEvioItemsPerEtBuf,
//                        null, 1, CompressionType.RECORD_UNCOMPRESSED);
//                writer.setSourceId(emu.getCodaid());

                // Use writer that will produce evio V4 output
                EventWriterUnsyncV4 writer = new EventWriterUnsyncV4(etBuffer, 4*1100000, maxEvioItemsPerEtBuf,
                        null, null);

                writer.close();

                int bytesToEtBuf, ringItemSize=0, banksInEtBuf, myRecordId;
                int etSize = (int) etSystem.getEventSize();
                boolean etEventInitialized, isUserOrControl=false;
                boolean isUser, isControl=false;
                boolean gotPrestart=false;

                // Variables for consuming ring buffer items
                long etNextFillSequence = etFillSequence.get() + 1L;
                long etAvailableFillSequence = -1L;

                EtContainer etContainer;
                EtEvent[] events;
                int validEvents;
                int itemCount;
                BitSet bitInfo = new BitSet(24);
                recordId = 1;

                top:
                while (true) {

                    // Init variables
                    event = null;

                    // Get events
                    try {
                        //System.out.print("      DataChannel Et out: " + name + " getEvents() ...");

                        // Will block here if no available slots in ring.
                        // It will unblock when more new, unfilled ET events are gotten by the evGetter thread.
                        if (etAvailableFillSequence < etNextFillSequence) {
                            // Wait for next available ring slot
//System.out.println("      DataChannel Et out (" + name + "): filler, wait for container");
                            etAvailableFillSequence = etFillBarrier.waitFor(etNextFillSequence);
                        }
                        etContainer = rb.get(etNextFillSequence);
                        events = etContainer.getEventArray();
                        validEvents = etContainer.getEventCount();
                        // All events have data unless otherwise specified
                        etContainer.setLastIndex(validEvents - 1);
//System.out.println("      DataChannel Et out (" + name + "): filler, got container with " + validEvents +
//                    " events, lastIndex = " + (validEvents - 1) + ", id = " + etContainer.getId());
                    }
                    catch (InterruptedException e) {
                        // Told to wake up because we're ending or resetting
                        if (haveInputEndEvent) {
                            System.out.println("      DataChannel Et out: wake up " + name + ", other thd found END, quit");
                        }
                        return;
                    }
                    catch (Exception e) {
                        throw new EmuException("Error getting events to fill", e);
                    }

                    // For each event ...
                    nextEvent:
                    for (int j=0; j < validEvents; j++) {
//System.out.println("      DataChannel Et out (" + name + "): filler, got ET event " + j);

                        // Set time we started dealing with this ET event
                        startTime = System.currentTimeMillis();

                        // Init variables
                        bytesToEtBuf = 0;
                        banksInEtBuf = 0;
                        etEventInitialized = false;
                        itemCount = 0;

                        // Very first time through, event is null and we skip this
                        if (event != null) {
//System.out.println("      DataChannel Et out (" + name + "): filler, close writer for event " + j);
                            // Finish up the writing for the current ET event
                            writer.close();

                            // Be sure to set length of ET event to bytes of data actually written
                            event.setLength((int) writer.getBytesWrittenToBuffer());
                        }

                        // Get another ET event from the container
                        event = events[j];

                        //------------------------------------------

                        while (true) {
                            //--------------------------------------------------------
                            // Get 1 item off of this channel's input rings which gets
                            // stuff from last module.
                            // (Have 1 ring for each module event-processing thread).
                            //--------------------------------------------------------

                            // If we already got a ring item (evio event) in
                            // the previous loop and have not used it yet, then
                            // don't bother getting another one right now.
                            if (unusedRingItem != null) {
                                ringItem = unusedRingItem;
                                unusedRingItem = null;
                            }
                            else {
                                try {
//System.out.println("      DataChannel Et out (" + name + "): filler, get ring item from ring " + outputRingIndex);
                                    ringItem = getNextOutputRingItem(outputRingIndex);
//System.out.println("      DataChannel Et out (" + name + "): filler, got ring item from ring " + outputRingIndex);

//                                if (isEB) sleep(1);
//System.out.println("     got evt from ring");
//System.out.println(outputIndex + " : " + outputRingIndex + " : " + nextEvent);
                                }
                                catch (InterruptedException e) {
                                    return;
                                }
//System.out.println("done");
                                pBankType = ringItem.getEventType();
                                pBankControlType = ringItem.getControlType();
                                isUser = pBankType.isUser();
                                isControl = pBankType.isControl();
                                isUserOrControl = pBankType.isUserOrControl();
//System.out.println("      DataChannel Et out (" + name + "): filler, isUserOrControl = " + isUserOrControl);

                                // If no prestart yet ...
                                if (!gotPrestart) {
                                    // and we have a control event ...
                                    if (pBankControlType != null) {
                                        // See if it's a prestart
                                        gotPrestart = pBankControlType.isPrestart();
                                        // If not, error
                                        if (!gotPrestart) {
                                            throw new EmuException("Prestart event must be first control event");
                                        }
                                    }
                                    // Else if not a user event, error
                                    else if (!isUser) {
                                        throw new EmuException("Only user or prestart event allowed to be first");
                                    }
                                }

                                // Allow for the possibility of having to write
                                // 2 block headers in addition to this evio event.
                                ringItemSize = ringItem.getTotalBytes() + 64;
                            }

                            //------------------------------------------------
//System.out.println("      DataChannel Et out: " + name + " etSize = " + etSize + " <? bytesToEtBuf(" +
//                           bytesToEtBuf + ") + ringItemSize (" + ringItemSize + ")" +
//", item count = " + itemCount);

                            // If this ring item will not fit into current ET buffer,
                            // either because there is no more room or there's a limit on
                            // the # of evio events in a single ET buffer ...
                            if ((bytesToEtBuf + ringItemSize > etSize) ||
                                    (itemCount >= maxEvioItemsPerEtBuf)) {
                                // If nothing written into ET buf yet ...
                                if (banksInEtBuf < 1) {
                                    // Get rid of this ET buf which is too small
                                    // Don't bother using the new methods for this as it likely never happens
                                    etSystem.dumpEvents(attachment, new EtEvent[]{event});

                                    // Get 1 bigger & better ET buf as a replacement
     System.out.println("\n      DataChannel Et out (" + name + "): filler, using over-sized (temp) ET event, DANGER !!!\n");

                                    EtEvent[] evts = etSystem.newEvents(attachment, Mode.SLEEP,0, 1, ringItemSize);
                                    // Put the new ET buf into container (realEvents array if remote, else jniEvents)
                                    events[j] = event = evts[0];
                                }
                                // If data was previously written into this ET buf ...
                                else {
//System.out.println("      DataChannel Et out (" + name + "): filler, item doesn't fit cause other stuff in there, do write close, get another ET event");
//System.out.println("      DataChannel Et out (" + name + "): filler, banks in ET buf = " + banksInEtBuf + ", isUserOrControl = " + isUserOrControl);
                                    // Get another ET event to put this evio data into
                                    // and hope there is enough room for it.
                                    //
                                    // On the next time through this while loop, do not
                                    // grab another ring item since we already have this
                                    // one we're in the middle of dealing with.
                                    unusedRingItem = ringItem;

                                    // Grab a new ET event and hope it fits in there
                                    continue nextEvent;
                                }
                            }
                            // If this event is a user or control event ...
                            else if (isUserOrControl) {
                                // If data was previously written into this ET buf ...
                                if (banksInEtBuf > 0) {
                                    // We want to put all user & control events into their
                                    // very own ET events. This makes things much easier to
                                    // handle downstream.

                                    // Get another ET event to put this evio data into.
                                    //
                                    // On the next time through this while loop, do not
                                    // grab another ring item since we already have this
                                    // one we're in the middle of dealing with.
                                    unusedRingItem = ringItem;

                                    // Grab a new ET event and use it. Don't mix data types.
                                    continue nextEvent;
                                }

                                // store info on END event
                                if (pBankControlType == ControlType.END) {
                                    etContainer.setHasEndEvent(true);
                                    etContainer.setLastIndex(j);
//System.out.println("      DataChannel Et out (" + name + "): filler found END, last index = " + j);
                                }
                            }


                            //-------------------------------------------------------
                            // Do the following once per holder/ET-event
                            //-------------------------------------------------------
                            if (!etEventInitialized) {
                                // Set control words of ET event
                                //
                                // CODA owns the first ET event control int which contains source id.
                                // If a PEB or SEB, set it to event type.
                                // If a DC,  set this to coda id.
                                if (isFinalEB) {
                                    control[0] = pBankType.getValue();
                                    event.setControl(control);
                                }
                                else if (isEB) {
                                    event.setControl(control);
                                }

                                // Set byte order
                                event.setByteOrder(byteOrder);

                                // Encode event type into bits
                                bitInfo.clear();
                                RecordHeader.setEventType(bitInfo, pBankType.getValue());

                                // Set recordId depending on what type this bank is
                                myRecordId = -1;
                                if (!isUserOrControl) {
                                    myRecordId = recordId;
                                }
                                // If user event which is to be the first event,
                                // mark it in the block header's bit info word.
                                else if (ringItem.isFirstEvent()) {
                                    RecordHeader.setFirstEvent(bitInfo);
                                }
                                recordId++;

                                // Prepare ET event's data buffer
                                etBuffer = event.getDataBuffer();
                                etBuffer.clear();
                                etBuffer.order(byteOrder);

                                // Do init once per ET event
                                etEventInitialized = true;

                                // Initialize the writer which writes evio banks into ET buffer
                                writer.setBuffer(etBuffer, bitInfo, myRecordId);
                            }

                            //----------------------------------
                            // Write evio bank into ET buffer
                            //----------------------------------
                            EvioNode node = ringItem.getNode();
                            ByteBuffer buf = ringItem.getBuffer();

                            if (buf != null) {
                                writer.writeEvent(buf);
                            }
                            else if (node != null) {
                                // The last arg is do we need to duplicate node's backing buffer?
                                // Don't have to do that to keep our own lim/pos because the only
                                // nodes that make it to an output channel are the USER events.
                                // Even though it is almost never the case, if 2 USER events share
                                // the same backing buffer, there still should be no problem.
                                // Even if 2nd event is being scanned by CompactEventReader, while
                                // previous event is being written right here, it should still be OK
                                // since buffer lim or pos are not changed during scanning process.
                                writer.writeEvent(node, false, false);
                            }

                            itemCount++;

                            // Added evio event/buf to this ET event
                            banksInEtBuf++;
                            // This is just a max ESTIMATE for purposes of deciding
                            // when to switch to a new event.
                            bytesToEtBuf += ringItemSize;

                            // If this ring item's data is in a buffer which is part of a
                            // ByteBufferSupply object, release it back to the supply now.
                            // Otherwise call does nothing.
                            ringItem.releaseByteBuffer();

                            // FREE UP this channel's input rings' slots/items --
                            // for the module's event producing threads.
                            releaseCurrentAndGoToNextOutputRingItem(outputRingIndex);
//System.out.println("      DataChannel Et out (" + name + "): filler, released item on ring " +
//                           outputRingIndex  + ", go to next");

                            // Handle END & GO events
                            if (pBankControlType != null) {
                                if (pBankControlType == ControlType.END) {
                                    // Finish up the writing
                                    writer.close();
                                    event.setLength((int) writer.getBytesWrittenToBuffer());

                                    // Tell Getter thread to stop getting new ET events
                                    stopGetterThread = true;

                                    // Pass END and all unused new events after it to Putter thread.
                                    // Cursor is the highest published sequence in the ring.

                                    //etFillSequence.set(rb.getCursor());
                                    etFillSequence.set(etNextFillSequence);

                                    // Do not call shutdown() here since putter
                                    // thread must still do a putEvents().
                                    return;
                                }
                                else if (pBankControlType == ControlType.GO) {
                                    // If the module has multiple build threads, then it's possible
                                    // that the first buildable event (next one in this case)
                                    // will NOT come on ring 0. Make sure we're looking for it
                                    // on the right ring. It was set to the correct value in
                                    // DataChannelAdapter.prestart().
                                    outputRingIndex = ringIndex;
                                }
                            }

//System.out.println("      DataChannel Et out (" + name + "): go to item " + nextSequences[outputRingIndex] +
//" on ring " + outputRingIndex);

                            // Do not go to the next ring if we got a control or user event.
                            // All prestart, go, & users go to the first ring. Just keep reading
                            // from the same ring until we get to a buildable event. Then start
                            // keeping count so we know when to switch to the next ring.
                            if (outputRingCount > 1 && !isUserOrControl) {
                                outputRingIndex = setNextEventAndRing();
//System.out.println("      DataChannel Et out (" + name + "): for next ev " + nextEvent +
//                           " SWITCH TO ring " + outputRingIndex + ", outputRingCount (bt threads) = " +
//                           outputRingCount);
                            }

                            // Implement a timeout for low rates.
                            // We're done with this event and this container.
                            // Send the container to the putter thread and to ET system.

                            // Also switch to new ET event for user & control banks
                            if ((System.currentTimeMillis() - startTime > TIMEOUT) || isUserOrControl) {
                                // We want the PRESTART event to go right through without delay.
                                // So don't wait for all new events to be filled before sending this
                                // container to be put back into the ET system.
                                if (pBankControlType == ControlType.PRESTART) {
//System.out.println("      DataChannel Et out (" + name + "): control ev = " + pBankControlType +
//                           ", go to next container, last index = " + j);
                                    // Finish up the writing for the last ET event
                                    writer.close();
                                    event.setLength((int) writer.getBytesWrittenToBuffer());

                                    // Forget about any other unused events in the container
                                    etContainer.setLastIndex(j);

                                    // Release this container to putter thread
                                    etFillSequence.set(etNextFillSequence++);
                                    continue top;
                                }
//                                if (emu.getTime() - startTime > TIMEOUT) {
//                                    System.out.println("TIME FLUSH ******************");
//                                }
                                continue nextEvent;
                            }
                        }
                    }

//System.out.println("      DataChannel Et out (" + name + "): filler, close writer for last event " + (validEvents - 1));
                    // Finish up the writing for the last ET event
                    writer.close();

                    // Be sure to set length of ET event to bytes of data actually written
                    event.setLength((int) writer.getBytesWrittenToBuffer());

                    //----------------------------------------
                    // Release container for putting thread
                    //----------------------------------------
                    etFillSequence.set(etNextFillSequence++);
                }
            }
            catch (Exception e) {
System.out.println("      DataChannel Et out: exit thd w/ error = " + e.getMessage());
                e.printStackTrace();
            }
        }


        /**
         * This class is a thread designed to put ET events that have been
         * filled with evio data, back into the ET system.
         * It runs simultaneously with the thread that fills these events
         * with evio data and the thread that gets them from the ET system.
         */
        private class EvPutter extends Thread {

            private final Sequence sequence;
            private final SequenceBarrier barrier;


            /**
             * Constructor.
             * @param name      name of thread.
             * @param sequence  ring buffer sequence to use.
             * @param barrier   ring buffer barrier to use.
             */
            EvPutter(String name,
                     Sequence sequence, SequenceBarrier barrier) {

                super(name);
                this.barrier = barrier;
                this.sequence = sequence;
            }


            /** {@inheritDoc} */
            public void run() {

                // Tell the world I've started
                startLatch.countDown();

                EtContainer etContainer;

                int  lastIndex=0, validEvents, eventsToPut, eventsToDump;
                long availableSequence = -1L;
                long nextSequence = sequence.get() + 1L;
                boolean hasEnd;

                try {

                    while (true) {

                        // Do we wait for next ring slot or do we already have something from last time?
                        if (availableSequence < nextSequence) {
                            // Wait for next available ring slot
//System.out.println("      DataChannel Et out (" + name + "): PUTTER try getting seq " + nextSequence);
                            availableSequence = barrier.waitFor(nextSequence);
                        }
                        etContainer = rb.get(nextSequence);

                        // Total # of events obtained by newEvents()
                        validEvents = etContainer.getEventCount();

                        // Index of last event containing data
                        lastIndex = etContainer.getLastIndex();

                        // Look for the END event
                        hasEnd = etContainer.hasEndEvent();

                        if (lastIndex + 1 < validEvents) {
                            eventsToPut = lastIndex + 1;
                            eventsToDump = validEvents - eventsToPut;
                        }
                        else {
                            eventsToPut = validEvents;
                            eventsToDump = 0;
                        }

//System.out.println("      DataChannel Et out (" + name + "): PUTTER got seq " + nextSequence +
//                   ", " + validEvents + " valid, hasEnd = " + hasEnd + ", lastIndex = " + lastIndex +
//                   ", toPut = " + eventsToPut + ", toDump = " + eventsToDump);

                        // Put all events with valid data back in ET system.
                        etContainer.putEvents(attachment, 0, eventsToPut);
                        etSystem.putEvents(etContainer);

                        if (eventsToDump > 0) {
                            // Dump all events with NO valid data. END is last valid event.
                            etContainer.dumpEvents(attachment, lastIndex+1, eventsToDump);
                            etSystem.dumpEvents(etContainer);
//System.out.println("      DataChannel Et out (" + name + "): PUTTER callED dumpEvents()");
                        }

                        sequence.set(nextSequence++);

                        // Checks the last event we're putting to see if it's the END event
                        if (hasEnd) {
System.out.println("      DataChannel Et out (" + name + "): PUTTER got END event, quitting thread");
                            return;
                        }
                    }
                }
                catch (AlertException e) {
                    // Quit thread
System.out.println("      DataChannel Et out: " + name + " putter thd, alerted, quitting");
                }
                catch (InterruptedException e) {
                    // Quit thread
System.out.println("      DataChannel Et out: " + name + " putter thd, interrupted");
                }
                catch (TimeoutException e) {
                    // Never happen in our ring buffer
                }
                catch (IOException e) {
System.out.println("      DataChannel Et out: " + name + " network communication error with Et");
                    e.printStackTrace();
                }
                catch (EtException e) {
System.out.println("      DataChannel Et out: " + name + " internal error handling Et");
                    e.printStackTrace();
                }
                catch (EtDeadException e) {
System.out.println("      DataChannel Et out: " + name + " Et system dead");
                }
                catch (EtClosedException e) {
System.out.println("      DataChannel Et out: " + name + " Et connection closed");
                }
                finally {
                    System.out.println("      DataChannel Et out: PUTTER is Quitting");
                }
            }
        }


        /**
         * This class is a thread designed to get new events from the ET system.
         * It runs simultaneously with the thread that fills these events
         * with evio data and the thread that puts them back.
         */
        final private class EvGetter extends Thread {

            /**
             * Constructor.
             * @param name   name of thread.
             */
            EvGetter(String name) {
                super(name);
            }

            /**
             * {@inheritDoc}<p>
             * Get the new ET events.
             */
            public void run() {

                long sequence;
                boolean gotError;
                String errorString;
                EtContainer etContainer;
                int eventSize = (int)getEtEventSize();

                // Tell the world I've started
                startLatch.countDown();

                try {
                    // If there are too few events to avoid a deadlock while newEvents is
                    // called in sleep mode, use a timed mode ...
                    if (deadLockAtPrestart) {
                        while (true) {
                            if (stopGetterThread) {
                                return;
                            }

                            // Will block here if no available slots in ring.
                            // It will unblock when ET events are put back by the other thread.
                            sequence = rb.nextIntr(1); // This just spins on parkNanos
                            etContainer = rb.get(sequence);

                            // Now that we have a free container, get new events & store them in container.
                            // The reason this is timed and not in sleep mode is that if there are 6 or less
                            // events in the ET system. This thread will block here and not in rb.next();
                            // If we completely block here, then we tie up the mutex which the evPutter
                            // threads needs to use to put events back. Thus we block all event flow.
                            etContainer.newEvents(attachment, Mode.TIMED, 100000, chunk, eventSize);
                            while (true) {
                                try {
//System.out.println("      DataChannel Et out (" + name + "): GETTER try getting new events");
                                    etSystem.newEvents(etContainer);
//System.out.println("      DataChannel Et out (" + name + "): GETTER got new events");
                                    break;
                                }
                                catch (EtTimeoutException e) {
                                    continue;
                                }
                            }

                            // Make container available for parsing/putting thread
                            rb.publish(sequence++);
                        }
                    }
                    else {
                        while (true) {
                            if (stopGetterThread) {
                                return;
                            }

                            sequence = rb.nextIntr(1);
                            etContainer = rb.get(sequence);

                            etContainer.newEvents(attachment, Mode.SLEEP, 0, chunk, eventSize);
                            etSystem.newEvents(etContainer);

                            rb.publish(sequence++);
                        }
                    }
                }
                catch (EtWakeUpException e) {
                    // Told to wake up because we're ending or resetting
                    if (haveInputEndEvent) {
                        System.out.println("      DataChannel Et out: wake up " + name + " getter thd, other thd found END, quit");
                    }
                    return;
                }
                catch (InterruptedException e) {
                    // Told to wake up because we're ending or resetting
                    if (haveInputEndEvent) {
                        System.out.println("      DataChannel Et out: interrupt " + name + " getter thd, other thd found END, quit");
                    }
                    return;
                }
                catch (IOException e) {
                    gotError = true;
                    errorString = "DataChannel Et out: network communication error with Et";
                    e.printStackTrace();
                }
                catch (EtException e) {
                    gotError = true;
                    errorString = "DataChannel Et out: internal error handling Et";
                    e.printStackTrace();
                }
                catch (EtDeadException e) {
                    gotError = true;
                    errorString = "DataChannel Et out: Et system dead";
                }
                catch (EtClosedException e) {
                    gotError = true;
                    errorString = "DataChannel Et out: Et connection closed";
                }
                catch (Exception e) {
                    gotError = true;
                    errorString = "DataChannel Et out: " + e.getMessage();
                }

                // ET system problem - run will come to an end
                if (gotError) {
                    System.out.println("      DataChannel Et out: " + name + ", " + errorString);
                }
System.out.println("      DataChannel Et out: GETTER is Quitting");
            }
        }

    }


}
