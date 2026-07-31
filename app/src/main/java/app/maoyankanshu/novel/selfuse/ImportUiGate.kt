package app.maoyankanshu.novel.selfuse

import android.app.Activity

/**
 * Pure gate for whether a host Activity may still receive Toast / Compose state / finish.
 * JVM unit tests call this overload; runtime uses [Activity.canAcceptUi].
 *
 * [Activity.isDestroyed] is API 17+; minSdk 23 so no version branch is required.
 */
internal fun canAcceptUi(isFinishing: Boolean, isDestroyed: Boolean): Boolean {
    if (isFinishing) return false
    return !isDestroyed
}

/** True when the host Activity can still accept UI side-effects (Toast / finish / state). */
internal fun Activity?.canAcceptUi(): Boolean {
    if (this == null) return false
    return canAcceptUi(isFinishing = isFinishing, isDestroyed = isDestroyed)
}
