package spam.blocker.ui.setting.quick

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.ColorPickerButton
import spam.blocker.ui.widgets.FloatingWindow
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

val DefaultCallerIdBgColor = 0xE6111827.toInt()
const val DefaultCallerIdTemplate = """<font color="#94A3B8"><small>来电类型</small></font>
<br>
<font color="{category_color}"><b><big><big><big>{category}</big></big></big></b></font>
<br><br>
<font color="#F1F5F9"><b><big>{geolocation}</big></b></font>
<br>
<font color="#CBD5E1"><big>{carrier}</big></font>
"""

private const val LegacyCallerIdTemplate =
"""<font color="#50c878">
    <big><big>
        {reason}
    </big></big>
</font>
<br>
<font color="#00bfff">
    <big>
        {geolocation}
    </big>
</font>
<br>
<font color="#ea86ff">
    <big>
        {carrier}
    </big>
</font>
"""

const val FullHtmlTagList = "https://github.com/aj3423/SpamBlocker/issues/555"
private val callerIdHideHandler by lazy { Handler(Looper.getMainLooper()) }
private var callerIdHideCallback: Runnable? = null

private fun migrateCallerIdDefaults(ctx: Context) {
    val prefs = spf.CallerID(ctx)
    if (prefs.template.trim() == LegacyCallerIdTemplate.trim()) {
        prefs.template = DefaultCallerIdTemplate
        if (prefs.bgColor == 0x20808080) prefs.bgColor = DefaultCallerIdBgColor
    }
}

fun callerIdView(ctx: Context, template: String) : View {
    return TextView(ctx).apply {
        text = Html.fromHtml(template, Html.FROM_HTML_MODE_LEGACY)
        gravity = Gravity.CENTER
    }
}

fun showCallerIdWindow(
    ctx: Context,
    reason: String? = "{reason}",
    geolocation: String? = "{geolocation}",
    carrier: String? = "{carrier}",
    category: String? = "{category}",
    categoryIsSpam: Boolean = false,
    autoHideMs: Long = 0,
) {
    migrateCallerIdDefaults(ctx)
    val spf = spf.CallerID(ctx)

    // Permission.phoneState is needed to close the floating dialog when the call gets answered or rejected.
    if(!Permission.phoneState.isGranted || !Permission.showOverlay.isGranted)
        return

    callerIdHideCallback?.let { callerIdHideHandler.removeCallbacks(it) }
    callerIdHideCallback = null

    val label = category?.trim()?.takeIf { it.isNotEmpty() }
        ?: ctx.getString(if (categoryIsSpam) R.string.caller_id_spam_type else R.string.caller_id_unknown_type)
    val labelColor = when {
        categoryIsSpam -> "#FF5252"
        category.isNullOrBlank() -> "#FFB74D"
        else -> "#38BDF8"
    }
    val values = mapOf(
        "{reason}" to (reason?.trim()?.takeIf { it.isNotEmpty() } ?: "—"),
        "{geolocation}" to (geolocation?.trim()?.takeIf { it.isNotEmpty() } ?: "—"),
        "{carrier}" to (carrier?.trim()?.takeIf { it.isNotEmpty() } ?: "—"),
        "{category}" to Html.escapeHtml(label),
        "{category_color}" to labelColor,
    )
    val content = Regex("\\{(?:reason|geolocation|carrier|category|category_color)\\}")
        .replace(spf.template) { values.getValue(it.value) }

    FloatingWindow.apply {
        update(ctx, bgColor = spf.bgColor, x = spf.x, y = spf.y)
        onDrag = { newX, newY ->
            spf.x = newX
            spf.y = newY
        }
        show(ctx, callerIdView(ctx, content))
    }
    if (autoHideMs > 0) {
        val application = ctx.applicationContext
        callerIdHideCallback = Runnable {
            FloatingWindow.hide(application)
            callerIdHideCallback = null
        }.also { callerIdHideHandler.postDelayed(it, autoHideMs) }
    }
}

