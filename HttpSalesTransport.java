package com.partnerstudio.runtime;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
/** One streamed POST per call, same-origin cookies, no redirects or application retries. */
final class HttpSalesTransport implements SalesClient.Wire {
 private final String base;private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
 HttpSalesTransport(String base){this.base=base;}
 @Override public JSONObject request(String path,JSONObject body,String auth)throws Exception{
  if(!path.matches("(?:client/getsession|membership/userlogin|web/getstations|web/getjourneys|web/getjourneyseats|payment/createorder|Payment/PurchasePendingPassengers)"))throw new IOException("İşlem desteklenmiyor.");
  URL url=new URL(base+path);URI uri=url.toURI();HttpURLConnection c=(HttpURLConnection)url.openConnection();
  c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setUseCaches(false);
  c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Language","tr-TR");c.setRequestProperty("Authorization","Basic "+auth);c.setRequestProperty("User-Agent","PartnerStudio/3");
  for(Map.Entry<String,List<String>> e:cookies.get(uri,Collections.emptyMap()).entrySet())if(!e.getValue().isEmpty())c.setRequestProperty(e.getKey(),String.join("; ",e.getValue()));
  byte[] bytes=body.toString().getBytes("UTF-8");c.setFixedLengthStreamingMode(bytes.length);
  try{
   try(OutputStream out=c.getOutputStream()){out.write(bytes);}
   int status=c.getResponseCode();cookies.put(uri,c.getHeaderFields());
   if(status<200||status>=300)throw new IOException(status==401||status==403?"Oturum veya işlem yetkisi reddedildi. Hesap bilgilerinizi kontrol edin.":"Servise ulaşılamadı (HTTP "+status+").");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("Servis yanıtı çok büyük.");out.write(buffer,0,n);}}
   return new JSONObject(out.toString("UTF-8"));
  }finally{c.disconnect();}
 }
}
