import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * A standalone utility to monitor ring buffer metrics in an ERSAP application.
 * This can be used to monitor any ring buffer that exposes JMX metrics.
 */
public class RingBufferMonitor {

    private static final int DEFAULT_INTERVAL_SECONDS = 1;
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    private final String objectNamePattern;
    private final int intervalSeconds;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    /**
     * Constructor.
     * 
     * @param objectNamePattern the JMX object name pattern to match
     * @param intervalSeconds the monitoring interval in seconds
     */
    public RingBufferMonitor(String objectNamePattern, int intervalSeconds) {
        this.objectNamePattern = objectNamePattern;
        this.intervalSeconds = intervalSeconds;
    }
    
    /**
     * Starts the monitoring.
     */
    public void start() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        
        System.out.println("Starting ring buffer monitoring...");
        System.out.println("Object name pattern: " + objectNamePattern);
        System.out.println("Interval: " + intervalSeconds + " seconds");
        System.out.println("Press Ctrl+C to exit");
        System.out.println();
        
        try {
            while (running.get()) {
                System.out.println("=== Ring Buffer Status at " + dateFormat.format(new Date()) + " ===");
                
                try {
                    // Query for matching MBeans
                    for (ObjectName name : mbs.queryNames(new ObjectName(objectNamePattern), null)) {
                        System.out.println("Buffer: " + name.getCanonicalName());
                        
                        // Get buffer metrics
                        long bufferSize = (Long) mbs.getAttribute(name, "BufferSize");
                        long bufferUsed = (Long) mbs.getAttribute(name, "BufferUsed");
                        long bufferAvailable = (Long) mbs.getAttribute(name, "BufferAvailable");
                        double fillPercentage = (Double) mbs.getAttribute(name, "FillPercentage");
                        long eventsPublished = (Long) mbs.getAttribute(name, "EventsPublished");
                        long eventsConsumed = (Long) mbs.getAttribute(name, "EventsConsumed");
                        double throughputMbps = (Double) mbs.getAttribute(name, "ThroughputMbps");
                        
                        // Display metrics
                        System.out.println("  Buffer Size: " + bufferSize);
                        System.out.println("  Buffer Used: " + bufferUsed + " (" + 
                                String.format("%.2f", fillPercentage) + "%)");
                        System.out.println("  Buffer Available: " + bufferAvailable);
                        System.out.println("  Events Published: " + eventsPublished);
                        System.out.println("  Events Consumed: " + eventsConsumed);
                        System.out.println("  Throughput: " + String.format("%.2f", throughputMbps) + " Mbps");
                        System.out.println();
                    }
                } catch (Exception e) {
                    System.err.println("Error querying MBeans: " + e.getMessage());
                }
                
                // Wait for the next interval
                TimeUnit.SECONDS.sleep(intervalSeconds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Stops the monitoring.
     */
    public void stop() {
        running.set(false);
    }
    
    /**
     * Main method.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java RingBufferMonitor <object_name_pattern> [interval_seconds]");
            System.err.println("Example: java RingBufferMonitor org.jlab.ersap:type=RingBuffer,* 2");
            System.exit(1);
        }
        
        String objectNamePattern = args[0];
        int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        
        if (args.length >= 2) {
            try {
                intervalSeconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid interval: " + args[1]);
                System.exit(1);
            }
        }
        
        RingBufferMonitor monitor = new RingBufferMonitor(objectNamePattern, intervalSeconds);
        
        // Add shutdown hook to stop the monitor gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down monitor...");
            monitor.stop();
        }));
        
        monitor.start();
    }
} 