"""Persistent tenant drafts, immutable build snapshots and authenticated sessions."""
import base64
import hashlib
import hmac
import json
import re
import secrets
import sqlite3
import struct
import time
from pathlib import Path

class ValidationError(ValueError): pass

class Store:
    def __init__(self, root):
        self.root=Path(root).resolve(); self.root.mkdir(parents=True,exist_ok=True)
        self.db=self.root/'studio.sqlite3'
        with self.connect() as c:
            c.executescript('''
            CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS companies(id TEXT PRIMARY KEY,body TEXT NOT NULL,created REAL NOT NULL);
            CREATE TABLE IF NOT EXISTS builds(id TEXT PRIMARY KEY,company_id TEXT NOT NULL,version_code INTEGER NOT NULL,status TEXT NOT NULL,snapshot TEXT NOT NULL,created REAL NOT NULL,artifact TEXT,sha256 TEXT,error TEXT,UNIQUE(company_id,version_code));
            CREATE TABLE IF NOT EXISTS published(company_id TEXT PRIMARY KEY,build_id TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY,expires REAL NOT NULL);
            ''')
            c.execute("UPDATE builds SET status='failed',error='Sunucu yeniden başlatıldı; üretimi tekrar başlatın.' WHERE status IN ('queued','building')")
    def connect(self):
        c=sqlite3.connect(self.db,timeout=30);c.row_factory=sqlite3.Row
        c.execute('PRAGMA journal_mode=WAL');return c
    def configured(self):
        with self.connect() as c:return c.execute("SELECT 1 FROM settings WHERE key='password'").fetchone() is not None
    def configure_password(self,password):
        if not isinstance(password,str) or len(password)<12:raise ValidationError('Parola en az 12 karakter olmalı.')
        salt=secrets.token_hex(16);digest=hashlib.scrypt(password.encode(),salt=salt.encode(),n=16384,r=8,p=1).hex()
        with self.connect() as c:
            c.execute("INSERT OR REPLACE INTO settings VALUES('password',?)",(salt+':'+digest,));c.execute('DELETE FROM sessions')
    def login(self,password):
        if not isinstance(password,str) or len(password)>1024:return None
        with self.connect() as c:
            row=c.execute("SELECT value FROM settings WHERE key='password'").fetchone()
            if not row:return None
            salt,expected=row[0].split(':');actual=hashlib.scrypt(password.encode(),salt=salt.encode(),n=16384,r=8,p=1).hex()
            if not hmac.compare_digest(expected,actual):return None
            token=secrets.token_urlsafe(32)
            c.execute('DELETE FROM sessions WHERE expires < ?',(time.time(),))
            c.execute('INSERT INTO sessions VALUES(?,?)',(hashlib.sha256(token.encode()).hexdigest(),time.time()+43200));return token
    def authenticated(self,token):
        if not token:return False
        with self.connect() as c:return c.execute('SELECT 1 FROM sessions WHERE token=? AND expires>?',(hashlib.sha256(token.encode()).hexdigest(),time.time())).fetchone() is not None
    def logout(self,token):
        with self.connect() as c:c.execute('DELETE FROM sessions WHERE token=?',(hashlib.sha256(token.encode()).hexdigest(),))
    @staticmethod
    def validate(data):
        if not isinstance(data,dict):raise ValidationError('Firma bilgileri nesne olmalı.')
        out={}
        for k,limit in [('name',80),('app_name',60),('partner_code',80),('color',7)]:
            v=data.get(k,'')
            if not isinstance(v,str) or not v.strip() or len(v)>limit or any(ord(x)<32 for x in v):raise ValidationError(k+' geçersiz.')
            out[k]=v.strip()
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',out['partner_code']):raise ValidationError('Partner kodunda yalnızca harf, rakam, nokta, tire ve alt çizgi kullanılabilir.')
        if not re.fullmatch(r'#[0-9a-fA-F]{6}',out['color']):raise ValidationError('Renk #RRGGBB biçiminde olmalı.')
        logo=data.get('logo','')
        if not isinstance(logo,str) or len(logo)>1400000:raise ValidationError('Logo çok büyük; en fazla 1 MB PNG kullanın.')
        if logo:
            if not logo.startswith('data:image/png;base64,'):raise ValidationError('Logo PNG olmalı.')
            try:
                raw=base64.b64decode(logo.split(',',1)[1],validate=True)
                from server.png import validate_png
                validate_png(raw)
            except Exception:raise ValidationError('PNG logo geçersiz veya boyutları desteklenmiyor.')
        out['logo']=logo
        firebase=data.get('firebase',{})
        if not isinstance(firebase,dict):raise ValidationError('Firebase ayarları geçersiz.')
        if firebase:
            firebase={k:firebase.get(k,'') for k in ['api_key','app_id','sender_id','project_id']}
            for k,v in firebase.items():
                if not isinstance(v,str) or not v or len(v)>200 or any(ord(x)<33 for x in v):raise ValidationError('Firebase '+k+' geçersiz.')
            if not re.fullmatch(r'[a-zA-Z0-9-]+',firebase['project_id']):raise ValidationError('Firebase proje kimliği geçersiz.')
        out['firebase']=firebase
        return out
    def create_company(self,data):
        body=self.validate(data);body['id']=secrets.token_hex(8);body['package_id']='com.partnerstudio.f'+body['id'];body['created']=time.time()
        with self.connect() as c:c.execute('INSERT INTO companies VALUES(?,?,?)',(body['id'],json.dumps(body,ensure_ascii=False),body['created']))
        return body
    def list_companies(self):
        with self.connect() as c:return [json.loads(x['body']) for x in c.execute('SELECT body FROM companies ORDER BY created DESC')]
    def get_company(self,id):
        with self.connect() as c:r=c.execute('SELECT body FROM companies WHERE id=?',(id,)).fetchone()
        if not r:raise KeyError('Firma bulunamadı.')
        return json.loads(r[0])
    def update_company(self,id,data):
        with self.connect() as c:
            c.execute('BEGIN IMMEDIATE')
            r=c.execute('SELECT body FROM companies WHERE id=?',(id,)).fetchone()
            if not r:raise KeyError('Firma bulunamadı.')
            old=json.loads(r[0]);old.update(self.validate({**old,**data}))
            c.execute('UPDATE companies SET body=? WHERE id=?',(json.dumps(old,ensure_ascii=False),id));return old
    def create_build(self,id):
        with self.connect() as c:
            c.execute('BEGIN IMMEDIATE')
            r=c.execute('SELECT body FROM companies WHERE id=?',(id,)).fetchone()
            if not r:raise KeyError('Firma bulunamadı.')
            if c.execute("SELECT COUNT(*) FROM builds WHERE status IN ('queued','building')").fetchone()[0]>=20:raise ValidationError('Üretim kuyruğu dolu; tamamlanmasını bekleyin.')
            n=c.execute('SELECT COALESCE(MAX(version_code),0)+1 FROM builds WHERE company_id=?',(id,)).fetchone()[0]
            bid=secrets.token_hex(12)
            c.execute('INSERT INTO builds(id,company_id,version_code,status,snapshot,created) VALUES(?,?,?,?,?,?)',(bid,id,n,'queued',r[0],time.time()))
        return self.get_build(bid)
    def set_build_status(self,id,status,error=None):
        if status not in ('building','failed'):raise ValidationError('Geçersiz durum.')
        with self.connect() as c:c.execute('UPDATE builds SET status=?,error=? WHERE id=?',(status,error,id))
    def get_build(self,id):
        with self.connect() as c:r=c.execute('SELECT * FROM builds WHERE id=?',(id,)).fetchone()
        if not r:raise KeyError('Sürüm bulunamadı.')
        d=dict(r);d['snapshot']=json.loads(d['snapshot']);return d
    def list_builds(self,company_id):
        with self.connect() as c:ids=[r[0] for r in c.execute('SELECT id FROM builds WHERE company_id=? ORDER BY version_code DESC',(company_id,))]
        return [self.get_build(i) for i in ids]
    def finish_build(self,id,path):
        path=Path(path).resolve()
        if not path.is_relative_to(self.root) or not path.is_file():raise ValidationError('APK çıktısı bulunamadı.')
        sha=hashlib.sha256(path.read_bytes()).hexdigest()
        with self.connect() as c:c.execute("UPDATE builds SET status='ready',artifact=?,sha256=?,error=NULL WHERE id=?",(str(path.relative_to(self.root)),sha,id))
    def publish(self,id):
        with self.connect() as c:
            c.execute('BEGIN IMMEDIATE')
            row=c.execute('SELECT * FROM builds WHERE id=?',(id,)).fetchone()
            if not row:raise KeyError('Sürüm bulunamadı.')
            if row['status']!='ready' or not row['artifact']:raise ValidationError('Yalnızca başarıyla üretilen APK yayınlanabilir.')
            artifact=self.root/row['artifact']
            if not artifact.is_file() or hashlib.sha256(artifact.read_bytes()).hexdigest()!=row['sha256']:raise ValidationError('APK bütünlük doğrulaması başarısız.')
            previous=c.execute('SELECT b.version_code FROM published p JOIN builds b ON b.id=p.build_id WHERE p.company_id=?',(row['company_id'],)).fetchone()
            if previous and previous[0]>row['version_code']:raise ValidationError('Eski sürüme geri yayın yapılamaz; yeni APK oluşturun.')
            c.execute('INSERT OR REPLACE INTO published VALUES(?,?)',(row['company_id'],id))
        return self.get_build(id)
    def latest(self,company_id):
        with self.connect() as c:r=c.execute('SELECT build_id FROM published WHERE company_id=?',(company_id,)).fetchone()
        return self.get_build(r[0]) if r else None
