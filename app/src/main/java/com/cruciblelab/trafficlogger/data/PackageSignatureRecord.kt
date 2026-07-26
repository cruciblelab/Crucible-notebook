package com.cruciblelab.trafficlogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Trust-on-first-use kaydı: bir paket adı için ilk gözlemlenen imzalayan sertifika hash'i.
 * Aynı paket adı sonradan farklı bir [signatureSha256] ile görülürse, uygulama gerçek bir
 * yeniden imzalama (örn. resmi güncelleme) ya da taklit/değiştirilmiş bir APK olabilir -
 * bkz. [PackageIntegrityRepository].
 */
@Entity(tableName = "package_signatures")
data class PackageSignatureRecord(
    @PrimaryKey
    val packageName: String,
    val signatureSha256: String,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastConfirmedAt: Long = System.currentTimeMillis()
)
