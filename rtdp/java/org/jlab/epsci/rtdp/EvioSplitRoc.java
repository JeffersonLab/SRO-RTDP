package org.jlab.epsci.rtdp;

import org.jlab.coda.jevio.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


public class EvioSplitRoc {

    private String fileName;
    private HashSet<Integer> rocIdSet;
    private HashMap<Integer, EventWriterUnsyncV4> writers;

    // loop over all events in file, copy data, swap, then write
    
    EvioSplitRoc(String file) {
        fileName = file;
        rocIdSet = new HashSet<>();
        writers = new HashMap<Integer, EventWriterUnsyncV4>();

    }


    public static void printRocIds(Set<Integer> rocIds) {
        if (rocIds.size() == 0) {
            System.out.println("printRocIds: set is empty");
        }

        System.out.print("Rocs:");
        for (int i : rocIds) {
            System.out.print(" " + i);
        }
        System.out.println();
    }
    
    
    public void getRocNums() {

        // Get the complete list of ROC ids from the file
        // by reading over all events once.
        try {

            // Open input file
            EvioReader reader = new EvioReader(fileName);

            System.out.println("Scanning " + fileName + " for Roc ids");
            
            EvioEvent ev;
            int unChangedCount = 0;

            while ((ev = reader.parseNextEvent()) != null) {
                //System.out.println("For event " + eventCount++);

                // Each built event is:
                //  1) bank of banks (num = # of events) which contains:
                //     A) 1 built trigger bank
                //     B) multiple data banks (1 for each Roc)
                //
                // Each built trigger bank (of segments with num = # of Rocs) contains:
                //  1) 2 segments of common data (skip over)
                //  2) one segment for each ROC (tag = Roc id)

                int banksContained = ev.getChildCount();
                DataType evType = ev.getHeader().getDataType();

                // Filter out control and other small events
                if ((ev.getTotalBytes() < 1000) || (banksContained < 2) || (!evType.isBank())) {
                    System.out.println("Skipping over event containing " + evType.toString() +
                                       " with " + ev.getTotalBytes() +
                                       " bytes & " + banksContained + " contained structures");
                    continue;
                }

                EvioBank triggerBank = (EvioBank) ev.getChildAt(0);
                int numSegments = triggerBank.getChildCount();

                DataType type = triggerBank.getHeader().getDataType();
                int tag = triggerBank.getHeader().getTag();

                CODATag codaTag = CODATag.getTagType(tag);
                if ((codaTag == null) || (!codaTag.isBuiltTrigger())) {
                    System.out.println("Skipping over event with wrong tag, " + tag);
                    continue;
                }

                // Filter out non-physics events
                if (type != DataType.SEGMENT) {
                    System.out.println("Skipping over event with wrong format");
                    continue;
                }

                HashSet<Integer> rocIds = new HashSet<>();

                // Skip over 2 common data segments
                // Look for tag in each Roc segment in trigger bank
                for (int j=2; j < numSegments; j++) {
                    EvioSegment rocSeg = (EvioSegment) triggerBank.getChildAt(j);
                    int rocId = rocSeg.getHeader().getTag();
                    // Some events seem to have large tag values in
                    // events not containing valid ROC data. Ignore these.
                    if (rocId > 1000) continue;
                    rocIds.add(rocId);
                }


                boolean changed = rocIdSet.addAll(rocIds);
                if (changed) {
                    unChangedCount = 0;
                }
                else {
                    unChangedCount++;
                }

                // If we have already found some rocIds but have not seen any
                // new ones for the last 1000 events then assume we've found them all
                if (unChangedCount >= 1000) {
                    break;
                }
            }

            reader.close();

            printRocIds(rocIdSet);
        }
        catch (IOException e) {
            // can't read file
            e.printStackTrace();
        }
        catch (EvioException e) {
            e.printStackTrace();
        }
    }


