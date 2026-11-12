package com.partnerstudio.runtime;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.InputFilter;
import android.view.Gravity;
import android.widget.*;
public class CustomerSettingsActivity extends Activity {
 private EditText username,password,branch;private TextView error;private CustomerSettings.Session session;
 private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density);}
 private EditText field(LinearLayout page,String label,String value,boolean secret,int limit){
  TextView title=new TextView(this);title.setText(label);title.setTextColor(0xff264439);title.setTextSize(14);title.setPadding(0,dp(18),0,dp(8));page.addView(title);
  EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(16);input.setTextColor(0xff142f28);input.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});input.setText(value);page.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));return input;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);session=CustomerSettings.session();
  if(session==null){Toast.makeText(this,"Ayarlar ekranını yeniden açın.",Toast.LENGTH_LONG).show();finish();return;}
  LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(32),dp(24),dp(24));page.setBackgroundColor(0xfff5f8f4);
  page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(24),i.getSystemWindowInsetTop()+dp(20),dp(24),i.getSystemWindowInsetBottom()+dp(24));return i;});
  TextView heading=new TextView(this);heading.setText("Hesap bilgileri");heading.setTextSize(28);heading.setTextColor(0xff183b2b);heading.setTypeface(null,Typeface.BOLD);page.addView(heading);
  TextView intro=new TextView(this);intro.setText("Kullanıcı adı, şifre ve şube bilgilerinizi kaydedin.");intro.setTextColor(0xff718074);intro.setTextSize(15);intro.setPadding(0,dp(12),0,dp(12));page.addView(intro);
  try{
   username=field(page,"Kullanıcı adı",CustomerAccount.get(session.config,"getUsername"),false,256);
   password=field(page,"Şifre",CustomerAccount.get(session.config,"getPassword"),true,1024);
   branch=field(page,"Şube ID",CustomerAccount.get(session.config,"getBranchId"),false,128);
  }catch(Exception e){CustomerSettings.clear();finish();return;}
  error=new TextView(this);error.setTextColor(0xffad342b);error.setTextSize(14);error.setPadding(0,dp(14),0,dp(14));page.addView(error);
  Button save=new Button(this);save.setText("Bilgileri kaydet");save.setAllCaps(false);save.setTextColor(Color.WHITE);save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff215b46));page.addView(save,new LinearLayout.LayoutParams(-1,dp(56)));
  save.setOnClickListener(v->{
   if(username.getText().toString().trim().isEmpty()||password.getText().toString().trim().isEmpty()||branch.getText().toString().trim().isEmpty()){error.setText("Kullanıcı adı, şifre ve şube ID alanlarını doldurun.");return;}
   save.setEnabled(false);
   try{Object updated=CustomerAccount.create(session.config,username.getText().toString(),password.getText().toString(),branch.getText().toString());CustomerAccount.invoke1(session.save,updated);CustomerSettings.clear();Toast.makeText(this,"Bilgiler kaydedildi.",Toast.LENGTH_SHORT).show();finish();}
   catch(Exception e){error.setText("Bilgiler kaydedilemedi. Alanları kontrol edip tekrar deneyin.");save.setEnabled(true);}
  });
  Button cancel=new Button(this);cancel.setText("Vazgeç");cancel.setAllCaps(false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.topMargin=dp(10);page.addView(cancel,lp);cancel.setOnClickListener(v->finish());
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);
 }
 @Override protected void onDestroy(){super.onDestroy();if(isFinishing())CustomerSettings.clear();}
}
