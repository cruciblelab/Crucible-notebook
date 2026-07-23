# Ağ Trafiği Defteri (Network Traffic Logger)

Cihazdan geçen DNS trafiğini pasif olarak izleyen, üçüncü parti bağımlılık
içermeyen (yalnızca AndroidX/Google kütüphaneleri) bir Android uygulaması.
Kotlin + Jetpack Compose ile yazılmıştır.

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

## Kısıtlar

- VPN API'si aktifken cihazdaki başka bir gerçek VPN uygulaması aynı anda
  çalışamaz (ayarlar ekranında uyarılır).
- Şifreli (HTTPS/TLS) trafiğin içeriği okunamaz; yalnızca domain/IP/port ve
  veri miktarı görülebilir.
- Root gerektirmez.
- Bu ilk aşamada yalnızca DNS sorguları yakalanır; TLS ClientHello SNI
  ayrıştırma ve genel TCP/UDP trafiği yakalama sonraki aşamalarda eklenecektir.

## Derleme

```
./gradlew assembleDebug
```

GitHub Actions üzerinde `.github/workflows/android-build.yml` her push/PR'da
debug APK'yı derler ve artifact olarak yükler.
