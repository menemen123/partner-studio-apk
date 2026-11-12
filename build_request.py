#!/usr/bin/env python3
"""Actions entry point. Input never contains credentials or signing keys."""
import argparse,base64,hashlib,json,os,re,subprocess,sys,tempfile,urllib.error,urllib.request
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from server.store import Store
from cloud.vault import restore_vault,save_vault,master_key
from builder.build import build_company,ROOT
REPO='menemen123/partner-studio-apk';PROJECT='partner-studio-967b6'

def validate_request(d):
    if not isinstance(d,dict) or not re.fullmatch('[a-f0-9]{24}',str(d.get('request_id',''))):raise ValueError('İstek kimliği geçersiz.')
    v=d.get('version_code')
    if type(v)!=int or not 1<=v<=2100000000:raise ValueError('Sürüm kodu geçersiz.')
    original=d.get('company',{});c=Store.validate(original);cid=original.get('id','')
    if not re.fullmatch('[a-f0-9]{16}',cid) or original.get('package_id')!='com.partnerstudio.f'+cid:raise ValueError('Paket/firma kimliği eşleşmedi.')
    c.update(id=cid,package_id=original['package_id'],firebase_project=PROJECT,github_repo=REPO)
    return {'request_id':d['request_id'],'company':c,'version_code':v}

def github(path,body=None,method=None):
    token=os.environ['GITHUB_TOKEN'];url='https://api.github.com/repos/'+REPO+path
    req=urllib.request.Request(url,data=json.dumps(body).encode() if body is not None else None,method=method,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=40) as r:return json.load(r)

def upload_asset(release,file):
    import urllib.parse
    url=release['upload_url'].split('{')[0]+'?name='+urllib.parse.quote(file.name)
    if not url.startswith('https://uploads.github.com/repos/'+REPO+'/releases/'):raise ValueError('Beklenmeyen GitHub yükleme adresi.')
    req=urllib.request.Request(url,data=file.read_bytes(),headers={'Authorization':'Bearer '+os.environ['GITHUB_TOKEN'],'Content-Type':'application/octet-stream','X-GitHub-Api-Version':'2022-11-28'},method='POST')
    with urllib.request.urlopen(req,timeout=120) as r:return json.load(r)

def main():
    p=argparse.ArgumentParser();p.add_argument('--request',required=True);p.add_argument('--local',action='store_true');a=p.parse_args()
    if not re.fullmatch('[a-f0-9]{24}',a.request):raise ValueError('Geçersiz istek.')
    if not a.local and os.environ.get('GITHUB_REPOSITORY')!=REPO:raise ValueError('Yanlış depo.')
    raw=(ROOT/'requests'/(a.request+'.json')).read_bytes()
    if len(raw)>1500000:raise ValueError('İstek çok büyük.')
    d=validate_request(json.loads(raw));cid=d['company']['id'];version=d['version_code'];tag=cid+'-v'+str(version)
    secret=os.environ.get('STUDIO_MASTER_KEY','');master_key(secret)
    if not a.local:
        try:github('/releases/tags/'+tag)
        except urllib.error.HTTPError as e:
            if e.code!=404:raise
        else:raise ValueError('Bu sürüm zaten var; üzerine yazılmadı.')
    with tempfile.TemporaryDirectory(prefix='studio-signing-') as tmp:
        restored=restore_vault(tmp,ROOT/'signing',cid,secret)
        if not restored and not a.local:
            # A removed archive and marker must not silently replace an already released identity.
            page=1
            while True:
                releases=github('/releases?per_page=100&page='+str(page))
                if any(r.get('tag_name','').startswith(cid+'-v') for r in releases):raise ValueError('Önceki sürüm var, imza yedeği yok. Tam yedekten geri yükleyin.')
                if len(releases)<100:break
                page+=1
        output=ROOT/'build/cloud'/a.request;output.mkdir(parents=True,exist_ok=True)
        apk=build_company(d['company'],version,output,'',tmp)
        changed=save_vault(tmp,ROOT/'signing',cid,secret)
        if not a.local and changed:
            for command in [['git','config','user.name','github-actions[bot]'],['git','config','user.email','41898282+github-actions[bot]@users.noreply.github.com'],['git','add','-f','--','signing/'+cid+'.enc','signing/'+cid+'.identity'],['git','commit','-m','build: preserve encrypted signing identity '+cid],['git','pull','--rebase','origin','main'],['git','push','origin','HEAD:main']]:subprocess.run(command,cwd=ROOT,check=True,stdout=subprocess.DEVNULL)
        filename=apk.name
        manifest={'company_id':cid,'package_id':d['company']['package_id'],'version_code':version,'version_name':'1.0.'+str(version),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'download_url':'https://github.com/'+REPO+'/releases/download/'+tag+'/'+filename}
        meta=output/'release.json';meta.write_text(json.dumps(manifest,ensure_ascii=False))
        if not a.local:
            release=github('/releases',{'tag_name':tag,'target_commitish':'main','name':d['company']['app_name']+' 1.0.'+str(version),'body':'Partner Studio build '+a.request,'draft':False,'prerelease':False})
            upload_asset(release,apk);upload_asset(release,meta)
        print(json.dumps({'ready':True,'release':manifest},ensure_ascii=False))
if __name__=='__main__':main()
