package com.yk.notiffilter.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appRules: List<AppRule>,
    val templateVerdicts: List<TemplateVerdict>,
    val notificationRules: List<NotificationRule>,
)

data class ImportResult(
    val success: Boolean,
    val appRulesCount: Int,
    val templateVerdictsCount: Int,
    val notificationRulesCount: Int,
    val error: String? = null,
)

object BackupManager {

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)

        val appRulesArray = JSONArray()
        data.appRules.forEach { rule ->
            val obj = JSONObject()
            obj.put("packageName", rule.packageName)
            obj.put("appLabel", rule.appLabel)
            obj.put("mode", rule.mode.name)
            obj.put("enabled", rule.enabled)
            obj.put("repostKeepers", rule.repostKeepers)
            obj.put("createdAt", rule.createdAt)
            appRulesArray.put(obj)
        }
        root.put("appRules", appRulesArray)

        val verdictsArray = JSONArray()
        data.templateVerdicts.forEach { verdict ->
            val obj = JSONObject()
            obj.put("fingerprint", verdict.fingerprint)
            obj.put("packageName", verdict.packageName)
            obj.put("verdict", verdict.verdict.name)
            obj.put("reason", verdict.reason)
            obj.put("source", verdict.source.name)
            obj.put("needsVerdict", verdict.needsVerdict)
            obj.put("score", verdict.score)
            obj.put("channelId", verdict.channelId)
            obj.put("sampleTitle", verdict.sampleTitle)
            obj.put("sampleText", verdict.sampleText)
            obj.put("updatedAt", verdict.updatedAt)
            verdictsArray.put(obj)
        }
        root.put("templateVerdicts", verdictsArray)

        val notifRulesArray = JSONArray()
        data.notificationRules.forEach { rule ->
            val obj = JSONObject()
            if (rule.packageName != null) {
                obj.put("packageName", rule.packageName)
            } else {
                obj.put("packageName", JSONObject.NULL)
            }
            obj.put("pattern", rule.pattern)
            obj.put("isRegex", rule.isRegex)
            obj.put("matchTarget", rule.matchTarget.name)
            obj.put("verdict", rule.verdict.name)
            obj.put("enabled", rule.enabled)
            if (rule.description != null) {
                obj.put("description", rule.description)
            } else {
                obj.put("description", JSONObject.NULL)
            }
            obj.put("createdAt", rule.createdAt)
            notifRulesArray.put(obj)
        }
        root.put("notificationRules", notifRulesArray)

        return root.toString(2)
    }

    fun fromJson(jsonStr: String): BackupData {
        val root = JSONObject(jsonStr)
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

        val appRules = mutableListOf<AppRule>()
        val appRulesArray = root.optJSONArray("appRules") ?: JSONArray()
        for (i in 0 until appRulesArray.length()) {
            val obj = appRulesArray.getJSONObject(i)
            appRules += AppRule(
                packageName = obj.getString("packageName"),
                appLabel = obj.getString("appLabel"),
                mode = runCatching { FilterMode.valueOf(obj.getString("mode")) }.getOrDefault(FilterMode.ACTIVE),
                enabled = obj.optBoolean("enabled", true),
                repostKeepers = obj.optBoolean("repostKeepers", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            )
        }

        val templateVerdicts = mutableListOf<TemplateVerdict>()
        val verdictsArray = root.optJSONArray("templateVerdicts") ?: JSONArray()
        for (i in 0 until verdictsArray.length()) {
            val obj = verdictsArray.getJSONObject(i)
            templateVerdicts += TemplateVerdict(
                fingerprint = obj.getString("fingerprint"),
                packageName = obj.getString("packageName"),
                verdict = runCatching { Verdict.valueOf(obj.getString("verdict")) }.getOrDefault(Verdict.KEEP),
                reason = obj.optString("reason", ""),
                source = runCatching { VerdictSource.valueOf(obj.getString("source")) }.getOrDefault(VerdictSource.USER),
                needsVerdict = obj.optBoolean("needsVerdict", false),
                score = obj.optDouble("score", 0.0),
                channelId = obj.optString("channelId", ""),
                sampleTitle = obj.optString("sampleTitle", ""),
                sampleText = obj.optString("sampleText", ""),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            )
        }

        val notificationRules = mutableListOf<NotificationRule>()
        val notifRulesArray = root.optJSONArray("notificationRules") ?: JSONArray()
        for (i in 0 until notifRulesArray.length()) {
            val obj = notifRulesArray.getJSONObject(i)
            val pkg = if (obj.isNull("packageName")) null else obj.optString("packageName").takeIf { it.isNotBlank() }
            val desc = if (obj.isNull("description")) null else obj.optString("description")
            notificationRules += NotificationRule(
                packageName = pkg,
                pattern = obj.getString("pattern"),
                isRegex = obj.optBoolean("isRegex", false),
                matchTarget = runCatching { MatchTarget.valueOf(obj.getString("matchTarget")) }.getOrDefault(MatchTarget.ANY),
                verdict = runCatching { Verdict.valueOf(obj.getString("verdict")) }.getOrDefault(Verdict.HIDE),
                enabled = obj.optBoolean("enabled", true),
                description = desc,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            )
        }

        return BackupData(
            version = version,
            exportedAt = exportedAt,
            appRules = appRules,
            templateVerdicts = templateVerdicts,
            notificationRules = notificationRules,
        )
    }

    suspend fun exportFromDatabase(db: NotifFilterDatabase): String {
        val apps = db.appRules().getAll()
        val verdicts = db.verdicts().getAll()
        val notifRules = db.notificationRules().getAll()

        val data = BackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            appRules = apps,
            templateVerdicts = verdicts,
            notificationRules = notifRules,
        )
        return toJson(data)
    }

    suspend fun importIntoDatabase(jsonStr: String, db: NotifFilterDatabase): ImportResult {
        return runCatching {
            val backup = fromJson(jsonStr)
            if (backup.appRules.isNotEmpty()) {
                db.appRules().insertAll(backup.appRules)
            }
            if (backup.templateVerdicts.isNotEmpty()) {
                db.verdicts().insertAll(backup.templateVerdicts)
            }
            if (backup.notificationRules.isNotEmpty()) {
                db.notificationRules().insertAll(backup.notificationRules)
            }
            ImportResult(
                success = true,
                appRulesCount = backup.appRules.size,
                templateVerdictsCount = backup.templateVerdicts.size,
                notificationRulesCount = backup.notificationRules.size,
            )
        }.getOrElse { error ->
            ImportResult(
                success = false,
                appRulesCount = 0,
                templateVerdictsCount = 0,
                notificationRulesCount = 0,
                error = error.localizedMessage ?: "Unknown parsing error",
            )
        }
    }
}
