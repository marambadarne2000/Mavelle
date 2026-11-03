package com.maramigo.accounts;

import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** The encryption key is supplied outside the database and never exposed by the API. @author Maram Abbas */
@Component
public class SecretVault {
 private final byte[] key;
 public SecretVault(@Value("${maramigo.account-encryption-key:}") String encoded){key=encoded.isBlank()?null:Base64.getDecoder().decode(encoded);if(key!=null&&key.length!=32)throw new IllegalArgumentException("Account encryption requires a 32-byte key.");}
 public boolean available(){return key!=null;}
 public String encrypt(String value){return transform(value,true);}
 public String decrypt(String value){return transform(value,false);}
 private String transform(String value,boolean encrypt){
  if(key==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Account security key is not configured.");
  try{byte[] nonce=new byte[12];byte[] data;if(encrypt){new SecureRandom().nextBytes(nonce);data=value.getBytes(StandardCharsets.UTF_8);}else{byte[] all=Base64.getDecoder().decode(value);nonce=java.util.Arrays.copyOfRange(all,0,12);data=java.util.Arrays.copyOfRange(all,12,all.length);}
   var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD("Maramigo-MFA-v1".getBytes(StandardCharsets.US_ASCII));byte[] result=cipher.doFinal(data);
   if(!encrypt)return new String(result,StandardCharsets.UTF_8);byte[] all=new byte[nonce.length+result.length];System.arraycopy(nonce,0,all,0,nonce.length);System.arraycopy(result,0,all,nonce.length,result.length);return Base64.getEncoder().encodeToString(all);
  }catch(Exception failure){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Account security key could not be used.");}
 }
}
