package app.maoyankanshu.novel.selfuse.ui.reader

/**
 * Pure footer label helpers (clock/page strip). Battery text is Kindle-style compact.
 */
object ReaderFooterFormat {
    /**
     * @param percent battery 0…100, or negative if unknown
     * @param charging true when AC/USB/wireless charging
     */
    fun batteryLabel(percent: Int, charging: Boolean): String {
        if (percent < 0) return if (charging) "⚡--" else "--%"
        val p = percent.coerceIn(0, 100)
        return if (charging) "⚡$p%" else "$p%"
    }

    fun batteryContentDescription(percent: Int, charging: Boolean): String {
        if (percent < 0) {
            return if (charging) "正在充电，电量未知" else "电量未知"
        }
        val p = percent.coerceIn(0, 100)
        return if (charging) "正在充电，百分之 $p" else "电量百分之 $p"
    }
}
