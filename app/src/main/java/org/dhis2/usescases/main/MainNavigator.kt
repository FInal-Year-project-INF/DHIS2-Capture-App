package org.dhis2.usescases.main

import android.transition.ChangeBounds
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dhis2.org.analytics.charts.ui.GroupAnalyticsFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.R
import org.dhis2.usescases.about.AboutFragment
import org.dhis2.usescases.main.program.ProgramFragment
import org.dhis2.usescases.qrReader.QrReaderFragment
import org.dhis2.usescases.settings.SyncManagerFragment
import org.dhis2.usescases.troubleshooting.TroubleshootingFragment

class MainNavigator(
    private val dispatcherProvider: dispatch.core.DispatcherProvider,
    private val fragmentManager: FragmentManager,
    private val onTransitionStart: () -> Unit,
    private val onScreenChanged: (
        titleRes: Int,
        showFilterButton: Boolean,
        showBottomNavigation: Boolean,
    ) -> Unit,
) {
    enum class MainScreen(@StringRes val title: Int, @IdRes val navViewId: Int) {
        PROGRAMS(R.string.done_task, R.id.menu_home),
        VISUALIZATIONS(R.string.done_task, R.id.menu_home),
        QR(R.string.QR_SCANNER, R.id.qr_scan),
        SETTINGS(R.string.SYNC_MANAGER, R.id.sync_manager),
        TROUBLESHOOTING(R.string.main_menu_troubleshooting, R.id.menu_troubleshooting),
        ABOUT(R.string.about, R.id.menu_about),
    }

    private var currentScreen = MutableLiveData<MainScreen?>(null)
    var selectedScreen: LiveData<MainScreen?> = currentScreen
    private var currentFragment: Fragment? = null

    fun isHome(): Boolean = isPrograms() || isVisualizations()

    fun isPrograms(): Boolean = currentScreen.value == MainScreen.PROGRAMS

    fun isVisualizations(): Boolean = currentScreen.value == MainScreen.VISUALIZATIONS

    fun getCurrentIfProgram(): ProgramFragment? {
        return currentFragment?.takeIf { it is ProgramFragment } as ProgramFragment
    }

    fun currentScreenName() = currentScreen.value?.name

    fun currentNavigationViewItemId(screenName: String): Int =
        MainScreen.valueOf(screenName).navViewId

    fun openHome() {
        when {
            isVisualizations() -> openVisualizations()
            else -> openPrograms()
        }
    }

    fun openPrograms() {
        val programFragment = ProgramFragment()
        val sharedView = if (isVisualizations()) {
            (currentFragment as GroupAnalyticsFragment).sharedView()
        } else {
            null
        }
        if (sharedView != null) {
            programFragment.sharedElementEnterTransition = ChangeBounds()
            programFragment.sharedElementReturnTransition = ChangeBounds()
        }
        beginTransaction(
            ProgramFragment(),
            MainScreen.PROGRAMS,
            sharedView,
        )
    }

    fun restoreScreen(screenToRestoreName: String, languageSelectorOpened: Boolean = false) {
        when (MainScreen.valueOf(screenToRestoreName)) {
            MainScreen.PROGRAMS -> openPrograms()
            MainScreen.VISUALIZATIONS -> openVisualizations()
            MainScreen.QR -> openQR()
            MainScreen.SETTINGS -> openSettings()
            MainScreen.ABOUT -> openAbout()
            MainScreen.TROUBLESHOOTING -> openTroubleShooting(languageSelectorOpened)
        }
    }

    fun openVisualizations() {
        beginTransaction(GroupAnalyticsFragment.forHome(), MainScreen.VISUALIZATIONS)
    }

    fun openSettings() {
        beginTransaction(
            SyncManagerFragment(),
            MainScreen.SETTINGS,
        )
    }

    fun openQR() {
        beginTransaction(
            QrReaderFragment(),
            MainScreen.QR,
        )
    }

    fun openAbout() {
        beginTransaction(
            AboutFragment(),
            MainScreen.ABOUT,
        )
    }

    fun openTroubleShooting(languageSelectorOpened: Boolean = false) {
        beginTransaction(
            fragment = TroubleshootingFragment.instance(languageSelectorOpened),
            screen = MainScreen.TROUBLESHOOTING,
            useFadeInTransition = languageSelectorOpened,
        )
    }

    private fun beginTransaction(
        fragment: Fragment,
        screen: MainScreen,
        sharedView: View? = null,
        useFadeInTransition: Boolean = false,
    ) {
        if (currentScreen.value != screen) {
            onTransitionStart()
            currentScreen.value = screen
            currentFragment = fragment

            CoroutineScope(dispatcherProvider.main).launch {
                withContext(dispatcherProvider.io) {
                    val transaction: FragmentTransaction = fragmentManager.beginTransaction()
                    transaction.apply {
                        if (sharedView == null) {
                            val (enterAnimation, exitAnimation) = getEnterExitAnimation(useFadeInTransition)
                            val (enterPopAnimation, exitPopAnimation) = getEnterExitPopAnimation(useFadeInTransition)
                            setCustomAnimations(
                                enterAnimation,
                                exitAnimation,
                                enterPopAnimation,
                                exitPopAnimation,
                            )
                        } else {
                            setReorderingAllowed(true)
                            addSharedElement(sharedView, "contenttest")
                        }
                    }
                        .replace(R.id.fragment_container, fragment, fragment::class.simpleName)
                        .commitAllowingStateLoss()
                }
                onScreenChanged(
                    screen.title,
                    isPrograms(),
                    isHome(),
                )
            }
        }
    }

    private fun getEnterExitPopAnimation(useFadeInTransition: Boolean): Pair<Int, Int> {
        return if (useFadeInTransition) {
            Pair(android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            Pair(R.anim.fragment_enter_left, R.anim.fragment_exit_right)
        }
    }

    private fun getEnterExitAnimation(useFadeInTransition: Boolean): Pair<Int, Int> {
        return if (useFadeInTransition) {
            Pair(android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            Pair(R.anim.fragment_enter_right, R.anim.fragment_exit_left)
        }
    }
}