    //------------------
    // ScanFile
    //------------------
    private void splitFile() {

        try {
            // Open input file
            EvioReader reader = new EvioReader(fileName);

            System.out.println("Scanning " + fileName + " for Roc ids");

            // Output directory
            String dname = "roc_files";

            // Open one output file per Roc
            for (int rocId : rocIdSet) {
                String subFileName = dname + "/roc" + rocId + ".evio";
                System.out.println("Opening " + subFileName + " for Roc ids");
                EventWriterUnsyncV4 writer = new EventWriterUnsyncV4(subFileName);
                // Store each writer for future recall
                writers.put(rocId, writer);
            }

            int trackEvents = 0;

            // Loop over all buffers (evio "events" or top-level banks) in file
            EvioEvent ev;

            while ((ev = reader.parseNextEvent()) != null) {

                if ( (++trackEvents) % 1000 ==0) {
                    System.out.println("At event " + trackEvents);
                }
                
                // Each built event (ev) is:
                //  1) bank of banks (num = # of events) which contains:
                //     A) 1 built trigger bank
                //     B) multiple data banks (1 for each Roc)
                //
                // Each built trigger bank (of segments with num = # of Rocs) contains:
                //  1) 2 segments of common data
                //  2) one segment for each ROC (tag = Roc id)

                int banksContained = ev.getChildCount();
                DataType evType = ev.getHeader().getDataType();
                int eventCount = ev.getHeader().getNumber();

                // Filter out control and other small events
                if ((ev.getTotalBytes() < 1000) || (banksContained < 2) || (!evType.isBank())) {
                    System.out.println("Skipping over event containing " + evType.toString() +
                                       " with " + ev.getTotalBytes() +
                                       " bytes & " + banksContained + " contained structures");
                    continue;
                }

                EvioBank triggerBank = (EvioBank) ev.getChildAt(0);
                int trigTag  = triggerBank.getHeader().getTag();
                int rocCount = triggerBank.getHeader().getNumber();
                int segCount = triggerBank.getChildCount();
                if (rocCount != segCount - 2) {
                    System.out.println("Roc count = " + rocCount +
                                       " does NOT match trigger bank's roc seg count =  " +
                                       (segCount - 2));
                }
                System.out.println("Trigger Bank tag = 0x" + Integer.toHexString(trigTag));

                DataType type = triggerBank.getHeader().getDataType();
                // Filter out non-physics events
                if (type != DataType.SEGMENT) {
                    System.out.println("Skipping over event with wrong format");
                    continue;
                }

                
                // Get useful CODA info out of this tag
                CODATag codaTag = CODATag.getTagType(trigTag);
                if ((codaTag == null) || (!codaTag.isBuiltTrigger())) {
                    System.out.println("Skipping over event with wrong tag, " + triggerBank.getHeader().getTag());
                    continue;
                }

                // Timestamps present?
                boolean hasTimestamps = codaTag.hasTimestamp();
                System.out.println("hasTimestamps = " + hasTimestamps);

                // Roc specific data present in Roc data segments of trigger bank?
                // Need this to extract Roc-specific timestamps from trigger bank.
                boolean hasRocSpecificData = codaTag.hasRunData();
                System.out.println("hasRocSpecificData = " + hasRocSpecificData);

                // TODO: pick apart the ROC-segs to pull out timestamps correctly

                // Find out how much there is. To do this look at first roc-specific seg of trig bank
                int intsToSkip = 0;
                if (hasTimestamps && hasRocSpecificData) {
                    EvioSegment firstRocSeg = (EvioSegment)triggerBank.getChildAt(22);
                    int dataIntsPerRocPerEvent = firstRocSeg.getIntData().length / eventCount;
                    System.out.println("data ints in seg = " + firstRocSeg.getIntData().length);
                    System.out.println("dataIntsPerRocPerEvent = " + dataIntsPerRocPerEvent);
                    if (dataIntsPerRocPerEvent < 2) {
                        // There must be at least 2 ints to represent 1 timestamp.
                        // And furthermore, if there is additional roc-specific-data, then it must be > 2.
                        // However, if == 2, we'll just assume timestamp is all that is present.
                        System.out.println("Have timestamps and roc-specific data, but ints/roc = " + dataIntsPerRocPerEvent);
                        return;
                    }
                    intsToSkip = dataIntsPerRocPerEvent - 2;
                }
                System.out.println("Ints to skip = " + intsToSkip);


                // The trigger bank directly from a ROC contains segments, 1 for each event.
                // Each segment contains 32 bit ints:
                //  1) event number
                //  2) lower 32 bits of timestamp
                //  3) upper 32 bits of timestamp (only lower half valid)
                //  4) miscellaneous data (roc specific data)
                //
                // The timestamp and misc. may not be present.
                // What is present in the trigger bank will be indicated by the tag.


                // The built trigger bank contains segments,
                // 2 common and 1 for each Roc.
                // The first common contains 64-bit ints:
                //      1) starting event number
                //      2 - M+1) avg timestamps for events 1-M
                //      M+2) run number (high 32) and run type (low 32)    (here if configured in DAQ)
                //
                // The second common contains shorts holding event-type (1 for each physics event)
                //
                // Each roc segment contains 32 bit ints:
                //  1)  timestamp event 1
                //  2)  misc event 1 (roc specific data)
                //  3)  timestamp event 2
                //  4)  misc event 2 (roc specific data)        ETC.
                //

                // So, to reconstruct a ROC's trigger bank from a built trigger bank.
                //      1) insert the event number
                //      2) get the Roc-specific timestamp if present


                // First common data segment has the first event # which we need
                EvioSegment commonSeg = (EvioSegment) triggerBank.getChildAt(0);
                // Extract the first event #
                int startingEventNum = (int) (commonSeg.getLongData()[0]);

                // Go Roc by Roc, within each Roc go event by event.
                // This should be the most efficient way of writing.

                // For each Roc
                for (int i = 0; i < rocCount; i++) {
 
                    // For Roc (Raw) trigger bank
                    int rocTrigTag = CODATag.RAW_TRIGGER.getValue();
                    if (hasTimestamps) {
                        rocTrigTag = CODATag.RAW_TRIGGER_TS.getValue();
                    }

                    //--------------------------------------------------
                    // First, get the physics data bank for this Roc
                    // (+1 to skip over built trigger bank)
                    EvioBank rocDataBank = (EvioBank) ev.getChildAt(i + 1);

                    // The tag and num and type of this bank will be the same as the top level
                    // of the event we are generating so copy them here
                    int tag = rocDataBank.getHeader().getTag();
                    int num = rocDataBank.getHeader().getNumber();
                    // Lower 12 bits of tag contain roc id, top 4 bits contain status
                    int rocId = tag & 0xfff;

                    //--------------------------------------------------
                    // Second, the 2nd common bank of the trigger bank contains
                    // event-types for each of the events which we'll
                    // need in reconstructing a Roc's trigger bank.
                    EvioSegment trigTypeSeg = (EvioSegment) triggerBank.getChildAt(1);
                    short[] trigTypeData = trigTypeSeg.getShortData();

                    //--------------------------------------------------
                    // Third, get timestamps if necessary
                    int[] rocTsData = null;

                    if (hasTimestamps) {
                        // Roc segment in trigger bank (needed if using timestamps)
                        EvioSegment rocSeg = (EvioSegment) triggerBank.getChildAt(2 + i);

                        // Sanity check for roc ids
                        if (rocId != rocSeg.getHeader().getTag()) {
                            System.out.println("Problem with rocId from data bank " + rocId +
                                                       " different than in trigger bank " +
                                                       rocSeg.getHeader().getTag());
                        }

                        // This check was in Elliott's code:
                        // Some events seem to have large tag values in
                        // events not containing valid ROC data. Ignore these.
                        if (rocId > 1000) {
                            System.out.println("Bad roc id " + rocId + " will mess things up");
                            continue;
                        }

                        // This contains the timestamps of all physics events for this Roc
                        rocTsData = rocSeg.getIntData();
                        // Another sanity check
                        if (rocTsData.length != (2 * eventCount)) {
                            System.out.println("ROC #" + i + " ts seg length = " + rocTsData.length + " NOT = 2*eventCount = " + (2 * eventCount));
                            //return;
                        }
                    }


                    // Construct a new event for this Roc.
                    // Create a Roc (raw not built) trigger bank for it,
                    // then add the data bank from each event for this particular ROC.
                    // Then write newly constructed event out to a file for this ROC.
                    //
                    // Remember: the 2nd header word of a ROC's data bank (rocDataBank) is
                    // identical to the 2nd header word of the new event we're constructing.
                    EventBuilder builder = new EventBuilder(tag, DataType.BANK, num);
                    EvioEvent rocOutputEv = builder.getEvent();
                    // New trigger bank we're constructing.
                    EvioBank rocOutputTrig = new EvioBank(rocTrigTag, DataType.SEGMENT, 1);


                    // Get file writer for this Roc
                    EventWriterUnsyncV4 writer = writers.get(rocId);


                    // For each physics event ...
                    for (int j = 0; j < eventCount; j++) {
                        // Number of the physics event (each roc has one of each physics event).
                        // Not talking about the top level CODA "event" containing everything which
                        // is really just a buffer and has no number associated with it.
                        int evNum = startingEventNum + j;

                        int[] rocTrigData;

                        if (hasTimestamps) {
                            // Since there are timestamps, we need to pull the ones specific
                            // to a particular Roc from that Roc's segment in the built trigger bank

                            // Create Roc (Raw) trigger bank, copy over some data
                            rocTrigData = new int[3];
                            // Roc trigger data, first word
                            rocTrigData[0] = evNum;
                            rocTrigData[1] = rocTsData[2*j];      // ts low 32 bits
                            rocTrigData[2] = rocTsData[2*j + 1];  // ts high 32 bits
                        }
                        else {
                            rocTrigData = new int[1];
                            rocTrigData[0] = evNum;
                        }

                        // Add a new segment to Roc's trigger bank for each event
                        EvioSegment evSeg = new EvioSegment(trigTypeData[j], DataType.INT32);
                        evSeg.setIntData(rocTrigData);
                        builder.addChild(rocOutputTrig, evSeg);

                    }  // next physics event

                    // Add trig bank to output event
                    builder.addChild(rocOutputEv, rocOutputTrig);

                    // All data-block-banks contained in rocDataBank must be added to our output event
                    // after the constructed trig bank. There "should" only be one, but if
                    // multiple DMAs were done, there could be more.
                    for (int k=0; k < rocDataBank.getChildCount(); k++) {
                        EvioBank dataBlockBank = (EvioBank) rocDataBank.getChildAt(k);
                        builder.addChild(rocOutputEv, dataBlockBank);
                    }

                    // Now write out this reformulated event (as coming directly from Roc)
                    // to the split-out file for this Roc.
                    writer.writeEvent(rocOutputEv);

                }   // next Roc

            }  // next top-level "event"

            // Close everything
            for (EventWriterUnsyncV4 writer : writers.values()) {
                writer.close();
            }

        }
        catch (IOException e) {
            // can't read file
            e.printStackTrace();
        }
        catch (EvioException e) {
            e.printStackTrace();
        }

    }






    public static void main(String args[]) {
        if (args.length < 1) {
            System.out.println("Need to specify a \"filename\" arg");
            return;
        }

        EvioSplitRoc splitter = new EvioSplitRoc(args[0]);
        splitter.getRocNums();
        splitter.splitFile();

    }


}
