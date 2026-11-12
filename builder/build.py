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
    if not re.fullmatch(r'[a-f0-9]{16}',company['id']) or company['package_id']!='com.partnerstudio.f'+company['id']:raise ValueError('Firma kimliği geçersiz.')
    output=Path(output_dir).resolve();output.mkdir(parents=True,exist_ok=True);log=output/'build.log'
    with tempfile.TemporaryDirectory(prefix='studio-build-') as tmp:
        directory=Path(tmp)/'app'
        with zipfile.ZipFile(ROOT/'template.zip') as z:z.extractall(directory)
        patch_branding(directory,company);patch_api_url(directory,company['api_url']);patch_manifest(directory/'AndroidManifest.xml',company)
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
