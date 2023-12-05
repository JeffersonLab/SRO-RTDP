	
/*
 * This is a code of a Watcher service in java ( inspired from https://www.codejava.net/java-se/file-io/file-change-notification-example-with-watch-service-api )
 * This can be integrated into the ERSAP to create a system where the actor can keep on reading a file and do something when there is a change/modification in a file. 
 * The file concerned can emulate the temporary buffer created in the file BufferV2.java
 */
 
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
 
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
 
/**
 * This program demonstrates how to use the Watch Service API to monitor change
 * events for a specific directory.
 * @author www.codejava.net
 *
 */
public class DemoWatcher {
 
    public static void main(String[] args) {
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            Path dir = Paths.get("/Users/ayan/Desktop/Projects/SRO-RTDP/E2E");
            dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
             
            System.out.println("Watch Service registered for dir: " + dir.getFileName());
             
            while (true) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException ex) {
                    return;
                }
                 
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                     
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                     
                    System.out.println(kind.name() + ": " + fileName);
                     
                    if (kind == ENTRY_MODIFY &&
                            fileName.toString().equals("RamBuffer.bin")) {
                        System.out.println("My source file has changed!!!");
                    }
                }
                 
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
             
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }
}