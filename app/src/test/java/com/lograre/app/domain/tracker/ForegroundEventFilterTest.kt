package com.lograre.app.domain.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundEventFilterTest {

    private val imeSet = setOf("com.google.android.inputmethod.latin")

    @Test
    fun `instagram opens with null active window root and still tracks instagram`() {
        val decision = ForegroundEventFilter.decide(
            isWindowStateChanged = true,
            isWindowsChanged = false,
            eventPackageName = "com.instagram.android",
            eventClassName = "com.instagram.mainactivity.InstagramMainActivity",
            isFullScreen = true,
            activeApplicationWindowPackage = null,
            inputMethodPackages = imeSet,
            isScreenInteractiveAndUnlocked = true
        )

        assertEquals(ForegroundEventDecision.SwitchTo("com.instagram.android"), decision)
    }

    @Test
    fun `non-fullscreen gms or autofill popup over instagram does not break instagram session`() {
        val decisionWithActiveRoot = ForegroundEventFilter.decide(
            isWindowStateChanged = true,
            isWindowsChanged = false,
            eventPackageName = "com.google.android.gms",
            eventClassName = "android.widget.PopupWindow",
            isFullScreen = false,
            activeApplicationWindowPackage = "com.instagram.android",
            inputMethodPackages = imeSet,
            isScreenInteractiveAndUnlocked = true
        )
        assertEquals(ForegroundEventDecision.SwitchTo("com.instagram.android"), decisionWithActiveRoot)

        val decisionWithoutActiveRoot = ForegroundEventFilter.decide(
            isWindowStateChanged = true,
            isWindowsChanged = false,
            eventPackageName = "com.google.android.gms",
            eventClassName = "android.widget.PopupWindow",
            isFullScreen = false,
            activeApplicationWindowPackage = null,
            inputMethodPackages = imeSet,
            isScreenInteractiveAndUnlocked = true
        )
        assertEquals(ForegroundEventDecision.Ignore, decisionWithoutActiveRoot)
    }

    @Test
    fun `soft keyboard and systemui overlays are ignored while screen is unlocked`() {
        val imeDecision = ForegroundEventFilter.decide(
            isWindowStateChanged = true,
            isWindowsChanged = false,
            eventPackageName = "com.google.android.inputmethod.latin",
            eventClassName = "android.inputmethodservice.SoftInputWindow",
            isFullScreen = false,
            activeApplicationWindowPackage = "com.instagram.android",
            inputMethodPackages = imeSet,
            isScreenInteractiveAndUnlocked = true
        )
        assertEquals(ForegroundEventDecision.Ignore, imeDecision)

        val systemUiDecision = ForegroundEventFilter.decide(
            isWindowStateChanged = true,
            isWindowsChanged = false,
            eventPackageName = "com.android.systemui",
            eventClassName = "com.android.systemui.volume.VolumeDialogImpl",
            isFullScreen = false,
            activeApplicationWindowPackage = "com.instagram.android",
            inputMethodPackages = imeSet,
            isScreenInteractiveAndUnlocked = true
        )
        assertEquals(ForegroundEventDecision.Ignore, systemUiDecision)
    }

    @Test
    fun `TYPE_WINDOWS_CHANGED detects returning to app from recents gesture`() {
        val decision = ForegroundEventFilter.decide(
            isWindowStateChanged = false,
            isWindowsChanged = true,
            eventPackageName = null,
            eventClassName = null,
            isFullScreen = true,
            activeApplicationWindowPackage = "com.instagram.android",
            inputMethodPackages = imeSet,
            isScreenInteractiveAndUnlocked = true
        )

        assertEquals(ForegroundEventDecision.SwitchTo("com.instagram.android"), decision)
    }

    @Test
    fun `locked or off screen always pauses tracking`() {
        val decision = ForegroundEventFilter.decide(
            isWindowStateChanged = true,
            isWindowsChanged = false,
            eventPackageName = "com.instagram.android",
            eventClassName = "com.instagram.mainactivity.InstagramMainActivity",
            isFullScreen = true,
            activeApplicationWindowPackage = "com.instagram.android",
            inputMethodPackages = imeSet,
            isScreenInteractiveAndUnlocked = false
        )

        assertEquals(ForegroundEventDecision.PauseScreenOff, decision)
    }
}
