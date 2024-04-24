package org.jlab.epsci.rtdp;

import org.jlab.coda.jevio.*;

import java.io.IOException;
import java.util.ArrayList;


public class EvioSplitRoc {

    private String fileName;

    private ArrayList<Integer> rocNumList;

    // loop over all events in file, copy data, swap, then write


    EvioSplitRoc(String file) {
        fileName = file;
    }


    public void printRocIds(ArrayList<Integer> rocIdList) {
        if (rocIdList.size() == 0) {
            System.out.println("printRocIds: list is empty");
        }

        System.out.print("Rocs:");
        for (int i : rocIdList) {
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

            int eventCount = reader.getEventCount();

            for (int i=0; i < eventCount; i++) {

                // Each built event has:
                //  1) bank of banks (event) which contains:
                //     A) built trigger bank (tag = # of Rocs)
                //     B) multiple data banks
                //
                // Each built trigger bank (bank of segments) contains:
                //  1) segment of common data (skip over)
                //  2) one segment for each ROC (tag = Roc id)

                EvioEvent ev = reader.parseNextEvent();
                int banksContained = ev.getChildCount();

                if ((ev.getTotalBytes() < 1000) || (banksContained < 2)) {
                    // filter out control and other small events
                    System.out.println("Skipping over event with " + ev.getTotalBytes() +
                                       " bytes & " + banksContained + " banks");
                    continue;
                }

                EvioBank triggerBank = (EvioBank) ev.getChildAt(0);
                int numRocs = triggerBank.getHeader().getTag();
                int numSegments = triggerBank.getChildCount();

                ArrayList<Integer> rocIdList = new ArrayList<Integer>();

                // Skip over common data seg
                // Look for tag in each Roc segment in trigger bank
                for (int j=1; j < numSegments; j++) {
                    EvioSegment rocSeg = (EvioSegment) triggerBank.getChildAt(j);
                    int rocId = rocSeg.getHeader().getTag();
                    // Some events seem to have large tag values in
                    // events not containing valid ROC data. Ignore these.
                    if (rocId > 1000) continue;
                    rocIdList.add(rocId);
                }

                printRocIds(rocIdList);

                // If we have already found some rocsnums but have not seen any
                // new ones for the last 1000 events then assume we've found them all
            }

            reader.close();

         //   System.out.println("Found " + rocNumList.size() + " Roc ids in file");

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


    }


}
