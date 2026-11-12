#!/usr/bin/env python3
"""Install pinned, official Android packaging tools into this project (Linux x86_64)."""
import argparse,hashlib,os,platform,shutil,sys,tempfile,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def install():
 if platform.system()!='Linux' or platform.machine() not in ('x86_64','AMD64'):raise SystemExit('Sunucu Linux x86_64 olmalı. Docker seçeneğini kullanabilirsiniz.')
 if not shutil.which('java'):raise SystemExit('Java 17+ gerekli. Önce işletim sisteminizden OpenJDK JRE kurun.')
 tools=ROOT/'tools';tools.mkdir(exist_ok=True)
 targets=[('https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar','apktool.jar'),('https://dl.google.com/android/repository/build-tools_r35_linux.zip','build-tools.zip')]
 for url,name in targets:
  output=tools/name
  if output.exists():continue
  print('İndiriliyor:',name,flush=True)
  temp=output.with_suffix('.part');urllib.request.urlretrieve(url,temp);temp.replace(output)
 with zipfile.ZipFile(tools/'build-tools.zip') as z:
  for name in ['android-15/zipalign','android-15/lib/apksigner.jar','android-15/lib64/libc++.so']:
   relative=Path(name).relative_to('android-15');out=tools/relative;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(z.read(name))
 (tools/'zipalign').chmod(0o755);shutil.copy2(tools/'lib/apksigner.jar',tools/'apksigner.jar')
 (tools/'build-tools.zip').unlink(missing_ok=True)
 print('Derleme araçları hazır. İmzalama anahtarları üretimde data/keys/ içinde oluşturulur.')
if __name__=='__main__':install()
