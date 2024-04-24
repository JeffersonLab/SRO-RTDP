package org.jlab.epsci.rtdp;

import org.jlab.coda.jevio.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


public class EvioSplitRoc {

    private String fileName;
    private HashSet<Integer> rocIdSet;

    // loop over all events in file, copy data, swap, then write
    
    EvioSplitRoc(String file) {
        fileName = file;
        rocIdSet = new HashSet<>();
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

                // Each built event has:
                //  1) bank of banks (event) which contains:
                //     A) built trigger bank (tag = # of Rocs)
                //     B) multiple data banks
                //
                // Each built trigger bank (bank of segments) contains:
                //  1) segment of common data (skip over)
                //  2) one segment for each ROC (tag = Roc id)

                int banksContained = ev.getChildCount();

                // Filter out control and other small events
                if ((ev.getTotalBytes() < 1000) || (banksContained < 2)) {
                    System.out.println("Skipping over event with " + ev.getTotalBytes() +
                                       " bytes & " + banksContained + " banks");
                    continue;
                }

                EvioBank triggerBank = (EvioBank) ev.getChildAt(0);
                int numSegments = triggerBank.getChildCount();

                DataType type = triggerBank.getHeader().getDataType();
                int tag = triggerBank.getHeader().getTag();

                // Filter out non-physics events
                if ((type != DataType.SEGMENT) || (tag > 0xFF27 || tag < 0xFF20)) {
                    System.out.println("Skipping over event with wrong format/tag");
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

    public static void main(String args[]) {
        if (args.length < 1) {
            System.out.println("Need to specify a \"filename\" arg");
            return;
        }

        EvioSplitRoc splitter = new EvioSplitRoc(args[0]);
        splitter.getRocNums();
    }


}
