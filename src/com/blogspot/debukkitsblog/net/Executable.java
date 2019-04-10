package com.blogspot.debukkitsblog.net;

import java.net.Socket;

/**
 * A functional interface implemented by <code>Client</code> and <code>Server</code> for handling
 * incoming Datapackages with specified identifiers.
 * 
 * @author Leonard Bienbeck
 * @version 2.4.1
 */
public interface Executable {

  /**
   * Implement this method using
   * <code>registerMethod(String identifier, Executable executable)</code> of a <code>Server</code>
   * or <code>Client</code> to handle incoming Datapackages. <b>Server only</b>: If you send a reply
   * to a client from an implementation of this method, use
   * <code>sendReply(Socket toSocket, Object... datapackageContent)</code>.
   * 
   * @param pack The Datapackage received
   * @param socket The Socket you received the Datapackage from
   */
  public abstract void run(Datapackage pack, Socket socket);

}
