import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test client with built-in ring buffer monitoring.
 */
public class TestClientWithMonitoring {

    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final int DEFAULT_MONITOR_INTERVAL_MS = 1000;

    private final String host;
    private final int port;
    private final int bufferSize;
    private final int monitorIntervalMs;

    private final CircularBuffer buffer;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong totalPacketsReceived = new AtomicLong(0);
    private final AtomicLong bytesReceivedSinceLastUpdate = new AtomicLong(0);
    private final AtomicLong packetsReceivedSinceLastUpdate = new AtomicLong(0);

    private long startTime;
    private long lastUpdateTime;

    /**
     * Constructor.
     * 
     * @param host              the host to connect to
     * @param port              the port to connect to
     * @param bufferSize        the buffer size
     * @param monitorIntervalMs the monitor interval in milliseconds
     */
    public TestClientWithMonitoring(String host, int port, int bufferSize, int monitorIntervalMs) {
        this.host = host;
        this.port = port;
        this.bufferSize = bufferSize;
        this.monitorIntervalMs = monitorIntervalMs;
        this.buffer = new CircularBuffer(bufferSize);
    }

    /**
     * Starts the client.
     */
    public void start() {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Start the receiver thread
        executor.submit(this::receiveData);

        // Start the monitor thread
        executor.submit(this::monitorBuffer);

        // Wait for the threads to complete
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops the client.
     */
    public void stop() {
        running.set(false);
    }

    /**
     * Receives data from the server.
     */
    private void receiveData() {
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;

        try (Socket socket = new Socket(host, port);
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connected to server: " + host + ":" + port);
            connected.set(true);

            while (running.get()) {
                try {
                    // Read packet length
                    int packetLength = in.readInt();

                    // Read packet data
                    byte[] packetData = new byte[packetLength];
                    in.readFully(packetData);

                    // Add to buffer
                    buffer.put(packetData);

                    // Update statistics
                    totalBytesReceived.addAndGet(packetLength);
                    totalPacketsReceived.incrementAndGet();
                    bytesReceivedSinceLastUpdate.addAndGet(packetLength);
                    packetsReceivedSinceLastUpdate.incrementAndGet();

                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Error reading from server: " + e.getMessage());
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        } finally {
            connected.set(false);
        }
    }

    /**
     * Monitors the buffer and prints statistics.
     */
    private void monitorBuffer() {
        while (running.get()) {
            try {
                Thread.sleep(monitorIntervalMs);

                long now = System.currentTimeMillis();
                long elapsedSinceStart = now - startTime;
                long elapsedSinceLastUpdate = now - lastUpdateTime;

                long bytesReceived = bytesReceivedSinceLastUpdate.getAndSet(0);
                long packetsReceived = packetsReceivedSinceLastUpdate.getAndSet(0);

                double bytesPerSecond = (bytesReceived * 1000.0) / elapsedSinceLastUpdate;
                double packetsPerSecond = (packetsReceived * 1000.0) / elapsedSinceLastUpdate;
                double mbps = (bytesPerSecond * 8.0) / (1024.0 * 1024.0);

                System.out.println("\n--- Buffer Status at " + java.time.LocalDateTime.now() + " ---");
                System.out.println("Connected: " + connected.get());
                System.out.println("Buffer Size: " + buffer.getCapacity());
                System.out.println("Buffer Used: " + buffer.getSize() + " (" +
                        String.format("%.2f", buffer.getFillPercentage()) + "%)");
                System.out.println("Buffer Available: " + buffer.getAvailable());
                System.out.println("Total Packets Received: " + totalPacketsReceived.get());
                System.out.println("Total Bytes Received: " + totalBytesReceived.get() + " (" +
                        (totalBytesReceived.get() / (1024 * 1024)) + " MB)");
                System.out.println("Throughput: " + String.format("%.2f", packetsPerSecond) +
                        " packets/s (" + String.format("%.2f", mbps) + " Mbps)");
                System.out.println("Running Time: " + (elapsedSinceStart / 1000) + " seconds");

                lastUpdateTime = now;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Main method.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        String host = "localhost";
        int port = 9000;
        int bufferSize = DEFAULT_BUFFER_SIZE;
        int monitorIntervalMs = DEFAULT_MONITOR_INTERVAL_MS;

        if (args.length >= 1) {
            host = args[0];
        }

        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        if (args.length >= 3) {
            bufferSize = Integer.parseInt(args[2]);
        }

        if (args.length >= 4) {
            monitorIntervalMs = Integer.parseInt(args[3]);
        }

        System.out.println("Starting test client with monitoring");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        System.out.println("Buffer Size: " + bufferSize);
        System.out.println("Monitor Interval: " + monitorIntervalMs + " ms");

        TestClientWithMonitoring client = new TestClientWithMonitoring(
                host, port, bufferSize, monitorIntervalMs);

        // Add shutdown hook to stop the client gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down client...");
            client.stop();
        }));

        client.start();
    }

    /**
     * Simple circular buffer implementation.
     */
    private static class CircularBuffer {
        private final byte[][] buffer;
        private final int capacity;
        private int head = 0;
        private int tail = 0;
        private int size = 0;

        /**
         * Constructor.
         * 
         * @param capacity the buffer capacity
         */
        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new byte[capacity][];
        }

        /**
         * Puts an item in the buffer.
         * 
         * @param item the item to put
         * @return true if the item was added, false if the buffer is full
         */
        public synchronized boolean put(byte[] item) {
            if (size == capacity) {
                // Buffer is full, overwrite oldest item
                buffer[head] = item;
                head = (head + 1) % capacity;
                tail = (tail + 1) % capacity;
                return true;
            }

            buffer[tail] = item;
            tail = (tail + 1) % capacity;
            size++;
            return true;
        }

        /**
         * Gets an item from the buffer.
         * 
         * @return the item, or null if the buffer is empty
         */
        public synchronized byte[] get() {
            if (size == 0) {
                return null;
            }

            byte[] item = buffer[head];
            buffer[head] = null;
            head = (head + 1) % capacity;
            size--;
            return item;
        }

        /**
         * Gets the buffer capacity.
         * 
         * @return the buffer capacity
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * Gets the number of items in the buffer.
         * 
         * @return the number of items in the buffer
         */
        public synchronized int getSize() {
            return size;
        }

        /**
         * Gets the number of available slots in the buffer.
         * 
         * @return the number of available slots
         */
        public synchronized int getAvailable() {
            return capacity - size;
        }

        /**
         * Gets the fill percentage of the buffer.
         * 
         * @return the fill percentage (0-100)
         */
        public synchronized double getFillPercentage() {
            return (double) size / capacity * 100.0;
        }
    }
}