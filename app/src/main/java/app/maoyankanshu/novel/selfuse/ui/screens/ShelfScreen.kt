package app.maoyankanshu.novel.selfuse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.AppIntents
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReadingHistory
import app.maoyankanshu.novel.selfuse.ui.components.BookCard
import app.maoyankanshu.novel.selfuse.ui.components.EmptyState

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShelfScreen(
    books: List<Book>,
    isLoading: Boolean = false,
    onLibraryChanged: () -> Unit,
    contentPadding: PaddingValues,
    historyVersion: Int = 0,
    onHistoryChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    var menuBook by remember { mutableStateOf<Book?>(null) }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    // Keep shelf controls through rotation/process recreation. Save the enum name instead of
    // relying on an enum saver so older saved state remains resilient to enum additions.
    var progressFilterName by rememberSaveable { mutableStateOf(ShelfProgressFilter.ALL.name) }
    var sortOrderName by rememberSaveable { mutableStateOf(ShelfSortOrder.DEFAULT.name) }
    var groupModeName by rememberSaveable { mutableStateOf(ShelfGroupMode.NONE.name) }
    val progressFilter = ShelfProgressFilter.entries.firstOrNull { it.name == progressFilterName }
        ?: ShelfProgressFilter.ALL
    val sortOrder = ShelfSortOrder.entries.firstOrNull { it.name == sortOrderName }
        ?: ShelfSortOrder.DEFAULT
    val groupMode = ShelfGroupMode.entries.firstOrNull { it.name == groupModeName }
        ?: ShelfGroupMode.NONE
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    val unknownAuthorLabel = stringResource(R.string.shelf_author_unknown)
    val recentReadAt = remember(books, historyVersion) {
        ReadingHistory.get(context).list().associate { it.bookId to it.at }
    }

    // 继续阅读 shares sortOrder with 全部书籍; progressFilter applies only to sectionAll.
    // groupMode only affects rendering of the all-books section (not continue-reading).
    val continueReading = remember(books, sortOrder, recentReadAt) {
        ShelfFilters.continueReading(books, sortOrder, recentReadAt)
    }
    val allBooks = remember(books, progressFilter, sortOrder, recentReadAt) {
        ShelfFilters.sectionAll(books, progressFilter, sortOrder, recentReadAt)
    }
    val authorGroups = remember(allBooks, groupMode, unknownAuthorLabel) {
        if (groupMode == ShelfGroupMode.BY_AUTHOR) {
            ShelfFilters.groupByAuthor(allBooks, unknownAuthorLabel)
        } else {
            emptyList()
        }
    }

    fun openDetail(book: Book) {
        context.startActivity(AppIntents.bookDetail(context, book.id))
    }

    fun openReader(book: Book) {
        context.startActivity(AppIntents.reader(context, book.id))
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .semantics { contentDescription = context.getString(R.string.shelf_loading_cd) },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                )
                Text(
                    text = stringResource(R.string.shelf_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .semantics { contentDescription = context.getString(R.string.shelf_screen_cd) },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (books.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.shelf_empty_title),
                        body = stringResource(R.string.shelf_empty_body),
                        contentDescription = stringResource(R.string.shelf_empty_cd),
                        primaryLabel = stringResource(R.string.cta_import),
                        primaryDescription = stringResource(R.string.import_local_txt_epub_cd),
                        onPrimary = {
                            context.startActivity(AppIntents.importLocal(context))
                        },
                        secondaryLabel = stringResource(R.string.cta_search),
                        secondaryDescription = stringResource(R.string.search_shelf_cd),
                        onSecondary = {
                            context.startActivity(AppIntents.search(context))
                        },
                    )
                }
            } else {
                if (continueReading.isNotEmpty()) {
                    item {
                        SectionLabel(stringResource(R.string.shelf_section_continue))
                    }
                    items(continueReading, key = { "continue-${it.id}" }) { book ->
                        BookCard(
                            book = book,
                            onClick = { openDetail(book) },
                            onLongClick = { menuBook = book },
                            onContinueReading = { openReader(book) },
                        )
                    }
                }

                item {
                    SectionLabel(stringResource(R.string.shelf_section_all))
                }

                item {
                    ShelfToolbar(
                        progressFilter = progressFilter,
                        onFilterChange = { progressFilterName = it.name },
                        sortOrder = sortOrder,
                        sortMenuExpanded = sortMenuExpanded,
                        onSortMenuExpandedChange = { sortMenuExpanded = it },
                        onSortOrderChange = {
                            sortOrderName = it.name
                            sortMenuExpanded = false
                        },
                        groupMode = groupMode,
                        groupMenuExpanded = groupMenuExpanded,
                        onGroupMenuExpandedChange = { groupMenuExpanded = it },
                        onGroupModeChange = {
                            groupModeName = it.name
                            groupMenuExpanded = false
                        },
                    )
                }

                if (allBooks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.shelf_filter_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .semantics {
                                    contentDescription = context.getString(R.string.shelf_filter_empty)
                                },
                        )
                    }
                } else if (groupMode == ShelfGroupMode.BY_AUTHOR) {
                    authorGroups.forEach { group ->
                        item(key = "author-h-${group.authorLabel}") {
                            AuthorSectionHeading(group.authorLabel)
                        }
                        items(group.books, key = { "all-${it.id}" }) { book ->
                            BookCard(
                                book = book,
                                onClick = { openDetail(book) },
                                onLongClick = { menuBook = book },
                                onContinueReading = { openReader(book) },
                            )
                        }
                    }
                } else {
                    items(allBooks, key = { "all-${it.id}" }) { book ->
                        BookCard(
                            book = book,
                            onClick = { openDetail(book) },
                            onLongClick = { menuBook = book },
                            onContinueReading = { openReader(book) },
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.shelf_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }

    menuBook?.let { book ->
        BookActionsDialog(
            book = book,
            onDismiss = { menuBook = null },
            onContinue = {
                openReader(book)
                menuBook = null
            },
            onPin = {
                LibraryStore.get(context).moveToTop(book.id)
                menuBook = null
                onLibraryChanged()
            },
            onEdit = {
                context.startActivity(AppIntents.bookDetailEdit(context, book.id))
                menuBook = null
            },
            onDelete = {
                menuBook = null
                pendingDelete = book
            },
        )
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_book_confirm_title)) },
            text = {
                Text(stringResource(R.string.delete_book_confirm_body, book.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LibraryStore.get(context).remove(book.id)
                        ReadingHistory.get(context).remove(book.id)
                        pendingDelete = null
                        onLibraryChanged()
                        onHistoryChanged()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.delete_book_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.delete_book_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfToolbar(
    progressFilter: ShelfProgressFilter,
    onFilterChange: (ShelfProgressFilter) -> Unit,
    sortOrder: ShelfSortOrder,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    onSortOrderChange: (ShelfSortOrder) -> Unit,
    groupMode: ShelfGroupMode,
    groupMenuExpanded: Boolean,
    onGroupMenuExpandedChange: (Boolean) -> Unit,
    onGroupModeChange: (ShelfGroupMode) -> Unit,
) {
    val filterAllCd = stringResource(R.string.shelf_filter_all_cd)
    val filterProgressCd = stringResource(R.string.shelf_filter_progress_cd)
    val filterNotStartedCd = stringResource(R.string.shelf_filter_not_started_cd)
    val filterFinishedCd = stringResource(R.string.shelf_filter_finished_cd)
    val sortCd = stringResource(R.string.shelf_sort_cd)
    val groupCd = stringResource(R.string.shelf_group_cd)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = filterAllCd // group description
                },
        ) {
            FilterChip(
                selected = progressFilter == ShelfProgressFilter.ALL,
                onClick = { onFilterChange(ShelfProgressFilter.ALL) },
                label = { Text(stringResource(R.string.shelf_filter_all)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = filterAllCd },
            )
            FilterChip(
                selected = progressFilter == ShelfProgressFilter.IN_PROGRESS,
                onClick = { onFilterChange(ShelfProgressFilter.IN_PROGRESS) },
                label = { Text(stringResource(R.string.shelf_filter_progress)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = filterProgressCd },
            )
            FilterChip(
                selected = progressFilter == ShelfProgressFilter.NOT_STARTED,
                onClick = { onFilterChange(ShelfProgressFilter.NOT_STARTED) },
                label = { Text(stringResource(R.string.shelf_filter_not_started)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = filterNotStartedCd },
            )
            FilterChip(
                selected = progressFilter == ShelfProgressFilter.FINISHED,
                onClick = { onFilterChange(ShelfProgressFilter.FINISHED) },
                label = { Text(stringResource(R.string.shelf_filter_finished)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = filterFinishedCd },
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                TextButton(
                    onClick = { onSortMenuExpandedChange(true) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = sortCd },
                ) {
                    Text(stringResource(sortLabelRes(sortOrder)))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { onSortMenuExpandedChange(false) },
                ) {
                    ShelfSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sortLabelRes(order))) },
                            onClick = { onSortOrderChange(order) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            Box {
                TextButton(
                    onClick = { onGroupMenuExpandedChange(true) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = groupCd },
                ) {
                    Text(stringResource(groupLabelRes(groupMode)))
                }
                DropdownMenu(
                    expanded = groupMenuExpanded,
                    onDismissRequest = { onGroupMenuExpandedChange(false) },
                ) {
                    ShelfGroupMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(groupLabelRes(mode))) },
                            onClick = { onGroupModeChange(mode) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun sortLabelRes(order: ShelfSortOrder): Int = when (order) {
    ShelfSortOrder.DEFAULT -> R.string.shelf_sort_default
    ShelfSortOrder.TITLE -> R.string.shelf_sort_title
    ShelfSortOrder.RECENT -> R.string.shelf_sort_recent
    ShelfSortOrder.PROGRESS_DESC -> R.string.shelf_sort_progress_desc
    ShelfSortOrder.PROGRESS_ASC -> R.string.shelf_sort_progress_asc
}

private fun groupLabelRes(mode: ShelfGroupMode): Int = when (mode) {
    ShelfGroupMode.NONE -> R.string.shelf_group_none
    ShelfGroupMode.BY_AUTHOR -> R.string.shelf_group_by_author
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

@Composable
private fun AuthorSectionHeading(authorLabel: String) {
    val cd = stringResource(R.string.shelf_author_heading_cd, authorLabel)
    Text(
        text = authorLabel,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .semantics {
                heading()
                contentDescription = cd
            },
    )
}

@Composable
private fun BookActionsDialog(
    book: Book,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = book.title,
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column {
                DialogAction(stringResource(R.string.continue_reading), onContinue)
                DialogAction(stringResource(R.string.shelf_action_pin), onPin)
                DialogAction(stringResource(R.string.shelf_action_edit), onEdit)
                DialogAction(stringResource(R.string.shelf_action_delete), onDelete)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.delete_book_cancel))
            }
        },
    )
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = label },
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
