package com.cruciblelab.trafficlogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RuleType { BLACKLIST, WHITELIST }

/**
 * A single allow/block rule. Rules are deliberately narrow by default: when created from
 * a traffic row, [appPackageName] is filled in, so the rule only matches that one app's
 * connections to [matchValue] - blocking e.g. just "MyApp -> googleads.g.doubleclick.net"
 * rather than every app's traffic to every Google service. [appPackageName] can be left
 * null to make a rule app-agnostic (matches the domain/IP for any app), which is opt-in
 * (the UI has to explicitly ask for "tüm uygulamalar").
 */
@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: RuleType,
    /** null = applies to every app */
    val appPackageName: String?,
    val appLabel: String?,
    /** A domain (matched as exact or parent-of-subdomain) or a literal IP address. */
    val matchValue: String,
    val createdAt: Long = System.currentTimeMillis()
)
