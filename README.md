### SimpleServerClient ###
Offers very simple and easy-to-use Java classes for Client-Server-Client or just Server-Client applications doing all the work for connection setup, reconnection, timeout, keep-alive, etc. in the background.

**Code Quality**

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/3d5b115186f44ecab613ac3f2ca0015b)](https://www.codacy.com/app/DeBukkIt/SimpleServerClient?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=DeBukkIt/SimpleServerClient&amp;utm_campaign=Badge_Grade)

# How to use THE SERVER
```java
import java.net.Socket;

import com.blogspot.debukkitsblog.Util.Datapackage;
import com.blogspot.debukkitsblog.Util.Executable;
import com.blogspot.debukkitsblog.Util.Server;

public class MyServer extends Server {

	public MyServer(int port) {
		super(port);
	}

	@Override
	public void preStart() {
		registerMethod("Ping", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				sendMessage(socket, "REPLY", "Pong");
			}
		});
	}

}
```



Just make your own class, e. g. MyServer extending Server, simply use the original constructor and implement
the preStart method. In the preStart method just add
```java
  registerMethod("IAMANIDENTIFIER", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				  doSomethingWith(msg, socket);
			}
	});
```
for every identifier of an Datapackge the server received, you want to react to.

EXAMPLE: So if you register "Ping" and an Executable repsonding "Pong" to the client, just register
```java
  registerMethod("Ping", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				  sendMessage(socket, "IdentifierForTheReplyPackage", "Pong");
				  // or
				  sendMessage(msg.clientid(), "IdentifierForTheReplyPackage", "Pong");
			}
	});
```
and that's it.

For more identifiers to react on, just put those lines multiple times into your preStart(). Do not forget to send
a reply to the clients you got the Datapackge from, because it will wait until world ends for a reply.

EXAMPLE for a server broadcasting a chat-message to all connected clients:
```java
  registerMethod("Message", new Executable() {			
			@Override
			public void run(Datapackage msg, Socket socket) {
			  	System.out.println("[Message] New chat message arrived, delivering to all the clients...");
			  	broadcastMessage(msg); //The broadcast to all the receivers
			  	sendMessage(socket, "REPLY", String.valueOf(reveicerCount)); //The reply (NECESSARY! unless you want the client to block while waiting for this package)
			  	close(socket); //Close the connection to the socket you got the Datapackage from
			}
	});
```

	
# How to use THE CLIENT
```java
import java.net.Socket;

import com.blogspot.debukkitsblog.Util.Client;
import com.blogspot.debukkitsblog.Util.Datapackage;
import com.blogspot.debukkitsblog.Util.Executable;

public class MyClient extends Client {

	public MyClient(String id, String address, int port) {
		super(id, address, port);

		registerMethod("Message", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				System.out.println("Look! I got a new message from the server: " + msg.get(0));
			}
		});

		start();
	}

}
```



Just make your own class, e. g. MyClient extending Client, simply use the original constructor.
Whenever you are ready for the client to login, call start(). The client will connect to the server
depending on the constructor-parameters and register itself on the server. From now on it can
receive messages from the server and stay connected (and reconnects if necessary) until you call stop().


To react on an incoming message, just add
```java
		registerMethod("IAMANIDENTIFIER", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				doSomethingWith(msg, socket);		
			}
		});
```
somewhere, I suggest the constructor itself.


EXMAPLE for an incoming chat message from the server:
```java
		registerMethod("Message", new Executable() {
			@Override
			public void run(Datapackage msg, Socket socket) {
				System.out.println("Look! I got a new message from the server: " + msg.get(0));				
			}
		});
```

Different from the client, the server will not expect a reply by default. So dont always send him an Reply-Package, because he
needs an extra-identifier-method registered for that.


# Useful methods
AS SERVER:

  Broadcast messages using:
	- broadcastMessage(Datapackage)
	- broadcastMessage(String id, Objects...)
  
  Send messages to a specified client using:
	- sendMessage(Socket, ID, Objects...)
	- sendMessage(ClientID, ID, Objects...)
	
  Receive messages from the client using registerMethod-Executables
  
AS CLIENT:

  Send messages to the server using: sendMessage(different parameters)
  
  Receive replys to this message using its return value (that will be reply Datapackage)
  
  Receive messages from the server using registerMethod-Executables


# Event handlers
As the client: To react on a (re)connect or disconnect from/to the server,
just override the onConnectionProblem() or onConnectionGood() methods and fill them with your code.
onConnectionGood() is called on every (re)connect AND successful registration to the server,
onConnectionProblem() is called on every exception thrown inside the listener thread and every disconnect (responding very fast!)
