package com.cruciblelab.trafficlogger.data

import com.cruciblelab.trafficlogger.util.SigningCertResolver
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IntegrityVerdict {
    /** Bu cihazda bu paket için ilk kez bir imza kaydedildi - referans olarak alındı. */
    FIRST_SEEN,
    /** İmza, daha önce kaydedilenle eşleşiyor - normal. */
    MATCH,
    /**
     * KRİTİK: paket adı aynı ama imzalayan sertifika değişti. Ya uygulama meşru şekilde
     * yeniden imzalandı (nadir, genelde sadece geliştiricinin kendi anahtar rotasyonuyla
     * olur) ya da paket kaldırılıp yerine FARKLI (taklit/değiştirilmiş) bir APK kondu.
     * Bu, "sistem/güvenilir görünüyor ama aslında değil" senaryosunun asıl yakalandığı yer.
     */
    SIGNATURE_MISMATCH,
    /** Sertifika okunamadı (izin/OEM kısıtı vb.) - ne doğrulama ne de alarm mümkün. */
    UNRESOLVED
}

data class IntegrityResult(val verdict: IntegrityVerdict, val previousHash: String?, val currentHash: String?)

/**
 * Trust-on-first-use imza pinleme. Sabit/önceden bilinen referans hash listesi KASITLI
 * OLARAK yok - bkz. [SigningCertResolver] dokümantasyonu. Bunun yerine her paket için "ilk
 * gördüğümüz imza" bu cihazda referans alınır.
 *
 * Önemli sınır: bir saldırgan uygulamayı SİZ HİÇ GERÇEĞİNİ KURMADAN önce sahte sürümü ilk
 * kez kurarsa, o sahte imza "ilk görülen/referans" olarak kaydedilir ve gerçek uygulama
 * sonradan kurulsa bile onu "değişti" diye işaretlemez ki bu ideal değil ama en azından
 * "kurulduktan sonra sessizce değiştirilme" (post-install tampering/re-signing) senaryosunu
 * güvenilir şekilde yakalar - ki asıl endişe edilen taklit/değiştirme senaryosu genelde budur.
 */
class PackageIntegrityRepository(
    private val dao: PackageSignatureDao,
    private val signingCertResolver: SigningCertResolver
) {
    // Her uygulama oturumunda paket başına tek PackageManager çağrısı yeterli.
    private val sessionCache = ConcurrentHashMap<String, IntegrityResult>()

    private val _mismatchedPackages = MutableStateFlow<Set<String>>(emptySet())
    /**
     * Şu anda SIGNATURE_MISMATCH durumunda olduğu bilinen paketler. [AppCategoryClassifier]
     * gibi senkron/UI-thread kodun her render'da suspend fonksiyon beklemesine gerek kalmadan
     * kullanabilmesi için reaktif bir küme olarak tutulur - gerçek kontrol [ensureChecked] ile
     * arka planda yapılır, sonucu buraya yazılır.
     */
    val mismatchedPackages: StateFlow<Set<String>> = _mismatchedPackages.asStateFlow()

    /**
     * Bir paketi arka planda kontrol etmeyi tetikler (fire-and-forget); sonucu bekletmeden
     * senkron UI kodunun kullanabilmesi için [mismatchedPackages] üzerinden yayınlar.
     * Çağıran taraf kendi coroutine scope'unda (örn. viewModelScope.launch) sarmalamalı.
     */
    suspend fun ensureChecked(packageName: String) {
        val result = check(packageName)
        if (result.verdict == IntegrityVerdict.SIGNATURE_MISMATCH) {
            _mismatchedPackages.value = _mismatchedPackages.value + packageName
        }
    }

    suspend fun check(packageName: String): IntegrityResult {
        sessionCache[packageName]?.let { return it }

        val currentHash = signingCertResolver.resolveSignatureHash(packageName)
        val result = if (currentHash == null) {
            IntegrityResult(IntegrityVerdict.UNRESOLVED, previousHash = null, currentHash = null)
        } else {
            val existing = dao.find(packageName)
            when {
                existing == null -> {
                    dao.upsert(PackageSignatureRecord(packageName = packageName, signatureSha256 = currentHash))
                    IntegrityResult(IntegrityVerdict.FIRST_SEEN, previousHash = null, currentHash = currentHash)
                }
                existing.signatureSha256 == currentHash -> {
                    dao.touch(packageName, System.currentTimeMillis())
                    IntegrityResult(IntegrityVerdict.MATCH, previousHash = existing.signatureSha256, currentHash = currentHash)
                }
                else -> {
                    // Bilerek ÜZERİNE YAZMIYORUZ: eski hash saklı kalmalı ki kullanıcı isterse
                    // "önceki imza neydi" bilgisini görebilsin. Onay/reddetme kullanıcıya kalmış
                    // bir UI aksiyonu olmalı (şimdilik sadece işaretliyoruz, otomatik silmiyoruz).
                    IntegrityResult(IntegrityVerdict.SIGNATURE_MISMATCH, previousHash = existing.signatureSha256, currentHash = currentHash)
                }
            }
        }

        sessionCache[packageName] = result
        return result
    }

    /** Kullanıcı "yeni imzayı onaylıyorum" dediğinde (örn. gerçekten kendi güncellediyse) çağrılır. */
    suspend fun acceptNewSignature(packageName: String, newHash: String) {
        dao.upsert(PackageSignatureRecord(packageName = packageName, signatureSha256 = newHash))
        sessionCache.remove(packageName)
        _mismatchedPackages.value = _mismatchedPackages.value - packageName
    }
}
