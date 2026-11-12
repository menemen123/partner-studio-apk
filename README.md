# Partner Studio — Firebase + GitHub

Bu depo yönetim APK'sından gelen firma derleme isteklerini GitHub Actions standart Linux çalıştırıcısında üretir ve müşteri APK'larını Releases'te dağıtır. Kendi sunucusu, Firebase Storage veya Blaze gerekmez. İnternet ve ücretsiz hizmet kotaları gereklidir. Derleme telefonda değil GitHub Actions'ta gerçekleşir.

## İlk kurulum — yalnızca bir kez

Firebase → Firestore Database → Rules alanına bu paketteki `firestore.rules` içeriğini yapıştırıp Publish deyin. Bu sürüm kuralları şirket verilerini yöneticiye açar, yalnızca güncelleme belgelerini herkese okunabilir tutar ve eski sürüme dönülmesini engeller.

1. `PartnerStudio-GitHub-Kurulum.zip` dosyasını bilgisayarda açın. GitHub deposunda **Add file → Upload files** ile içindeki dosya ve klasörleri yükleyin; `main` dalına commit edin. ZIP dosyasının kendisini yüklemeyin. `.github/workflows/build-apk.yml`, `builder/`, `cloud/`, `server/`, `scripts/`, `runtime.dex`, iki `template-*.part` ve `template-parts.json` depoda olmalı. Her dosya GitHub web yükleme sınırının altındadır.
2. **Eski `PartnerStudio-Kurulum-Paketi.zip` dosyasını yüklemeyin.** O pakette özel imza yedeği vardır. Yalnızca bu yeni GitHub kurulum paketi açık depoya uygun hazırlanmıştır.
3. Telefona `PartnerStudio-Firebase-GitHub.apk` kurun. Eski yönetim uygulamasıyla aynı imza/paket ve daha yüksek sürüm kodunu kullanır. Firebase Authentication'da oluşturduğunuz e-posta/parola ile giriş yapın. Yetkili UID: `feurzeZrRKfSgZSfMN3eP8YSWS72`.
4. Panelde **Kurulum ve bağlantılar** bölümüne girin. İlk kurulum için **Anahtarı oluştur ve kopyala** düğmesine basın. GitHub deposunda **Settings → Secrets and variables → Actions → New repository secret** açın. Adı `STUDIO_MASTER_KEY`, değeri uygulamanın kopyaladığı rastgele 32 baytlık Base64 anahtar olsun. Anahtarı güvenli bir parola yöneticisinde yedekleyin. Anahtarı sohbete veya depodaki dosyalara koymayın.
5. Uygulamadaki secret kurulum onay kutusunu işaretleyip kaydedin. Sonraki telefon kurulumunda GitHub secret'ı zaten varsa değiştirmeyin; mevcut secret'ı koruduğunuzu onaylayın. Yeni bir secret değeriyle değiştirmek önceki firmaların imzalarını açmayı engeller.
6. GitHub → **Settings → Developer settings → Personal access tokens → Fine-grained tokens** bölümünden token oluşturun. Resource owner `menemen123`; **Only select repositories → partner-studio-apk**. Repository permissions: **Contents: Read and write**, **Actions: Read and write**. Token değerini yalnızca telefon panelindeki GitHub belirteci alanına yapıştırın. Süresi dolunca yenileyin. Token şifreli cihaz depolamasında tutulur; GitHub kaynaklarına yazılmaz.
7. Actions sekmesinde iş akışının mevcut olduğundan emin olun. Depo/hesap politikası Actions'ı kapatmışsa etkinleştirin. Ücretli/büyük çalıştırıcı seçmeyin; paket standart `ubuntu-latest` kullanır.

## Günlük kullanım

- Firmayı oluşturun: isim, uygulama adı, partner kodu, logo ve renk.
- Kaydedin; **APK oluştur** düğmesine basın. Beklerken uygulama iş durumunu kontrol eder.
- Üretim hazır olduğunda **APK'yı indir** veya **Sürümü yayınla** seçeneğini kullanın. Sadece üretmek, müşterilerin güncelleme kaydını değiştirmez.
- Yeni bir düzenleme için aynı firmayı kullanın. Yeni firma açmak ayrı paket kimliği üretir.
- Başarısız veya iptal edilmiş işler yayınlanamaz. GitHub Actions iş kaydı hata nedenini gösterir. Kuyruk çok doluysa GitHub bekleyen işleri iptal edebilir; iptal edilen üretimi panelden tekrar başlatın. Sürüm numaralarında atlama olabilir.

