"""Per-company signing backup, encrypted before entering a public repository."""
import base64,hashlib,json,os,re
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

def master_key(value):
    try:key=base64.b64decode(value,validate=True)
    except Exception:raise ValueError('STUDIO_MASTER_KEY geçerli Base64 olmalı.')
    if len(key)!=32:raise ValueError('STUDIO_MASTER_KEY 32 rastgele bayt olmalı.')
    return key

def paths(cid):
    if not re.fullmatch('[a-f0-9]{16}',cid):raise ValueError('Firma kimliği geçersiz.')
    return ['keys/'+cid+'.p12','keys/'+cid+'.password','signing-identities/'+cid+'.sha256']

def restore_vault(data_dir,signing_dir,cid,key):
    names=paths(cid);k=master_key(key);source=Path(signing_dir)/(cid+'.enc');marker=Path(signing_dir)/(cid+'.identity')
    if not source.exists():
        if marker.exists():raise ValueError('Şifreli imza yedeği kayıp. Yeni anahtar oluşturulmadı.')
        return False
    if not marker.exists():raise ValueError('İmza kimliği kaydı eksik; tam yedeği geri yükleyin.')
    raw=source.read_bytes()
    try:
        if raw[:7]!=b'PSKEYS1':raise ValueError()
        data=json.loads(AESGCM(k).decrypt(raw[7:19],raw[19:],cid.encode()))
        if set(data)!=set(names):raise ValueError()
        decoded={n:base64.b64decode(data[n],validate=True) for n in names}
        if hashlib.sha256(decoded[names[0]]).hexdigest()!=marker.read_text().strip():raise ValueError()
    except Exception:raise ValueError('İmza yedeği açılamadı. Ana anahtar yanlış veya yedek bozuk; yeni imza üretilmedi.')
    for n,content in decoded.items():
        target=Path(data_dir)/n;target.parent.mkdir(parents=True,exist_ok=True,mode=0o700);target.write_bytes(content);target.chmod(0o600)
    return True

def save_vault(data_dir,signing_dir,cid,key):
    names=paths(cid);k=master_key(key);files={n:(Path(data_dir)/n).read_bytes() for n in names};digest=hashlib.sha256(files[names[0]]).hexdigest()
    dest=Path(signing_dir);dest.mkdir(parents=True,exist_ok=True);marker=dest/(cid+'.identity');output=dest/(cid+'.enc')
    if marker.exists():
        if marker.read_text().strip()!=digest or not output.exists():raise ValueError('İmza kaydı değişmiş veya eksik; yayın durduruldu.')
        return False
    if output.exists():raise ValueError('Kimlik kaydı olmayan imza arşivi bulundu.')
    payload=json.dumps({n:base64.b64encode(b).decode() for n,b in files.items()}).encode();nonce=os.urandom(12)
    output.write_bytes(b'PSKEYS1'+nonce+AESGCM(k).encrypt(nonce,payload,cid.encode()));marker.write_text(digest+'\n');return True
