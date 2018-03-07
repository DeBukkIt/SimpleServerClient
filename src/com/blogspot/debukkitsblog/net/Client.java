package com.blogspot.debukkitsblog.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.AlreadyConnectedException;
import java.util.HashMap;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

/**
 * A very simple Client class for Java network applications<br>
 * originally created on March 9, 2016 in Horstmar, Germany
 * 
 * @author Leonard Bienbeck
 * @version 2.4.0
 */
public class Client {

	protected String id;
	protected String group;

	protected Socket loginSocket;
	protected InetSocketAddress address;
	protected int timeout;

	protected Thread listeningThread;
	protected HashMap<String, Executable> idMethods = new HashMap<String, Executable>();

	protected int errorCount;

	protected boolean autoKill;
	protected boolean secureMode;
	protected boolean muted;

	/**
	 * Constructs a simple client with just a hostname and port to connect to
	 * 
	 * @param hostname
	 *            The hostname to connect to
	 * @param port
	 *            The port to connect to
	 */
	public Client(String hostname, int port) {
		this(hostname, port, 10000, false, false, UUID.randomUUID().toString(), "_DEFAULT_GROUP_");
	}

	/**
	 * Constructs a simple client with a hostname and port to connect to and an id 
	 * the server uses to identify this client in the future (e.g. for sending 
	 * messages only this client should receive)
	 * 
	 * @param hostname
	 *            The hostname to connect to
	 * @param port
	 *            The port to connect to
	 * @param id
	 *            The id the server may use to identify this client
	 */
	public Client(String hostname, int port, String id) {
		this(hostname, port, 10000, false, false, id, "_DEFAULT_GROUP_");
	}
	
	/**
	 * Constructs a simple client with a hostname and port to connect to and an id 
	 * the server uses to identify this client in the future (e.g. for sending 
	 * messages only this client should receive)
	 * 
	 * @param hostname
	 *            The hostname to connect to
	 * @param port
	 *            The port to connect to
	 * @param timeout
	 *            The timeout after a connection attempt will be given up
	 */
	public Client(String hostname, int port, int timeout) {
		this(hostname, port, timeout, false, false, UUID.randomUUID().toString(), "_DEFAULT_GROUP_");
	}

	/**
	 * Constructs a simple client with a hostname and port to connect to, an id the 
	 * server uses to identify this client in the future (e.g. for sending messages 
	 * only this client should receive) and a group name the server uses to identify 
	 * this and some other clients in the future (e.g. for sending messages to the 
	 * members of this group, but no other clients)
	 * 
	 * @param hostname
	 *            The hostname to connect to
	 * @param port
	 *            The port to connect to
	 * @param id
	 *            The id the server may use to identify this client
	 * @param group
	 *            The group name the server may use to identify this and similar clients
	 */
	public Client(String hostname, int port, String id, String group) {
		this(hostname, port, 10000, false, false, id, group);
	}

	/**
	 * Constructs a simple client with all possible configurations
	 * 
	 * @param hostname
	 *            The hostname to connect to
	 * @param port
	 *            The port to connect to
	 * @param timeout
	 *            The timeout after a connection attempt will be given up
	 * @param autoKill
	 *            Whether the program should exit after 30 failed connection attempts
	 * @param useSSL
	 *            Whether a secure SSL connection should be used
	 * @param id
	 *            The id the server may use to identify this client
	 * @param group
	 *            The group name the server may use to identify this and similar clients
	 */
	public Client(String hostname, int port, int timeout, boolean autoKill, boolean useSSL, String id, String group) {
		this.id = id;
		this.group = group;

		this.errorCount = 0;
		this.address = new InetSocketAddress(hostname, port);
		this.timeout = timeout;
		this.autoKill = autoKill;

		if (secureMode = useSSL) {
			System.setProperty("javax.net.ssl.trustStore", "ssc.store");
			System.setProperty("javax.net.ssl.keyStorePassword", "SimpleServerClient");
		}
	}

	/**
	 * Checks whether the client is connected to the server and waiting for incoming messages.
	 * 
	 * @return true, if the client is connected to the server and waiting for incoming messages
	 */
	public boolean isListening() {
		return isConnected() && listeningThread != null && listeningThread.isAlive() && errorCount == 0;
	}

