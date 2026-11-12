package com.partnerstudio.runtime;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import androidx.core.content.FileProvider;

public class BrandActivity extends Activity {
    private JSONObject config;private TextView status;private Button update;private JSONObject latest;
    private ExecutorService worker=Executors.newSingleThreadExecutor();private File pendingApk;private boolean checking;private long lastCheck;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);config=StudioConfig.get(this);int color;
        try{color=Color.parseColor(config.optString("color","#176b56"));}catch(Exception e){color=0xff176b56;}
        final int brand=color;getWindow().setStatusBarColor(brand);getWindow().setNavigationBarColor(Color.WHITE);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER_HORIZONTAL);page.setPadding(dp(28),dp(32),dp(28),dp(24));page.setBackgroundColor(0xfff5f7f9);
        page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(28),insets.getSystemWindowInsetTop()+dp(24),dp(28),insets.getSystemWindowInsetBottom()+dp(24));return insets;});
        ImageView logo=new ImageView(this);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try(InputStream in=getAssets().open("studio-logo.png")){logo.setImageBitmap(BitmapFactory.decodeStream(in));}catch(Exception e){logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(108),dp(108));lp.topMargin=dp(26);lp.bottomMargin=dp(24);page.addView(logo,lp);
        TextView title=new TextView(this);title.setText(config.optString("name","Bilet"));title.setTextSize(28);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(0xff142833);title.setGravity(Gravity.CENTER);page.addView(title);
        TextView sub=new TextView(this);sub.setText("Yolculuğun buradan başlıyor.");sub.setTextSize(16);sub.setTextColor(0xff647482);sub.setPadding(0,dp(12),0,dp(30));page.addView(sub);
        Button book=new Button(this);book.setText("Açık bilet bağlama");book.setAllCaps(false);book.setTextColor(Color.WHITE);book.setBackgroundTintList(android.content.res.ColorStateList.valueOf(brand));page.addView(book,new LinearLayout.LayoutParams(-1,dp(56)));
        book.setOnClickListener(v->{try {Intent i=new Intent();i.setClassName(getPackageName(),"com.biletbagla.MainActivity");startActivity(i);}catch(Exception e){new AlertDialog.Builder(this).setMessage("Bilet ekranı açılamadı.").setPositiveButton("Tamam",null).show();}});
        Button sell=new Button(this);sell.setText("Bilet satışı");sell.setAllCaps(false);sell.setTextColor(Color.WHITE);sell.setBackgroundTintList(android.content.res.ColorStateList.valueOf(brand));LinearLayout.LayoutParams saleLp=new LinearLayout.LayoutParams(-1,dp(56));saleLp.topMargin=dp(12);page.addView(sell,saleLp);sell.setOnClickListener(v->startActivity(new Intent(this,SalesActivity.class)));
        Button account=new Button(this);account.setText("Hesap bilgileri");account.setAllCaps(false);page.addView(account,new LinearLayout.LayoutParams(-1,dp(52)));account.setOnClickListener(v->CustomerSettings.openAccount(this));
        update=new Button(this);update.setAllCaps(false);update.setText("Güncellemeleri kontrol et");LinearLayout.LayoutParams u=new LinearLayout.LayoutParams(-1,dp(54));u.topMargin=dp(12);page.addView(update,u);
        update.setOnClickListener(v->{if(latest!=null)offerUpdate();else checkUpdates();});
        status=new TextView(this);status.setTextSize(14);status.setTextColor(0xff647482);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(20),0,dp(20));page.addView(status);
        JSONObject firebase=config.optJSONObject("firebase");
        if((!config.optString("firebase_project").isEmpty() || (firebase!=null && !firebase.optString("app_id").isEmpty())) && Build.VERSION.SDK_INT>=33){
            Button notifications=new Button(this);notifications.setText("Güncelleme bildirimlerini aç");notifications.setAllCaps(false);page.addView(notifications);notifications.setOnClickListener(v->requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},31));
        }
        TextView version=new TextView(this);version.setText("Sürüm 1.0."+config.optInt("version_code"));version.setTextColor(0xff8995a0);page.addView(version);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);checkUpdates();
    }
    private void checkUpdates(){
        if(checking)return;checking=true;lastCheck=android.os.SystemClock.elapsedRealtime();update.setEnabled(false);status.setText("Yeni sürüm kontrol ediliyor…");
        worker.execute(()->{
            String message;JSONObject result=null;
            try {
                JSONObject release=ReleaseClient.read(this);
                int installed=getPackageManager().getPackageInfo(getPackageName(),0).versionCode;
                if(release!=null&&release.getLong("version_code")>installed){result=release;message="Yeni sürüm hazır: "+release.optString("version_name");}
                else message="Uygulaman güncel.";
            }catch(Exception e){message="Güncelleme kontrol edilemedi. İnternet bağlantını kontrol edip tekrar deneyebilirsin.";}
            final String text=message;final JSONObject found=result;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;checking=false;latest=found;status.setText(text);update.setEnabled(true);update.setText(found==null?"Tekrar kontrol et":"Yeni sürümü yükle");if(found!=null)offerUpdate();});
        });
    }
    private void offerUpdate(){if(latest==null)return;new AlertDialog.Builder(this).setTitle("Güncelleme hazır").setMessage("Yeni sürümü indirip yüklediğinde firma değişiklikleri uygulanacak.").setNegativeButton("Daha sonra",null).setPositiveButton("İndir ve yükle",(d,w)->download()).show();}
    private void download(){
        final JSONObject release=latest;if(release==null)return;update.setEnabled(false);status.setText("Güncelleme indiriliyor…");
        worker.execute(()->{
            File file=new File(getCacheDir(),"studio-update.apk");
            try{
                HttpURLConnection c=ReleaseClient.download(this,release.getString("download_url"));
                MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
                try{
                    if(c.getResponseCode()!=200)throw new IOException("İndirme başarısız");
                    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){total+=n;if(total>150*1024*1024)throw new IOException("APK çok büyük");out.write(b,0,n);digest.update(b,0,n);}}
                }finally{c.disconnect();}
                StringBuilder sha=new StringBuilder();for(byte b:digest.digest())sha.append(String.format("%02x",b&255));
                if(!sha.toString().equals(release.getString("sha256")))throw new IOException("APK bütünlüğü doğrulanamadı");
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;pendingApk=file;update.setEnabled(true);installDownloaded();});
            }catch(Exception e){file.delete();runOnUiThread(()->{if(isFinishing()||isDestroyed())return;update.setEnabled(true);status.setText("İndirme tamamlanamadı. Tekrar deneyebilirsin.");});}
        });
    }
    private void installDownloaded(){
        if(pendingApk==null)return;
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("Kurulum izni").setMessage("Android ayarlarında bu uygulama için bilinmeyen uygulama yükleme iznini aç. Ardından buraya dön.").setPositiveButton("Ayarları aç",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())))).setNegativeButton("İptal",null).show();return;
        }
        try{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".studio-files",pendingApk);Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);pendingApk=null;}
        catch(Exception e){status.setText("Android yükleyicisi açılamadı.");}
    }
    @Override public void onResume(){super.onResume();if(pendingApk!=null && getPackageManager().canRequestPackageInstalls())installDownloaded();else if(status!=null&&!checking&&android.os.SystemClock.elapsedRealtime()-lastCheck>60000)checkUpdates();}
    @Override public void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
