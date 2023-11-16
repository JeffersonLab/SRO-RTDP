/*
 * This code has been inspired from an online material
 * Reference: https://www.tutorialspoint.com/java_nio/java_nio_quick_guide.htm
 * Author : Ayan Roy
 * Email : ayan@jlab.org
 */

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public class Buffer {
   public static void main(String args[]) throws IOException {
    try {
        String filePath = "../Detector_Files/file1.bin";
        writeFileChannel(ByteBuffer.wrap(Files.readAllBytes(Paths.get(filePath, args))));
        //read the file
        readFileChannel();

    }catch (IOException e){
        e.printStackTrace();
    } 
      
   }
   public static void readFileChannel() throws IOException {
      RandomAccessFile randomAccessFile = new RandomAccessFile("./tempBuffer.bin",
      "rw");
      FileChannel fileChannel = randomAccessFile.getChannel();
      ByteBuffer byteBuffer = ByteBuffer.allocate(512);
      Charset charset = Charset.forName("US-ASCII");
      while (fileChannel.read(byteBuffer) > 0) {
         byteBuffer.rewind();
         System.out.print(charset.decode(byteBuffer));
         byteBuffer.flip();
      }
      fileChannel.close();
      randomAccessFile.close();
   }
   public static void writeFileChannel(ByteBuffer byteBuffer)throws IOException {
      Set<StandardOpenOption> options = new HashSet<>();
      options.add(StandardOpenOption.CREATE);
      options.add(StandardOpenOption.APPEND);
      Path path = Paths.get("./tempBuffer.bin");
      FileChannel fileChannel = FileChannel.open(path, options);
      fileChannel.write(byteBuffer);
      fileChannel.close();
   }
}