package org.jlab.epsci.rtdp;

import org.jlab.coda.cMsg.cMsgConstants;
import org.jlab.coda.cMsg.cMsgException;
import org.jlab.coda.cMsg.cMsgNetworkConstants;
import org.jlab.coda.cMsg.cMsgUtilities;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Local copy of EmuDomainUdpListener class. */
class EmuDomainUdpListener extends Thread {

    /** Emu multicast server that created this object. */
    private final EmuDomainServer server;

    /** UDP port on which to listen for emu client multi/unicasts. */
    private final int multicastPort;

    /** UDP port on which to listen for emu client multi/unicasts. */
    private final int tcpPort;

    /** UDP socket on which to read packets sent from rc clients. */
    private MulticastSocket multicastSocket;

    /** Level of debug output for this class. */
    private final boolean debug;

    private String expid;
    private String emuName;

    /** Number of clients expected to connect. */
    private int clientCount;

    /** Setting this to true will kill all threads. */
    private volatile boolean killThreads;


    /** Kills this and all spawned threads. */
    void killAllThreads() {
        killThreads = true;
        this.interrupt();
    }



    /**
     * Constructor.
     * @param server emu server that created this object
     * @param port udp port on which to receive transmissions from emu clients
     * @param clientCount # of clients expected to connect
     * @param expid EXPID of experiment
     * @param emuName name of emu
     * @throws cMsgException if multicast port is taken
     */
    public EmuDomainUdpListener(EmuDomainServer server, int port, int clientCount,
                                String expid, String emuName, boolean debug) throws cMsgException {

        this.expid = expid;
        this.emuName = emuName;
        this.clientCount = clientCount;
        this.debug = debug;
        multicastPort = tcpPort = port;

        try {
            if (debug) System.out.println("Listening for multicasts on port " + multicastPort);

            // Create a UDP socket for accepting multi/unicasts from the Emu client
            multicastSocket = new MulticastSocket(multicastPort);
            SocketAddress sa =
                new InetSocketAddress(InetAddress.getByName(cMsgNetworkConstants.emuMulticast), multicastPort);
            // Be sure to join the multicast address group of all network interfaces
            // (something not mentioned in any javadocs or books!).
            Enumeration<NetworkInterface> enumer = NetworkInterface.getNetworkInterfaces();
            while (enumer.hasMoreElements()) {
                NetworkInterface ni = enumer.nextElement();
                if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
//System.out.println("Join group for " + cMsgNetworkConstants.emuMulticast +
//                    ", port = " + multicastPort + ", ni = " + ni.getName());
                    multicastSocket.joinGroup(sa, ni);
                }
            }
            multicastSocket.setReceiveBufferSize(65535);
            multicastSocket.setReuseAddress(true);
            multicastSocket.setTimeToLive(32);
        }
        catch (IOException e) {
            throw new cMsgException("Port " + multicastPort + " is taken", e);
        }
        this.server = server;
        // die if no more non-daemon threads running
        setDaemon(true);
    }


    /** This method is executed as a thread. */
    public void run() {

        if (debug) System.out.println("Emu Multicast Listening Thread: running");

        // Create a packet to be written into from client
        byte[] buf = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buf, 2048);

        // Prepare a packet to be send back to the client
        byte[] outBuf = null;
        DatagramPacket sendPacket  = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        DataOutputStream out       = new DataOutputStream(baos);

        // Get our local IP addresses, canonical first
        ArrayList<String> ipAddresses = new ArrayList<>(cMsgUtilities.getAllIpAddresses());
        List<InterfaceAddress> ifAddrs = cMsgUtilities.getAllIpInfo();

        try {
            // Put our special #s, TCP listening port, expid,
            // and all IP addresses into byte array.
            out.writeInt(cMsgNetworkConstants.magicNumbers[0]);
            out.writeInt(cMsgNetworkConstants.magicNumbers[1]);
            out.writeInt(cMsgNetworkConstants.magicNumbers[2]);
            out.writeInt(tcpPort);
            //out.writeInt(ipAddresses.size());
            // List of all IP data (no IPv6, no loopback, no down interfaces)
            int addrCount = ifAddrs.size();
            // Let folks know how many address pairs are coming
            out.writeInt(addrCount);
//System.out.println("Emu listen: create a response packet with port = " + tcpPort +
//                               ", addr list items = " + ipAddresses.size());

            for (InterfaceAddress ifAddr : ifAddrs) {
                Inet4Address bAddr;
                try { bAddr = (Inet4Address)ifAddr.getBroadcast(); }
                catch (ClassCastException e) {
                    // should never happen since IPv6 already removed
                    continue;
                }
                // send IP addr
                String ipAddr = ifAddr.getAddress().getHostAddress();
                out.writeInt(ipAddr.length());
                out.write(ipAddr.getBytes("US-ASCII"));
//System.out.println("Emu listen: addr = " + ipAddr + ", len = " + ipAddr.length());
                // send broadcast addr
                String broadcastAddr = bAddr.getHostAddress();
                out.writeInt(broadcastAddr.length());
                out.write(broadcastAddr.getBytes("US-ASCII"));
//System.out.println("Emu listen: bcast addr = " + broadcastAddr + ", len = " + broadcastAddr.length());
            }

            out.flush();
            out.close();

            // Create buffer to multicast from the byte array
            outBuf = baos.toByteArray();
            baos.close();
        }
        catch (IOException e) {
            if (debug) {
                System.out.println("I/O Error: " + e);
            }
        }

        // EmuDomainServer object is waiting for this thread to start in, so tell it we've started.
        synchronized (this) {
            notifyAll();
        }

        // Listen for multicasts and interpret packets
        try {
            while (true) {
                if (killThreads) { return; }

                packet.setLength(2048);
                if (debug) System.out.println("Emu listen: WAITING TO RECEIVE PACKET");
                multicastSocket.receive(packet);   // blocks

                if (killThreads) { return; }

                // Pick apart byte array received
                InetAddress multicasterAddress = packet.getAddress();
                String multicasterHost = multicasterAddress.getHostName();
                int multicasterUdpPort = packet.getPort();   // Port to send response packet to

                if (packet.getLength() < 4*4) {
                    System.out.println("Emu listen: got multicast packet that's too small");
                    continue;
                }

                int magic1  = cMsgUtilities.bytesToInt(buf, 0);
                int magic2  = cMsgUtilities.bytesToInt(buf, 4);
                int magic3  = cMsgUtilities.bytesToInt(buf, 8);
                if (magic1 != cMsgNetworkConstants.magicNumbers[0] ||
                    magic2 != cMsgNetworkConstants.magicNumbers[1] ||
                    magic3 != cMsgNetworkConstants.magicNumbers[2])  {
                        System.out.println("Emu listen: got multicast packet with bad magic #s");
                    continue;
                }

                int msgType = cMsgUtilities.bytesToInt(buf, 12); // What type of message is this ?

                switch (msgType) {
                    // Multicasts from emu clients
                    case cMsgNetworkConstants.emuDomainMulticastClient:
                        if (debug) System.out.println("Emu listen: client wants to connect");
                        break;
                    // Packet from client just trying to locate emu multicast servers.
                    // Send back a normal response but don't do anything else.
                    case cMsgNetworkConstants.emuDomainMulticastProbe:
                        if (debug) System.out.println("Emu listen: I was probed");
                        break;
                    // Ignore packets from unknown sources
                    default:
                        if (debug) System.out.println("Emu listen: unknown command");
                        continue;
                }

                int cMsgVersion = cMsgUtilities.bytesToInt(buf, 16); // cMsg version (see cMsg.EmuDomain.EmuClient.java)
                int nameLen     = cMsgUtilities.bytesToInt(buf, 20); // length of sender's name (# chars)
                int expidLen    = cMsgUtilities.bytesToInt(buf, 24); // length of expid (# chars)
                int pos = 28;

                 // Check for conflicting cMsg versions
                if (cMsgVersion != cMsgConstants.version) {
                        System.out.println("Emu listen: conflicting cMsg versions, client = " + cMsgVersion +
                        ", cMsg lib = " + cMsgConstants.version);
                    continue;
                }

                // sender's name
                String componentName = null;
                try {
                    componentName = new String(buf, pos, nameLen, "US-ASCII");
                    pos += nameLen;
                }
                catch (UnsupportedEncodingException e) {}

                // sender's EXPID
                String multicasterExpid = null;
                try {
                    multicasterExpid = new String(buf, pos, expidLen, "US-ASCII");
                    pos += expidLen;
                }
                catch (UnsupportedEncodingException e) {}

//                if (debug >= cMsgConstants.debugInfo) {
//                    System.out.println("Emu listen: multicaster's host = " + multicasterHost + ", UDP port = " + multicasterUdpPort +
//                        ", cMsg version = " + cMsgVersion + ", name = " + multicasterName +
//                        ", expid = " + multicasterExpid);
//                }


                // Check for conflicting expids
                if (!expid.equalsIgnoreCase(multicasterExpid)) {
                        System.out.println("Emu listen: conflicting EXPIDs, got " + multicasterExpid +
                                           ", need " + expid);
                    continue;
                }

                // Before sending a reply, check to see if we simply got a packet
                // from our self when first connecting. Just ignore our own probing
                // multicast.

//                System.out.println("Emu listen: accepting Clients = " + server.acceptingClients);
//                System.out.println("          : local host = " + InetAddress.getLocalHost().getCanonicalHostName());
//                System.out.println("          : multicaster's packet's host = " + multicasterHost);
//                System.out.println("          : multicaster's packet's UDP port = " + multicasterUdpPort);
//                System.out.println("          : multicaster's expid = " + multicasterExpid);
//                System.out.println("          : component's name = " + componentName);
//                System.out.println("          : our port = " + server.localTempPort);

                if (multicasterUdpPort == server.localTempPort) {
//System.out.println("Emu listen: ignore my own udp messages");
                    continue;
                }

                // If connection request from client, don't accept if they're
                // looking to connect to a different emu name
                if (msgType == cMsgNetworkConstants.emuDomainMulticastClient &&
                    !componentName.equalsIgnoreCase(emuName)) {

                        System.out.println("Emu UDP listen: this emu wrong destination, I am " +
                                                   emuName + ", client looking for " + componentName);
                    continue;
                }

                try {
                    sendPacket = new DatagramPacket(outBuf, outBuf.length, multicasterAddress, multicasterUdpPort);
//System.out.println("Emu UDP listen: send response-to-probe packet to client");
                    multicastSocket.send(sendPacket);
                }
                catch (IOException e) {
                    System.out.println("I/O Error: " + e);
                }
            }
        }
        catch (IOException e) {
                System.out.println("Emu listen: I/O ERROR in emu multicast server");
                System.out.println("Emu listen: close multicast socket, port = " +  multicastSocket.getLocalPort());
        }
        finally {
            if (!multicastSocket.isClosed())  multicastSocket.close();
        }

        return;
    }


}
