package com.partnerstudio.runtime;
import java.lang.reflect.*;
final class CustomerAccount {
 static String get(Object config,String method)throws Exception{return (String)config.getClass().getMethod(method).invoke(config);}
 static Object create(Object old,String username,String password,String branch)throws Exception{
  username=username.trim();branch=branch.trim();
  if(username.isEmpty()||password.trim().isEmpty()||branch.isEmpty())throw new IllegalArgumentException("Kullanıcı adı, şifre ve şube ID alanlarını doldurun.");
  Constructor<?> ctor=old.getClass().getConstructor(String.class,String.class,String.class,String.class,String.class,boolean.class,String.class,String.class);
  return ctor.newInstance(get(old,"getBaseUrl"),get(old,"getBasicAuth"),username,password,branch,false,get(old,"getContractJson"),get(old,"getSearchOrderAuth"));
 }
 static void invoke0(Object callback)throws Exception{Method m=callback.getClass().getMethod("invoke");m.setAccessible(true);m.invoke(callback);}
 static void invoke1(Object callback,Object value)throws Exception{Method m=callback.getClass().getMethod("invoke",Object.class);m.setAccessible(true);m.invoke(callback,value);}
 static String redact(String message,String api,String mt,String rx){
  if(message==null)return "İşlem tamamlanamadı.";
  for(String secret:new String[]{mt,rx})if(secret!=null&&!secret.isEmpty())message=message.replace(secret,"[gizli]");
  if(api!=null&&!api.isEmpty())try{java.net.URI u=new java.net.URI(api);message=message.replace(api,"[servis]");if(u.getHost()!=null)message=message.replace(u.getHost(),"[servis]");}catch(Exception ignored){}
  return message.replaceAll("(?i)https?://[^\\s\\\"'<>]+","[servis]");
 }
}
