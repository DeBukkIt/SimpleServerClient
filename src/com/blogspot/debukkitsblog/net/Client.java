package com.blogspot.debukkitsblog.net;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.AlreadyConnectedException;
import java.util.HashMap;
import java.util.UUID;

/**
 * A very simple Client class for Java network applications<br>
 * originally created on March 9, 2016 in Horstmar, Germany
 *
 * @author Leonard Bienbeck
 * @version 2.4.1
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class Client {

    protected String id;
    protected String group;

    protected Socket loginSocket;
    protected InetSocketAddress address;
    protected int timeout;

    protected Thread listeningThread;
    protected HashMap<String, Executable> idMethods = new HashMap<String, Executable>();

    protected int errorCount;

    protected boolean secureMode;
    protected boolean muted;
    protected boolean stopped;

    /**
     * The default user id Datapackes are signed with. This is a type 4 pseudo
     * randomly generated UUID.
     */
    public static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
    /**
     * The default group id Datapackages are signed with: <i>_DEFAULT_GROUP_</i>
     */
    public static final String DEFAULT_GROUP_ID = "_DEFAULT_GROUP_";

    /**
     * Constructs a simple client with just a hostname and port to connect to
     *
     * @param hostname The hostname to connect to
     * @param port     The port to connect to
     */
    public Client(String hostname, int port) {
        this(hostname, port, 10000, false, DEFAULT_USER_ID, DEFAULT_GROUP_ID);
    }

    public Client(String hostname, int port, int timeout) {
        this(hostname, port, timeout, false, DEFAULT_USER_ID, DEFAULT_GROUP_ID);
    }

    /**
     * Constructs a simple client with a hostname and port to connect to and an id
     * the server uses to identify this client in the future (e.g. for sending
     * messages only this client should receive)
     *
     * @param hostname The hostname to connect to
     * @param port     The port to connect to
     * @param id       The id the server may use to identify this client
     */
    public Client(String hostname, int port, String id) {
        this(hostname, port, 10000, false, id, DEFAULT_GROUP_ID);
    }

    /**
     * Constructs a simple client with a hostname and port to connect to, an id the
     * server uses to identify this client in the future (e.g. for sending messages
     * only this client should receive) and a group name the server uses to identify
     * this and some other clients in the future (e.g. for sending messages to the
     * members of this group, but no other clients)
     *
     * @param hostname The hostname to connect to
     * @param port     The port to connect to
     * @param id       The id the server may use to identify this client
     * @param group    The group name the server may use to identify this and similar
     *                 clients
     */
    public Client(String hostname, int port, String id, String group) {
        this(hostname, port, 10000, false, id, group);
    }

    /**
     * Constructs a simple client with all possible configurations
     *
     * @param hostname The hostname to connect to
     * @param port     The port to connect to
     * @param timeout  The timeout after a connection attempt will be given up
     * @param useSSL   Whether a secure SSL connection should be used
     * @param id       The id the server may use to identify this client
     * @param group    The group name the server may use to identify this and similar
     *                 clients
     */
    public Client(String hostname, int port, int timeout, boolean useSSL, String id, String group) {
        this.id = id;
        this.group = group;

        this.errorCount = 0;
        this.address = new InetSocketAddress(hostname, port);
        this.timeout = timeout;

        this.secureMode = useSSL;
        if (secureMode) {
            System.setProperty("javax.net.ssl.trustStore", "ssc.store");
            System.setProperty("javax.net.ssl.keyStorePassword", "SimpleServerClient");
        }
    }

    /**
     * Checks whether the client is connected to the server and waiting for incoming
     * messages.
     *
     * @return true, if the client is connected to the server and waiting for
     * incoming messages
     */
    public boolean isListening() {
        return isConnected() && errorCount == 0;
    }

    /**
     * Checks whether the persistent connection to the server listening for incoming
     * messages is connected. This does not check whether the client actually waits
     * for incoming messages with the help of the <i>listening thread</i>, but only
     * the pure connection to the server.
     *
     * @return true, if connected
     */
    public boolean isConnected() {
        return loginSocket != null && loginSocket.isConnected();
    }

    /**
     * Checks the connectivity to the server
     *
     * @return true, if the server can be reached at all using the given address
     * data
     */
    public boolean isServerReachable() {
        try {
            Socket tempSocket = new Socket();
            tempSocket.connect(this.address);
            tempSocket.isConnected();
            tempSocket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Mutes the console output of this instance, stack traces will still be
     * printed.<br>
     * <b>Be careful:</b> This will not prevent processing of messages passed to the
     * onLog and onLogError methods, if they were overwritten.
     *
     * @param muted true if there should be no console output
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    /**
     * Starts the client. This will cause a connection attempt, a login on the
     * server and the start of a new listening thread (both to receive messages and
     * broadcasts from the server)
     */
    public void start() {
        stopped = false;
        login();
        startListening();
    }

    /**
     * Stops the client. The connection to the server is interrupted as soon as
     * possible and then no further Datapackages are received. <b>Warning</b>: The
     * whole process of stopping can take as long as the server needs to the next
     * Datapackage, which will wake up the Client and cause him to stop.
     */
    public void stop() {
        stopped = true;
        onLog("[Client] Stopping...");
    }

    /**
     * Called to repair the connection if it is lost
     */
    protected void repairConnection() {
        onLog("[Client] [Connection-Repair] Repairing connection...");
        if (loginSocket != null) {
            try {
                loginSocket.close();
            } catch (IOException e) {
                // This exception does not need to result in any further action or output
            }
            loginSocket = null;
        }

        login();
        startListening();
    }

    /**
     * Logs in to the server to receive messages and broadcasts from the server
     * later
     */
    protected void login() {
        if (stopped) {
            return;
        }

        // 1. connect
        try {
            onLog("[Client] Connecting" + (secureMode ? " using SSL..." : "..."));
            if (loginSocket != null && loginSocket.isConnected()) {
                throw new AlreadyConnectedException();
            }

            if (secureMode) {
                loginSocket = SSLSocketFactory.getDefault().createSocket(address.getAddress(), address.getPort());
            } else {
                loginSocket = new Socket();
                loginSocket.connect(this.address, this.timeout);
            }

            onLog("[Client] Connected to " + loginSocket.getRemoteSocketAddress());

            // 2. login
            try {
                onLog("[Client] Logging in...");
                // open an outputstream
                ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(loginSocket.getOutputStream()));
                // create a magic login package
                Datapackage loginPackage = new Datapackage("_INTERNAL_LOGIN_", id, group);
                loginPackage.sign(id, group);
                // send the package to the server
                out.writeObject(loginPackage);
                out.flush();
                // note: this special method does not expect the server to send a reply
                onLog("[Client] Logged in.");
                onReconnect();
            } catch (IOException ex) {
                onLogError("[Client] Login failed.");
            }

        } catch (ConnectException e) {
            onLogError("[Client] Connection failed: " + e.getMessage());
            onConnectionProblem();
        } catch (IOException e) {
            e.printStackTrace();
            onConnectionProblem();
        }
    }

    /**
     * Starts a new thread listening for messages from the server. A message will
     * only be processed if a handler for its identifier has been registered before
     * using <code>registerMethod(String identifier, Executable executable)</code>
     */
    protected void startListening() {

        // do not restart the listening thread if it is already running
        if (listeningThread != null && listeningThread.isAlive()) {
            return;
        }

        // run
        listeningThread = new Thread(() -> {

            // always repeat if not stopped
            while (!stopped) {
                try {
                    // repait connection if something went wrong with the connection
                    if (loginSocket != null && !loginSocket.isConnected()) {
                        while (!loginSocket.isConnected()) {
                            repairConnection();
                            if (loginSocket.isConnected()) {
                                break;
                            }

                            Thread.sleep(5000);
                            repairConnection();
                        }
                    }

                    onConnectionGood();

                    // wait for incoming messages and read them
                    ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(loginSocket.getInputStream()));
                    Object raw = ois.readObject();

                    // if the client has been stopped while this thread was listening to an arriving
                    // Datapackage, stop the proccess at this point
                    if (stopped) {
                        return;
                    }

                    if (raw instanceof Datapackage) {
                        final Datapackage msg = (Datapackage) raw;

                        // inspect all registered methods
                        for (final String current : idMethods.keySet()) {
                            // if the identifier of a method equals the identifier of the Datapackage...
                            if (current.equalsIgnoreCase(msg.id())) {
                                onLog("[Client] Message received. Executing method for '" + msg.id() + "'...");
                                // execute the registered Executable on a new thread
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        idMethods.get(current).run(msg, loginSocket);
                                    }
                                }).start();
                                break;
                            }
                        }

                    }

                } catch (SocketException e) {
                    onConnectionProblem();
                    onLogError("[Client] Connection lost");
                    repairConnection();
                } catch (ClassNotFoundException | IOException | InterruptedException ex) {
                    ex.printStackTrace();
                    onConnectionProblem();
                    onLogError("[Client] Error: The connection to the server is currently interrupted!");
                    repairConnection();
                }

                // reset errorCount if no errors occured until this point
                errorCount = 0;

            } // while not stopped

        });

        // start the thread
        listeningThread.start();
    }

    /**
     * Sends a message to the server using a brand new socket and returns the
     * server's response
     *
     * @param message The message to send to the server
     * @param timeout The timeout after a connection attempt will be given up
     * @return The server's response. The identifier of this Datapackage should be
     * "REPLY" by default, the rest is custom data.
     */
    public Datapackage sendMessage(Datapackage message, int timeout) {
        try {
            // connect to the target client's socket
            Socket tempSocket;
            if (secureMode) {
                tempSocket = SSLSocketFactory.getDefault().createSocket(address.getAddress(), address.getPort());
            } else {
                tempSocket = new Socket();
                tempSocket.connect(address, timeout);
            }

            // Open output stream and write message
            ObjectOutputStream tempOOS = new ObjectOutputStream(new BufferedOutputStream(tempSocket.getOutputStream()));
            message.sign(id, group);
            tempOOS.writeObject(message);
            tempOOS.flush();

            // open input stream and wait for server's response. Warning: If the server
            // won't send an answer, this lines might block the program or throw an
            // EOFException
            ObjectInputStream tempOIS = new ObjectInputStream(new BufferedInputStream(tempSocket.getInputStream()));
            Object raw = tempOIS.readObject();

            // close all streams and the socket
            tempOOS.close();
            tempOIS.close();
            tempSocket.close();

            // return the server's reply if it is a Datapackage
            if (raw instanceof Datapackage) {
                return (Datapackage) raw;
            }
        } catch (EOFException ex) {
            onLogError("[Client] Error right after sending message: EOFException (did the server forget to send a reply?)");
        } catch (IOException | ClassNotFoundException ex) {
            onLogError("[Client] Error while sending message");
            ex.printStackTrace();
        }

        return null;
    }

    /**
     * Sends a message to the server using a brand new socket and returns the
     * server's response
     *
     * @param ID      The ID of the message, allowing the server to decide what to do
     *                with its content
     * @param content The content of the message
     * @return The server's response. The identifier of this Datapackage should be
     * "REPLY" by default, the rest is custom data.
     */
    public Datapackage sendMessage(String ID, Object... content) {
        return sendMessage(new Datapackage(ID, content));
    }

    /**
     * Sends a message to the server using a brand new socket and returns the
     * server's response
     *
     * @param message The message to send to the server
     * @return The server's response. The identifier of this Datapackage should be
     * "REPLY" by default, the rest is custom data.
     */
    public Datapackage sendMessage(Datapackage message) {
        return sendMessage(message, this.timeout);
    }

    /**
     * Registers a method that will be executed if a message containing
     * <i>identifier</i> is received
     *
     * @param identifier The ID of the message to proccess
     * @param executable The method to be called when a message with <i>identifier</i> is
     *                   received
     */
    public void registerMethod(String identifier, Executable executable) {
        idMethods.put(identifier, executable);
    }

    /**
     * Called on the listener's main thread when there is a problem with the
     * connection. Overwrite this method when extending this class.
     */
    public void onConnectionProblem() {
        // Overwrite this method when extending this class
    }

    /**
     * Called on the listener's main thread when there is no problem with the
     * connection and everything is fine. Overwrite this method when extending this
     * class.
     */
    public void onConnectionGood() {
        // Overwrite this method when extending this class
    }

    /**
     * Called on the listener's main thread when the client logs in to the server.
     * This happens on the first and every further login (e.g. after a
     * re-established connection). Overwrite this method when extending this class.
     */
    public void onReconnect() {
        // Overwrite this method when extending this class
    }

    /**
     * By default, this method is called whenever an output is to be made. If this
     * method is not overwritten, the output is passed to the system's default
     * output stream (if output is not muted).<br>
     * Error messages are passed to the <code>onLogError</code> event listener.<br>
     * <b>Override this method to catch and process the message in a custom way.</b>
     *
     * @param message The content of the output to be made
     */
    public void onLog(String message) {
        if (!muted) {
            System.out.println(message);
        }
    }

    /**
     * By default, this method is called whenever an error output is to be made. If
     * this method is not overwritten, the output is passed to the system's default
     * error output stream (if output is not muted).<br>
     * Non-error messages are passed to the <code>onLog</code> event listener.<br>
     * <b>Override this method to catch and process the message in a custom way.</b>
     *
     * @param message The content of the error output to be made
     */
    public void onLogError(String message) {
        if (!muted) {
            System.err.println(message);
        }
    }

}