@Composable
private fun ConfigDialog(
    trigger: MutableState<Boolean>
) {
    val ctx = LocalContext.current
    migrateCallerIdDefaults(ctx)
    val spf = spf.CallerID(ctx)

    var bgColor by remember { mutableStateOf(Color(spf.bgColor)) }
    var template by remember { mutableStateOf(spf.template) }

    // Destroy the floating window when the app is killed
    DisposableEffect(true) {
        onDispose {
            FloatingWindow.hide(ctx)
        }
    }
    PopupDialog(
        trigger,
        popupSize = PopupSize(),
        onDismiss = {
            FloatingWindow.hide(ctx)
        },
        buttons = {
            val C = G.palette

            val confirmTrigger = remember { mutableStateOf(false) }
            PopupDialog(
                trigger = confirmTrigger,
                buttons = {
                    // Reset
                    StrokeButton(Str(R.string.reset), color = C.error) {
                        spf.bgColor = DefaultCallerIdBgColor
                        spf.template = DefaultCallerIdTemplate
                        bgColor = Color(DefaultCallerIdBgColor)
                        template = DefaultCallerIdTemplate
                        showCallerIdWindow(ctx)
                        confirmTrigger.value = false
                    }
                }
            ) {
                GreyText(Str(R.string.confirm_to_reset))
            }

            // Reset button
            StrokeButton(Str(R.string.reset), color = G.palette.error) {
                confirmTrigger.value = true
            }
        }
    ) {
        // BG Color
        LabeledRow(R.string.background_color) {
            ColorPickerButton(
                color = bgColor,
                onSelect = {
                    if (it != null) {
                        bgColor = it
                        spf.bgColor = it.toArgb()
                        FloatingWindow.update(ctx, bgColor = spf.bgColor)
                    }
                }
            )
        }

        // 2. Template
        StrInputBox(
            text = template,
            label = { Text(Str(R.string.content_template)) },
            helpTooltip = Str(R.string.help_caller_id_template).format(FullHtmlTagList),
            onValueChange = {
                template = it
                spf.template = it

                showCallerIdWindow(ctx)
            }
        )
    }
}

@Composable
fun CallerID() {
    val ctx = LocalContext.current
    val spf = spf.CallerID(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled && Permission.phoneState.isGranted && Permission.showOverlay.isGranted) }

    LabeledRow(
        R.string.caller_id,
        helpTooltip = Str(R.string.help_caller_id),
        content = {
            val C = G.palette
            RowVCenterSpaced(4) {
                val trigger = remember { mutableStateOf(false) }
                ConfigDialog(trigger)

                if (isEnabled) {

                    // Preview button
                    StrokeButton(
                        label = Str(R.string.preview),
                        color = C.textGrey,
                    ) {
                        showCallerIdWindow(ctx)

                        trigger.value = true
                    }
                }

                SwitchBox(isEnabled) { isTurningOn ->
                    if (isTurningOn) {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                PermissionWrapper(Permission.showOverlay),
                                // Permission.phoneState is needed to close the floating dialog when the call gets answered or rejected.
                                PermissionWrapper(Permission.phoneState),
                            )
                        ) { granted ->
                            if (granted) {
                                isEnabled = true
                                spf.isEnabled = true
                            }
                        }
                    } else {
                        isEnabled = false
                        spf.isEnabled = false
                    }
                }
            }
        }
    )
}

@Composable
fun CallerIDSummary() {
    val ctx = LocalContext.current
    val spf = spf.CallerID(ctx)

    val isEnabled by remember { mutableStateOf(spf.isEnabled && Permission.phoneState.isGranted && Permission.showOverlay.isGranted) }
    if (isEnabled) {
        val C = G.palette
        StrokeButton(
            label = "ID",
            icon = { GreyIcon16(R.drawable.ic_call) },
            color = C.textGrey,
            enabled = false,
        )
    }
}
