package cn.yixin.todo

import android.content.Intent
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yixin.core.Mood
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun YixinApp(model: YixinViewModel, window: Window, setFocusOrientation: (Boolean?) -> Unit) {
    val deck by model.state.collectAsStateWithLifecycle()
    val pages by model.screens.collectAsStateWithLifecycle()
    val startupError by model.startupError.collectAsStateWithLifecycle()
    val current = pages.last()
    val focus = current is Screen.Focus
    val taskPanel = current is Screen.Detail || current is Screen.Editor
    val visible = pages.last { it !is Screen.Detail && it !is Screen.Editor }
    val theme = deck?.preferences?.theme ?: Mood.sage
    val reduce = deck?.preferences?.reduceMotion == true
    val context = LocalContext.current
    val view = LocalView.current
    val pageStates = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    var celebration by remember { mutableStateOf<Feedback.Completed?>(null) }

    LaunchedEffect(model) {
        var notice: Job? = null
        model.feedback.collect { event ->
            when (event) {
                is Feedback.Share -> context.startActivity(Intent.createChooser(event.intent, "分享一心备份"))
                is Feedback.Message -> {
                    notice?.cancel()
                    notice = launch { snackbar.showSnackbar(event.text, withDismissAction = true) }
                }
                is Feedback.Completed -> {
                    celebration = event
                    notice?.cancel()
                    notice = launch {
                        if (snackbar.showSnackbar("这件已收好", "撤销", true, SnackbarDuration.Long) ==
                            SnackbarResult.ActionPerformed
                        ) model.restore(event.token.id, event.token)
                    }
                }
            }
        }
    }

    BackHandler(enabled = pages.size > 1 && !taskPanel, onBack = model::back)
    LaunchedEffect(focus) {
        if (!focus) setFocusOrientation(null)
    }
    val dark = moodColors(theme).dark
    DisposableEffect(window, focus, dark) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark && !focus
        controller.isAppearanceLightNavigationBars = !dark && !focus
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (focus) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { if (focus) controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    YixinTheme(theme) {
        Box(Modifier.fillMaxSize()) {
            val background = if (taskPanel) Modifier.blur(5.dp).clearAndSetSemantics { } else Modifier
            Box(Modifier.fillMaxSize().then(background)) {
                if (!focus) Scene(theme, Modifier.fillMaxSize(), subdued = visible !is Screen.Home)
                val data = deck
                if (data == null) {
                    Loading(startupError, model::retry, Modifier.align(Alignment.Center))
                } else {
                    when (visible) {
                        Screen.Home, Screen.Stack, Screen.Archive, Screen.Settings ->
                            pageStates.SaveableStateProvider(visible.javaClass.simpleName) { Page(visible, data, model, setFocusOrientation) }
                        else -> Page(visible, data, model, setFocusOrientation)
                    }
                    celebration?.let { event ->
                        CompletionEffect(event, reduce) { if (celebration == event) celebration = null }
                    }
                }
            }
            deck?.let { data ->
                when (current) {
                    is Screen.Detail -> data.task(current.id)?.let { TaskDetails(it, data, model) }
                    is Screen.Editor -> TaskEditor(current, data, model)
                    else -> Unit
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, end = 16.dp, bottom = if (visible == Screen.Home) 92.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun Page(
    screen: Screen,
    deck: cn.yixin.core.DeckState,
    model: YixinViewModel,
    rotateFocus: (Boolean) -> Unit,
) {
    when (screen) {
        Screen.Home -> Home(deck, model)
        Screen.Settings -> Settings(deck, model)
        Screen.Stack -> TaskList(deck, model, archive = false)
        Screen.Archive -> TaskList(deck, model, archive = true)
        Screen.Categories -> Categories(deck, model)
        Screen.Backups -> Backups(model)
        is Screen.Draw -> DrawScreen(screen, deck, model)
        is Screen.Focus -> FocusScreen(screen.id, deck, model, rotateFocus)
        is Screen.Review -> BackupReview(screen, model)
        is Screen.Detail, is Screen.Editor -> Unit
    }
}
