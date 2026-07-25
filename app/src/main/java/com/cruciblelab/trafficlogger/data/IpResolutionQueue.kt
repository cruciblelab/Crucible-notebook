package com.cruciblelab.trafficlogger.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

data class PendingIpResolution(val ip: String, val firstSeenAt: Long)

/**
 * "İstediğin her IP'yi anında sorgulama" yerine kuyruğa alıp, SADECE uygulama ön plandayken
 * ve yavaş/ekonomik bir hızda arka planda (foreground service değil, normal uygulama
 * ömrü içinde) çözen kuyruk.
 *
 * Tasarım kararları (hepsi bilinçli):
 * - VPN servisi (arka planda 7/24 çalışabilir) bu kuyruğu KULLANMIYOR - sadece yeni IP'yi
 *   kuyruğa YAZIYOR (bellek içi, bedavaya yakın). Asıl ağ isteklerini atan işleyici, sadece
 *   [ProcessLifecycleOwner] "started" (yani en az bir Activity/ekran görünür) olduğunda çalışır.
 *   Uygulama arka plana alınır alınmaz işleyici duruyor - hiçbir ek pil/veri tüketimi olmuyor.
 * - Bir defada tek istek, aralarında [THROTTLE_MS] bekleme - "hepsi aynı anda" değil, "yavaş
 *   yavaş". Zaten [IpInfoRepository] 30 günlük TTL cache kullanıyor, yani her IP ömür boyu
 *   sadece birkaç kez sorgulanıyor.
 * - Her giriş [firstSeenAt] (ilk görülme tarihi) ile tutuluyor - "tarih kaydedilir" isteği.
 */
class IpResolutionQueue(
    private val ipInfoRepository: IpInfoRepository
) : DefaultLifecycleObserver {

    private val queueLock = Any()
    // LinkedHashMap: giriş sırasını korur (FIFO), aynı IP iki kez kuyruğa girmez.
    private val pending = LinkedHashMap<String, Long>()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _lastResolvedAt = MutableStateFlow<Long?>(null)
    val lastResolvedAt: StateFlow<Long?> = _lastResolvedAt.asStateFlow()

    private var workerJob: Job? = null
    private val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** Yeni görülen bir IP'yi kuyruğa ekler. Zaten kuyrukta ya da yakın zamanda çözülmüşse (TTL) iş yapılmaz. */
    fun submit(ip: String) {
        synchronized(queueLock) {
            if (pending.containsKey(ip)) return
            pending[ip] = System.currentTimeMillis()
            _pendingCount.value = pending.size
        }
    }

    /** Uygulama ön plana geldi (en az bir ekran görünür oldu) - işleyiciyi başlat. */
    override fun onStart(owner: LifecycleOwner) {
        if (workerJob?.isActive == true) return
        workerJob = ownerScope.launch {
            while (true) {
                val next = synchronized(queueLock) { pending.entries.firstOrNull() }
                if (next == null) {
                    delay(IDLE_POLL_MS) // kuyruk boşken de düşük frekansta kontrol et, meşgul-bekleme değil
                    continue
                }
                // Ağ isteğini burada, yavaşça atıyoruz - IpInfoRepository zaten önbelleği
                // kontrol edip taze ise ağa hiç çıkmıyor.
                ipInfoRepository.getOrFetch(next.key)
                synchronized(queueLock) {
                    pending.remove(next.key)
                    _pendingCount.value = pending.size
                }
                _lastResolvedAt.value = System.currentTimeMillis()
                delay(THROTTLE_MS) // ekonomik: art arda değil, aralıklı
            }
        }
    }

    /** Uygulama arka plana gitti - işleyiciyi durdur, hiçbir arka plan ağ/pil tüketimi kalmasın. */
    override fun onStop(owner: LifecycleOwner) {
        workerJob?.cancel()
        workerJob = null
    }

    private companion object {
        const val THROTTLE_MS = 3_000L
        const val IDLE_POLL_MS = 5_000L
    }
}
