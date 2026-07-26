# Canlı Ağ Trafiği (Network Traffic Logger)

Cihazdan geçen DNS trafiğini pasif olarak izleyen, üçüncü parti bağımlılık
içermeyen (yalnızca AndroidX/Google kütüphaneleri) bir Android uygulaması.
Kotlin + Jetpack Compose ile yazılmıştır.

## ⚠️ Sorumluluk Reddi / Uyarı

- **Bu uygulama bir antivirüs, güvenlik duvarı veya güvenlik ürünü değildir.**
  Kötü amaçlı yazılımları tespit etme, engelleme veya cihazı koruma garantisi
  vermez. Yalnızca DNS trafiğini gözlemleyip görselleştiren bir araçtır.
- IP/ASN/organizasyon bilgileri (`ipwho.is`) ve "İtibar Veritabanları"
  (hazır/preset veya kullanıcı tarafından içe aktarılan listeler) **dış ve/veya
  topluluk kaynaklıdır**. Bu veriler hatalı, eksik, güncel olmayan veya
  yanıltıcı olabilir; doğruluğu hiçbir şekilde garanti edilmez.
- Bir uygulamanın "TRUSTED / Güvenilir" olarak işaretlenmesi, o uygulamanın
  zararsız olduğunun kanıtı **değildir**; "FLAGGED / İşaretli" olması da o
  uygulamanın kesin olarak zararlı olduğu anlamına **gelmez**. Kötü amaçlı bir
  yazılım kendini bilinen/güvenilir bir paket adına benzeterek bu tür
  listeleri atlatabilir (spoofing).
- "Otomatik engelle" özelliği bu itibar verilerine dayanır; dolayısıyla yanlış
  pozitif (zararsız bir uygulamayı engelleme) veya yanlış negatif (zararlı bir
  uygulamayı kaçırma) üretebilir. Kritik kararları yalnızca bu uygulamanın
  verisine dayanarak almayın.
- Uygulama "olduğu gibi" (as-is), açık veya zımni hiçbir garanti verilmeksizin
  sunulur; kullanım sorumluluğu kullanıcıya aittir. Ayrıntılar için `LICENSE`
  dosyasına bakın.

## Mimari (ilk aşama)

- `TrafficVpnService`: `VpnService` tabanlı, foreground service olarak çalışan
  local-loopback bir VPN. Cihazın gerçek DNS sunucu adreslerini TUN arayüzüne
  yönlendirir, yalnızca UDP/53 sorgularını okur; her sorguyu `protect()`
  edilmiş bir soket üzerinden gerçek DNS sunucusuna iletir ve gelen cevabı
  aynen TUN'a geri yazar. Trafik hiçbir sunucuya yönlendirilmez, sadece
  okunup gerçek ağa bırakılır.
- UID → uygulama eşlemesi `ConnectivityManager.getConnectionOwnerUid` (API 29+)
  ve `PackageManager.getPackagesForUid` ile yapılır.
- Yakalanan kayıtlar Room veritabanında (`TrafficEntry`) tutulur; liste ve
  detay ekranları Jetpack Compose ile gösterilir. Detay ekranından
  "Google'da Ara" ve "Uygulama Ayarları" aksiyonlarına gidilebilir.
- Ayarlar ekranında kayıt saklama süresi (1/7/30 gün) seçilebilir; eski
  kayıtlar arka planda periyodik olarak temizlenir.
- Ayarlar ekranında günlük veri kullanım limiti belirlenebilir; limit
  aşıldığında bildirim gönderilir.
- "İstatistikler" ekranında uygulama/hedef bazında trafik özetleri gösterilir.

## Engelleme kuralları

"Kurallar" ekranından paket adı, domain veya IP bazlı engelleme/izin
kuralları eklenebilir. Trafik listesinden bir kayıt üzerinden de doğrudan
"engelle" veya "izin ver" kuralı oluşturulabilir.

## İtibar veritabanları (deneysel)

"İtibar Veritabanları" ekranı, paket adlarını TRUSTED/FLAGGED olarak
işaretleyen listeleri yönetir: uygulamayla gelen hazır bir "bilinen
yayıncılar" listesi yüklenebilir ya da kullanıcı kendi JSON dosyasını içe
aktarabilir. FLAGGED olarak işaretlenmiş bir paket için "otomatik engelle"
açılabilir. **Bu veriler dış/topluluk kaynaklıdır ve doğruluğu garanti
edilmez** — ayrıntı için yukarıdaki Sorumluluk Reddi bölümüne bakın.

## IP bilgisi (ASN / organizasyon / ülke)

Liste ve detay ekranlarında her hedef IP için ASN/organizasyon adı ve ülke
bayrağı gösterilir. Bunlar `https://ipwho.is` üzerinden (anahtarsız, ücretsiz)
tek IP'lik sorgularla anlık olarak çekilir ve cihazda Room'da 30 gün önbelleğe
alınır; aynı IP tekrar sorgulanmaz. Özel/yerel IP aralıkları (10.x, 192.168.x,
172.16-31.x, 127.x, CGNAT 100.64/10) hiç ağa çıkmadan "Yerel ağ" olarak
işaretlenir. Sorgu isteği başarısız olursa (İnternet yok, servis yanıt
vermiyor) satırda sadece IP/domain gösterilmeye devam edilir.

## Kısıtlar

- VPN API'si aktifken cihazdaki başka bir gerçek VPN uygulaması aynı anda
  çalışamaz (ayarlar ekranında uyarılır).
- Şifreli (HTTPS/TLS) trafiğin içeriği okunamaz; yalnızca domain/IP/port ve
  veri miktarı görülebilir.
- Root gerektirmez.
- DNS sorgularının yanı sıra, DNS bu VPN'i atlayan bağlantılar (DoH/DoT, ya da IP'yi
  doğrudan bilen uygulamalar) için de TLS ClientHello'dan SNI (Server Name Indication)
  ayrıştırılarak hedef domain tespit edilir - bkz. `TlsSni`. Ayarlar ekranından bilinen
  genel DoH sunucularını (Google/Cloudflare/Quad9 vb.) tamamen engelleme seçeneği de
  bulunur - bkz. `DohProviders`. İkisi de en iyi çaba (best-effort): parçalanmış bir
  ClientHello ya da listede olmayan özel bir DoH sunucusu bu şekilde yakalanmaz.

## Derleme

```
./gradlew assembleDebug
```

GitHub Actions üzerinde `.github/workflows/android-build.yml` her push/PR'da
debug APK'yı derler ve artifact olarak yükler.

## Geliştirici

- **Geliştirici / Kuruluş:** Cruciblelab
- **Yetkili:** Fırat Coşkun
- **İletişim:** cruciblelab@hotmail.com

> Not: Bu bilgiler Play Console'daki "Mağaza Ayarları / Geliştirici İletişim
> Bilgileri" alanlarına ayrıca girilmeli - kod içindeki bu README, Play
> Store'daki listelemeyi otomatik güncellemez.
