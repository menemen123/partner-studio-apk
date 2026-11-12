package com.partnerstudio.runtime;
import org.json.*;
import java.math.BigDecimal;
import java.time.*;

/** The observed one-passenger Cash contract. No branch overrides or live I/O. */
final class SaleProtocol {
 static class Selection {
  final long journey,origin,destination;final int seat;final String departure;final boolean gender;
  Selection(long j,String d,long o,long dest,int seat,boolean gender){
   if(j<=0||o<=0||dest<=0||o==dest||seat<=0)throw new IllegalArgumentException("Sefer ve koltuk seçimini kontrol edin.");
   LocalDateTime.parse(d);journey=j;departure=d;origin=o;destination=dest;this.seat=seat;this.gender=gender;
  }
 }
 static final class Passenger {
  final String first,last,identity,phone;final boolean gender;
  Passenger(String f,String l,String i,String p,boolean g){
   first=f.trim();last=l.trim();identity=i.trim();phone=p.replaceAll("[\\s()+-]","");gender=g;
   if(first.isEmpty()||last.isEmpty()||first.length()>80||last.length()>80||!validIdentity(identity)||!phone.matches("(?:90|0)?[1-9][0-9]{9}"))throw new IllegalArgumentException("Ad, soyad, geçerli T.C. kimlik numarası ve telefon girin.");
  }
 }
 static boolean validIdentity(String s){
  if(!s.matches("[1-9][0-9]{10}"))return false;int odd=0,even=0,total=0;
  for(int n=0;n<9;n++){int d=s.charAt(n)-'0';total+=d;if(n%2==0)odd+=d;else even+=d;}
  int tenth=((odd*7-even)%10+10)%10;return tenth==s.charAt(9)-'0'&&(total+tenth)%10==s.charAt(10)-'0';
 }
 static final class Order {
  final JSONObject raw;final Selection selection;final long id;final String pnr;final BigDecimal price;
  Order(JSONObject raw,Selection s,long id,String pnr,BigDecimal price){this.raw=raw;selection=s;this.id=id;this.pnr=pnr;this.price=price;}
 }
 static final class Receipt {final String pnr;Receipt(String pnr){this.pnr=pnr;}}
 static JSONObject copy(JSONObject x)throws JSONException{return new JSONObject(x.toString());}
 static long positive(JSONObject o,String field)throws JSONException{long n=o.getLong(field);if(n<=0)throw new IllegalArgumentException("Sipariş bilgileri eksik.");return n;}
 static JSONObject data(JSONObject r)throws JSONException{
  if(!"Success".equals(r.optString("status"))||(r.has("success")&&!r.optBoolean("success")))throw new IllegalArgumentException("Servis işlemi onaylamadı.");
  return r.getJSONObject("data");
 }
 static JSONObject createOrder(Selection s)throws JSONException{return new JSONObject().put("id",0).put("journey-id",s.journey).put("departure-time",LocalDateTime.parse(s.departure).toLocalDate().toString()).put("state","PurchasePending").put("origin-id",s.origin).put("destination-id",s.destination).put("gender",s.gender).put("seat-numbers",new JSONArray().put(s.seat)).put("processType",0).put("seat-count",1);}
 static Order prepareOrder(JSONObject response,Selection s)throws Exception{
  JSONObject o=copy(data(response));long id=positive(o,"id");
  if(positive(o,"journey-id")!=s.journey||positive(o,"origin-id")!=s.origin||positive(o,"destination-id")!=s.destination||!o.getString("departure-time").equals(s.departure))throw new IllegalArgumentException("Sipariş seçilen seferle eşleşmiyor.");
  // The server owns the physical sales branch; do not compare it to itinerary or entered branch.
  positive(o,"branch-id");positive(o,"user-id");String pnr=o.getString("pnr-code").trim();if(pnr.isEmpty())throw new IllegalArgumentException("Sipariş referansı eksik.");
  JSONArray passengers=o.getJSONArray("passengers");if(passengers.length()!=1)throw new IllegalArgumentException("Tek yolcu siparişi bekleniyor.");
  JSONObject p=passengers.getJSONObject(0);positive(p,"id");positive(p,"customer-type-id");
  if(positive(p,"order-id")!=id||p.getInt("seat-number")!=s.seat)throw new IllegalArgumentException("Sipariş koltuğu eşleşmiyor.");
  if(p.has("journey-id")&&positive(p,"journey-id")!=s.journey)throw new IllegalArgumentException("Yolcu seferi eşleşmiyor.");
  for(String name:new String[]{"origin-id","destination-id"})if(p.has(name)&&positive(p,name)!=(name.equals("origin-id")?s.origin:s.destination))throw new IllegalArgumentException("Yolcu güzergâhı eşleşmiyor.");
  BigDecimal price=new BigDecimal(p.get("price").toString());if(price.signum()<=0)throw new IllegalArgumentException("Geçerli satış fiyatı alınamadı.");
  return new Order(o,s,id,pnr,price);
 }
 static JSONObject completeOrder(Order order,Passenger person)throws JSONException{
  if(person.gender!=order.selection.gender)throw new IllegalArgumentException("Cinsiyet değişti. Koltuk seçimini yenileyin.");
  JSONObject o=copy(order.raw);JSONObject p=o.getJSONArray("passengers").getJSONObject(0);
  p.put("first-name",person.first).put("last-name",person.last).put("full-name",person.first+" "+person.last).put("gov-id",person.identity).put("phone",person.phone).put("gender",person.gender).put("nationality","TR").put("use-passport",false).put("sale-type","Normal").put("payment-type","Cash").put("price",order.price).put("total-price-input",order.price.toPlainString());
  o.put("selectedRow",0).put("phone",person.phone).put("payment-type","Cash").put("sales-person-state",true).put("total-price",order.price);
  // Large lookup lists are not part of the observed completion body.
  o.remove("wayStations");o.remove("customer-type-prices");o.remove("applied-rule-ids");return o;
 }
 static Receipt purchased(JSONObject response,Order o)throws JSONException{
  JSONObject d=data(response);
  if(!d.optBoolean("success")||!d.optBoolean("completed")||!"Success".equals(d.optString("order-status"))||!o.pnr.equals(d.optString("order-code")))throw new IllegalArgumentException("Satış sonucu kesinleştirilemedi; şube programından kontrol edin.");
  if(d.has("pos-status")&&!d.isNull("pos-status")&&!"Success".equals(d.optString("pos-status")))throw new IllegalArgumentException("Ödeme sonucu doğrulanamadı.");
  JSONArray tickets=d.getJSONArray("tickets");if(tickets.length()!=1)throw new IllegalArgumentException("Satılan bilet doğrulanamadı.");JSONObject t=tickets.getJSONObject(0);
  String pnr=t.optString("pnr-code",t.optString("code"));
  if(!"Purchased".equals(t.optString("state"))||!o.pnr.equals(pnr)||(!t.has("seat-number")||t.getInt("seat-number")!=o.selection.seat)||(t.has("order-id")&&t.getLong("order-id")!=o.id))throw new IllegalArgumentException("Bilet sonucu siparişle eşleşmiyor.");
  for(String field:new String[]{"journey-id","origin-id","destination-id"}){
   long expected=field.equals("journey-id")?o.selection.journey:field.equals("origin-id")?o.selection.origin:o.selection.destination;
   if(t.has(field)&&positive(t,field)!=expected)throw new IllegalArgumentException("Bilet güzergâhı siparişle eşleşmiyor.");
  }
  return new Receipt(o.pnr);
 }
}
