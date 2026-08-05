package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech

/** One installed (or virtual) TTS engine the user can pick in-app. */
data class TtsEngineOption(
    /** Empty string = system default constructor (no package pin). */
    val packageName: String,
    val label: String,
)

/**
 * Discover installed system TTS engines (Google / 讯飞 / 华为 / 三星 …)
 * and map package ids to readable Chinese labels.
 *
 * Listing uses [TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE] — same engines
 * shown under system「文字转语音」settings.
 */
object TtsEngineCatalog {
    const val SYSTEM_DEFAULT_PACKAGE: String = ""

    fun systemDefaultOption(label: String = "系统默认"): TtsEngineOption =
        TtsEngineOption(SYSTEM_DEFAULT_PACKAGE, label)

    /**
     * Human-readable name for an engine package.
     * [rawLabel] is the package manager label when available.
     */
    fun friendlyLabel(packageName: String, rawLabel: String? = null): String {
        if (packageName.isEmpty()) return "系统默认"
        val p = packageName.lowercase()
        val mapped = when {
            p.contains("google") && p.contains("tts") -> "Google 文字转语音"
            p == "com.google.android.tts" -> "Google 文字转语音"
            p.contains("iflytek") || p.contains("xfyun") || p.contains("speechcloud") -> "讯飞语音"
            p.contains("samsung") || p.contains(".smt") || p.endsWith(".smt") -> "三星语音"
            p.contains("huawei") || p.contains("harmony") -> "华为语音"
            p.contains("xiaomi") || p.contains("miui") || p.contains("xiaoai") -> "小米语音"
            p.contains("oppo") || p.contains("coloros") || p.contains("heytap") -> "OPPO 语音"
            p.contains("vivo") || p.contains("bbk") -> "vivo 语音"
            p.contains("meizu") || p.contains("flyme") -> "魅族语音"
            p.contains("baidu") -> "百度语音"
            p.contains("microsoft") || p.contains("cortana") -> "Microsoft 语音"
            p.contains("rhvoice") -> "RHVoice"
            p.contains("espeak") -> "eSpeak"
            p.contains("svox") -> "SVOX"
            else -> null
        }
        if (mapped != null) return mapped
        val raw = rawLabel?.trim().orEmpty()
        if (raw.isNotEmpty() && raw != packageName) return raw
        // Last segment of package as fallback.
        return packageName.substringAfterLast('.').ifEmpty { packageName }
    }

    /**
     * Installed engines on this device, always starting with system default.
     * Safe to call off the main thread (PackageManager only).
     */
    fun listInstalled(context: Context): List<TtsEngineOption> {
        val app = context.applicationContext
        val out = ArrayList<TtsEngineOption>()
        out.add(systemDefaultOption())
        val seen = HashSet<String>()
        seen.add(SYSTEM_DEFAULT_PACKAGE)

        try {
            val pm = app.packageManager
            val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, 0)
            }
            for (info in services) {
                val pkg = info.serviceInfo?.packageName?.trim().orEmpty()
                if (pkg.isEmpty() || !seen.add(pkg)) continue
                val raw = try {
                    info.loadLabel(pm)?.toString()
                } catch (_: Exception) {
                    null
                }
                out.add(TtsEngineOption(pkg, friendlyLabel(pkg, raw)))
            }
        } catch (_: Exception) {
            // Fall through to known packages.
        }

        // Ensure common engines appear if installed but not returned by the intent query.
        for (pkg in knownPackages()) {
            if (!seen.add(pkg)) continue
            if (!isPackageInstalled(app, pkg)) continue
            out.add(TtsEngineOption(pkg, friendlyLabel(pkg, null)))
        }
        return out
    }

    fun knownPackages(): List<String> = listOf(
        "com.google.android.tts",
        "com.iflytek.speechsuite",
        "com.iflytek.inputmethod.tts",
        "com.iflytek.vflynote",
        "com.samsung.SMT",
        "com.huawei.voiceservice",
        "com.huawei.voiceengine",
        "com.xiaomi.mibrain.speech",
        "com.baidu.duersdk.opensdk",
        "com.github.olga_yakovleva.rhvoice.android",
    )

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isEmpty()) return true
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Intent to open system TTS settings (user can install voices there). */
    fun systemTtsSettingsIntent(): Intent {
        return Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun normalizePackage(packageName: String?): String =
        packageName?.trim().orEmpty()
}
