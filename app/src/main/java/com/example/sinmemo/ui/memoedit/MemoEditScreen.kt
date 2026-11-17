package com.example.sinmemo.ui.memoedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sinmemo.R
import com.example.sinmemo.data.memo.SavedMemo
import com.example.sinmemo.ui.theme.FontSize
import com.example.sinmemo.ui.theme.LocalFontSize
import com.example.sinmemo.ui.theme.SinMemoTheme

/**
 * S-02 メモ編集。ViewModel から状態を取り出して [MemoEditContent] に渡す（メモ編集 詳細設計 4 章）。
 * [onClose] には、新規・更新で保存して閉じたときだけ保存したメモ（id と更新日時）を渡す（それ以外は null。
 * メモ一覧 詳細設計 9.3）。
 */
@Composable
fun MemoEditScreen(
    onClose: (savedMemo: SavedMemo?) -> Unit,
    viewModel: MemoEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.isClosed) {
        if (uiState.isClosed) {
            // 一覧へ戻る前にキーボードを閉じる。閉じないと一覧に移ってからシステムが閉じるまで残る（メモ編集 基本設計 3.6・決定事項 5、作り方は詳細設計 4.2）
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            onClose(uiState.savedMemo)
        }
    }

    // 裏に回ったら書きかけを画面の状態に残す（下書き 詳細設計 2 章）
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onStop()
    }

    MemoEditContent(
        uiState = uiState,
        text = viewModel.text,
        inputTransformation = viewModel.inputTransformation,
        // 文字サイズの設定（設定 詳細設計 5 章）
        fontSize = LocalFontSize.current,
        onSave = viewModel::onSave,
        onBack = viewModel::onBack,
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onDiscardConfirm = viewModel::onDiscardConfirm,
        onDialogDismiss = viewModel::onDialogDismiss,
        onTooLongMessageShown = viewModel::onTooLongMessageShown,
        onFailureShown = viewModel::onFailureShown,
    )
}