## Güncelleme uyarıları

Müşteri APK'sı `partner-studio-967b6` Firestore projesindeki yalnızca kendi `releases/{companyId}` belgesini okur. Uygulama açılışında ve Android'in izin verdiği yaklaşık altı saatlik arka plan kontrollerinde sürüm karşılaştırılır. Yeni sürüm için bildirim izni varsa yerel bildirim gösterilir. Bu anlık FCM push değildir; internet/pil/cihaz kısıtları ve zorla durdurma nedeniyle gecikebilir veya uygulama açılana kadar gerçekleşmeyebilir. Android 13+ bildirim iznini kullanıcı açmalıdır.

APK indirilirken GitHub HTTPS alanlarına ait yönlendirmeler ve SHA-256 özeti doğrulanır. Android bilinmeyen uygulama yükleme izni ve kurulum onayı kullanıcıdan alınır. Silent/zorla güncelleme yoktur.

## Güvenlik ve yedekler

Açık depoda firma adı, logo, partner kodu, derleme istekleri ve müşteri APK'ları bulunur. Bilet API parolaları bu alanlara girilmez; mevcut müşteri uygulamasındaki yetkili Obus ayarlarında yapılandırılır. Sunucudaki bir hizmet hesabı özel anahtarı kullanılmaz.

`signing/{companyId}.enc` dosyaları AES-256-GCM ile şifreli imza yedekleridir. `STUDIO_MASTER_KEY` GitHub secret'ı ile birlikte korunmalıdır. Şifreli yedeği veya ana anahtarı kaybetmek mevcut kurulumların üzerine yeni sürüm kurmayı engeller. Sistem önceki imzasını açamazsa sessizce yeni imza üretmez. GitHub'a yalnızca şifreli yedek yazılır; açık anahtar dosyaları derleme sırasında geçici dizinde tutulur ve iş bitince silinir.

GitHub secret'ları sonradan okunamaz. Yedeklediğiniz anahtarı saklayın. Depoyu, şifreli imza kayıtlarını ve ana anahtarı birlikte kaybetmeyin. Token veya Firebase parolası paylaşılmaz.

## Maliyet

Public depoda standart GitHub-hosted Actions çalıştırıcısı ücretsizdir. GitHub Releases APK dağıtır. Firebase Spark planı ve Firestore ücretsiz kullanım kotaları geçerlidir; bu paket Firebase Storage, Cloud Functions, ücretli çalıştırıcı veya Blaze açmaz. Ücretsiz kota/ağ/servis kısıtları oluşursa işlemler başarısız olabilir; sınırsız veya kesintisiz hizmet garantisi yoktur. Android'in altı saatlik kontrolleri yaklaşık cihaz başına günde dört belge okuması ve açılış okumaları oluşturur.

## Doğrulama durumu

Yerel testlerde şifreli imza geri yükleme ile aynı firmanın iki sürümü üretildi ve aynı sertifikayı koruduğu doğrulandı. Yönetim APK'sı ve müşteri runtime'ı derlendi. Firebase public sürüm belgesi okuma yolu gerçek projede denendi; olmayan belge için beklenen 404 döndü. Kullanıcının Firebase parolası ve GitHub token'ı paylaşılmadığından gerçek hesapla giriş/Actions dispatch/yayınlama uçtan uca çalıştırılmadı. Gerçek Android cihazda doğrulama da bekliyor; emülatörde donanım hızlandırması bulunmadı ve ADB kurulum/açılış tamamlanamadı. Derleme/imza testi cihazda kullanım testinin yerine geçmez.

Bu depo ek Android runtime kaynak projesinin tamamı değildir. `runtime.dex` ve şablon paketlenmiş girdilerdir; müşteri bilet ekranları kullanıcının verdiği APK'dan korunur. Yerel geliştirici kaynakları ayrı çalışma alanında tutulur.
