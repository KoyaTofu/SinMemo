package com.example.sinmemo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sinmemo.data.memo.SavedMemo
import com.example.sinmemo.ui.memoedit.MemoEditScreen
import com.example.sinmemo.ui.memolist.MemoListScreen
import com.example.sinmemo.ui.settings.SettingsScreen

@Composable
fun SinMemoNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = MemoListRoute,
        modifier = modifier,
    ) {
        composable<MemoListRoute> { entry ->
            // 編集画面で保存したメモ（id と更新日時）。あればその行へ動かし、動かし終えたら消す（メモ一覧 詳細設計 9.3）
            val savedMemo by entry.savedStateHandle.getStateFlow<LongArray?>(KEY_SAVED_MEMO, null)
                .collectAsStateWithLifecycle()
            MemoListScreen(
                onOpenMemo = { id -> entry.ifResumed { navController.navigate(MemoEditRoute(id)) } },
                onCreateMemo = { entry.ifResumed { navController.navigate(MemoEditRoute()) } },
                onOpenSettings = { entry.ifResumed { navController.navigate(SettingsRoute) } },
                scrollTo = savedMemo?.toSavedMemo(),
                onScrolled = { entry.savedStateHandle[KEY_SAVED_MEMO] = null },
            )
        }
        composable<MemoEditRoute> { entry ->
            MemoEditScreen(
                // 保存・削除・破棄が終わると ViewModel の状態（isClosed）で呼ばれる。保存の完了がアプリを
                // 裏に回している間になることもあるので、RESUMED かどうかではなく「この画面がまだ一番上か」で
                // 判断する（2 回呼ばれても一覧まで戻りすぎない）
                onClose = { savedMemo ->
                    if (navController.currentBackStackEntry?.id == entry.id) {
                        // 保存したときだけ、戻り先の一覧に保存したメモを渡す（メモ一覧 詳細設計 9.3）
                        if (savedMemo != null) {
                            navController.previousBackStackEntry?.savedStateHandle
                                ?.set(KEY_SAVED_MEMO, savedMemo.toLongArray())
                        }
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsScreen(onBack = { entry.ifResumed { navController.popBackStack() } })
        }
    }
}

/**
 * 編集画面から一覧へ「保存したメモ」を渡すときの、一覧の SavedStateHandle のキー。
 * SavedStateHandle にそのまま入る型にするため、longArrayOf(id, updatedAt) で入れる（メモ一覧 詳細設計 9.3）
 */
private const val KEY_SAVED_MEMO = "savedMemo"

private fun SavedMemo.toLongArray(): LongArray = longArrayOf(id, updatedAt)

private fun LongArray.toSavedMemo(): SavedMemo? = if (size == 2) SavedMemo(id = this[0], updatedAt = this[1]) else null

/**
 * 二度押し対策（共通 詳細設計 5 章）。
 * 画面の切り替え中（この画面が RESUMED でない間）の操作は無視し、同じ画面が 2 枚開いたり、戻りすぎたりしないようにする。
 */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
