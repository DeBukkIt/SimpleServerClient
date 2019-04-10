package com.blogspot.debukkitsblog.crypt;

import java.io.Serializable;

/**
 * A CryptedObject stores the encrypted data generated using the <code>Crypter</code> utility
 * 
 * @author Leonard Bienbeck
 * @version 2.3.0
 */
public class CryptedObject implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -5575861158027167628L;

  private byte[] bytes;

  /**
   * Constructs a CryptedObject by taking an array of bytes and storing it
   * 
   * @param bytes The array of bytes to be stored
   */
  public CryptedObject(byte[] bytes) {
    this.bytes = bytes;
  }

  /**
   * Returns the bytes stored in the CryptedObject
   * 
   * @return The bytes stored in the CryptedObject
   */
  public byte[] getBytes() {
    return bytes;
  }

}
