package com.partnerstudio.runtime;
import android.os.*;
import org.json.*;
import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
/** Retained across screen rotation; workers never retain an Activity or repeat a transaction. */
final class SalesSession {
 interface Listener {void changed();}
 interface Work {void run()throws Exception;}
 final SalesClient.Config account;final SalesClient client;final SalesQuery query;final SaleFlow flow;
 final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
 private WeakReference<Listener> listener=new WeakReference<>(null);private boolean started;
 volatile boolean busy,closed;volatile String stage="START",message="";
 List<SalesQuery.Station> stations=new ArrayList<>();List<SalesQuery.Journey> journeys=new ArrayList<>();List<SalesQuery.Seat> seats=new ArrayList<>();
 SalesQuery.Station origin,destination;LocalDate date=LocalDate.now();SalesQuery.Journey journey;SalesQuery.Seat seat;SaleProtocol.Passenger passenger;SaleProtocol.Receipt receipt;
 SalesSession(SalesClient.Config account,SalesClient client,SalesQuery query,SaleFlow flow)throws Exception{
  this.account=account;this.client=client;this.query=query;this.flow=flow;
  if(flow.hasPending()){
   JSONObject j=new JSONObject(flow.journal());if("PURCHASED".equals(j.getString("phase"))){receipt=new SaleProtocol.Receipt(j.getString("pnr"));stage="RESULT";}else stage="PENDING";
  }
 }
 void attach(Listener l){listener=new WeakReference<>(l);}void detach(){listener.clear();}
 void notifyUI(){main.post(()->{Listener l=listener.get();if(l!=null&&!closed)l.changed();});}
 synchronized void start(){if(started||flow.hasPending())return;started=true;run(()->{client.login();stations=query.stations();stage="SEARCH";},"Giriş veya durak sorgusu tamamlanamadı. Hesap bilgilerinizi ve bağlantınızı kontrol edin.");}
 synchronized void run(Work work,String error){
  if(busy||closed)return;busy=true;message="";notifyUI();worker.execute(()->{try{work.run();}catch(Exception e){message=error;stage=flow.hasPending()?"PENDING":"ERROR";}finally{busy=false;notifyUI();}});
 }
 void findJourneys(){run(()->{journeys=query.journeys(origin.id,destination.id,date);stage="JOURNEYS";},"Seferler alınamadı. Durakları, tarihi ve bağlantınızı kontrol edin.");}
 void loadSeats(SalesQuery.Journey j){journey=j;run(()->{seats=query.seats(j);stage="SEATS";},"Koltuklar alınamadı. Bağlantınızı kontrol edip tekrar deneyin.");}
 void createOrder(){final SaleProtocol.Selection selection=new SaleProtocol.Selection(journey.id,journey.departure,origin.id,destination.id,seat.number,passenger.gender);run(()->{flow.create(selection);stage="REVIEW";},"Sipariş sonucu doğrulanamadı. Yeni sipariş oluşturmadan önce şube programından kontrol edin.");}
 void completeOrder(){run(()->{receipt=flow.complete(passenger);passenger=null;stage="RESULT";},"Satış sonucu kesinleştirilemedi. Aynı işlemi tekrar göndermeyin; şube programından kontrol edin.");}
 void resetChecked(){run(()->{flow.resetAfterManualCheck();passenger=null;journey=null;seat=null;if(stations.isEmpty()){client.login();stations=query.stations();}stage="SEARCH";},"İşlem kaydı kapatılamadı. Yeni satış başlatılmadı.");}
 void close(){closed=true;detach();worker.shutdown();}
}
