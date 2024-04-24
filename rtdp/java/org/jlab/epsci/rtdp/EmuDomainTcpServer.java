package org.jlab.epsci.rtdp;

import org.jlab.coda.cMsg.cMsgConstants;
import org.jlab.coda.cMsg.cMsgNetworkConstants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

/** Local copy of EmuDomainTcpServer class.
 * Originally, this class is the cMsg Emu domain TCP server run inside of an EMU.
 * It accepts connections from ROCs and SEBs. Its purpose is to implement
 * fast, efficient communication between those components and EBs and ERs.
 * It's now been modified to run standalone and accept connection from ROCs only.
 * @author timmer (1/19/24)
 */
class EmuDomainTcpServer extends Thread {


    /** Level of debug output for this class. */
    private final boolean debug;

    /** If this is true, then we use streaming format input channels,
     * DataChannelImplTcpStream. If false, we use regular emu channels,
     * DataChannelImplEmu.
     */
    private final boolean streaming;

    private final int serverPort;

    /** Number of clients expected to connect. */
    private final int clientCount;
    
    /** Setting this to true will kill all threads. */
    private volatile boolean killThreads;

    /** Store all input channels created in this server (no longer done in emu.java, prestart() method). */
    private final ArrayList<DataChannel> inputChannels;

    /** Set this when given # of clients connect. */
    private final CountDownLatch clientAttachLatch;


    /** Kills this and all spawned threads. */
    void killAllThreads() {
        killThreads = true;
        this.interrupt();
    }


    /**
     * Get the input channels created by this server.
     * @return ArrayList containing the input channels of this server
     */
    public ArrayList<DataChannel> getInputChannels() {return inputChannels;}


    /**
     * Constructor.
     * @param serverPort TCP port on which to receive transmissions from emu clients.
     * @param inputChannels channel objects to associated with incoming connections.
     * @param latch sync object to let caller know when all connections are made.
     * @param debug
     * @param streaming if this is true, then we use streaming format input channels,
     *                  DataChannelImplTcpStream. If false, we use regular emu channels,
     *                  DataChannelImplEmu.
     */
    public EmuDomainTcpServer(int serverPort, ArrayList<DataChannel> inputChannels,
                              CountDownLatch latch, boolean debug, boolean streaming) {
        this.serverPort = serverPort;
        this.inputChannels = inputChannels;
        this.clientCount = inputChannels.size();
        this.clientAttachLatch = latch;
        this.debug = debug;
        this.streaming = streaming;

        inputChannels = new ArrayList<>(20);
    }


