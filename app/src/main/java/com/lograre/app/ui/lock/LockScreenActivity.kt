package com.lograre.app.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lograre.app.LograreApplication
import com.lograre.app.R
import com.lograre.app.ui.components.BREATH_CYCLE_DURATION_MS
import com.lograre.app.ui.components.BreathingCircle
import com.lograre.app.ui.components.GlassCard
import com.lograre.app.ui.components.PinDots
import com.lograre.app.ui.components.PinKeypad
import com.lograre.app.ui.components.ZenBackground
import com.lograre.app.ui.components.ZenButton
import com.lograre.app.ui.components.ZenButtonStyle
import com.lograre.app.ui.components.ZenHaptics
import com.lograre.app.ui.components.ZenPillChip
import com.lograre.app.ui.components.ZenTextButton
import com.lograre.app.ui.components.rememberAnimationsEnabled
import com.lograre.app.ui.components.rememberIsLifecycleResumed
import com.lograre.app.ui.main.AppIconBadge
import com.lograre.app.ui.theme.AppTheme
import com.lograre.app.ui.theme.LocalZenPalette
import com.lograre.app.ui.theme.LocalZenTypography
import com.lograre.app.ui.theme.ThemeMode
import com.lograre.app.ui.theme.ZenDimens
import com.lograre.app.ui.theme.ZenShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun totalWaitDurationMillis(
    requiredBreathCycles: Int = LockScreenActivity.BREATH_CYCLES_BEFORE_KEYPAD
): Long = requiredBreathCycles.coerceAtLeast(0) * BREATH_CYCLE_DURATION_MS

internal fun totalWaitSeconds(
    requiredBreathCycles: Int = LockScreenActivity.BREATH_CYCLES_BEFORE_KEYPAD
): Int = (totalWaitDurationMillis(requiredBreathCycles) / 1_000L).toInt()

internal fun computeWaterFillFraction(
    elapsedMillis: Long,
    totalWaitMillis: Long
): Float {
    if (totalWaitMillis <= 0L) return 1f
    return (elapsedMillis.coerceAtLeast(0L).toFloat() / totalWaitMillis.toFloat()).coerceIn(0f, 1f)
}

internal fun shouldShowLockKeypad(
    elapsedMillis: Long,
    requiredBreathCycles: Int = LockScreenActivity.BREATH_CYCLES_BEFORE_KEYPAD
): Boolean = elapsedMillis >= totalWaitDurationMillis(requiredBreathCycles)

private sealed interface ExtraTimeChoice {
    data class Preset(val minutes: Int) : ExtraTimeChoice
    data object Custom : ExtraTimeChoice
}

/**
 * Full-screen overlay activity shown over a blocked app when its daily limit is reached.
 *
 * Designed as a calm breathing pause ("Lago y bosque"):
 * - Centered [BreathingCircle] with the blocked app icon inside for [BREATH_CYCLES_BEFORE_KEYPAD] cycles.
 * - Shrinks smoothly to reveal [PinDots] and [PinKeypad] with haptics and water ripples.
 * - If the user cancels, presses Back, or leaves without unlocking, they are sent directly
 *   to the device launcher ([Intent.ACTION_MAIN] + [Intent.CATEGORY_HOME]).
 */
class LockScreenActivity : ComponentActivity() {

    private var blockedPackageState by mutableStateOf<String?>(null)
    private var lockSessionId by mutableIntStateOf(0)
    private var unlockedAndReturning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val initialPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.takeIf { it.isNotBlank() }
        if (initialPackage == null) {
            goToLauncherAndFinish()
            return
        }
        blockedPackageState = initialPackage
        lockSessionId += 1

        val container = (application as LograreApplication).container

