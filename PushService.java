package com.partnerstudio.runtime;
import android.app.*;
import android.content.*;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;
import org.json.JSONObject;
public class PushService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage message) {
        JSONObject c=StudioConfig.get(this);Map<String,String> data=message.getData();
        if(!c.optString("company_id").equals(data.get("company_id")))return;
        try {if(Integer.parseInt(data.get("version_code"))<=getPackageManager().getPackageInfo(getPackageName(),0).versionCode)return;}catch(Exception e){return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("studio_updates","Uygulama güncellemeleri",NotificationManager.IMPORTANCE_DEFAULT));
        Intent i=new Intent(this,BrandActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,10,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"studio_updates").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(c.optString("name")+" güncellemesi").setContentText("Yeni sürüm hazır. Güncellemek için dokun.").setAutoCancel(true).setContentIntent(pi).build();
        nm.notify(71,n);
    }
    @Override public void onNewToken(String token) {
        JSONObject c=StudioConfig.get(this);
        if(c.optJSONObject("firebase")!=null)try {com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("studio_"+c.getString("company_id"));}catch(Exception ignored){}
    }
}
