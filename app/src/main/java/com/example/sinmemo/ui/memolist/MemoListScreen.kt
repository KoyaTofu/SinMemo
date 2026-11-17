package com.example.sinmemo.ui.memolist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sinmemo.R
import com.example.sinmemo.data.memo.SavedMemo
import com.example.sinmemo.ui.theme.FontSize
import com.example.sinmemo.ui.theme.LocalFontSize
import com.example.sinmemo.ui.theme.SinMemoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select

/** S-01 メモ一覧。ViewModel から状態を取り出して [MemoListContent] に渡す（メモ一覧 詳細設計 1 章・2 章）。 */
@Composable
fun MemoListScreen(
    onOpenMemo: (Long) -> Unit,
    onCreateMemo: () -> Unit,
    onOpenSettings: () -> Unit,
    scrollTo: SavedMemo? = null,
    onScrolled: () -> Unit = {},
    viewModel: MemoListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loadErrorId by viewModel.loadErrorId.collectAsStateWithLifecycle()
    MemoListContent(
        uiState = uiState,
        // 文字サイズの設定（設定 詳細設計 5 章）
        fontSize = LocalFontSize.current,
        onOpenMemo = onOpenMemo,
        onCreateMemo = onCreateMemo,
        onOpenSettings = onOpenSettings,
        scrollTo = scrollTo,
        onScrolled = onScrolled,
        loadErrorId = loadErrorId,
        onLoadErrorShown = viewModel::onLoadErrorShown,
    )
}

/**
 * 状態だけで S-01 を描く。プレビューと UI テストではこちらを使う。
 * [scrollTo] は編集画面で保存したメモ（id と更新日時）。その行が新しい一覧に入ったらその行へ動かし（ふつうは先頭）、
 * [onScrolled] を呼ぶ（メモ一覧 詳細設計 9.3）。null なら今の位置のまま。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoListContent(
    uiState: MemoListUiState,
    fontSize: FontSize,
    onOpenMemo: (Long) -> Unit,
    onCreateMemo: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    scrollTo: SavedMemo? = null,
    onScrolled: () -> Unit = {},
    loadErrorId: Int? = null,
    onLoadErrorShown: (Int) -> Unit = {},
) {
    val items by rememberUpdatedState(uiState.items)
    val scrolled by rememberUpdatedState(onScrolled)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(scrollTo) {
        val saved = scrollTo ?: return@LaunchedEffect
        // 保存したメモが新しい一覧に入るのを待ってから動かす。DB からの新しい一覧が届く前に動かすと、
        // 一覧は「それまで先頭だった行」の位置を保とうとするので、届いた新しい行が上に隠れてしまう。
        // id と更新日時の両方で見る（古い一覧にある同じ id の行（更新前の日時）では進まない）。
        // 端末の時計が戻っていると先頭に来ないので、先頭かどうかは問わずその行へ動かす。
        // 画面が表示されている（RESUMED）間の合計で 2 秒たっても入らなければ、位置を変えずにあきらめる
        // （裏にいる間は数えない。✓ の直後にホームへ戻っても、戻ったときに動けるように。メモ一覧 詳細設計 9.3）
        val index = coroutineScope {
            val found = async {
                snapshotFlow {
                    items.indexOfFirst { it.id == saved.id && it.updatedAtMillis == saved.updatedAt }
                }.first { it >= 0 }
            }
            val timeout = async { waitWhileResumed(lifecycle, SCROLL_WAIT_MILLIS) }
            select<Int?> {
                found.onAwait { it }
                timeout.onAwait { null }
            }.also {
                found.cancel()
                timeout.cancel()
            }
        }
        if (index != null) listState.scrollToItem(index)
        scrolled()
    }

    // 一覧を読めなかったときの知らせ（メモ一覧 詳細設計 9.4）。通し番号を鍵にして 1 回ずつ出す
    val snackbarHostState = remember { SnackbarHostState() }
    val loadFailedMessage = stringResource(R.string.memo_load_failed)
    LaunchedEffect(loadErrorId) {
        val id = loadErrorId ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(loadFailedMessage)
        onLoadErrorShown(id)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { OverflowMenu(onOpenSettings = onOpenSettings) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateMemo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.create_memo),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // 最後の行が FAB に隠れないように下を空ける
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            items(uiState.items, key = { it.id }) { item ->
                MemoListRow(item = item, fontSize = fontSize, onClick = { onOpenMemo(item.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** 保存したメモが一覧に入るのを待つ最長の時間。画面が表示されている間だけ数える（メモ一覧 詳細設計 9.3） */
internal const val SCROLL_WAIT_MILLIS = 2_000L

/** 表示されている時間を数える刻み */
private const val SCROLL_WAIT_TICK_MILLIS = 100L

/**
 * [lifecycle] が RESUMED（画面が表示されている）の間の合計が [millis] になるまで待つ。
 * 裏にいる間（RESUMED でない間）は数えない。[SCROLL_WAIT_TICK_MILLIS] ごとに数える。
 */
private suspend fun waitWhileResumed(lifecycle: Lifecycle, millis: Long) {
    var counted = 0L
    while (counted < millis) {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        delay(SCROLL_WAIT_TICK_MILLIS)
        counted += SCROLL_WAIT_TICK_MILLIS
    }
}

/** アプリバー右上の ⋮ と、「設定」だけのメニュー。 */
@Composable
private fun OverflowMenu(onOpenSettings: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.menu),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
        }
    }
}

/** 一覧の 1 行（本文 1 行目＋更新日時）。 */
@Composable
private fun MemoListRow(
    item: MemoListItem,
    fontSize: FontSize,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = item.title,
            fontSize = fontSize.title,
            lineHeight = 1.5.em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.updatedAt,
            fontSize = fontSize.date,
            lineHeight = 1.5.em,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val previewState = MemoListUiState(
    items = listOf(
        MemoListItem(1, "牛乳、卵、パン、洗剤、トイレットペーパー、ティッシュ、電池", "11/12 14:30", 1_762_925_400_000L),
        MemoListItem(2, "打ち合わせメモ", "11/10 09:15", 1_762_733_700_000L),
        MemoListItem(3, "旅行の持ち物", "2025/12/28 21:04", 1_766_923_440_000L),
    ),
)

@Preview(showBackground = true)
@Composable
private fun MemoListPreview() {
    SinMemoTheme {
        MemoListContent(previewState, FontSize.MEDIUM, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoListDarkLargePreview() {
    SinMemoTheme(darkTheme = true) {
        MemoListContent(previewState, FontSize.LARGE, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoListEmptyPreview() {
    SinMemoTheme {
        MemoListContent(MemoListUiState(), FontSize.MEDIUM, {}, {}, {})
    }
}