	/**
	 * Checks whether the persistent connection to the server listening for incoming messages is connected. This does not check whether the client actually waits for incoming messages with the help of
	 * the <i>listening thread</i>, but only the pure connection to the server.
	 * 
	 * @return true, if connected
	 */
	public boolean isConnected() {
		return loginSocket != null && loginSocket.isConnected();
	}

	/**
	 * Checks the connectivity to the server
	 * 
	 * @return true, if the server can be reached at all using the given address data
	 */
	public boolean isServerReachable() {
		try {
			Socket tempSocket = new Socket();
			tempSocket.connect(this.address);
			tempSocket.isConnected();
			tempSocket.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Mutes the console output of this instance, stack traces will still be printed.<br>
	 * <b>Be careful:</b> This will not prevent processing of messages passed to the onLog and onLogError methods, if they were overwritten.
	 * 
	 * @param muted
	 *            true if there should be no console output
	 */
	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	/**
	 * Starts the client. This will cause a connection attempt, a login on the server and the start of a new listening thread (both to receive messages and broadcasts from the server)
	 */
	public void start() {
		login();
		startListening();
	}

	/**
	 * Called to repair the connection if it is lost
	 */
	protected void repairConnection() {
		onLog("[Client-Connection-Repair] Repairing connection...");
		if (loginSocket != null) {
			try {
				loginSocket.close();
			} catch (IOException e) {
			}
			loginSocket = null;
		}

		login();
		startListening();
	}

	/**
	 * Logs in to the server to receive messages and broadcasts from the server later
	 */
	protected void login() {
		// Verbindung herstellen
		try {
			onLog("[Client] Connecting" + (secureMode ? " using SSL..." : "..."));
			if (loginSocket != null && loginSocket.isConnected())
				throw new AlreadyConnectedException();

			if (secureMode) {
				loginSocket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(address.getAddress(), address.getPort());
			} else {
				loginSocket = new Socket();
				loginSocket.connect(this.address, this.timeout);
			}

			onLog("[Client] Connected to " + loginSocket.getRemoteSocketAddress());
		} catch (IOException ex) {
			ex.printStackTrace();
			onConnectionProblem();
		}

		// Einloggen
		try {
			onLog("[Client] Logging in...");
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(loginSocket.getOutputStream()));
			Datapackage loginPackage = new Datapackage("_INTERNAL_LOGIN_", id, group);
			loginPackage.sign(id, group);
			out.writeObject(loginPackage);
			out.flush();
			onLog("[Client] Logged in.");
			onReconnect();
		} catch (IOException ex) {
			onLogError("[Client] Login failed.");
		}
	}

	/**
	 * Starts a new thread listening for messages from the server. A message will only be processed if a handler for its identifier has been registered before using
	 * <code>registerMethod(String identifier, Executable executable)</code>
	 */
	protected void startListening() {

		// Wenn der ListeningThread lebt, nicht neu starten!
		if (listeningThread != null && listeningThread.isAlive()) return;

		listeningThread = new Thread(new Runnable() {

			@Override
			public void run() {
				// Staendig wiederholen
				while (true) {
					try {
						// Bei fehlerhafter Verbindung, diese reparieren
						if (loginSocket != null && !loginSocket.isConnected()) {
							while (!loginSocket.isConnected()) {
								repairConnection();
								if (loginSocket.isConnected()) break;

								Thread.sleep(5000);
								repairConnection();
							}
						}

						onConnectionGood();

						// Auf eingehende Nachricht warten und diese bei Eintreffen lesen
						ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(loginSocket.getInputStream()));
						Object raw = ois.readObject();

						// Nachricht auswerten
						if (raw instanceof Datapackage) {
							final Datapackage msg = (Datapackage) raw;

							for (final String current : idMethods.keySet()) {
								if (!msg.id().equalsIgnoreCase(current)) continue;
								onLog("[Client] Message received. Executing method for '" + msg.id() + "'...");
								new Thread(new Runnable() {
									@Override
									public void run() {
										idMethods.get(current).run(msg, loginSocket);
									}
								}).start();
							}
						}
					} catch (IOException | ClassNotFoundException | InterruptedException ex) {
						ex.printStackTrace();
						onConnectionProblem();
						onLogError("Server offline?");
						if ((++errorCount > 30) && autoKill) {
							onLogError("Server dauerhaft nicht erreichbar, beende.");
							System.exit(0);
						} else {
							repairConnection();
						}
					}
					// Bis hieher fehlerfrei? Dann errorCount auf Null setzen:
					errorCount = 0;
				} // while true
			}// run
		});
		// Thread starten
		listeningThread.start();
	}

