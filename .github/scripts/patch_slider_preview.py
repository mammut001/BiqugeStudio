from pathlib import Path

path = Path("app/src/main/java/app/maoyankanshu/novel/selfuse/ui/reader/ReaderScreen.kt")
text = path.read_text(encoding="utf-8")

old_state = '''    // While the chrome slider is down, pager snapshots must not overwrite [progress]
    // or the thumb fights the finger and the page animates every tick.
    var sliderScrubbing by remember { mutableStateOf(false) }
'''
new_state = '''    // Keep slider drag preview separate from committed reading progress. The body/pager must stay
    // completely still while the thumb is down; otherwise every pointer tick remounts another page
    // and can also persist an intermediate seek through the debounced progress writer.
    var sliderScrubbing by remember(book.id) { mutableStateOf(false) }
    var sliderPreviewProgress by remember(book.id) { mutableStateOf<Int?>(null) }
'''

old_slider = '''                    val sliderPercent = ProgressMath.percentOfProgress(progress)
                    val sliderProgressCd = stringResource(R.string.reader_progress_cd, sliderPercent)
                    // Percent label + slider on one row so progress is never “bar only”.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics { contentDescription = sliderProgressCd },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "$sliderPercent%",
                            color = palette.onBar,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Slider(
                            value = progress.toFloat(),
                            onValueChange = {
                                sliderScrubbing = true
                                val next = ProgressMath.clampProgress(it.roundToInt())
                                progress = next
                                val count = if (useApproxPaging) {
                                    PageIndex.approximatePageCount(
                                        book.text.length,
                                        approxCharsPerPage,
                                    )
                                } else {
                                    pageStarts.size
                                }
                                val page = PageIndex.pageForProgress(next, count)
                                if (page != pagerState.currentPage) {
                                    scope.launch { pagerState.scrollToPage(page) }
                                }
                            },
                            onValueChangeFinished = {
                                sliderScrubbing = false
                                jumpToProgress(progress)
                            },
'''
new_slider = '''                    val sliderDisplayProgress = sliderPreviewProgress ?: progress
                    val sliderPercent = ProgressMath.percentOfProgress(sliderDisplayProgress)
                    val sliderProgressCd = stringResource(R.string.reader_progress_cd, sliderPercent)
                    // Percent label + slider on one row so progress is never “bar only”.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics { contentDescription = sliderProgressCd },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "$sliderPercent%",
                            color = palette.onBar,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Slider(
                            value = sliderDisplayProgress.toFloat(),
                            onValueChange = {
                                sliderScrubbing = true
                                sliderPreviewProgress = ProgressMath.clampProgress(it.roundToInt())
                            },
                            onValueChangeFinished = {
                                val target = sliderPreviewProgress ?: progress
                                // Release the pager observer before the one committed seek so the
                                // resulting page can refresh anchor/chapter/progress normally.
                                sliderPreviewProgress = null
                                sliderScrubbing = false
                                progress = target
                                jumpToProgress(target)
                            },
'''

for old, new, label in [
    (old_state, new_state, "slider state"),
    (old_slider, new_slider, "slider callbacks"),
]:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} block, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
