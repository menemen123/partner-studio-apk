"""Rebrand a prepared Android template and retain per-company signing identity."""
import base64
import json
import os
import re
import secrets
import shutil
import sqlite3
import hashlib
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TOOLS=Path(os.environ.get('STUDIO_TOOLS',ROOT/'tools')).resolve()
NS='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',NS[1:-1])

def tool(name):
    patterns={'apktool':['apktool.jar'],'apksigner':['apksigner.jar','build-tools/*/lib/apksigner.jar'],'zipalign':['zipalign','build-tools/*/zipalign'],'android':['platform/*/android.jar'],'aapt2':['build-tools/*/aapt2'],'javac':['jdk/*/bin/javac'],'d8':['build-tools/*/lib/d8.jar'],'java':['jdk/*/bin/java']}
    for pattern in patterns.get(name,[name]):
        found=list(TOOLS.glob(pattern))
        if found:return str(found[0])
    if name in ('java','javac') and shutil.which(name):return shutil.which(name)
    raise RuntimeError('Derleme aracı eksik: '+name+'; scripts/setup_tools.py çalıştırın.')

def run(args,log=None,env=None):
    r=subprocess.run([str(x) for x in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,env=env,timeout=300)
    out=r.stdout.decode(errors='replace')
    if log:
        with Path(log).open('a') as f:f.write(out+'\n')
    if r.returncode:raise RuntimeError(out[-3000:])
    return out

def tools_ready():
    try:
        for t in ['java','apktool','apksigner','zipalign']:tool(t)
        return (ROOT/'template.zip').is_file() and (ROOT/'runtime.dex').is_file()
    except RuntimeError:return False

def patch_branding(directory,company):
    def quoted(s):return json.dumps(s,ensure_ascii=True)
    for p in Path(directory).glob('smali*/com/biletbagla/*.smali'):
        s=p.read_text();updated=s.replace('"muttas"',quoted(company['partner_code'])).replace('"muttas|"',quoted(company['partner_code']+'|'))
        updated=updated.replace('"Mutta\\u015f"',quoted(company['app_name'])).replace('"Muttaş"',quoted(company['app_name']))
        if updated!=s:p.write_text(updated)

def patch_api_url(directory,address):
    from server.store import normalize_api_url,DEFAULT_API_URL
    address=normalize_api_url(address)
    root=Path(directory)
    configs=list(root.glob('smali*/com/biletbagla/AppConfig.smali'))
    pattern=r'(?ms)^\.method public final getBaseUrl\(\)Ljava/lang/String;.*?^\.end method'
    if len(configs)!=1 or len(re.findall(pattern,configs[0].read_text()))!=1:raise RuntimeError('Şablonda API yapılandırma metodu bulunamadı; yanlış adresle APK üretilmedi.')
    body='.method public final getBaseUrl()Ljava/lang/String;\n    .locals 1\n    const-string v0, '+json.dumps(address,ensure_ascii=True)+'\n    return-object v0\n.end method'
    for p in root.glob('smali*/com/biletbagla/*.smali'):
        original=p.read_text();updated=original.replace(json.dumps(DEFAULT_API_URL),json.dumps(address))
        if p==configs[0]:updated=re.sub(pattern,lambda _:body,updated)
        if updated!=original:p.write_text(updated)

def normalize_common_auth(value,name):
    if not isinstance(value,str) or not value.strip():raise ValueError(name+' GitHub Actions secret olarak tanımlanmalı; kullanıcıdan Auth kodu istenmez.')
    value=re.sub(r'(?i)^Basic\s+','',value.strip()).strip()
    if len(value)>8192 or not re.fullmatch(r'[A-Za-z0-9+/]+={0,2}',value):raise ValueError(name+' geçerli bir Basic Auth kodu olmalı.')
    try:base64.b64decode(value+'='*(-len(value)%4),validate=True)
    except Exception:raise ValueError(name+' geçerli bir Basic Auth kodu olmalı.')
    return value

def required_common_auth():
    values=[normalize_common_auth(os.environ.get(name,''),name) for name in ['OBUS_MT_AUTH','OBUS_RX_AUTH']]
    if os.environ.get('GITHUB_ACTIONS')=='true':
        for value in values:print('::add-mask::'+value,flush=True)
    return values

def patch_customer_settings(directory,mt_auth,rx_auth):
    mt_auth=normalize_common_auth(mt_auth,'OBUS_MT_AUTH');rx_auth=normalize_common_auth(rx_auth,'OBUS_RX_AUTH')
    root=Path(directory);sources={}
    for name in ['AppConfig','SettingsScreenKt','BookingViewModel','PurchaseResult']:
        paths=list(root.glob('smali*/com/biletbagla/'+name+'.smali'))
        if len(paths)!=1:raise RuntimeError('Müşteri ayar şablonu bulunamadı: '+name)
        sources[name]=[paths[0],paths[0].read_text()]
    def method(text,name,signature,body):
        pattern=r'(?ms)^\.method [^\n]*?\b'+re.escape(name+signature)+r'\n.*?^\.end method'
        match=re.search(pattern,text)
        if not match:raise RuntimeError('Şablon metodu bulunamadı: '+name)
        header=match.group().splitlines()[0]
        return re.sub(pattern,lambda _:header+'\n'+body+'\n.end method',text,count=1)
    cfg=sources['AppConfig'][1]
    for name,value in [('getBasicAuth',mt_auth),('getSearchOrderAuth',rx_auth)]:
        cfg=method(cfg,name,'()Ljava/lang/String;','    .locals 1\n    const-string v0, '+json.dumps(value)+'\n    return-object v0')
    cfg=method(cfg,'getDemo','()Z','    .locals 1\n    const/4 v0, 0x0\n    return v0')
    cfg=method(cfg,'toString','()Ljava/lang/String;','    .locals 1\n    const-string v0, "AppConfig(ayarlar gizli)"\n    return-object v0')
    sources['AppConfig'][1]=cfg
    sources['PurchaseResult'][1]=method(sources['PurchaseResult'][1],'getDemo','()Z','    .locals 1\n    const/4 v0, 0x0\n    return v0')
    signature='(Lcom/biletbagla/AppConfig;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I)V'
    sources['SettingsScreenKt'][1]=method(sources['SettingsScreenKt'][1],'SettingsScreen',signature,'    .locals 0\n    invoke-static {p0, p1, p2}, Lcom/partnerstudio/runtime/CustomerSettings;->open(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V\n    return-void')
    vm=sources['BookingViewModel'][1]
    old='    new-instance p1, Lcom/biletbagla/DemoRepository;\n\n    invoke-direct {p1}, Lcom/biletbagla/DemoRepository;-><init>()V'
    if vm.count(old)!=1:raise RuntimeError('Başlangıç repository şablonu bulunamadı.')
    vm=vm.replace(old,'    invoke-static {}, Lcom/partnerstudio/runtime/CustomerSettings;->unconfiguredRepository()Ljava/lang/Object;\n\n    move-result-object p1')
    body='''    .locals 7
    new-instance v0, Lcom/biletbagla/LiveRepository;
    iget-object v4, p0, Lcom/biletbagla/BookingViewModel;->attempts:Lcom/biletbagla/AttemptStore;
    const/4 v5, 0x2
    const/4 v6, 0x0
    const/4 v3, 0x0
    move-object v1, v0
    move-object v2, p1
    invoke-direct/range {v1 .. v6}, Lcom/biletbagla/LiveRepository;-><init>(Lcom/biletbagla/AppConfig;Lcom/biletbagla/TicketApi;Lcom/biletbagla/AttemptStore;ILkotlin/jvm/internal/DefaultConstructorMarker;)V
    return-object v0'''
    sources['BookingViewModel'][1]=method(vm,'repositoryFor','(Lcom/biletbagla/AppConfig;)Lcom/biletbagla/BookingRepository;',body)
    main_paths=list(root.glob('smali*/com/biletbagla/MainActivityKt.smali'))
    if len(main_paths)!=1:raise RuntimeError('Ana ekran şablonu bulunamadı.')
    main=main_paths[0].read_text();header='.method public static final ErrorBox(Ljava/lang/String;Landroidx/compose/runtime/Composer;I)V'
    if main.count(header)!=1:raise RuntimeError('Hata ekranı şablonu bulunamadı.')
    main=main.replace(header,header.replace(' ErrorBox(', ' ErrorBoxUnredacted('),1)
    main+='''
.method public static final ErrorBox(Ljava/lang/String;Landroidx/compose/runtime/Composer;I)V
    .locals 0
    invoke-static {p0}, Lcom/partnerstudio/runtime/CustomerSettings;->safeMessage(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p0
    invoke-static {p0, p1, p2}, Lcom/biletbagla/MainActivityKt;->ErrorBoxUnredacted(Ljava/lang/String;Landroidx/compose/runtime/Composer;I)V
    return-void
.end method
'''
    for path,text in sources.values():path.write_text(text)
    main_paths[0].write_text(main)

def validate_sales_template(directory):
    """Fail before building when reflected account/query APIs do not match runtime."""
    root=Path(directory)
    contracts={
        'com/biletbagla/Responses':[
            ('stations','(Lcom/google/gson/JsonObject;)Ljava/util/List;'),
            ('journeys','(Lcom/google/gson/JsonObject;I)Ljava/util/List;'),
            ('seats','(Lcom/google/gson/JsonObject;)Ljava/util/List;')],
        'com/biletbagla/Station':[('getId','()I'),('getName','()Ljava/lang/String;')],
        'com/biletbagla/Journey':[('getId','()Ljava/lang/String;'),('getDeparture','()Ljava/lang/String;'),('getTitle','()Ljava/lang/String;'),('getPrice','()Lcom/google/gson/JsonElement;')],
        'com/biletbagla/Seat':[('getNumber','()I'),('getAvailable','()Z'),('getSelectable','()Z'),('getPrice','()Ljava/math/BigDecimal;')],
        'com/biletbagla/AndroidConfigStoreKt':[('androidConfigStore','(Landroid/content/Context;)Lcom/biletbagla/ConfigStore;')],
        'com/biletbagla/ConfigStore':[('load','()Lcom/biletbagla/AppConfig;'),('save','(Lcom/biletbagla/AppConfig;)V')],
        'com/biletbagla/AppConfig':[
            ('<init>','()V'),('<init>','(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;)V'),
            *[(name,'()Ljava/lang/String;') for name in ['getBaseUrl','getBasicAuth','getSearchOrderAuth','getUsername','getPassword','getBranchId','getContractJson']]],
        'com/google/gson/JsonParser':[('parseString','(Ljava/lang/String;)Lcom/google/gson/JsonElement;')],
        'com/google/gson/JsonElement':[('getAsJsonObject','()Lcom/google/gson/JsonObject;')]
    }
    for cls,methods in contracts.items():
        paths=list(root.glob('smali*/'+cls+'.smali'))
        if len(paths)!=1:raise RuntimeError('Bilet satışı şablonu uyumsuz: '+cls)
        source=paths[0].read_text()
        if not re.search(r'(?m)^\.class public\b',source):raise RuntimeError('Bilet satışı sınıfına erişilemiyor: '+cls)
        for name,signature in methods:
            pattern=r'(?m)^\.method public\b[^\n]*?'+re.escape(name+signature)+r'$'
            found=re.search(pattern,source)
            if not found:raise RuntimeError('Bilet satışı şablonu uyumsuz: '+cls+'.'+name)
            if name in ['androidConfigStore','parseString'] and ' static ' not in found.group():raise RuntimeError('Bilet satışı statik metodu uyumsuz: '+cls+'.'+name)
        if cls.endswith('/Responses') and not re.search(r'(?m)^\.field public static final INSTANCE:Lcom/biletbagla/Responses;$',source):raise RuntimeError('Bilet satışı ayrıştırıcısı uyumsuz: Responses.INSTANCE')

def patch_manifest(path,company):
    tree=ET.parse(path);root=tree.getroot();old=root.attrib['package'];new=company['package_id'];root.set('package',new)
    for el in root.iter():
        for a in [NS+'authorities',NS+'permission']:
            if a in el.attrib:el.set(a,el.get(a).replace(old+'.',new+'.'))
        if el.tag in ('permission','uses-permission') and el.get(NS+'name','').startswith(old+'.'):el.set(NS+'name',el.get(NS+'name').replace(old+'.',new+'.'))
    app=root.find('application');app.set(NS+'label',company['app_name']);app.set(NS+'debuggable','false');app.set(NS+'allowBackup','false');app.set(NS+'usesCleartextTraffic','false')
    main=next(x for x in app.findall('activity') if x.get(NS+'name')=='com.biletbagla.MainActivity')
    for x in list(main):
        if x.tag=='intent-filter':main.remove(x)
    main.set(NS+'exported','false')
    for x in list(app):
        if x.tag=='activity' and 'PreviewActivity' in x.get(NS+'name',''):app.remove(x)
    launch=next((x for x in app.findall('activity') if x.get(NS+'name')=='com.partnerstudio.runtime.BrandActivity'),None)
    if launch is None:
        launch=ET.SubElement(app,'activity',{NS+'name':'com.partnerstudio.runtime.BrandActivity',NS+'exported':'true',NS+'theme':'@android:style/Theme.Material.Light.NoActionBar'})
        filt=ET.SubElement(launch,'intent-filter');ET.SubElement(filt,'action',{NS+'name':'android.intent.action.MAIN'});ET.SubElement(filt,'category',{NS+'name':'android.intent.category.LAUNCHER'})
    if not any(x.get(NS+'name')=='com.partnerstudio.runtime.CustomerSettingsActivity' for x in app.findall('activity')):
        ET.SubElement(app,'activity',{NS+'name':'com.partnerstudio.runtime.CustomerSettingsActivity',NS+'exported':'false',NS+'theme':'@android:style/Theme.Material.Light.NoActionBar',NS+'windowSoftInputMode':'adjustResize',NS+'configChanges':'orientation|screenSize|keyboardHidden'})
    if not any(x.get(NS+'name')=='com.partnerstudio.runtime.SalesActivity' for x in app.findall('activity')):
        ET.SubElement(app,'activity',{NS+'name':'com.partnerstudio.runtime.SalesActivity',NS+'exported':'false',NS+'launchMode':'singleTop',NS+'theme':'@android:style/Theme.Material.Light.NoActionBar',NS+'windowSoftInputMode':'adjustResize'})
    if not any(x.get(NS+'name')=='com.partnerstudio.runtime.RefundActivity' for x in app.findall('activity')):
        ET.SubElement(app,'activity',{NS+'name':'com.partnerstudio.runtime.RefundActivity',NS+'exported':'false',NS+'launchMode':'singleTop',NS+'theme':'@android:style/Theme.Material.Light.NoActionBar',NS+'windowSoftInputMode':'adjustResize'})
    tree.write(path,encoding='utf-8',xml_declaration=True)

def signing_key(data_dir,identity):
    if not re.fullmatch(r'(admin|[a-f0-9]{16})',identity):raise ValueError('Geçersiz imzalama kimliği.')
    directory=Path(data_dir)/'keys';directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    path=directory/(identity+'.p12');password_file=directory/(identity+'.password')
    registry=Path(data_dir)/'signing-identities';registry.mkdir(exist_ok=True,mode=0o700)
    marker=registry/(identity+'.sha256')
    database=Path(data_dir)/'studio.sqlite3'
    previous=False
    if database.exists():
        with sqlite3.connect(database) as c:
            previous=c.execute('SELECT 1 FROM builds WHERE company_id=? AND artifact IS NOT NULL LIMIT 1',(identity,)).fetchone() is not None
    if not path.exists() and (marker.exists() or previous):raise RuntimeError('İmzalama anahtarı eksik; eski uygulamalar için yeni anahtar oluşturulmaz. Tam yedekten geri yükleyin.')
    if path.exists():
        if not password_file.exists():raise RuntimeError('İmzalama anahtarı parolası eksik; yedekten geri yükleyin.')
        digest=hashlib.sha256(path.read_bytes()).hexdigest()
        if marker.exists() and marker.read_text()!=digest:raise RuntimeError('İmzalama anahtarı kimliği değişmiş; doğru yedeği geri yükleyin.')
        marker.write_text(digest);marker.chmod(0o600)
        return path,password_file
    if password_file.exists():raise RuntimeError('İmzalama anahtarı eksik; yedekten geri yükleyin. Yeni anahtar oluşturulmadı.')
    password_file.write_text(secrets.token_urlsafe(32));password_file.chmod(0o600)
    env=os.environ.copy();env['STUDIO_KEY_PASSWORD']=password_file.read_text()
    try:
        java=Path(tool('java'));keytool=java.with_name('keytool')
        run([keytool,'-genkeypair','-noprompt','-keystore',path,'-storetype','PKCS12','-storepass:env','STUDIO_KEY_PASSWORD','-keypass:env','STUDIO_KEY_PASSWORD','-alias','studio','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Partner Studio '+identity],env=env)
        path.chmod(0o600)
        marker.write_text(hashlib.sha256(path.read_bytes()).hexdigest());marker.chmod(0o600)
    except Exception:
        if not path.exists():password_file.unlink(missing_ok=True)
        raise
    return path,password_file

def sign_apk(unsigned,output,data_dir,identity,log=None):
    aligned=Path(unsigned).with_suffix('.aligned.apk')
    run([tool('zipalign'),'-f','-p','4',unsigned,aligned],log)
    key,pw=signing_key(data_dir,identity)
    run([tool('java'),'-jar',tool('apksigner'),'sign','--ks',key,'--ks-key-alias','studio','--ks-pass','file:'+str(pw),'--out',output,aligned],log)
    run([tool('java'),'-jar',tool('apksigner'),'verify','--verbose',output],log)
    aligned.unlink(missing_ok=True)
    return Path(output)

def replace_logo(directory,logo):
    directory=Path(directory)
    assets=directory/'assets';assets.mkdir(exist_ok=True)
    (assets/'studio-logo.png').write_bytes(logo)
    for pattern in ['drawable*/ic_ticket.*','mipmap*/ic_ticket.*']:
        for old in (directory/'res').glob(pattern):old.unlink()
    target=directory/'res/drawable';target.mkdir(exist_ok=True)
    (target/'ic_ticket.png').write_bytes(logo)

def build_company(snapshot,version,output_dir,server_url,data_dir):
    from server.store import Store
    company={**snapshot,**Store.validate(snapshot)}
    mt_auth,rx_auth=required_common_auth()
    if any(name not in (ROOT/'runtime.dex').read_bytes() for name in [b'Lcom/partnerstudio/runtime/CustomerSettings;',b'Lcom/partnerstudio/runtime/SalesActivity;',b'Lcom/partnerstudio/runtime/RefundActivity;']):raise RuntimeError('runtime.dex dosyasını bilet satışı güncellemesindeki dosyayla değiştirin.')
    if not re.fullmatch(r'[a-f0-9]{16}',company['id']) or company['package_id']!='com.partnerstudio.f'+company['id']:raise ValueError('Firma kimliği geçersiz.')
    output=Path(output_dir).resolve();output.mkdir(parents=True,exist_ok=True);log=output/'build.log'
    with tempfile.TemporaryDirectory(prefix='studio-build-') as tmp:
        directory=Path(tmp)/'app'
        with zipfile.ZipFile(ROOT/'template.zip') as z:z.extractall(directory)
        validate_sales_template(directory)
        patch_branding(directory,company);patch_api_url(directory,company['api_url']);patch_customer_settings(directory,mt_auth,rx_auth);patch_manifest(directory/'AndroidManifest.xml',company)
        y=directory/'apktool.yml';s=y.read_text();s=re.sub(r'(?m)^  versionCode:.*','  versionCode: '+str(int(version)),s);s=re.sub(r'(?m)^  versionName:.*','  versionName: 1.0.'+str(int(version)),s);y.write_text(s)
        assets=directory/'assets';assets.mkdir(exist_ok=True)
        config={'company_id':company['id'],'package_id':company['package_id'],'name':company['app_name'],'partner_code':company['partner_code'],'api_url':company['api_url'],'color':company['color'],'server_url':server_url.rstrip('/'),'version_code':int(version),'firebase':company.get('firebase',{})}
        if company.get('firebase_project'):
            config['firebase_project']=company['firebase_project'];config['github_repo']=company['github_repo']
        (assets/'studio.json').write_text(json.dumps(config,ensure_ascii=False))
        if company.get('logo'):
            logo=base64.b64decode(company['logo'].split(',',1)[1]);replace_logo(directory,logo)
        unsigned=Path(tmp)/'unsigned.apk'
        run([tool('java'),'-jar',tool('apktool'),'b',directory,'-o',unsigned],log)
        with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as z:
            index=1
            while ('classes.dex' if index==1 else 'classes'+str(index)+'.dex') in z.namelist():index+=1
            z.write(ROOT/'runtime.dex','classes'+str(index)+'.dex')
        return sign_apk(unsigned,output/(company['package_id']+'-'+str(version)+'.apk'),data_dir,company['id'],log)
