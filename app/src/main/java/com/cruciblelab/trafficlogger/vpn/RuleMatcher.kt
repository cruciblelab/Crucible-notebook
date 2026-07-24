package com.cruciblelab.trafficlogger.vpn

import com.cruciblelab.trafficlogger.data.BlockRule
import com.cruciblelab.trafficlogger.data.RuleType
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory snapshot of the block/allow rules, refreshed whenever [update] is called
 * (the VpnService keeps this in sync with the DB via a Flow collector). Kept separate
 * from the DB so the hot packet path never touches Room directly.
 *
 * Matching is per (app, domain-or-ip) pair, not "block the whole domain everywhere":
 * a rule with a non-null [BlockRule.appPackageName] only ever matches that one app.
 * Domain matching allows subdomains of a blocked domain to match too (blocking
 * "doubleclick.net" also blocks "googleads.g.doubleclick.net"), which keeps rules
 * created from a single row effective even if the same tracker resolves to a
 * slightly different subdomain next time.
 */
class RuleMatcher {

    private val rulesRef = AtomicReference<List<BlockRule>>(emptyList())

    fun update(rules: List<BlockRule>) {
        rulesRef.set(rules)
    }

    /** Returns true if this specific app+destination should be blocked. */
    fun isBlocked(appPackageName: String, domain: String?, destIp: String): Boolean {
        val rules = rulesRef.get()
        if (rules.isEmpty()) return false

        val whitelisted = rules.any { it.type == RuleType.WHITELIST && matches(it, appPackageName, domain, destIp) }
        if (whitelisted) return false

        return rules.any { it.type == RuleType.BLACKLIST && matches(it, appPackageName, domain, destIp) }
    }

    private fun matches(rule: BlockRule, appPackageName: String, domain: String?, destIp: String): Boolean {
        if (rule.appPackageName != null && rule.appPackageName != appPackageName) return false

        // "*" is a wildcard used for reputation-database-driven auto-block rules (see
        // RuleRepository): it means "block this app entirely", not tied to one domain/IP.
        if (rule.matchValue == "*") return true

        if (rule.matchValue == destIp) return true

        val d = domain ?: return false
        return d.equals(rule.matchValue, ignoreCase = true) ||
            d.endsWith(".${rule.matchValue}", ignoreCase = true)
    }
}
