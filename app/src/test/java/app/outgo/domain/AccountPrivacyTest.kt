package app.outgo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPrivacyTest {
    private val always = AccountPrivacy(progressHide = SavingProgressHide.ALWAYS)
    private val reached = AccountPrivacy(progressHide = SavingProgressHide.WHEN_REACHED)

    @Test
    fun whenReachedHidesOnlyOnceTheTargetIsMet() {
        assertFalse(reached.progressHidden(saved = 999, target = 1_000))
        assertTrue(reached.progressHidden(saved = 1_000, target = 1_000))
        assertFalse("no target is never reached", reached.progressHidden(saved = 5_000, target = null))
    }

    @Test
    fun neverAndAlways() {
        assertFalse(AccountPrivacy().progressHidden(saved = 1_000, target = 1_000))
        assertTrue(always.progressHidden(saved = 0, target = null))
    }

    @Test
    fun analysisExceptionKeepsProgressOnlyInAnalysis() {
        val kept = always.copy(progressInAnalysis = true)
        assertTrue(kept.progressHidden(0, 100))
        assertFalse(kept.progressHiddenInAnalysis(0, 100))
        assertTrue(always.progressHiddenInAnalysis(0, 100))
    }

    @Test
    fun parentSettingsCoverSubaccounts() {
        val parent = AccountPrivacy(hideBalance = true, progressHide = SavingProgressHide.ALWAYS, progressInAnalysis = true)
        val sub = AccountPrivacy.resolve(AccountPrivacy(progressHide = SavingProgressHide.WHEN_REACHED), parent, savings = true)
        assertEquals(parent, sub)
    }

    @Test
    fun subaccountKeepsOwnSettingsUnderAnOpenParent() {
        val own = AccountPrivacy(hideBalance = true, progressHide = SavingProgressHide.WHEN_REACHED)
        assertEquals(own, AccountPrivacy.resolve(own, AccountPrivacy(), savings = true))
    }

    @Test
    fun progressSettingsMeanNothingOnANormalAccount() {
        val resolved = AccountPrivacy.resolve(always.copy(hideBalance = true, progressInAnalysis = true), null, savings = false)
        assertEquals(AccountPrivacy(hideBalance = true), resolved)
    }

    @Test
    fun analysisExceptionIgnoredWhileNothingIsHidden() {
        val resolved = AccountPrivacy.resolve(AccountPrivacy(progressInAnalysis = true), null, savings = true)
        assertFalse(resolved.progressInAnalysis)
    }
}
