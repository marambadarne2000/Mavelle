package com.maramigo.accounts;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Locale;

/** RFC 6238 SHA-1, 30-second steps and six decimal digits. @author Maram Abbas */
public final class Totp {
 private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
 private Totp(){}
 public static String secret(){byte[] bytes=new byte[20];new SecureRandom().nextBytes(bytes);return encode(bytes);}
 static String encode(byte[] bytes){StringBuilder out=new StringBuilder();int bits=0,value=0;for(byte b:bytes){value=(value<<8)|(b&255);bits+=8;while(bits>=5){bits-=5;out.append(ALPHABET.charAt((value>>>bits)&31));}}if(bits>0)out.append(ALPHABET.charAt((value<<(5-bits))&31));return out.toString();}
 static byte[] decode(String text){var out=new java.io.ByteArrayOutputStream();int bits=0,value=0;for(char c:text.toUpperCase(Locale.ROOT).toCharArray()){int index=ALPHABET.indexOf(c);if(index<0)throw new IllegalArgumentException("Invalid authenticator key");value=(value<<5)|index;bits+=5;if(bits>=8){bits-=8;out.write((value>>>bits)&255);}}return out.toByteArray();}
 public static String code(String secret,long step){try{var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(decode(secret),"HmacSHA1"));byte[] h=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());int i=h[h.length-1]&15;int n=((h[i]&127)<<24)|((h[i+1]&255)<<16)|((h[i+2]&255)<<8)|(h[i+3]&255);return String.format(Locale.ROOT,"%06d",n%1000000);}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 public static long match(String secret,String code,long now,long last){if(code==null||!code.matches("[0-9]{6}"))return -1;for(long step=now/30-1;step<=now/30+1;step++)if(step>last&&MessageDigest.isEqual(code(secret,step).getBytes(java.nio.charset.StandardCharsets.US_ASCII),code.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))return step;return -1;}
}