/** 状態だけで S-02 を描く。プレビューと UI テストではこちらを使う。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoEditContent(
    uiState: MemoEditUiState,
    text: TextFieldState,
    inputTransformation: InputTransformation?,
    fontSize: FontSize,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDiscardConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onTooLongMessageShown: () -> Unit,
    onFailureShown: (failureId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 本文から決まる表示（入力のたびに ViewModel の状態を作り直さないよう、画面側で求める）
    val canSave by remember(text, uiState.isBusy, uiState.isLoading) {
        derivedStateOf { !uiState.isBusy && !uiState.isLoading && !MemoTextLimit.isBlank(text.text) }
    }
    val countToShow by remember(text) { derivedStateOf { MemoTextLimit.countToShow(text.text.length) } }

    // Android の戻る操作も ← と同じ扱い（ダイアログ表示中はダイアログ側が戻る操作を受け取る）
    BackHandler(onBack = onBack)

    val snackbarHostState = remember { SnackbarHostState() }
    val tooLongMessage = stringResource(R.string.input_too_long)
    LaunchedEffect(uiState.showTooLongMessage) {
        if (uiState.showTooLongMessage) {
            snackbarHostState.showSnackbar(tooLongMessage)
            onTooLongMessageShown()
        }
    }
    // 保存・削除の失敗。上限のメッセージと同じ帯で出す（同時には 1 つずつ。メモ編集 詳細設計 9.7）
    val saveFailedMessage = stringResource(R.string.save_failed)
    val deleteFailedMessage = stringResource(R.string.delete_failed)
    val overLimitMessage = stringResource(R.string.save_over_limit)
    // 通し番号も鍵にする。出している間に次の失敗が起きたら、出し直す（同じ種類でも）
    LaunchedEffect(uiState.failure, uiState.failureId) {
        val failure = uiState.failure ?: return@LaunchedEffect
        val failureId = uiState.failureId
        snackbarHostState.showSnackbar(
            when (failure) {
                EditFailure.SaveFailed -> saveFailedMessage
                EditFailure.DeleteFailed -> deleteFailedMessage
                EditFailure.OverLimit -> overLimitMessage
            },
        )
        onFailureShown(failureId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSave,
                        enabled = canSave,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(painterResource(R.drawable.ic_check), stringResource(R.string.save))
                    }
                    // 新規作成中は ⋮ を出さない（画面一覧 決定事項 11）
                    if (!uiState.isNew) {
                        DeleteMenu(onDeleteRequest = onDeleteRequest)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // キーボードの分も Scaffold の余白に入れる。入力欄・文字数の行と、上限のメッセージ（Snackbar）が
        // どちらもキーボードの上に来る（メモ編集 基本設計 決定事項 4、詳細設計 4.3・決定事項 6）
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            // 本文は残りの高さいっぱい。文字数はその下の別の行にして、本文と重ならないようにする
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 既存メモは読み込むまで入力欄を出さない（3.3）
                if (!uiState.isLoading) {
                    MemoBodyField(
                        text = text,
                        inputTransformation = inputTransformation,
                        fontSize = fontSize,
                        // 書きかけを戻して開いたときは、新規でもキーボードを自動で出さない（下書き 基本設計 3.2）
                    requestFocus = uiState.isNew && !uiState.isRestored,
                    )
                }
            }
            countToShow?.let { count ->
                Text(
                    text = stringResource(
                        R.string.char_count,
                        MemoTextLimit.format(count),
                        MemoTextLimit.format(MemoTextLimit.MAX_LENGTH),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (count >= MemoTextLimit.MAX_LENGTH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    when (uiState.dialog) {
        EditDialog.Delete -> DeleteConfirmDialog(onConfirm = onDeleteConfirm, onDismiss = onDialogDismiss)
        EditDialog.Discard -> DiscardConfirmDialog(onConfirm = onDiscardConfirm, onDismiss = onDialogDismiss)
        null -> Unit
    }
}

/** 本文の入力欄。空のときは「メモを入力」と薄く表示する。 */
@Composable
private fun MemoBodyField(
    text: TextFieldState,
    inputTransformation: InputTransformation?,
    fontSize: FontSize,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 新規のときだけ、最初に 1 回フォーカスを当ててキーボードを出す（メモ編集 基本設計 決定事項 1）
    var focusRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestFocus) {
        if (requestFocus && !focusRequested) {
            focusRequested = true
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val bodyDescription = stringResource(R.string.memo_body)
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        state = text,
        inputTransformation = inputTransformation,
        textStyle = TextStyle(fontSize = fontSize.body, lineHeight = 1.7.em, color = textColor),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .semantics { contentDescription = bodyDescription },
        decorator = { innerTextField ->
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (text.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.memo_body_placeholder),
                        fontSize = fontSize.body,
                        lineHeight = 1.7.em,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** アプリバー右上の ⋮ と、「削除」だけのメニュー。 */
@Composable
private fun DeleteMenu(onDeleteRequest: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.menu))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    expanded = false
                    onDeleteRequest()
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoEditPreview() {
    SinMemoTheme {
        MemoEditContent(
            uiState = MemoEditUiState(isNew = false),
            text = TextFieldState("牛乳、卵、パン\n洗剤"),
            inputTransformation = null,
            fontSize = FontSize.MEDIUM,
            onSave = {}, onBack = {}, onDeleteRequest = {}, onDeleteConfirm = {},
            onDiscardConfirm = {}, onDialogDismiss = {}, onTooLongMessageShown = {}, onFailureShown = { _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoEditNewEmptyPreview() {
    SinMemoTheme {
        MemoEditContent(
            uiState = MemoEditUiState(isNew = true),
            text = TextFieldState(),
            inputTransformation = null,
            fontSize = FontSize.MEDIUM,
            onSave = {}, onBack = {}, onDeleteRequest = {}, onDeleteConfirm = {},
            onDiscardConfirm = {}, onDialogDismiss = {}, onTooLongMessageShown = {}, onFailureShown = { _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoEditDiscardDarkPreview() {
    SinMemoTheme(darkTheme = true) {
        MemoEditContent(
            uiState = MemoEditUiState(isNew = false, dialog = EditDialog.Discard),
            text = TextFieldState("あ".repeat(9_688)),
            inputTransformation = null,
            fontSize = FontSize.LARGE,
            onSave = {}, onBack = {}, onDeleteRequest = {}, onDeleteConfirm = {},
            onDiscardConfirm = {}, onDialogDismiss = {}, onTooLongMessageShown = {}, onFailureShown = { _ -> },
        )
    }
}
