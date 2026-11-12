package com.partnerstudio.runtime;
import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.*;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import org.json.JSONObject;

public class SalesActivity extends Activity implements SalesSession.Listener {
 private SalesSession session;private LinearLayout page;private int brand=0xff176b56;private boolean missingAccount;
 private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density);}
 protected SalesSession createSession()throws Exception{
  SalesClient.Config config=AccountStore.config(this);SalesClient client=new SalesClient(config,new HttpSalesTransport(config.api));return new SalesSession(config,client,new SalesQuery(client),new SaleFlow(client,new SaleVault(this)));
 }
 @Override public void onCreate(Bundle state){super.onCreate(state);try{brand=Color.parseColor(StudioConfig.get(this).optString("color","#176b56"));}catch(Exception ignored){}Object retained=getLastNonConfigurationInstance();if(retained instanceof SalesSession)session=(SalesSession)retained;connect();}
 private void connect(){
  if(session==null)try{session=createSession();missingAccount=false;}catch(Exception e){missingAccount=true;}
  if(session!=null){session.attach(this);session.start();}render();
 }
 @Override protected void onResume(){super.onResume();if(missingAccount)connect();else refreshAccount();}
 private void refreshAccount(){
  SalesSession old=session;if(old==null||old.busy)return;
  try{if(old.account.same(AccountStore.config(this)))return;}catch(Exception e){old.stage="PENDING";old.message="Hesap bilgileri okunamadı. Satış gönderilmedi.";render();return;}
  if(old.flow.hasPending()){old.stage="PENDING";old.message="Hesap değişti. Önce mevcut işlemi şube programından kontrol edin.";render();}
  else{old.close();session=null;connect();}
 }
 @Override public Object onRetainNonConfigurationInstance(){if(session!=null)session.detach();return session;}
 @Override protected void onDestroy(){if(session!=null){session.detach();if(!isChangingConfigurations())session.close();}super.onDestroy();}
 @Override public void changed(){if(!isFinishing()&&!isDestroyed()){refreshAccount();render();}}
 private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff203d32);t.setPadding(0,dp(9),0,dp(9));page.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
 private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(brand));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.topMargin=dp(10);page.addView(b,lp);b.setEnabled(session==null||!session.busy);b.setOnClickListener(v->action.run());return b;}
 private EditText field(String label,String value,int type,int limit){text(label,14);EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(type);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});e.setText(value);page.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));return e;}
 private void render(){
  page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(28),dp(24),dp(28));page.setBackgroundColor(0xfff5f8f4);page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(24),dp(18)+i.getSystemWindowInsetTop(),dp(24),dp(20)+i.getSystemWindowInsetBottom());return i;});
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);TextView title=text("Bilet satışı",28);title.setTypeface(null,Typeface.BOLD);
  if(session==null){text("Satışa başlamak için hesap bilgilerinizi kaydedin. Mevcut işlem kaydı okunamıyorsa uygulama verilerini silmeyin; şube programından sonucu kontrol edin.",16);button("Hesap bilgilerini aç",()->CustomerSettings.openAccount(this));button("Ana ekrana dön",()->finish());return;}
  SalesSession s=session;
  if(s.busy){ProgressBar progress=new ProgressBar(this);page.addView(progress);text("İşlem sürüyor…",18);text("Satış sırasında uygulamayı kapatmayın.",14);return;}
  if(!s.message.isEmpty()){TextView e=text(s.message,15);e.setTextColor(0xffa33d2b);}
  switch(s.stage){
   case "SEARCH":search();break;
   case "JOURNEYS":journeys();break;
   case "SEATS":seats();break;
   case "PASSENGER":passenger();break;
   case "REVIEW":review();break;
   case "RESULT":result();break;
   case "PENDING":pending();break;
   case "ERROR":button("Tekrar dene",()->{s.run(()->{s.client.login();s.stations=s.query.stations();s.stage="SEARCH";},"Giriş veya sorgu tamamlanamadı. Hesap bilgilerinizi kontrol edin.");});button("Hesap bilgileri",()->CustomerSettings.openAccount(this));break;
   default:text("Hesabınızla bağlantı kuruluyor…",16);
  }
  button("Ana ekrana dön",()->finish());
 }
 private void search(){
  SalesSession s=session;text("Nereden, nereye ve gidiş tarihini seçin.",16);
  button(s.origin==null?"Nereden":s.origin.name,()->pickStation(true));button(s.destination==null?"Nereye":s.destination.name,()->pickStation(false));
  button("Gidiş tarihi: "+s.date,()->{LocalDate d=s.date;DatePickerDialog dialog=new DatePickerDialog(this,(v,y,m,day)->{s.date=LocalDate.of(y,m+1,day);render();},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());dialog.getDatePicker().setMinDate(System.currentTimeMillis()-1000);dialog.show();});
  button("Seferleri sorgula",()->{if(s.origin==null||s.destination==null||s.origin.id==s.destination.id||s.date.isBefore(LocalDate.now())){Toast.makeText(this,"Farklı kalkış/varış durakları ve geçerli bir tarih seçin.",Toast.LENGTH_LONG).show();return;}s.findJourneys();});
 }
 private void pickStation(boolean origin){
  SalesSession s=session;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),0);EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Durak ara");box.addView(search);ListView list=new ListView(this);box.addView(list,new LinearLayout.LayoutParams(-1,dp(350)));
  ArrayAdapter<SalesQuery.Station> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>(s.stations));list.setAdapter(adapter);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(origin?"Nereden":"Nereye").setView(box).setNegativeButton("Vazgeç",null).create();
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence t,int a,int c,int f){}public void onTextChanged(CharSequence t,int a,int before,int count){adapter.getFilter().filter(t);}public void afterTextChanged(Editable t){}});
  list.setOnItemClickListener((parent,v,position,id)->{if(origin)s.origin=adapter.getItem(position);else s.destination=adapter.getItem(position);dialog.dismiss();render();});dialog.show();
 }
 private void journeys(){SalesSession s=session;text(s.origin.name+" → "+s.destination.name+" · "+s.date,16);if(s.journeys.isEmpty())text("Seçtiğiniz güzergâh ve tarih için sefer bulunamadı.",16);for(SalesQuery.Journey j:s.journeys){Button b=button(j.toString(),()->s.loadSeats(j));b.setSingleLine(false);b.setMinHeight(dp(64));}button("Aramayı değiştir",()->{s.stage="SEARCH";render();});}
 private void seats(){
  SalesSession s=session;text(s.journey.toString(),16);text("Müsait bir koltuk seçin. Kesin fiyat sipariş özetinde gösterilir.",15);GridLayout grid=new GridLayout(this);grid.setColumnCount(4);page.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  for(SalesQuery.Seat seat:s.seats){Button b=new Button(this);b.setAllCaps(false);b.setText(Integer.toString(seat.number));b.setEnabled(seat.available);b.setAlpha(seat.available?1f:0.35f);GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=dp(64);lp.height=dp(58);lp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(b,lp);b.setOnClickListener(v->{s.seat=seat;s.stage="PASSENGER";render();});}
  if(s.seats.isEmpty())text("Koltuk bilgisi bulunamadı.",16);button("Seferlere dön",()->{s.stage="JOURNEYS";render();});
 }
 private void passenger(){
  SalesSession s=session;SaleProtocol.Passenger old=s.passenger;text(s.origin.name+" → "+s.destination.name+" · Koltuk "+s.seat.number,16);
  EditText first=field("Ad",old==null?"":old.first,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,80);
  EditText last=field("Soyad",old==null?"":old.last,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,80);
  EditText identity=field("T.C. kimlik numarası",old==null?"":old.identity,InputType.TYPE_CLASS_NUMBER,11);
  EditText phone=field("Telefon",old==null?"":old.phone,InputType.TYPE_CLASS_PHONE,16);
  text("Cinsiyet",14);RadioGroup gender=new RadioGroup(this);gender.setOrientation(RadioGroup.HORIZONTAL);RadioButton male=new RadioButton(this);male.setId(View.generateViewId());male.setText("Erkek");RadioButton female=new RadioButton(this);female.setId(View.generateViewId());female.setText("Kadın");gender.addView(male);gender.addView(female);if(old!=null)gender.check(old.gender?male.getId():female.getId());page.addView(gender);text("Ödeme: Nakit · Normal yolcu",14);
  button("Sipariş oluştur ve fiyatı göster",()->{
   if(gender.getCheckedRadioButtonId()==-1){Toast.makeText(this,"Yolcu cinsiyetini seçin.",Toast.LENGTH_LONG).show();return;}
   try{s.passenger=new SaleProtocol.Passenger(first.getText().toString(),last.getText().toString(),identity.getText().toString(),phone.getText().toString(),male.isChecked());s.createOrder();}catch(Exception e){Toast.makeText(this,"Ad, soyad, geçerli T.C. kimlik numarası ve telefon girin.",Toast.LENGTH_LONG).show();}
  });
  button("Koltuklara dön",()->{s.stage="SEATS";render();});
 }
 private void review(){
  SalesSession s=session;SaleProtocol.Order order=s.flow.currentOrder();if(order==null){s.stage="PENDING";pending();return;}
  text("Satış özeti",22);text(s.origin.name+" → "+s.destination.name,18);text(order.selection.departure.replace('T',' ')+" · Koltuk "+order.selection.seat,16);text(s.passenger.first+" "+s.passenger.last,18);
  text("Satış şubesi: "+order.raw.optString("branch-id")+" · Kullanıcı: "+order.raw.optString("user-id"),14);text("Toplam: "+order.price.toPlainString()+" TL",24);text("Ödeme: Nakit. Tutarı şubede tahsil edin. Bu onay gerçek bilet satışı yapar.",15);
  button("Satışı tamamla",()->new AlertDialog.Builder(this).setTitle("Nakit bilet satışı").setMessage(order.price.toPlainString()+" TL · Koltuk "+order.selection.seat+"\nYolcu ve tutarı kontrol ettiniz mi?").setNegativeButton("Vazgeç",null).setPositiveButton("Satışı onayla",(d,w)->verifyAccountAndComplete()).show());
  text("Sipariş açıldı. Yeni sefer veya koltuk seçmeden önce mevcut siparişin durumunu şube programından kontrol edin.",14);button("İşlem durumunu kontrol et",()->{s.stage="PENDING";render();});
 }
 private void verifyAccountAndComplete(){
  try{if(!session.account.same(AccountStore.config(this))){session.message="Hesap değişti. Mevcut siparişi şube programından kontrol edin.";session.stage="PENDING";render();return;}}catch(Exception e){session.message="Hesap bilgileri okunamadı. Satış gönderilmedi.";session.stage="PENDING";render();return;}
  session.completeOrder();
 }
 private void result(){SalesSession s=session;text("Satış tamamlandı",25);text("PNR: "+s.receipt.pnr,24);text("Bilet durumu: Satın alındı",16);button("Yeni bilet satışı",()->s.resetChecked());}
 private void pending(){
  SalesSession s=session;text("Kontrol edilmesi gereken işlem",22);text("Yeni sipariş açılmadı. Şube programında önceki işlemin bilet veya bekleyen sipariş durumunu kontrol edin.",16);
  try{JSONObject j=new JSONObject(s.flow.journal());if(j.has("pnr"))text("PNR / sipariş referansı: "+j.getString("pnr"),18);if(j.has("order_id"))text("Sipariş: "+j.getLong("order_id"),15);text("Koltuk "+j.getInt("seat")+" · "+j.getString("departure").replace('T',' '),15);}catch(Exception ignored){}
  if(s.flow.currentOrder()!=null&&s.passenger!=null)button("Mevcut sipariş özetine dön",()->{s.stage="REVIEW";render();});
  button("Şube programından kontrol ettim",()->new AlertDialog.Builder(this).setTitle("İşlem sonucunu kontrol et").setMessage("Önceki satışın sonucunu ve varsa koltuk tutan siparişi şube programından kontrol edip gerekli işlemi yaptınız mı? Bu düğme sunucudaki siparişi iptal etmez. Kontrol etmeden yeni satış açmak mükerrer bilete neden olabilir.").setNegativeButton("Geri dön",null).setPositiveButton("Kontrol ettim, kaydı kapat",(d,w)->s.resetChecked()).show());
 }
 @Override public void onBackPressed(){if(session!=null&&(session.busy||session.flow.hasPending())){new AlertDialog.Builder(this).setMessage("İşlem kaydı korunacak. Çıkarsanız yeni satıştan önce sonucu şube programından kontrol etmeniz gerekebilir.").setNegativeButton("Kal",null).setPositiveButton("Ana ekrana dön",(d,w)->finish()).show();}else super.onBackPressed();}
}
