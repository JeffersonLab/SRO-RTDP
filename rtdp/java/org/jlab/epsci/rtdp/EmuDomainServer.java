package org.jlab.epsci.rtdp;

import org.jlab.coda.cMsg.cMsgException;

import java.util.concurrent.CountDownLatch;

/** Local copy of EmuDomainServer class. */
class EmuDomainServer extends Thread {

    /** This server's UDP listening port. */
    final int serverPort;

    private final String expid;
    private final String name;
    private final boolean debug;

    /** Expecting data input from ROCs over TCP only. */
    private final boolean tcp;
    /** Expecting data input from ROCs over UDP only. */
    private final boolean udp;

    /** Number of clients expected to connect. */
    private final int clientCount;
    /** Set this when given # of clients connect. */
    private final CountDownLatch clientAttachLatch;

    /** The local port used temporarily while multicasting for other rc multicast servers. */
    int localTempPort;

    /** Thread that listens for UDP multicasts to this server and then responds. */
    private EmuDomainUdpListener listener;
    /** Thread that listens for TCP client connections and then handles client. */
    private EmuDomainTcpServer tcpServer;


    public EmuDomainServer(int port, int clientCount, String expid, String name, boolean tcp, CountDownLatch latch, boolean debug) {

        this.name = name;
        this.expid = expid;
        this.serverPort = port;
        this.clientCount = clientCount;
        this.tcp = tcp;
        this.udp = !tcp;
        this.clientAttachLatch = latch;
        this.debug = debug;
    }


    public EmuDomainTcpServer getTcpServer() {
        return tcpServer;
    }


    /** Stop all communication with Emu domain clients. */
    public void stopServer() {
        listener.killAllThreads();
        tcpServer.killAllThreads();
    }


    public void run() {

        try {
            // Start TCP server thread
            tcpServer = new EmuDomainTcpServer(this, serverPort, clientCount, clientAttachLatch, debug);
            tcpServer.start();

            // Wait for indication thread is running before continuing on
            if (tcp) {
                synchronized (tcpServer) {
                    if (!tcpServer.isAlive()) {
                        try {
                            tcpServer.wait();
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }


            if (udp) {
                // Start listening for udp packets
                listener = new EmuDomainUdpListener(this, serverPort, clientCount, expid, name, debug);
                listener.start();

                // Wait for indication listener thread is running before continuing on
                synchronized (listener) {
                    if (!listener.isAlive()) {
                        try {
                            listener.wait();
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
         }
        catch (cMsgException e) {
            e.printStackTrace();
        }
    }
}
