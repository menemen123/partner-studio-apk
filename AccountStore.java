package com.partnerstudio.runtime;
import android.content.Context;
import java.lang.reflect.*;
final class AccountStore {
 private static Object store(Context c)throws Exception{return Class.forName("com.biletbagla.AndroidConfigStoreKt").getMethod("androidConfigStore",Context.class).invoke(null,c.getApplicationContext());}
 static Object load(Context c)throws Exception{Object value=Class.forName("com.biletbagla.ConfigStore").getMethod("load").invoke(store(c));return value!=null?value:Class.forName("com.biletbagla.AppConfig").getConstructor().newInstance();}
 static void save(Context c,Object value)throws Exception{Class.forName("com.biletbagla.ConfigStore").getMethod("save",Class.forName("com.biletbagla.AppConfig")).invoke(store(c),value);}
 static SalesClient.Config config(Context c)throws Exception{
  Object a=load(c);return new SalesClient.Config(CustomerAccount.get(a,"getBaseUrl"),CustomerAccount.get(a,"getBasicAuth"),CustomerAccount.get(a,"getSearchOrderAuth"),CustomerAccount.get(a,"getUsername"),CustomerAccount.get(a,"getPassword"),CustomerAccount.get(a,"getBranchId"),StudioConfig.get(c).getString("partner_code"));
 }
 public static final class Save {private final Context context;Save(Context c){context=c.getApplicationContext();}public Object invoke(Object value)throws Exception{save(context,value);return null;}}
}