    /** This method is executed as a thread. */
    public void run() {
        if (debug)  System.out.println("Emu domain TCP server: running @ port " + serverPort);

        // Direct buffer for reading 3 magic & 5 other integers with non-blocking IO
        int BYTES_TO_READ = 8*4;
        ByteBuffer buffer = ByteBuffer.allocateDirect(BYTES_TO_READ);

        Selector selector = null;
        ServerSocketChannel serverChannel = null;

        int connectCount = 0;

        try {
            // Get things ready for a select call
            selector = Selector.open();

            // Bind to the given TCP listening port. If not possible, throw exception
            try {
                serverChannel = ServerSocketChannel.open();
                ServerSocket listeningSocket = serverChannel.socket();
                listeningSocket.setReuseAddress(true);
                // We prefer high bandwidth, low latency, & short connection times, in that order
                listeningSocket.setPerformancePreferences(0,1,2);
                listeningSocket.bind(new InetSocketAddress(serverPort));
            }
            catch (IOException ex) {
                System.out.println("Agg TCP server: TCP port number " + serverPort + " in use.");
                System.exit(-1);
            }

            // Set non-blocking mode for the listening socket
            serverChannel.configureBlocking(false);

            // Register the channel with the selector for accepts
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            // EmuDomainServer object is waiting for this thread to start, so tell it we've started.
            synchronized (this) {
                notifyAll();
            }

            while (true) {
                // 3 second timeout
                int n = selector.select(3000);

                // If no channels (sockets) are ready, listen some more
                if (n == 0) {
                    // But first check to see if we've been commanded to die
                    if (killThreads) {
                        return;
                    }
                    continue;
                }
                if (debug) System.out.println("Agg TCP server: someone trying to connect");

                // Get an iterator of selected keys (ready sockets)
                Iterator it = selector.selectedKeys().iterator();

                // Look at each key
                keyLoop:
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();

                    // Is this a new connection coming in?
                    if (key.isValid() && key.isAcceptable()) {

                        // Accept the connection from the client
                        SocketChannel channel = serverChannel.accept();

                        // Check to see if this is a legit cMsg client or some imposter.
                        // Don't want to block on read here since it may not be a cMsg
                        // client and may block forever - tying up the server.
                        int version, codaId=-1, bufferSizeDesired=-1, socketCount=-1, socketPosition=-1;
                        int bytes, bytesRead=0, loops=0;
                        buffer.clear();
                        buffer.limit(BYTES_TO_READ);
                        channel.configureBlocking(false);

                        // Loop until all 6 integers of incoming data read or timeout
                        while (bytesRead < BYTES_TO_READ) {
                            if (debug)  System.out.println("Agg TCP server: try reading rest of Buffer");

                            bytes = channel.read(buffer);

                            // Check for End-of-stream ...
                            if (bytes == -1) {
                                channel.close();
                                it.remove();
                                continue keyLoop;
                            }

                            bytesRead += bytes;

                            if (debug) System.out.println("Agg TCP server: bytes read = " + bytesRead);

                            // If we've read everything, look to see what we got ...
                            if (bytesRead >= BYTES_TO_READ) {
                                buffer.flip();

                                // Check for correct magic #s
                                int magic1 = buffer.getInt();
                                int magic2 = buffer.getInt();
                                int magic3 = buffer.getInt();
                                if (magic1 != cMsgNetworkConstants.magicNumbers[0] ||
                                    magic2 != cMsgNetworkConstants.magicNumbers[1] ||
                                    magic3 != cMsgNetworkConstants.magicNumbers[2])  {
                                    if (debug) {
                                        System.out.println("Agg TCP server: Magic #s did NOT match, ignore");
                                    }
                                    channel.close();
                                    it.remove();
                                    continue keyLoop;
                                }

                                // Check for server / client compatibility for cMsg version
                                version = buffer.getInt();
System.out.println("Got cMsg version = " + version);
                                if (version != cMsgConstants.version) {
                                    if (debug) {
                                        System.out.println("Agg TCP server: version mismatch, got " +
                                                            version + ", needed " + cMsgConstants.version);
                                    }
                                    channel.close();
                                    it.remove();
                                    continue keyLoop;
                                }

                                // CODA id of sender
                                codaId = buffer.getInt();
System.out.println("Got coda id = " + codaId);
                                if (codaId < 0) {
                                    if (debug) {
                                        System.out.println("Agg TCP server: bad coda id of sender (" +
                                                           codaId + ')');
                                    }
                                    channel.close();
                                    it.remove();
                                    continue keyLoop;
                                }

                                // Max size buffers to hold incoming data in bytes
                                bufferSizeDesired = buffer.getInt();
System.out.println("Got buffer size = " + bufferSizeDesired);
                                if (bufferSizeDesired < 4*10) {
                                    // 40 bytes is smallest possible evio file format size
                                    if (debug) {
                                        System.out.println("Agg TCP server: bad buffer size from sender (" +
                                                           bufferSizeDesired + ')');
                                    }
                                    channel.close();
                                    it.remove();
                                    continue keyLoop;
                                }

                                // Number of sockets expected to be made by client
                                socketCount = buffer.getInt();
if (debug) System.out.println("Got socket count = " + socketCount);
                                if (socketCount < 1) {
                                    if (debug) {
                                        System.out.println("    Transport Emu: domain server, bad socket count of sender (" +
                                                                   socketCount + ')');
                                    }
                                    channel.close();
                                    it.remove();
                                    continue keyLoop;
                                }

                                // Position of this socket compared to others: 1, 2, ...
                                socketPosition = buffer.getInt();
if (debug) System.out.println("Got socket position = " + socketPosition);
                                if (socketCount < 1) {
                                    if (debug) {
                                        System.out.println("    Transport Emu: domain server, bad socket position of sender (" +
                                                                   socketPosition + ')');
                                    }
                                    channel.close();
                                    it.remove();
                                    continue keyLoop;
                                }
                            }
                            else {
                                // Give client 10 loops (.1 sec) to send its stuff, else no deal
                                if (++loops > 10) {
                                    channel.close();
                                    it.remove();
                                    continue keyLoop;
                                }
                                try { Thread.sleep(30); }
                                catch (InterruptedException e) { }
                            }
                        }

                        // Go back to using streams
                        channel.configureBlocking(true);

                        // The emu (not socket) channel will start a
                        // thread to handle all further communication.

                        // Will release aggregator/builder to continue once last client is connected
                        clientAttachLatch.countDown();

                        try {
                            if (streaming) {
                                // Create a new TCP streaming channel each with unique name, 2nd arg is only used to
                                // distinguish between streams from one VTP. Don't care about that here.
                                DataChannelImplTcpStream dc = (DataChannelImplTcpStream) inputChannels.get(connectCount);

                                // Starts thread to handle socket input & thread to parse and
                                // place banks into ring buffer for aggregation
                                dc.attachToInput(channel, codaId, bufferSizeDesired);
                            }
                            else {
                                // Create a new EMU (non-streaming) channel each with unique name
                                DataChannelImplEmu ec = (DataChannelImplEmu) inputChannels.get(connectCount);
                                ec.attachToInput(channel, codaId, bufferSizeDesired);
                            }
                            connectCount++;
                        }
                        catch (IOException e) {
                            if (debug) {
                                System.out.println("Agg TCP server: " + e.getMessage());
                            }
                            channel.close();
                            it.remove();
                            continue;
                        }

                        if (debug) {
                            System.out.println("Agg TCP server: new connection");
                        }

                        if (connectCount >= clientCount) {
                            // All clients have connected so shut down server
                            System.out.println("Agg TCP server: all clients connected, shut down TCP server");
                            return;
                        }
                    }

                    // remove key from selected set since it's been handled
                    it.remove();
                }
            }
        }
        catch (IOException ex) {
            System.out.println("Agg TCP server: main server IO error");
            ex.printStackTrace();
        }
        finally {
            try {if (serverChannel != null) serverChannel.close();} catch (IOException e) {}
            try {if (selector != null) selector.close();} catch (IOException e) {}
        }

        if (debug) {
            System.out.println("Agg TCP server: quitting");
        }
    }

}
