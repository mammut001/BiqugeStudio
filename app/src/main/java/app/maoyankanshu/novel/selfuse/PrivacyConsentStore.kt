package app.maoyankanshu.novel.selfuse

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores only the local acknowledgement of the currently bundled privacy policy.
 *
 * No identifier, account, timestamp, or network request is involved. Bump
 * [CURRENT_POLICY_VERSION] only when a policy change requires the user to consent again.
 */
class PrivacyConsentStore private constructor(
    private val prefs: SharedPreferences,
) {
    fun hasAcceptedCurrentPolicy(): Boolean =
        isAcceptedVersion(prefs.getInt(KEY_ACCEPTED_VERSION, 0), CURRENT_POLICY_VERSION)

    fun acceptCurrentPolicy() {
        prefs.edit().putInt(KEY_ACCEPTED_VERSION, CURRENT_POLICY_VERSION).apply()
    }

    companion object {
        const val CURRENT_POLICY_VERSION: Int = 1
        private const val PREFS = "privacy_consent"
        private const val KEY_ACCEPTED_VERSION = "accepted_policy_version"

        @JvmStatic
        fun get(context: Context): PrivacyConsentStore = PrivacyConsentStore(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        )

        internal fun isAcceptedVersion(storedVersion: Int, currentVersion: Int): Boolean =
            currentVersion > 0 && storedVersion >= currentVersion
    }
}
