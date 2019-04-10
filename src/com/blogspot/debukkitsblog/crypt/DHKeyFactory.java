package com.blogspot.debukkitsblog.crypt;

import java.math.BigInteger;
import java.util.Random;

public class DHKeyFactory {

  private BigInteger p, g, s, M, R, K;

  public DHKeyFactory() {
    System.out.println("[DHKey] Generating secure key...");
    p = new BigInteger(2048, 12, new Random());
    g = new BigInteger(String.valueOf(4 - new Random().nextInt(3)));
    do {
      s = new BigInteger(160, new Random());
    } while (s.compareTo(new BigInteger("2")) == -1
        || s.compareTo(p.subtract(new BigInteger("1"))) == 0);
  }

  public DHKeyFactory(BigInteger p, BigInteger g) {
    System.out.println("[DHKey] Generating secure key...");
    this.p = p;
    this.g = g;
    do {
      s = new BigInteger(160, new Random());
    } while (s.compareTo(new BigInteger("2")) == -1
        || s.compareTo(p.subtract(new BigInteger("1"))) == 0);
  }

  public void overwriteSecret(BigInteger s) {
    this.s = s;
  }

  public BigInteger getPrime() {
    return p;
  }

  public BigInteger getGenerator() {
    return g;
  }

  public BigInteger getPublicKey() {
    if (M == null) {
      M = g.modPow(s, p);
    }
    return M;
  }

  public void setRemotePublicKey(BigInteger r) {
    this.R = r;
  }

  public BigInteger getFinalKey() {
    if (K == null) {
      K = R.modPow(s, p);
    }

    return K;
  }

}
