package com.partnerstudio.runtime;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
final class ReleaseClient {
 static JSONObject read(Context context)throws Exception{
  JSONObject c=StudioConfig.get(context);String project=c.optString("firebase_project"),address;
  boolean cloud=!project.isEmpty();
  if(cloud){if(!project.matches("[a-z0-9-]+"))throw new IOException("Proje kimliği geçersiz");address="https://firestore.googleapis.com/v1/projects/"+project+"/databases/(default)/documents/releases/"+c.getString("company_id");}
  else{String s=c.optString("server_url");if(!s.startsWith("https://"))throw new IOException("Güncelleme adresi yok");address=s+"/v1/apps/"+c.getString("company_id")+"/latest";}
  HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
  try{
   int code=connection.getResponseCode();if(code==204||code==404)return null;if(code!=200)throw new IOException("Sürüm kontrolü: "+code);
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>32768)throw new IOException("Yanıt çok büyük");out.write(b,0,n);}}
   JSONObject result=new JSONObject(out.toString("UTF-8"));if(cloud){JSONObject fields=result.getJSONObject("fields"),r=new JSONObject();for(String key:new String[]{"company_id","package_id","version_name","sha256","download_url"})r.put(key,fields.getJSONObject(key).getString("stringValue"));r.put("version_code",Long.parseLong(fields.getJSONObject("version_code").getString("integerValue")));result=r;}
   if(!context.getPackageName().equals(result.optString("package_id"))||!c.getString("company_id").equals(result.optString("company_id"))||!result.optString("sha256").matches("[a-f0-9]{64}"))throw new IOException("Firma/sürüm bilgisi eşleşmedi");
   if(cloud){String expected="https://github.com/"+c.getString("github_repo")+"/releases/download/"+c.getString("company_id")+"-v"+result.getLong("version_code")+"/"+context.getPackageName()+"-"+result.getLong("version_code")+".apk";if(!expected.equals(result.getString("download_url")))throw new IOException("APK adresi eşleşmedi");}
   return result;
  }finally{connection.disconnect();}
 }
 static HttpURLConnection download(Context context,String address)throws Exception{
  JSONObject c=StudioConfig.get(context);boolean cloud=!c.optString("firebase_project").isEmpty();URL origin=cloud?null:new URL(c.getString("server_url"));
  for(int i=0;i<6;i++){
   URL u=new URL(address);String h=u.getHost();
   boolean valid=cloud?(h.equals("github.com")||h.equals("githubusercontent.com")||h.endsWith(".githubusercontent.com")):(h.equals(origin.getHost())&&u.getPort()==origin.getPort());
   if(!u.getProtocol().equals("https")||u.getUserInfo()!=null||!valid)throw new IOException("Güvenilmeyen indirme adresi");
   HttpURLConnection connection=(HttpURLConnection)u.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);int status=connection.getResponseCode();
   if(cloud&&status>=300&&status<400){String next=connection.getHeaderField("Location");connection.disconnect();if(next==null)throw new IOException("Yönlendirme eksik");address=new URL(u,next).toString();continue;}
   if(status!=200){connection.disconnect();throw new IOException("APK indirilemedi: "+status);}return connection;
  }throw new IOException("Çok fazla yönlendirme");
 }
}
