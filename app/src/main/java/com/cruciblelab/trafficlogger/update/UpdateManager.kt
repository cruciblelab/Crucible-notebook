package com.cruciblelab.trafficlogger.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    /** [progress] null = toplam boyut bilinmiyor (belirsiz ilerleme). */
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState
    /** İndirildi ama Android bu uygulamaya henüz "bilinmeyen uygulama yükleme" izni vermedi. */
    data class NeedsInstallPermission(val release: ReleaseInfo) : UpdateState
    /** İndirildi, sistem yükleyicisi açıldı; kullanıcı iptal ettiyse tekrar denenebilir. */
    data class ReadyToInstall(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * Uygulama içi güncelleme akışı: GitHub'daki son sürümü denetle -> APK'yı önbelleğe indir ->
 * paketin gerçekten bu uygulamanın daha yeni bir sürümü olduğunu doğrula -> sistem
 * yükleyicisini aç. İmza kontrolünü Android'in kendisi yapar: APK kurulu sürümle aynı anahtarla
 * imzalanmamışsa yükleyici reddeder, yani başkası bizim adımıza güncelleme yayınlayamaz.
 */
class UpdateManager(context: Context) {

    private class UpdateException(message: String) : Exception(message)

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updatesDir = File(appContext.cacheDir, "updates")
    private var activeJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Güncelleme diyaloğu açık mı. Ayarlar ekranı durumu pasif gösterir, etkileşim diyalogda. */
    private val _promptVisible = MutableStateFlow(false)
    val promptVisible: StateFlow<Boolean> = _promptVisible.asStateFlow()

    /** "Sonra" denilen sürüm, bu oturumdaki otomatik denetimlerde tekrar sorulmaz. */
    @Volatile private var dismissedVersionCode: Long? = null

    val currentVersionName: String
    private val currentVersionCode: Long

    init {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        currentVersionName = info.versionName ?: "?"
        currentVersionCode = PackageInfoCompat.getLongVersionCode(info)
        // Önceki bir oturumdan kalan (kurulmuş ya da yarım kalmış) indirmeler artık gereksiz.
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    fun check(userInitiated: Boolean) {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            _state.value = UpdateState.Checking
            val result = try {
                val latest = GithubReleases.fetchLatest()
                if (latest == null || latest.versionCode <= currentVersionCode) {
                    UpdateState.UpToDate
                } else {
                    UpdateState.Available(latest)
                }
            } catch (e: GithubReleases.RateLimitedException) {
                UpdateState.Failed("GitHub istekleri geçici olarak sınırladı. Biraz sonra tekrar deneyin.")
            } catch (e: Exception) {
                UpdateState.Failed("Güncelleme sunucusuna ulaşılamadı. İnternet bağlantınızı kontrol edin.")
            }
            _state.value = result
            if (result is UpdateState.Available &&
                (userInitiated || result.release.versionCode != dismissedVersionCode)
            ) {
                _promptVisible.value = true
            }
        }
    }

    fun showPrompt() {
        _promptVisible.value = true
    }

    fun dismissPrompt() {
        _promptVisible.value = false
        (_state.value as? UpdateState.Available)?.let { dismissedVersionCode = it.release.versionCode }
    }

    fun downloadAndInstall() {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release ?: return
            else -> return
        }
        if (activeJob?.isActive == true) return
        _promptVisible.value = true
        activeJob = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            try {
                val apk = download(release)
                verify(apk)
                if (appContext.packageManager.canRequestPackageInstalls()) {
                    launchInstaller(release, apk)
                } else {
                    _state.value = UpdateState.NeedsInstallPermission(release)
                }
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(e.message ?: "", release)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("İndirme yarıda kaldı. Bağlantınızı kontrol edip tekrar deneyin.", release)
            }
        }
    }

    /** Kullanıcı dokunuşuyla çağrılır: izin yoksa izin ekranını açar, varsa yükleyiciyi. */
    fun install() {
        val release = when (val current = _state.value) {
            is UpdateState.NeedsInstallPermission -> current.release
            is UpdateState.ReadyToInstall -> current.release
            else -> return
        }
        val apk = apkFileFor(release)
        if (!apk.exists()) {
            _state.value = UpdateState.Available(release)
            downloadAndInstall()
            return
        }
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release)
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        launchInstaller(release, apk)
    }

    private fun launchInstaller(release: ReleaseInfo, apk: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", apk)
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        _state.value = UpdateState.ReadyToInstall(release)
    }

    private fun apkFileFor(release: ReleaseInfo) = File(updatesDir, "update-${release.versionCode}.apk")

    private fun download(release: ReleaseInfo): File {
        updatesDir.mkdirs()
        updatesDir.listFiles()?.forEach { it.delete() }
        val target = apkFileFor(release)
        val partial = File(updatesDir, "${target.name}.part")

        // GitHub indirme adresi https -> https yönlendirir; HttpURLConnection bunu kendisi takip eder.
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", GithubReleases.USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw UpdateException("İndirme sunucusu hata döndürdü (HTTP ${connection.responseCode}).")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
                ?: release.apkSizeBytes.takeIf { it > 0 }
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total != null) {
                            val percent = copied * 100 / total
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(
                                    release,
                                    (copied.toFloat() / total).coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!partial.renameTo(target)) throw UpdateException("İndirilen dosya kaydedilemedi.")
        return target
    }

    private fun verify(apk: File) {
        val archive = appContext.packageManager.getPackageArchiveInfo(apk.path, 0)
        val problem = when {
            archive == null -> "İndirilen dosya geçerli bir Android paketi değil."
            archive.packageName != appContext.packageName -> "İndirilen paket bu uygulamaya ait değil; kurulmadı."
            PackageInfoCompat.getLongVersionCode(archive) <= currentVersionCode ->
                "İndirilen sürüm, kurulu sürümden daha yeni değil."
            else -> null
        }
        if (problem != null) {
            apk.delete()
            throw UpdateException(problem)
        }
    }
}
