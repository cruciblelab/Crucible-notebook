package com.cruciblelab.trafficlogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kullanıcının kendi oluşturduğu ya da içe aktardığı bir [NetworkProfile]'ın DB karşılığı.
 * Uygulamayla birlikte gelen, önceden tanımlı hiçbir profil YOK - "Bankacılık Modu" sadece
 * bir örnekti; kullanıcı kendi profilini arayüzden ya da JSON içe aktararak oluşturur.
 *
 * Profil tanımının tamamı [json] sütununda tutulur (bkz. [NetworkProfile.toJson] /
 * [NetworkProfile.fromJson]) - gerçek kimlik bu satırın [id]'si; JSON içindeki olası bir
 * "id" alanı yok sayılır, çakışma olmasın diye.
 */
@Entity(tableName = "network_profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val json: String,
    val createdAt: Long = System.currentTimeMillis()
)
