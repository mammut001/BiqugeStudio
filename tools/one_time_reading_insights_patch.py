from pathlib import Path

path = Path("app/src/main/java/app/maoyankanshu/novel/selfuse/ui/screens/DiscoverScreen.kt")
text = path.read_text(encoding="utf-8")

old = '''    var dayEntries by remember { mutableStateOf<List<ReadingStats.DayEntry>>(emptyList()) }
    LaunchedEffect(historyVersion, timeRange) {
        dayEntries = withContext(Dispatchers.IO) {
            ReadingStats.days(context, timeRange.dayCount)
        }
    }
    val dayMillis = remember(dayEntries) { dayEntries.map { it.millis } }
'''
new = '''    var dayEntries by remember { mutableStateOf<List<ReadingStats.DayEntry>>(emptyList()) }
    LaunchedEffect(historyVersion, timeRange) {
        dayEntries = withContext(Dispatchers.IO) {
            ReadingStats.days(context, timeRange.dayCount)
        }
    }

    // Habit insights use a longer local-only window than the short-range chart. ReadingStats.days()
    // includes zero-value dates, which gives streak and heatmap calculations a stable calendar axis.
    var insightEntries by remember { mutableStateOf<List<ReadingStats.DayEntry>>(emptyList()) }
    var weeklyGoalMillis by remember { mutableStateOf(ReadingStats.DEFAULT_WEEKLY_GOAL_MILLIS) }
    LaunchedEffect(historyVersion) {
        val loaded = withContext(Dispatchers.IO) {
            ReadingStats.days(context, ReadingInsights.INSIGHT_LOOKBACK_DAYS) to
                ReadingStats.weeklyGoalMillis(context)
        }
        insightEntries = loaded.first
        weeklyGoalMillis = loaded.second
    }
    val dayMillis = remember(dayEntries) { dayEntries.map { it.millis } }
'''
if text.count(old) != 1:
    raise SystemExit(f"data hook: expected 1 match, found {text.count(old)}")
text = text.replace(old, new, 1)

old = '''                    Spacer(Modifier.height(12.dp))
                    ReadingTimeRangeChips(
                        selected = timeRange,
'''
new = '''                    Spacer(Modifier.height(12.dp))
                    ReadingHabitInsights(
                        entries = insightEntries,
                        weeklyGoalMillis = weeklyGoalMillis,
                        onWeeklyGoalChanged = { millis ->
                            weeklyGoalMillis = millis
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    ReadingStats.setWeeklyGoalMillis(context, millis)
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    ReadingTimeRangeChips(
                        selected = timeRange,
'''
if text.count(old) != 1:
    raise SystemExit(f"UI hook: expected 1 match, found {text.count(old)}")
text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
