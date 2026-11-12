from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[1]
info=json.loads((root/'template-parts.json').read_text())
out=root/'template.zip';tmp=out.with_suffix('.tmp')
with tmp.open('wb') as f:
 for name in info['parts']:f.write((root/name).read_bytes())
if hashlib.sha256(tmp.read_bytes()).hexdigest()!=info['sha256']:tmp.unlink();raise SystemExit('Şablon parçaları doğrulanamadı.')
tmp.replace(out)
print('APK template restored.')
