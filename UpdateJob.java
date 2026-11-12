package com.partnerstudio.runtime;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
public class UpdateJob extends JobService {
 private volatile boolean stopped;private Thread worker;
 static void schedule(Context context){
  if(StudioConfig.get(context).optString("firebase_project").isEmpty())return;
  JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
  if(scheduler.getPendingJob(63871)!=null)return;
  JobInfo info=new JobInfo.Builder(63871,new ComponentName(context,UpdateJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(6*60*60*1000L).setPersisted(true).build();scheduler.schedule(info);
 }
 @Override public boolean onStartJob(JobParameters params){stopped=false;worker=new Thread(()->{try{
  JSONObject release=ReleaseClient.read(this);if(release!=null&&!stopped){long version=release.getLong("version_code"),installed=getPackageManager().getPackageInfo(getPackageName(),0).versionCode;android.content.SharedPreferences prefs=getSharedPreferences("studio-notifications",0);
   boolean allowed=Build.VERSION.SDK_INT<33||checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED;
   if(version>installed&&version>prefs.getLong("notified_version",0)&&allowed){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("studio_updates","Uygulama güncellemeleri",NotificationManager.IMPORTANCE_DEFAULT));Intent i=new Intent(this,BrandActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(this,71,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification n=new Notification.Builder(this,"studio_updates").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(StudioConfig.get(this).optString("name")+" güncellemesi").setContentText("Yeni sürüm hazır. İncelemek için dokun.").setContentIntent(pi).setAutoCancel(true).build();if(!stopped){nm.notify(71,n);prefs.edit().putLong("notified_version",version).apply();}}
  }
 }catch(Exception ignored){}finally{if(!stopped)jobFinished(params,false);}},"studio-update-check");worker.start();return true;}
 @Override public boolean onStopJob(JobParameters params){stopped=true;if(worker!=null)worker.interrupt();return true;}
}
