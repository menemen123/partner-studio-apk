package com.partnerstudio.runtime;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
/** Adapt the original APK's validated station/journey/seat parsing. */
class SalesQuery {
 static final class Station {final int id;final String name;Station(int id,String n){this.id=id;name=n;}@Override public String toString(){return name;}}
 static final class Journey {final long id;final String departure,title,price;Journey(long id,String d,String t,String p){this.id=id;departure=d;title=t;price=p;}@Override public String toString(){return departure.replace('T',' ')+" · "+title+(price.isEmpty()?"":" · "+price);}}
 static final class Seat {final int number;final boolean available;final BigDecimal price;Seat(int n,boolean a,BigDecimal p){number=n;available=a;price=p;}}
 private final SalesClient client;
 SalesQuery(SalesClient client){this.client=client;}
 private static Object get(Object x,String getter)throws Exception{return x.getClass().getMethod(getter).invoke(x);}
 private static List<?> parse(String method,JSONObject json,Integer origin)throws Exception{
  Class<?> gson=Class.forName("com.google.gson.JsonParser"),object=Class.forName("com.google.gson.JsonObject");Object element=gson.getMethod("parseString",String.class).invoke(null,json.toString());Object root=element.getClass().getMethod("getAsJsonObject").invoke(element);
  Class<?> responses=Class.forName("com.biletbagla.Responses");Object instance=responses.getField("INSTANCE").get(null);
  try{return (List<?>)(origin==null?responses.getMethod(method,object).invoke(instance,root):responses.getMethod(method,object,int.class).invoke(instance,root,origin));}catch(InvocationTargetException e){throw new IllegalArgumentException("Servis yanıtı eşleştirilemedi. Firma sisteminden kontrol edin.");}
 }
 List<Station> stations()throws Exception{List<Station> out=new ArrayList<>();for(Object x:parse("stations",client.query("web/getstations",JSONObject.NULL),null))out.add(new Station(((Number)get(x,"getId")).intValue(),(String)get(x,"getName")));return out;}
 List<Journey> journeys(int origin,int destination,LocalDate date)throws Exception{
  if(origin==destination||date.isBefore(LocalDate.now()))throw new IllegalArgumentException("Farklı duraklar ve geçerli bir tarih seçin.");
  JSONObject data=new JSONObject().put("origin",origin).put("destination",destination).put("from",date.atStartOfDay().toString()).put("to",date.plusDays(1).atStartOfDay().toString());
  List<Journey> out=new ArrayList<>();for(Object x:parse("journeys",client.query("web/getjourneys",data),origin)){Object p=get(x,"getPrice");out.add(new Journey(Long.parseLong((String)get(x,"getId")),(String)get(x,"getDeparture"),(String)get(x,"getTitle"),p==null||p.toString().equals("null")?"":p.toString().replace("\"","")+" TL"));}return out;
 }
 List<Seat> seats(Journey journey)throws Exception{List<Seat> out=new ArrayList<>();for(Object x:parse("seats",client.query("web/getjourneyseats",Long.toString(journey.id)),null)){boolean selectable=(Boolean)get(x,"getSelectable")&&(Boolean)get(x,"getAvailable");out.add(new Seat(((Number)get(x,"getNumber")).intValue(),selectable,(BigDecimal)get(x,"getPrice")));}return out;}
}
