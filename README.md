### SimpleServerClient ###

A very lightweight and easy-to-use library for client-server-client or simply server-client applications in Java doing all the work for you: connection setup, reconnection, timeout, keep-alive etc. in the background. Simply tell the application what to send and receive, and the rest will happen by itself. That's almost magic!

**Code Quality and Build Status**

[![Build Status](https://travis-ci.org/DeBukkIt/SimpleServerClient.svg?branch=master)](https://travis-ci.org/DeBukkIt/SimpleServerClient)

# How to use THE SERVER
```java
import java.net.Socket;

public class MyServer extends Server {

	public MyServer(int port) {
		super(port);
	}

	@Override
	public void preStart() {
		registerMethod("SOME_MESSAGE", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				doSomethingWith(msg);
				sendReply(socket, "Hey, thanks for your message. Greetings!")
			}
		});
	}

}
```

Just make your own class, e. g. MyServer extending Server, simply use the original constructor and implement
the preStart() method. In the preStart method just add
```java
  registerMethod("IDENTIFIER", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				  doSomethingWith(msg, socket);
				  sendReply(socket, "Some Reply");
			}
	});
```
for every identifier of an Datapackge the server received, you want to react to.

EXAMPLE: So if you register "Ping" and an Executable responding "Pong" to the client, just register
```java
  registerMethod("PING", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				  sendReply(socket, "Pong");
			}
	});
```
and that's it.

For more identifiers to react on, just put those lines multiple times into your preStart(). Do not forget to send
a reply to the clients you got the Datapackge from, because they will wait until the world ends for a response from you.

EXAMPLE for a server broadcasting a chat-message to all connected clients:
```java
  registerMethod("Message", new Executable() {			
		@Override
		public void run(Datapackage msg, Socket socket) {
			System.out.println("[Message] New chat message arrived, delivering to all the clients...");
			broadcastMessage(msg); //The broadcast to all the receivers
			sendReply(socket, String.valueOf(reveicerCount)); //The reply (NECESSARY! unless you want the client to block while waiting for this package)
		}
	});
```

	
# How to use THE CLIENT
```java
import java.net.Socket;

public class MyClient extends Client {

	public MyClient(String id, String address, int port) {
		super(id, address, port);

		registerMethod("SOME_MESSAGE", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				System.out.println("Look! I got a new message from the server: " + msg.get(1));
			}
		});

		start(); // Do not forget to start the client!
	}

}
```



Just make your own class, e. g. MyClient extending Client, simply use the original constructor. Whenever you are ready for the client to login, call start(). The client will connect to the server depending on the constructor-parameters and register itself on the server. From now on it can receive messages from the server and stay connected (and reconnects if necessary) until you call stop().


To react on an incoming message, just add
```java
		registerMethod("IDENTIFIER", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				doSomethingWith(msg, socket);		
			}
		});
```
somewhere, I suggest the constructor itself, for every message ID you want to handle.


EXMAPLE for an incoming chat message from the server:
```java
		registerMethod("PING", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				System.out.println("Look! I got a new message from the server: " + msg.get(1));	
				// msg.get(1); should now return "Pong" in our example.
			}
		});
```

Different from the client, the server will not expect a reply by default. So dont always send him an Reply-Package, because he
needs an extra-identifier-method registered for that.


# Useful methods
AS SERVER:
 - Broadcast messages using: broadcastMessage(...)
 - Use IDs and group names to register your clients to the server. This allows you to decide what clients or groups of clients may receive a particular broadcast.
 - Send messages to a specified client using: sendMessage(...)
 - Receive messages from the client using registerMethod-Executables
  
AS CLIENT:
 - Send messages to the server using: sendMessage(...)
 - Receive replys to this message using its return value (that will be reply Datapackage)
 - Receive messages from the server using registerMethod-Executables at any time

# Event handlers
There a some event handlers (e.g. onConnectionGood(), onConnectionProblem(), onClientRegistered(...), etc.) you can overwrite to handle these events.

# Custom message processing
The client and server classes both provide the onLog and onLogError event handlers, which can be overridden in the implementation if the info messages and error messages should not (only) be output in the console.
