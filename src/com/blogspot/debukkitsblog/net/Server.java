package com.blogspot.debukkitsblog.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.IllegalBlockingModeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import javax.net.ssl.SSLServerSocketFactory;

/**
 * A very simple-to-use Server class for Java network applications<br>
 * originally created on March 9, 2016 in Horstmar, Germany
 * 
 * @author Leonard Bienbeck
 * @version 2.4.0
 */
public abstract class Server {

	protected HashMap<String, Executable> idMethods = new HashMap<String, Executable>();

	protected ServerSocket server;
	protected int port;
	protected ArrayList<RemoteClient> clients;
	protected ArrayList<RemoteClient> toBeDeleted;

	protected Thread listeningThread;

	protected boolean autoRegisterEveryClient;
	protected boolean secureMode;

	protected boolean muted;
	protected long pingInterval = 30000;

	protected static final String INTERNAL_LOGIN_ID = "_INTERNAL_LOGIN_";

	/**
	 * Constructs a simple server listening on the given port. Every client that
	 * connects to this server is registered and can receive broadcast and direct
	 * messages, the connection will be kept alive using a ping and ssl will not be
	 * used.
	 * 
	 * @param port
	 *            The port to listen on
	 */
	public Server(int port) {
		this(port, true, true, false);
	}

	/**
	 * Constructs a simple server with all possible configurations
	 * 
	 * @param port
	 *            The port to listen on
	 * @param autoRegisterEveryClient
	 *            Whether a client that connects should be registered to send it
	 *            broadcast and direct messages later
	 * @param keepConnectionAlive
	 *            Whether the connection should be kept alive using a ping package.
	 *            The transmission interval can be set using
	 *            <code>setPingInterval(int seconds)</code>.
	 * @param useSSL
	 *            Whether SSL should be used to establish a secure connection
	 */
	public Server(int port, boolean autoRegisterEveryClient, boolean keepConnectionAlive, boolean useSSL) {
		this.clients = new ArrayList<RemoteClient>();
		this.port = port;
		this.autoRegisterEveryClient = autoRegisterEveryClient;
		this.muted = false;

		if (secureMode = useSSL) {
			System.setProperty("javax.net.ssl.keyStore", "ssc.store");
			System.setProperty("javax.net.ssl.keyStorePassword", "SimpleServerClient");
		}
		if (autoRegisterEveryClient) {
			registerLoginMethod();
		}
		preStart();

		start();

		if (keepConnectionAlive) {
			startPingThread();
		}
	}

	/**
	 * Mutes the console output of this instance, stack traces will still be
	 * printed.<br>
	 * <b>Be careful:</b> This will not prevent processing of messages passed to the
	 * onLog and onLogError methods, if they were overwritten.
	 * 
	 * @param muted
	 *            true if there should be no console output
	 */
	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	/**
	 * Sets the interval in which ping packages should be sent to keep the
	 * connection alive. Default is 30 seconds.
	 * 
	 * @param seconds
	 *            The interval in which ping packages should be sent
	 */
	public void setPingInterval(int seconds) {
		this.pingInterval = seconds * 1000;
	}

	/**
	 * Starts the thread sending a dummy package every <i>pingInterval</i> seconds.
	 * Adjust the interval using <code>setPingInterval(int seconds)</code>.
	 */
	protected void startPingThread() {
		new Thread(new Runnable() {
			@Override
			public void run() {

				while (server != null) {
					try {
						Thread.sleep(pingInterval);
					} catch (InterruptedException e) {
					}
					broadcastMessage(new Datapackage("_INTERNAL_PING_", "OK"));
				}

			}
		}).start();
	}

