package com.partnerstudio.runtime;
import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
/** Synchronous, encrypted operation marker. It never stores a passenger body or session token. */
final class SaleVault implements SaleFlow.PendingStore {
 private final android.content.SharedPreferences prefs;private final SecretKey key;
 SaleVault(Context context)throws Exception{
  prefs=context.getSharedPreferences("sales-pending-v1",0);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="partner-studio-sales-v1";
  synchronized(SaleVault.class){if(!ks.containsAlias(alias)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}}
  key=(SecretKey)ks.getKey(alias,null);
 }
 public synchronized String read()throws Exception{
  String value=prefs.getString("operation",null);if(value==null)return null;byte[] bytes=Base64.decode(value,Base64.NO_WRAP);if(bytes.length<29)throw new GeneralSecurityException("İşlem kaydı okunamadı.");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,bytes,0,12));cipher.updateAAD("sale-operation-v1".getBytes("UTF-8"));return new String(cipher.doFinal(bytes,12,bytes.length-12),"UTF-8");
 }
 public synchronized void write(String value)throws Exception{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD("sale-operation-v1".getBytes("UTF-8"));byte[] encrypted=cipher.doFinal(value.getBytes("UTF-8")),iv=cipher.getIV(),all=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(encrypted,0,all,iv.length,encrypted.length);
  if(!prefs.edit().putString("operation",Base64.encodeToString(all,Base64.NO_WRAP)).commit())throw new GeneralSecurityException("İşlem kaydı saklanamadı; satış gönderilmedi.");
 }
 public synchronized void clear()throws Exception{if(!prefs.edit().remove("operation").commit())throw new GeneralSecurityException("İşlem kaydı silinemedi.");}
}
