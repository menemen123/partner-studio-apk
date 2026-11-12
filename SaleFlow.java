package com.partnerstudio.runtime;
import org.json.*;

/** Persist before transaction I/O. Never retry ambiguous create/complete requests. */
final class SaleFlow {
 interface Transport {JSONObject post(String endpoint,JSONObject data)throws Exception;}
 interface PendingStore {String read()throws Exception;void write(String value)throws Exception;void clear()throws Exception;}
 private static final Object TRANSACTION_LOCK=new Object();
 private String operation;
 private final Transport transport;private final PendingStore store;private SaleProtocol.Order order;private String phase;
 SaleFlow(Transport t,PendingStore s)throws Exception{transport=t;store=s;String old=s.read();phase=old==null||old.isEmpty()?null:new JSONObject(old).getString("phase");operation=phase==null?null:new JSONObject(old).optString("operation","legacy");}
 synchronized boolean hasPending(){return phase!=null;}
 synchronized SaleProtocol.Order currentOrder(){return "READY".equals(phase)?order:null;}
 synchronized String journal()throws Exception{return store.read();}
 private void save(String next,SaleProtocol.Selection s,SaleProtocol.Order o)throws Exception{
  JSONObject j=new JSONObject().put("operation",operation).put("phase",next).put("journey",s.journey).put("departure",s.departure).put("origin",s.origin).put("destination",s.destination).put("seat",s.seat);
  if(o!=null)j.put("order_id",o.id).put("pnr",o.pnr);
  store.write(j.toString());phase=next;
 }
 synchronized SaleProtocol.Order create(SaleProtocol.Selection s)throws Exception{
  JSONObject payload=SaleProtocol.createOrder(s);
  synchronized(TRANSACTION_LOCK){
   String saved=store.read();
   if(phase!=null||(saved!=null&&!saved.isEmpty())){
    if(saved!=null&&!saved.isEmpty()){JSONObject j=new JSONObject(saved);phase=j.getString("phase");operation=j.optString("operation","legacy");}
    throw new IllegalStateException("Önce mevcut işlemin sonucunu kontrol edin.");
   }
   operation=java.util.UUID.randomUUID().toString();save("CREATE_SENT",s,null);
   order=SaleProtocol.prepareOrder(transport.post("payment/createorder",payload),s);save("READY",s,order);return order;
  }
 }
 synchronized SaleProtocol.Receipt complete(SaleProtocol.Passenger person)throws Exception{
  synchronized(TRANSACTION_LOCK){
   if(!"READY".equals(phase)||order==null)throw new IllegalStateException("Bu işlem yeniden gönderilemez. Sonucu şube programından kontrol edin.");
   String saved=store.read();
   if(saved==null||!operation.equals(new JSONObject(saved).optString("operation"))||!"READY".equals(new JSONObject(saved).optString("phase")))throw new IllegalStateException("İşlem kaydı değişti. Satış gönderilmedi.");
   JSONObject payload=SaleProtocol.completeOrder(order,person);save("COMPLETE_SENT",order.selection,order);
   SaleProtocol.Receipt receipt=SaleProtocol.purchased(transport.post("Payment/PurchasePendingPassengers",payload),order);save("PURCHASED",order.selection,order);return receipt;
  }
 }
 synchronized void resetAfterManualCheck()throws Exception{
  synchronized(TRANSACTION_LOCK){
   String saved=store.read();
   if(saved!=null&&(operation==null||!operation.equals(new JSONObject(saved).optString("operation","legacy"))))throw new IllegalStateException("Başka bir işlem başladı. Önce güncel işlem kaydını kontrol edin.");
   store.clear();phase=null;order=null;operation=null;
  }
 }
}
