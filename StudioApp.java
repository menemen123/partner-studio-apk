package com.partnerstudio.runtime;
import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
public class StudioApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CustomerSettings.install(this);
        UpdateJob.schedule(this);
        JSONObject c=StudioConfig.get(this), f=c.optJSONObject("firebase");
        if(f==null || f.optString("app_id").isEmpty())return;
        try {
            FirebaseOptions options=new FirebaseOptions.Builder().setApiKey(f.getString("api_key")).setApplicationId(f.getString("app_id")).setGcmSenderId(f.getString("sender_id")).setProjectId(f.getString("project_id")).build();
            if(FirebaseApp.getApps(this).isEmpty())FirebaseApp.initializeApp(this,options);
            FirebaseMessaging.getInstance().subscribeToTopic("studio_"+c.getString("company_id"));
        }catch(Exception e){android.util.Log.w("PartnerStudio","Bildirim kaydı başarısız",e);}
    }
}
