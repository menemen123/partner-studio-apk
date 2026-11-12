package com.partnerstudio.runtime;
import org.json.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;
final class SalesClient implements SaleFlow.Transport {
 interface Wire {JSONObject request(String endpoint,JSONObject envelope,String auth)throws Exception;}
 static final class Config {
  final String api,mt,rx,username,password,branch,partner;
  Config(String api,String mt,String rx,String u,String p,String b,String partner)throws Exception{
   URI uri=new URI(api);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)throw new IllegalArgumentException("Firma bağlantısı geçersiz.");
   this.api=api.endsWith("/")?api:api+"/";this.mt=mt;this.rx=rx;username=u;password=p;branch=b;this.partner=partner;
   if(mt.trim().isEmpty()||rx.trim().isEmpty()||u.trim().isEmpty()||p.trim().isEmpty()||b.trim().isEmpty()||partner.trim().isEmpty())throw new IllegalArgumentException("Önce hesap bilgilerinizi kaydedin.");
  }
  boolean same(Config o){return api.equals(o.api)&&username.equals(o.username)&&password.equals(o.password)&&branch.equals(o.branch)&&partner.equals(o.partner);}
 }
 static final Set<String> RX=new HashSet<>(Arrays.asList("web/getstations","web/getjourneys","web/getjourneyseats"));
 private final Config config;private final Wire wire;private String sid="",did="",token="";
 SalesClient(Config config,Wire wire){this.config=config;this.wire=wire;}
 private JSONObject envelope(Object data,boolean signed)throws Exception{
  JSONObject body=new JSONObject().put("data",data).put("date",OffsetDateTime.now().toString()).put("language","tr-TR");
  if(!sid.isEmpty())body.put("device-session",new JSONObject().put("session-id",sid).put("device-id",did));
  if(signed)body.put("token",token);return body;
 }
 synchronized void login()throws Exception{
  if(!token.isEmpty())return;
  JSONObject connection=new JSONObject().put("type",1).put("connection",new JSONObject().put("ip-address","0.0.0.0").put("port","0")).put("browser",new JSONObject().put("name","Partner Studio Android"));
  JSONObject session=SaleProtocol.data(wire.request("client/getsession",envelope(connection,false),config.mt));
  sid=session.getString("session-id");did=session.getString("device-id");if(sid.trim().isEmpty()||did.trim().isEmpty())throw new IllegalArgumentException("Oturum açılamadı.");
  JSONObject data=new JSONObject().put("username",config.username).put("password",config.password).put("remember-me",0).put("branch-id",config.branch).put("partner-code",config.partner);
  JSONObject result=SaleProtocol.data(wire.request("membership/userlogin",envelope(data,false),config.mt));Object value=result.get("token");
  String next=value instanceof JSONObject?((JSONObject)value).optString("data",""):value instanceof String?(String)value:"";
  if(next.trim().isEmpty())throw new IllegalArgumentException("Kullanıcı oturumu doğrulanamadı.");token=next;
 }
 synchronized JSONObject query(String endpoint,Object data)throws Exception{
  if(!RX.contains(endpoint))throw new IllegalArgumentException("Sorgu desteklenmiyor.");requireLogin();JSONObject body=envelope(data,false);if(endpoint.equals("web/getstations"))body.put("token",JSONObject.NULL);return wire.request(endpoint,body,config.rx);
 }
 private void requireLogin(){if(token.isEmpty())throw new IllegalStateException("Önce hesabınızla giriş yapın.");}
 @Override public synchronized JSONObject post(String endpoint,JSONObject data)throws Exception{
  if(!endpoint.equals("payment/createorder")&&!endpoint.equals("Payment/PurchasePendingPassengers"))throw new IllegalArgumentException("Satış işlemi desteklenmiyor.");
  requireLogin();return wire.request(endpoint,envelope(data,true),config.mt);
 }
}
