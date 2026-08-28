package com.yinxing.launcher.automation.wechat.teaching

import android.content.Context

enum class WeChatTeachingProfileStatus {
    NOT_TAUGHT,
    VIDEO_CONFIRMED_NO_RULES,
    PARTIAL,
    READY,
    NEEDS_RETEACH
}

data class WeChatTeachingSnapshot(
    val status: WeChatTeachingProfileStatus,
    val learnedActions: Set<WeChatTeachingAction>,
    val reliabilityScore: Int?,
    val verifiedActions: Set<WeChatTeachingAction> = emptySet(),
    val addedActions: Set<WeChatTeachingAction> = emptySet()
)

class WeChatTeachingStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun save(profile: WeChatTeachingProfile) {
        preferences.edit().putString(KEY_PROFILE, WeChatTeachingProfileCodec.encode(profile)).apply()
    }

    fun saveIfComplete(result: WeChatTeachingResult): Boolean {
        val profile = (result as? WeChatTeachingResult.Complete)?.profile ?: return false
        save(profile)
        return true
    }

    fun load(): WeChatTeachingProfile? =
        WeChatTeachingProfileCodec.decode(preferences.getString(KEY_PROFILE, null))

    fun loadRecord(): WeChatTeachingRecord? =
        WeChatTeachingRecordCodec.decode(preferences.getString(KEY_RECORD, null))

    fun deleteLearnedAction(
        fingerprint: WeChatTeachingFingerprint,
        action: WeChatTeachingAction
    ): Boolean {
        val profile = load()?.takeIf { it.fingerprint == fingerprint }
        val record = loadRecord()?.takeIf { it.fingerprint == fingerprint }
        val profileContainsAction = profile?.steps?.any { it.action == action } == true
        val recordContainsAction = action in record?.learnedActions.orEmpty()
        if (!profileContainsAction && !recordContainsAction) return false

        val remainingProfile = profile?.copy(
            steps = profile.steps.filterNot { it.action == action }
        )
        preferences.edit().apply {
            when {
                remainingProfile == null -> Unit
                remainingProfile.steps.isEmpty() -> remove(KEY_PROFILE)
                else -> putString(KEY_PROFILE, WeChatTeachingProfileCodec.encode(remainingProfile))
            }
            record?.let {
                putString(
                    KEY_RECORD,
                    WeChatTeachingRecordCodec.encode(
                        it.copy(
                            learnedActions = it.learnedActions - action,
                            addedActions = it.addedActions - action
                        )
                    )
                )
            }
        }.apply()
        return true
    }

    fun clearLearnedRules(fingerprint: WeChatTeachingFingerprint): Boolean {
        val profile = load()?.takeIf { it.fingerprint == fingerprint }
        val record = loadRecord()?.takeIf { it.fingerprint == fingerprint }
        if (profile == null && record?.learnedActions.orEmpty().isEmpty()) return false

        preferences.edit().apply {
            if (profile != null) remove(KEY_PROFILE)
            record?.let {
                putString(
                    KEY_RECORD,
                    WeChatTeachingRecordCodec.encode(
                        it.copy(
                            learnedActions = emptySet(),
                            addedActions = emptySet()
                        )
                    )
                )
            }
        }.apply()
        return true
    }

    fun resetAll(): Boolean {
        if (!preferences.contains(KEY_PROFILE) && !preferences.contains(KEY_RECORD)) return false
        preferences.edit()
            .remove(KEY_PROFILE)
            .remove(KEY_RECORD)
            .apply()
        return true
    }

    fun saveVideoOutcome(
        result: WeChatTeachingResult,
        fingerprint: WeChatTeachingFingerprint,
        createdAtEpochMs: Long
    ): WeChatTeachingRecord {
        val candidate = result.learnedProfileOrNull()
            ?.takeIf { it.fingerprint == fingerprint }
        val classification = WeChatTeachingRuleClassifier.classify(candidate)
        val differenceProfile = candidate
            ?.copy(steps = classification.learnedSteps)
            ?.takeIf { it.steps.isNotEmpty() }
        val merged = mergeProfiles(
            loadCompatible(fingerprint),
            differenceProfile,
            createdAtEpochMs
        )
        val actions = merged?.steps
            ?.sortedBy { WeChatTeachingAction.entries.indexOf(it.action) }
            ?.mapTo(linkedSetOf()) { it.action }
            .orEmpty()
        val record = WeChatTeachingRecord(
            fingerprint = fingerprint,
            videoConfirmed = true,
            learnedActions = actions,
            verifiedActions = classification.verifiedActions,
            addedActions = classification.learnedSteps.mapTo(linkedSetOf()) { it.action },
            createdAtEpochMs = createdAtEpochMs
        )
        preferences.edit().apply {
            if (merged != null) {
                putString(KEY_PROFILE, WeChatTeachingProfileCodec.encode(merged))
            }
            putString(KEY_RECORD, WeChatTeachingRecordCodec.encode(record))
        }.apply()
        return record
    }

    fun loadCompatible(fingerprint: WeChatTeachingFingerprint): WeChatTeachingProfile? =
        load()?.takeIf { it.fingerprint == fingerprint }

    fun status(fingerprint: WeChatTeachingFingerprint): WeChatTeachingProfileStatus {
        return snapshot(fingerprint).status
    }

    fun snapshot(fingerprint: WeChatTeachingFingerprint): WeChatTeachingSnapshot {
        val record = loadRecord()
        val profile = load()
        val newestFingerprint = record?.fingerprint ?: profile?.fingerprint
        if (newestFingerprint == null) {
            return WeChatTeachingSnapshot(
                WeChatTeachingProfileStatus.NOT_TAUGHT,
                emptySet(),
                null
            )
        }
        if (newestFingerprint != fingerprint) {
            return WeChatTeachingSnapshot(
                WeChatTeachingProfileStatus.NEEDS_RETEACH,
                emptySet(),
                null
            )
        }
        val compatibleProfile = profile?.takeIf { it.fingerprint == fingerprint }
        val learnedActions = record?.takeIf { it.fingerprint == fingerprint }?.learnedActions
            ?: compatibleProfile?.steps?.mapTo(linkedSetOf()) { it.action }
            ?: emptySet()
        val status = when {
            learnedActions.isEmpty() && record?.videoConfirmed == true ->
                WeChatTeachingProfileStatus.VIDEO_CONFIRMED_NO_RULES
            learnedActions.size >= WeChatTeachingAction.entries.size ->
                WeChatTeachingProfileStatus.READY
            learnedActions.isNotEmpty() -> WeChatTeachingProfileStatus.PARTIAL
            else -> WeChatTeachingProfileStatus.NOT_TAUGHT
        }
        return WeChatTeachingSnapshot(
            status = status,
            learnedActions = learnedActions,
            reliabilityScore = compatibleProfile?.reliabilityScore,
            verifiedActions = record?.verifiedActions.orEmpty(),
            addedActions = record?.addedActions.orEmpty()
        )
    }

    private fun mergeProfiles(
        existing: WeChatTeachingProfile?,
        candidate: WeChatTeachingProfile?,
        createdAtEpochMs: Long
    ): WeChatTeachingProfile? {
        if (existing == null) return candidate
        if (candidate == null) return existing
        val latestByAction = linkedMapOf<WeChatTeachingAction, WeChatTeachingStep>()
        existing.steps.forEach { latestByAction[it.action] = it }
        candidate.steps.forEach { latestByAction[it.action] = it }
        val steps = WeChatTeachingAction.entries.mapNotNull(latestByAction::get)
        val weightedScore = (
            existing.reliabilityScore * existing.steps.size +
                candidate.reliabilityScore * candidate.steps.size
            ) / (existing.steps.size + candidate.steps.size).coerceAtLeast(1)
        val reliability = if (weightedScore >= 80) {
            WeChatTeachingReliability.RELIABLE
        } else {
            WeChatTeachingReliability.USABLE_WITH_POSITION_FALLBACK
        }
        return candidate.copy(
            steps = steps,
            reliabilityScore = weightedScore,
            reliability = reliability,
            createdAtEpochMs = createdAtEpochMs
        )
    }

    private companion object {
        const val PREFS_NAME = "wechat_teaching_profile"
        const val KEY_PROFILE = "profile"
        const val KEY_RECORD = "record"
    }
}