        setContent {
            val themeMode by container.themePreferenceRepository.themeMode.collectAsState()
            AppTheme(themeMode = themeMode) {
                val activePackage = blockedPackageState
                if (activePackage == null) {
                    LaunchedEffect(Unit) { goToLauncherAndFinish() }
                    return@AppTheme
                }

                val appLabel = remember(activePackage) { resolveAppLabel(activePackage) }
                val controller = remember(activePackage) {
                    LockScreenController(
                        packageName = activePackage,
                        appLabel = appLabel,
                        pinRepository = container.pinRepository,
                        appLimitRepository = container.appLimitRepository,
                        dailyResetCoordinator = container.dailyResetCoordinator
                    )
                }
                val uiState by controller.uiState.collectAsState()
                val scope = rememberCoroutineScope()
                val view = LocalView.current

                var pinErrorTrigger by remember(activePackage) { mutableIntStateOf(0) }

                BackHandler {
                    controller.onExitRequested()
                }

                LaunchedEffect(uiState.outcome) {
                    when (uiState.outcome) {
                        LockScreenOutcome.UnlockedReturnToApp -> {
                            unlockedAndReturning = true
                            finish()
                        }
                        LockScreenOutcome.ExitToLauncher -> {
                            goToLauncherAndFinish()
                        }
                        null -> Unit
                    }
                }

                LockScreenContent(
                    uiState = uiState,
                    sessionKey = lockSessionId,
                    pinErrorTrigger = pinErrorTrigger,
                    requiredBreathCycles = BREATH_CYCLES_BEFORE_KEYPAD,
                    onDigitClick = controller::onDigitPressed,
                    onBackspaceClick = controller::onBackspacePressed,
                    onSubmitPin = {
                        scope.launch(Dispatchers.Default) {
                            val accepted = controller.submitPin()
                            withContext(Dispatchers.Main) {
                                if (accepted) {
                                    ZenHaptics.confirm(view)
                                } else {
                                    pinErrorTrigger += 1
                                    ZenHaptics.reject(view)
                                }
                            }
                        }
                    },
                    onPresetExtraMinutes = { minutes ->
                        scope.launch(Dispatchers.IO) {
                            val ok = controller.grantExtraMinutes(minutes)
                            if (ok) {
                                withContext(Dispatchers.Main) { ZenHaptics.confirm(view) }
                            }
                        }
                    },
                    onCustomMinutesChanged = controller::onCustomMinutesChanged,
                    onSubmitCustomMinutes = {
                        scope.launch(Dispatchers.IO) {
                            val ok = controller.grantCustomExtraMinutes()
                            withContext(Dispatchers.Main) {
                                if (ok) ZenHaptics.confirm(view) else ZenHaptics.reject(view)
                            }
                        }
                    },
                    onIgnoreToday = {
                        scope.launch(Dispatchers.IO) {
                            val ok = controller.ignoreForToday()
                            if (ok) {
                                withContext(Dispatchers.Main) { ZenHaptics.confirm(view) }
                            }
                        }
                    },
                    onExitToLauncher = controller::onExitRequested
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.takeIf { it.isNotBlank() }
        if (newPackage != null) {
            unlockedAndReturning = false
            blockedPackageState = newPackage
            lockSessionId += 1
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!unlockedAndReturning) {
            finish()
        }
    }

    private fun resolveAppLabel(packageName: String): String = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun goToLauncherAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "com.lograre.app.EXTRA_BLOCKED_PACKAGE"
        const val BREATH_CYCLES_BEFORE_KEYPAD: Int = 2

        fun createIntent(context: Context, packageName: String): Intent =
            Intent(context, LockScreenActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
    }
}

@Composable
internal fun LockScreenContent(
    uiState: LockScreenUiState,
    sessionKey: Int = 0,
    pinErrorTrigger: Int,
    requiredBreathCycles: Int,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onSubmitPin: () -> Unit,
    onPresetExtraMinutes: (Int) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    onSubmitCustomMinutes: () -> Unit,
    onIgnoreToday: () -> Unit,
    onExitToLauncher: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val animationsEnabled = rememberAnimationsEnabled()
    val isResumed = rememberIsLifecycleResumed()
    val density = LocalDensity.current
    val view = LocalView.current

    val totalWaitMs = remember(requiredBreathCycles) {
        totalWaitDurationMillis(requiredBreathCycles)
    }
    val waitSeconds = remember(requiredBreathCycles) {
        totalWaitSeconds(requiredBreathCycles)
    }

    var elapsedWaitMs by remember(sessionKey, uiState.packageName, requiredBreathCycles) {
        mutableLongStateOf(0L)
    }
    var completionRippleTrigger by remember(sessionKey, uiState.packageName, requiredBreathCycles) {
        mutableIntStateOf(0)
    }
    var completionNotified by remember(sessionKey, uiState.packageName, requiredBreathCycles) {
        mutableStateOf(totalWaitMs <= 0L)
    }

    // Monotonic linear wait loop (continues rising linearly even when ANIMATOR_DURATION_SCALE == 0,
    // and pauses when the screen is not RESUMED).
    LaunchedEffect(sessionKey, uiState.packageName, requiredBreathCycles, uiState.stage, isResumed) {
        if (uiState.stage != LockScreenStage.PIN_ENTRY || totalWaitMs <= 0L) {
            elapsedWaitMs = totalWaitMs
            return@LaunchedEffect
        }
        if (!isResumed || elapsedWaitMs >= totalWaitMs) return@LaunchedEffect

        var lastNano = System.nanoTime()
        while (isActive && elapsedWaitMs < totalWaitMs) {
            delay(16L)
            val nowNano = System.nanoTime()
            val deltaMs = ((nowNano - lastNano) / 1_000_000L).coerceAtLeast(0L)
            lastNano = nowNano
            elapsedWaitMs = (elapsedWaitMs + deltaMs).coerceAtMost(totalWaitMs)
        }

        if (elapsedWaitMs >= totalWaitMs && !completionNotified) {
            completionNotified = true
            ZenHaptics.keyPress(view)
            if (animationsEnabled) {
                completionRippleTrigger += 1
            }
        }
    }

    val waterLevel = remember(elapsedWaitMs, totalWaitMs, uiState.stage) {
        if (uiState.stage != LockScreenStage.PIN_ENTRY) {
            1f
        } else {
            computeWaterFillFraction(elapsedWaitMs, totalWaitMs)
        }
    }
    val showKeypad = shouldShowLockKeypad(
        elapsedMillis = elapsedWaitMs,
        requiredBreathCycles = requiredBreathCycles
    )
    val isCompactCircle = uiState.stage != LockScreenStage.PIN_ENTRY || showKeypad

    val iconSize by animateDpAsState(
        targetValue = if (isCompactCircle) ZenDimens.AppIconSize else ZenDimens.LockScreenAppIconSize,
        animationSpec = tween(
            durationMillis = if (animationsEnabled) 400 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "lock_icon_size"
    )

    // TalkBack polite live region: announces ONCE on open ("El PIN estará disponible en N segundos")
    // and ONCE when the keypad becomes available ("Teclado disponible. Escribe tu PIN.").
    val circleTalkBackAnnouncement = when {
        uiState.stage != LockScreenStage.PIN_ENTRY -> ""
        !showKeypad && waitSeconds > 0 -> stringResource(R.string.lock_talkback_wait, waitSeconds)
        else -> stringResource(R.string.lock_talkback_keypad_ready)
    }

    ZenBackground(dimmedForFocus = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = ZenDimens.ScreenHorizontalPadding,
                    vertical = ZenDimens.Space24
                )
                .semantics { isTraversalGroup = true },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (!showKeypad && uiState.stage == LockScreenStage.PIN_ENTRY) {
                Arrangement.Center
            } else {
                Arrangement.spacedBy(ZenDimens.Space16)
            }
        ) {
            BreathingCircle(
                compact = isCompactCircle,
                waterLevel = waterLevel,
                completionRippleTrigger = completionRippleTrigger,
                modifier = Modifier.semantics {
                    traversalIndex = 4f
                    if (circleTalkBackAnnouncement.isNotEmpty()) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = circleTalkBackAnnouncement
                    }
                }
            ) {
                AppIconBadge(
                    packageName = uiState.packageName,
                    appLabel = uiState.appLabel,
                    size = iconSize
                )
            }

            if (!showKeypad && uiState.stage == LockScreenStage.PIN_ENTRY) {
                Spacer(modifier = Modifier.height(ZenDimens.Space24))
            }

            // TalkBack order 1: Message
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { traversalIndex = 0f },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
            ) {
                Text(
                    text = stringResource(R.string.lock_title, uiState.appLabel),
                    style = typo.title,
                    color = palette.text,
                    textAlign = TextAlign.Center
                )
                val subtitleText = when (uiState.stage) {
                    LockScreenStage.UNLOCKED_OPTIONS ->
                        stringResource(R.string.lock_unlocked_subtitle, uiState.appLabel)
                    LockScreenStage.PIN_NOT_CONFIGURED ->
                        stringResource(R.string.lock_pin_not_configured)
                    LockScreenStage.PIN_ENTRY -> if (showKeypad) {
                        stringResource(R.string.lock_subtitle_enter_pin)
                    } else {
                        stringResource(R.string.lock_subtitle)
                    }
                }
                AnimatedContent(
                    targetState = subtitleText,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        if (!animationsEnabled) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = 260,
                                    delayMillis = 140,
                                    easing = FastOutSlowInEasing
                                )
                            ) togetherWith fadeOut(
                                animationSpec = tween(
                                    durationMillis = 140,
                                    easing = FastOutSlowInEasing
                                )
                            ) using SizeTransform(clip = false)
                        }
                    },
                    label = "lock_subtitle_transition"
                ) { textValue ->
                    Text(
                        text = textValue,
                        style = typo.body,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when (uiState.stage) {
                LockScreenStage.PIN_ENTRY -> {
                    AnimatedVisibility(
                        visible = showKeypad,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = if (animationsEnabled) 400 else 0,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = if (animationsEnabled) 400 else 0,
                                easing = FastOutSlowInEasing
                            )
                        ) {
                            with(density) { 16.dp.roundToPx() }
                        }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)
                        ) {
                            // TalkBack order 2: PIN dots
                            PinDots(
                                enteredLength = uiState.pinInput.length,
                                errorTrigger = pinErrorTrigger,
                                modifier = Modifier.semantics { traversalIndex = 1f }
                            )

                            val calmError = if (uiState.pinError != null) {
                                stringResource(R.string.pin_incorrect)
                            } else {
                                null
                            }

                            // TalkBack order 3: PIN keypad
                            PinKeypad(
                                pinLength = uiState.pinInput.length,
                                errorMessage = calmError,
                                confirmLabel = stringResource(R.string.pin_confirm_unlock),
                                enabled = !uiState.isProcessing,
                                showDots = false,
                                onDigitClick = onDigitClick,
                                onBackspaceClick = onBackspaceClick,
                                onConfirmClick = onSubmitPin,
                                modifier = Modifier.semantics { traversalIndex = 2f }
                            )
                        }
                    }

                    if (!showKeypad) {
                        Spacer(modifier = Modifier.height(ZenDimens.Space32))
                    }

                    // TalkBack order 4: Exit to launcher
                    ZenTextButton(
                        text = stringResource(R.string.action_exit),
                        onClick = onExitToLauncher,
                        color = palette.textSecondary,
                        modifier = Modifier.semantics { traversalIndex = 3f }
                    )
                }

                LockScreenStage.UNLOCKED_OPTIONS -> {
                    UnlockedOptionsSection(
                        uiState = uiState,
                        onPresetExtraMinutes = onPresetExtraMinutes,
                        onCustomMinutesChanged = onCustomMinutesChanged,
                        onSubmitCustomMinutes = onSubmitCustomMinutes,
                        onIgnoreToday = onIgnoreToday,
                        onExitToLauncher = onExitToLauncher
                    )
                }

                LockScreenStage.PIN_NOT_CONFIGURED -> {
                    Spacer(modifier = Modifier.height(ZenDimens.Space16))
                    ZenButton(
                        text = stringResource(R.string.action_exit),
                        onClick = onExitToLauncher,
                        style = ZenButtonStyle.PRIMARY
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnlockedOptionsSection(
    uiState: LockScreenUiState,
    onPresetExtraMinutes: (Int) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    onSubmitCustomMinutes: () -> Unit,
    onIgnoreToday: () -> Unit,
    onExitToLauncher: () -> Unit
) {
    val palette = LocalZenPalette.current
    val typo = LocalZenTypography.current
    val animationsEnabled = rememberAnimationsEnabled()
    val largeFont = LocalDensity.current.fontScale >= 1.5f

    var selectedChoice by remember {
        mutableStateOf<ExtraTimeChoice>(
            ExtraTimeChoice.Preset(LockScreenController.PRESET_EXTRA_MINUTES.first())
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space16)) {
                Text(
                    text = stringResource(R.string.lock_add_more_time),
                    style = typo.subtitle,
                    color = palette.text
                )

                if (largeFont) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space8),
                        verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
                    ) {
                        LockScreenController.PRESET_EXTRA_MINUTES.forEach { minutes ->
                            ZenPillChip(
                                text = stringResource(R.string.lock_preset_minutes, minutes),
                                selected = selectedChoice == ExtraTimeChoice.Preset(minutes),
                                enabled = !uiState.isProcessing,
                                onClick = { selectedChoice = ExtraTimeChoice.Preset(minutes) }
                            )
                        }
                        ZenPillChip(
                            text = stringResource(R.string.lock_preset_other),
                            selected = selectedChoice == ExtraTimeChoice.Custom,
                            enabled = !uiState.isProcessing,
                            onClick = { selectedChoice = ExtraTimeChoice.Custom }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZenDimens.Space8)
                    ) {
                        LockScreenController.PRESET_EXTRA_MINUTES.forEach { minutes ->
                            ZenPillChip(
                                text = stringResource(R.string.lock_preset_minutes, minutes),
                                selected = selectedChoice == ExtraTimeChoice.Preset(minutes),
                                enabled = !uiState.isProcessing,
                                onClick = { selectedChoice = ExtraTimeChoice.Preset(minutes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ZenPillChip(
                            text = stringResource(R.string.lock_preset_other),
                            selected = selectedChoice == ExtraTimeChoice.Custom,
                            enabled = !uiState.isProcessing,
                            onClick = { selectedChoice = ExtraTimeChoice.Custom },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selectedChoice == ExtraTimeChoice.Custom,
                    enter = fadeIn(tween(if (animationsEnabled) 250 else 0))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ZenDimens.Space8)) {
                        OutlinedTextField(
                            value = uiState.customMinutesInput,
                            onValueChange = onCustomMinutesChanged,
                            label = { Text(stringResource(R.string.lock_custom_minutes_label)) },
                            singleLine = true,
                            enabled = !uiState.isProcessing,
                            shape = ZenShapes.WaterCapsule,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = uiState.customMinutesError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accent,
                                unfocusedBorderColor = palette.border,
                                focusedTextColor = palette.text,
                                unfocusedTextColor = palette.text,
                                focusedLabelColor = palette.accent,
                                unfocusedLabelColor = palette.textSecondary,
                                cursorColor = palette.accent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.customMinutesError != null) {
                            Text(
                                text = uiState.customMinutesError,
                                style = typo.label,
                                color = palette.text
                            )
                        }
                    }
                }

                when (val choice = selectedChoice) {
                    is ExtraTimeChoice.Preset -> {
                        ZenButton(
                            text = stringResource(R.string.lock_confirm_add_minutes, choice.minutes),
                            onClick = { onPresetExtraMinutes(choice.minutes) },
                            style = ZenButtonStyle.PRIMARY,
                            enabled = !uiState.isProcessing
                        )
                    }

                    ExtraTimeChoice.Custom -> {
                        ZenButton(
                            text = stringResource(R.string.lock_confirm_add_custom),
                            onClick = onSubmitCustomMinutes,
                            style = ZenButtonStyle.PRIMARY,
                            enabled = !uiState.isProcessing && uiState.customMinutesInput.isNotEmpty()
                        )
                    }
                }
            }
        }

        ZenButton(
            text = stringResource(R.string.lock_ignore_today),
            onClick = onIgnoreToday,
            style = ZenButtonStyle.SECONDARY,
            enabled = !uiState.isProcessing
        )

        ZenTextButton(
            text = stringResource(R.string.action_exit),
            onClick = onExitToLauncher,
            enabled = !uiState.isProcessing,
            color = palette.textSecondary
        )
    }
}

@Preview(name = "LockScreen PIN — Claro", showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun LockScreenPinLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LockScreenContent(
            uiState = LockScreenUiState(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                stage = LockScreenStage.PIN_ENTRY,
                pinInput = "12"
            ),
            pinErrorTrigger = 0,
            requiredBreathCycles = 0,
            onDigitClick = {},
            onBackspaceClick = {},
            onSubmitPin = {},
            onPresetExtraMinutes = {},
            onCustomMinutesChanged = {},
            onSubmitCustomMinutes = {},
            onIgnoreToday = {},
            onExitToLauncher = {}
        )
    }
}