	/**
	 * Sends a message to the server using a brand new socket and returns the server's response
	 * 
	 * @param message
	 *            The message to send to the server
	 * @param timeout
	 *            The timeout after a connection attempt will be given up
	 * @return The server's response. The identifier of this Datapackage should be "REPLY" by default, the rest is custom data.
	 */
	public Datapackage sendMessage(Datapackage message, int timeout) {
		try {
			Socket tempSocket;
			if (secureMode) {
				tempSocket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(address.getAddress(), address.getPort());
			} else {
				tempSocket = new Socket();
				tempSocket.connect(address, timeout);
			}

			ObjectOutputStream tempOOS = new ObjectOutputStream(new BufferedOutputStream(tempSocket.getOutputStream()));
			message.sign(id, group);
			tempOOS.writeObject(message);
			tempOOS.flush();

			ObjectInputStream tempOIS = new ObjectInputStream(new BufferedInputStream(tempSocket.getInputStream()));
			Object raw = tempOIS.readObject();

			tempOOS.close();
			tempOIS.close();
			tempSocket.close();

			if (raw instanceof Datapackage)
				return (Datapackage) raw;

		} catch (IOException | ClassNotFoundException ex) {
			onLogError("[Client] Error while sending message:");
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * Sends a message to the server using a brand new socket and returns the server's response
	 * 
	 * @param ID
	 *            The ID of the message, allowing the server to decide what to do with its content
	 * @param content
	 *            The content of the message
	 * @return The server's response. The identifier of this Datapackage should be "REPLY" by default, the rest is custom data.
	 */
	public Datapackage sendMessage(String ID, String... content) {
		return sendMessage(new Datapackage(ID, (Object[]) content));
	}

	/**
	 * Sends a message to the server using a brand new socket and returns the server's response
	 * 
	 * @param message
	 *            The message to send to the server
	 * @return The server's response. The identifier of this Datapackage should be "REPLY" by default, the rest is custom data.
	 */
	public Datapackage sendMessage(Datapackage message) {
		return sendMessage(message, this.timeout);
	}

	/**
	 * Registers a method that will be executed if a message containing <i>identifier</i> is received
	 * 
	 * @param identifier
	 *            The ID of the message to proccess
	 * @param executable
	 *            The method to be called when a message with <i>identifier</i> is received
	 */
	public void registerMethod(String identifier, Executable executable) {
		idMethods.put(identifier, executable);
	}

	/**
	 * Called on the listener's main thread when there is a problem with the connection. Overwrite this method when extending this class.
	 */
	public void onConnectionProblem() {
		// Overwrite this method when extending this class
	}

	/**
	 * Called on the listener's main thread when there is no problem with the connection and everything is fine. Overwrite this method when extending this class.
	 */
	public void onConnectionGood() {
		// Overwrite this method when extending this class
	}

	/**
	 * Called on the listener's main thread when the client logs in to the server. This happens on the first and every further login (e.g. after a re-established connection). Overwrite this method
	 * when extending this class.
	 */
	public void onReconnect() {
		// Overwrite this method when extending this class
	}

	/**
	 * By default, this method is called whenever an output is to be made. If this method is not overwritten, the output is passed to the system's default output stream (if output is not muted).<br>
	 * Error messages are passed to the <code>onLogError</code> event listener.<br>
	 * <b>Override this method to catch and process the message in a custom way.</b>
	 * 
	 * @param message
	 *            The content of the output to be made
	 */
	public void onLog(String message) {
		if (!muted)
			System.out.println(message);
	}

	/**
	 * By default, this method is called whenever an error output is to be made. If this method is not overwritten, the output is passed to the system's default error output stream (if output is not
	 * muted).<br>
	 * Non-error messages are passed to the <code>onLog</code> event listener.<br>
	 * <b>Override this method to catch and process the message in a custom way.</b>
	 * 
	 * @param message
	 *            The content of the error output to be made
	 */
	public void onLogError(String message) {
		if (!muted)
			System.err.println(message);
	}

}
