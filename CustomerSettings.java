package com.partnerstudio.runtime;
import android.app.*;
import android.os.*;
import android.content.Intent;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
public final class CustomerSettings {
 private static WeakReference<Activity> current=new WeakReference<>(null);private static Application app;
 private static boolean pending;private static Session active;
 static final class Session {final Object config,save;Session(Object c,Object s){config=c;save=s;}}
 static void install(Application application){
  app=application;
  application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   public void onActivityResumed(Activity a){if(a.getClass().getName().equals("com.biletbagla.MainActivity"))current=new WeakReference<>(a);}
   public void onActivityDestroyed(Activity a){if(current.get()==a)current.clear();}
   public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
  });
 }
 public static synchronized void open(Object config,Object back,Object save){
  if(pending||active!=null)return;pending=true;
  new Handler(Looper.getMainLooper()).post(()->{
   Activity a=current.get();
   try{
    if(a==null||a.isFinishing()||a.isDestroyed())throw new IllegalStateException();
    active=new Session(config,save);CustomerAccount.invoke0(back);a.startActivity(new Intent(a,CustomerSettingsActivity.class));
   }catch(Exception e){clear();if(app!=null)Toast.makeText(app,"Ayarlar açılamadı. Uygulamayı yeniden açıp deneyin.",Toast.LENGTH_LONG).show();}
  });
 }
 public static synchronized void openAccount(Activity host){
  if(pending||active!=null)return;
  try{active=new Session(AccountStore.load(host),new AccountStore.Save(host));pending=true;host.startActivity(new Intent(host,CustomerSettingsActivity.class));}
  catch(Exception e){clear();Toast.makeText(host,"Hesap ayarları açılamadı.",Toast.LENGTH_LONG).show();}
 }
 static synchronized Session session(){return active;}
 static synchronized void clear(){active=null;pending=false;}
 public static Object unconfiguredRepository(){
  try{
   Class<?> type=Class.forName("com.biletbagla.BookingRepository");
   return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
    if(method.getDeclaringClass()==Object.class){if(method.getName().equals("toString"))return "UnconfiguredRepository";if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return args!=null&&args.length==1&&proxy==args[0];}
    throw new IllegalStateException("Önce ayarlardan kullanıcı adı, şifre ve şube ID bilgilerinizi kaydedin.");
   });
  }catch(Exception e){throw new IllegalStateException("Hesap ayarları başlatılamadı.");}
 }
 public static String safeMessage(String message){
  String api="",mt="",rx="";
  try{Object config=Class.forName("com.biletbagla.AppConfig").getConstructor().newInstance();api=CustomerAccount.get(config,"getBaseUrl");mt=CustomerAccount.get(config,"getBasicAuth");rx=CustomerAccount.get(config,"getSearchOrderAuth");}catch(Exception ignored){}
  return CustomerAccount.redact(message,api,mt,rx);
 }
}