@Preview(name = "LockScreen PIN — Oscuro", showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun LockScreenPinDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LockScreenContent(
            uiState = LockScreenUiState(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                stage = LockScreenStage.PIN_ENTRY,
                pinInput = "123"
            ),
            pinErrorTrigger = 0,
            requiredBreathCycles = 0,
            onDigitClick = {},
            onBackspaceClick = {},
            onSubmitPin = {},
            onPresetExtraMinutes = {},
            onCustomMinutesChanged = {},
            onSubmitCustomMinutes = {},
            onIgnoreToday = {},
            onExitToLauncher = {}
        )
    }
}

@Preview(name = "LockScreen Opciones — Claro", showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun LockScreenUnlockedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LockScreenContent(
            uiState = LockScreenUiState(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                stage = LockScreenStage.UNLOCKED_OPTIONS
            ),
            pinErrorTrigger = 0,
            requiredBreathCycles = 0,
            onDigitClick = {},
            onBackspaceClick = {},
            onSubmitPin = {},
            onPresetExtraMinutes = {},
            onCustomMinutesChanged = {},
            onSubmitCustomMinutes = {},
            onIgnoreToday = {},
            onExitToLauncher = {}
        )
    }
}

@Preview(name = "LockScreen Opciones — Oscuro", showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun LockScreenUnlockedDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LockScreenContent(
            uiState = LockScreenUiState(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                stage = LockScreenStage.UNLOCKED_OPTIONS
            ),
            pinErrorTrigger = 0,
            requiredBreathCycles = 0,
            onDigitClick = {},
            onBackspaceClick = {},
            onSubmitPin = {},
            onPresetExtraMinutes = {},
            onCustomMinutesChanged = {},
            onSubmitCustomMinutes = {},
            onIgnoreToday = {},
            onExitToLauncher = {}
        )
    }
}
