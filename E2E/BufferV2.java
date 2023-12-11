/*
 * In this version of the buffer, the streamer reads from the binary file and then writes into a temporary file.
 * Once the size of the file exceeds a given threshold, it clears the buffer and starts filling it with new content.
 * Purpose: The purpose is to simulate how a temporary buffer will be working in real life. As the buffer/data center where the data will be stores will not have unlimited size,
 *          it will be replacing old content with new ones 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BufferV2 {

    static final int MEM_LIMIT = 8000;
    public static void main(String[] args)
    {
        
        File inputFile = new File("../Detector_Files/file1.bin");
        File outputFile = new File("./RamBuffer.bin");
        
        try {
            FileInputStream fr = new FileInputStream(inputFile);
            FileOutputStream fw = new FileOutputStream(outputFile);
            int i;
            
            while ((i = fr.read()) != -1) {
                long bytes = Files.size(Paths.get("./RamBuffer.bin"));
                if (bytes>MEM_LIMIT){
                    System.out.println("Current file size: "+bytes); 
                    System.out.println("Maximum limit of the buffer is reached. Cleaning the buffer .......");
                    fw = new FileOutputStream(outputFile);
                    bytes = Files.size(Paths.get("./tempBuffer1.bin"));
                    System.out.println(String.format("Current size of the file %,d bytes", bytes));
                    System.out.println("File has been cleared");
                }
                fw.write(i);
            }
            fr.close();
            fw.close();
            System.out.println(
                "File reading and writing both done");
        }
        catch (IOException e) {
            e.printStackTrace();
        System.out.println(
                "There are some IOException");
        }
    }
}
