package app.outgo.domain

/**
 * What an account hides, as it applies (see [resolve]): its balance, and for a savings account its
 * monthly savings progress ([progressHide], a [SavingProgressHide] value). [progressInAnalysis] keeps
 * that progress in Analysis while it is hidden everywhere else; the balance stays hidden there.
 */
data class AccountPrivacy(
    val hideBalance: Boolean = false,
    val progressHide: Int = SavingProgressHide.NEVER,
    val progressInAnalysis: Boolean = false,
) {
    /** Hidden on Home and the Accounts screen, for [saved] of this month's [target] (null: no target). */
    fun progressHidden(saved: Long, target: Long?): Boolean = when (progressHide) {
        SavingProgressHide.ALWAYS -> true
        SavingProgressHide.WHEN_REACHED -> target != null && saved >= target
        else -> false
    }

    /** Hidden in Analysis: like [progressHidden], unless kept there. */
    fun progressHiddenInAnalysis(saved: Long, target: Long?): Boolean = !progressInAnalysis && progressHidden(saved, target)

    companion object {
        /**
         * What applies to an account whose own settings are [own], under [parent] (null for a parent
         * account). A parent's settings cover its subaccounts: a hidden balance hides theirs too, and a
         * progress setting other than [SavingProgressHide.NEVER] replaces theirs. Progress settings mean
         * nothing on a normal account ([savings] false).
         */
        fun resolve(own: AccountPrivacy, parent: AccountPrivacy?, savings: Boolean): AccountPrivacy {
            val progress = if (parent != null && parent.progressHide != SavingProgressHide.NEVER) parent else own
            return AccountPrivacy(
                hideBalance = own.hideBalance || parent?.hideBalance == true,
                progressHide = if (savings) progress.progressHide else SavingProgressHide.NEVER,
                progressInAnalysis = savings && progress.progressHide != SavingProgressHide.NEVER && progress.progressInAnalysis,
            )
        }
    }
}