	/**
	 * Starts the listening thread waiting for messages from clients
	 */
	protected void startListening() {
		if (listeningThread == null && server != null) {
			listeningThread = new Thread(new Runnable() {

				@Override
				public void run() {
					while (server != null) {

						try {
							onLog("[Server] Waiting for connection" + (secureMode ? " using SSL..." : "..."));
							@SuppressWarnings("resource")
							final Socket tempSocket = server.accept();//Potential resource leak: 'tempSocket' may not be closed


							ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(tempSocket.getInputStream()));
							Object raw = ois.readObject();

							if (raw instanceof Datapackage) {
								final Datapackage msg = (Datapackage) raw;
								onLog("[Server] Message received: " + msg);

								for (final String current : idMethods.keySet()) {
									if (msg.id().equalsIgnoreCase(current)) {
										onLog("[Server] Executing method for identifier '" + msg.id() + "'");
										new Thread(new Runnable() {
											@Override
											public void run() {
												// Run the method registered for the ID of this Datapackage
												idMethods.get(current).run(msg, tempSocket);
												// and close the temporary socket if it is not longer needed
												if (!msg.id().equals(INTERNAL_LOGIN_ID)) {
													try {
														tempSocket.close();
													} catch (Exception e) {
														e.printStackTrace();
													}
												}
											}
										}).start();
										break;
									}
								}

							}

						} catch (EOFException e) {
							e.printStackTrace();
						} catch (IllegalBlockingModeException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}

					}
				}

			});

			listeningThread.start();
		}
	}

	/**
	 * Sends a reply to client. This method should only be called from within the
	 * run-Method of an <code>Executable</code> implementation.
	 * 
	 * @param toSocket
	 *            The socket the message should be delivered to
	 * @param datapackageContent
	 *            The content of the message to be delivered. The ID of this
	 *            Datapackage will be "REPLY".
	 */
	public synchronized void sendReply(Socket toSocket, Object... datapackageContent) {
		sendMessage(new RemoteClient(UUID.randomUUID().toString(), toSocket),
				new Datapackage("REPLY", datapackageContent));
	}

	/**
	 * Sends a message to a client with specified id
	 * 
	 * @param remoteClientId
	 *            The id of the client it registered on login
	 * @param datapackageId
	 *            The id of message
	 * @param datapackageContent
	 *            The content of the message
	 */
	public synchronized void sendMessage(String remoteClientId, String datapackageId, Object... datapackageContent) {
		sendMessage(remoteClientId, new Datapackage(datapackageId, datapackageContent));
	}

	/**
	 * Sends a message to a client with specified id
	 * 
	 * @param remoteClientId
	 *            The id of the client it registered on login
	 * @param message
	 *            The message
	 */
	public synchronized void sendMessage(String remoteClientId, Datapackage message) {
		for (RemoteClient current : clients) {
			if (current.getId().equals(remoteClientId)) {
				sendMessage(current, message);
			}
		}
	}

	/**
	 * Sends a message to a client
	 * 
	 * @param remoteClient
	 *            The target client
	 * @param datapackageId
	 *            The id of message
	 * @param datapackageContent
	 *            The content of the message
	 */
	public synchronized void sendMessage(RemoteClient remoteClient, String datapackageId,
			Object... datapackageContent) {
		sendMessage(remoteClient, new Datapackage(datapackageId, datapackageContent));
	}

	/**
	 * Sends a message to a client
	 * 
	 * @param remoteClient
	 *            The target client
	 * @param message
	 *            The message
	 */
	public synchronized void sendMessage(RemoteClient remoteClient, Datapackage message) {
		try {
			// Nachricht senden
			if (!remoteClient.getSocket().isConnected()) {
				throw new Exception("Socket not connected.");
			}
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(remoteClient.getSocket().getOutputStream()));
			out.writeObject(message);
			out.flush();
		} catch (Exception e) {
			onLogError("[SendMessage] Fehler: " + e.getMessage());

			// Bei Fehler: Socket aus Liste loeschen
			if (toBeDeleted != null) {
				toBeDeleted.add(remoteClient);
			} else {
				clients.remove(remoteClient);
				onClientRemoved(remoteClient);
			}
		}
	}

	/**
	 * Use <code>sendMessage(RemoteClient remoteClient, Datapackage message)</code>
	 * instead. Only the order of the parameters has changed.
	 * 
	 * @param message
	 *            The message
	 * @param remoteClient
	 *            The client
	 */
	@Deprecated
	public synchronized void sendMessage(Datapackage message, RemoteClient remoteClient) {
		sendMessage(remoteClient, message);
	}

	/**
	 * Broadcasts a message to a group of clients
	 * 
	 * @param group
	 *            The group name the clients registered on their login
	 * @param message
	 *            The message
	 * @return The number of clients reached
	 */
	public synchronized int broadcastMessageToGroup(String group, Datapackage message) {
		toBeDeleted = new ArrayList<RemoteClient>();

		// Nachricht an alle Sockets senden
		int rxCounter = 0;
		for (RemoteClient current : clients) {
			if (current.getGroup().equals(group)) {
				sendMessage(current, message);
				rxCounter++;
			}
		}

		// Alle Sockets, die fehlerhaft waren, im Anschluss loeschen
		rxCounter -= toBeDeleted.size();
		for (RemoteClient current : toBeDeleted) {
			clients.remove(current);
			onClientRemoved(current);
		}

		toBeDeleted = null;

		return rxCounter;
	}

	/**
	 * Broadcasts a message to a group of clients
	 * 
	 * @param message
	 *            The message
	 * @return The number of clients reached
	 */
	public synchronized int broadcastMessage(Datapackage message) {
		toBeDeleted = new ArrayList<RemoteClient>();

		// Nachricht an alle Sockets senden
		int rxCounter = 0;
		for (RemoteClient current : clients) {
			sendMessage(current, message);
			rxCounter++;
		}

		// Alle Sockets, die fehlerhaft waren, im Anschluss loeschen
		rxCounter -= toBeDeleted.size();
		for (RemoteClient current : toBeDeleted) {
			clients.remove(current);
			onClientRemoved(current);
		}

		toBeDeleted = null;

		return rxCounter;
	}

	/**
	 * Registers a method that will be executed if a message containing
	 * <i>identifier</i> is received
	 * 
	 * @param identifier
	 *            The ID of the message to proccess
	 * @param executable
	 *            The method to be called when a message with <i>identifier</i> is
	 *            received
	 */
	public void registerMethod(String identifier, Executable executable) {
		if (identifier.equalsIgnoreCase(INTERNAL_LOGIN_ID) && autoRegisterEveryClient) {
			throw new IllegalArgumentException("Identifier may not be '" + INTERNAL_LOGIN_ID + "'. "
					+ "Since v1.0.1 the server automatically registers new clients. "
					+ "To react on new client registed, use the onClientRegisters() Listener by overwriting it.");
		}
		idMethods.put(identifier, executable);
	}

	/**
	 * Registers a login handler. This method is called only if the constructor has
	 * been applied to register clients.
	 */
	protected void registerLoginMethod() {
		idMethods.put(INTERNAL_LOGIN_ID, new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				if (msg.size() == 3) {
					registerClient((String) msg.get(1), (String) msg.get(2), socket);
				} else if (msg.size() == 2) {
					registerClient((String) msg.get(1), socket);
				} else {
					registerClient(UUID.randomUUID().toString(), socket);
				}
				onClientRegistered(msg, socket);
				onClientRegistered();
			}
		});
	}

	/**
	 * Registers a client to allow sending it direct and broadcast messages later
	 * 
	 * @param id
	 *            The client's id
	 * @param newClientSocket
	 *            The client's socket
	 */
	protected synchronized void registerClient(String id, Socket newClientSocket) {
		clients.add(new RemoteClient(id, newClientSocket));
	}

	/**
	 * Registers a client to allow sending it direct and broadcast messages later
	 * 
	 * @param id
	 *            The client's id
	 * @param group
	 *            The client's group name
	 * @param newClientSocket
	 *            The client's socket
	 */
	protected synchronized void registerClient(String id, String group, Socket newClientSocket) {
		clients.add(new RemoteClient(id, group, newClientSocket));
	}

	/**
	 * Starts the server. This method is automatically called after
	 * <code>preStart()</code> and starts the actual and the listening thread.
	 */
	protected void start() {
		server = null;
		try {

			if (secureMode) {
				server = ((SSLServerSocketFactory) SSLServerSocketFactory.getDefault()).createServerSocket(port);
			} else {
				server = new ServerSocket(port);
			}

		} catch (IOException e) {
			onLogError("Error opening ServerSocket");
			e.printStackTrace();
		}
		startListening();
	}

	/**
	 * Stops the server
	 */
	public void stop() {
		if (listeningThread.isAlive()) {
			listeningThread.interrupt();
		}

		if (server != null) {
			try {
				server.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Counts the number of clients registered
	 * 
	 * @return The number of clients registered
	 */
	public synchronized int getClientCount() {
		return clients.size();
	}

	/**
	 * Called just before the actual server starts. Register your handler methods in
	 * here using
	 * <code>registerMethod(String identifier, Executable executable)</code>!
	 */
	public abstract void preStart();

	/**
	 * Called on the listener's main thread when a new client registers
	 */
	public void onClientRegistered() {
		// Overwrite this method when extending this class
	}

	/**
	 * Called on the listener's main thread when a new client registers
	 * 
	 * @param msg
	 *            The message the client registered with
	 * @param socket
	 *            The socket the client registered with. Be careful with this! You
	 *            should not close this socket, because the server should have
	 *            stored it normally to reach this client later.
	 */
	public void onClientRegistered(Datapackage msg, Socket socket) {
		// Overwrite this method when extending this class
	}

	/**
	 * Use <code>onClientRemoved(RemoteClient remoteClient)</code> instead. Only the
	 * name of the method has changed.
	 * 
	 * @param socket
	 *            The socket of the client that was removed from the list of
	 *            reachable clients
	 */
	@Deprecated
	public void onSocketRemoved(Socket socket) {
		// Overwrite this method when extending this class
	}

	/**
	 * Called on the listener's main thread when a client is removed from the list.
	 * This normally happens if there was a problem with its connection. You should
	 * wait for the client to connect again.
	 * 
	 * @param remoteClient
	 *            The client that was removed from the list of reachable clients
	 */
	public void onClientRemoved(RemoteClient remoteClient) {
		// Overwrite this method when extending this class
	}

	/**
	 * By default, this method is called whenever an output is to be made. If this
	 * method is not overwritten, the output is passed to the system's default
	 * output stream (if output is not muted).<br>
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
	 * By default, this method is called whenever an error output is to be made. If
	 * this method is not overwritten, the output is passed to the system's default
	 * error output stream (if output is not muted).<br>
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

	/**
	 * A RemoteClient representating a client connected to this server storing an id
	 * for identification and a socket for communication.
	 */
	protected class RemoteClient {
		private String id;
		private String group;
		private Socket socket;

		/**
		 * Creates a RemoteClient representating a client connected to this server
		 * storing an id for identification and a socket for communication. The client
		 * will be member of the default group.
		 * 
		 * @param id
		 *            The clients id (to use for identification; choose a custom String)
		 * @param socket
		 *            The socket (to use for communication)
		 */
		public RemoteClient(String id, Socket socket) {
			this.id = id;
			this.group = "_DEFAULT_GROUP_";
			this.socket = socket;
		}

		/**
		 * Creates a RemoteClient representating a client connected to this server
		 * storing an id for identification and a socket for communication. The client
		 * can be set as a member of a group of clients to receive messages broadcasted
		 * to a group.
		 * 
		 * @param id
		 *            The clients id (to use for identification; choose a custom String)
		 * @param group
		 *            The group the client is member of
		 * @param socket
		 *            The socket (to use for communication)
		 */
		public RemoteClient(String id, String group, Socket socket) {
			this.id = id;
			this.group = group;
			this.socket = socket;
		}

		public String getId() {
			return id;
		}

		public String getGroup() {
			return group;
		}

		public Socket getSocket() {
			return socket;
		}
	}

}