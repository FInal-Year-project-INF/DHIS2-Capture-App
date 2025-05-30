package org.dhis2.usescases.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.dhis2.commons.filters.FilterManager
import org.dhis2.commons.filters.FilterManager.PeriodRequest
import org.dhis2.commons.filters.Filters
import org.dhis2.commons.filters.data.FilterRepository
import org.dhis2.commons.matomo.Categories.Companion.HOME
import org.dhis2.commons.matomo.MatomoAnalyticsController
import org.dhis2.commons.prefs.Preference.Companion.DEFAULT_CAT_COMBO
import org.dhis2.commons.prefs.Preference.Companion.PIN
import org.dhis2.commons.prefs.Preference.Companion.PREF_DEFAULT_CAT_OPTION_COMBO
import org.dhis2.commons.prefs.Preference.Companion.SESSION_LOCKED
import org.dhis2.commons.prefs.PreferenceProvider
import org.dhis2.commons.schedulers.SchedulerProvider
import org.dhis2.commons.viewmodel.DispatcherProvider
import org.dhis2.data.schedulers.TrampolineSchedulerProvider
import org.dhis2.data.server.UserManager
import org.dhis2.data.service.SyncStatusController
import org.dhis2.data.service.VersionRepository
import org.dhis2.data.service.workManager.WorkManagerController
import org.dhis2.usescases.login.SyncIsPerformedInteractor
import org.dhis2.usescases.settings.DeleteUserData
import org.hisp.dhis.android.core.category.CategoryCombo
import org.hisp.dhis.android.core.category.CategoryOptionCombo
import org.hisp.dhis.android.core.configuration.internal.DatabaseAccount
import org.hisp.dhis.android.core.systeminfo.SystemInfo
import org.hisp.dhis.android.core.user.User
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MainPresenterTest {

    private lateinit var presenter: MainPresenter
    private val repository: HomeRepository = mock()
    private val schedulers: SchedulerProvider = TrampolineSchedulerProvider()
    private val view: MainView = mock()
    private val preferences: PreferenceProvider = mock()
    private val workManagerController: WorkManagerController = mock()
    private val filterManager: FilterManager = mock()
    private val filterRepository: FilterRepository = mock()

    private val matomoAnalyticsController: MatomoAnalyticsController = mock()
    private val userManager: UserManager = mock()
    private val deleteUserData: DeleteUserData = mock()
    private val syncIsPerfomedInteractor: SyncIsPerformedInteractor = mock()
    private val syncStatusController: SyncStatusController = mock()
    private val versionRepository: VersionRepository = mock()
    private val testingDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = mock {
        on { io() } doReturn testingDispatcher
        on { ui() } doReturn testingDispatcher
    }

    private val forceToNotSynced: Boolean = false

    @Rule
    @JvmField
    val rule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(testingDispatcher)
        whenever(versionRepository.newAppVersion) doReturn MutableSharedFlow()
        presenter =
            MainPresenter(
                view,
                repository,
                schedulers,
                preferences,
                workManagerController,
                filterManager,
                filterRepository,
                matomoAnalyticsController,
                userManager,
                deleteUserData,
                syncIsPerfomedInteractor,
                syncStatusController,
                versionRepository,
                dispatcherProvider,
                forceToNotSynced,
            )
    }

    @Test
    fun `Should save default settings and render user name when the activity is resumed`() {
        presenterMocks()

        presenter.init()

        verify(view).renderUsername(any())
        verify(preferences).setValue(DEFAULT_CAT_COMBO, "uid")
        verify(preferences).setValue(PREF_DEFAULT_CAT_OPTION_COMBO, "uid")
    }

    @Test
    fun `Should log out`() {
        whenever(repository.logOut()) doReturn Completable.complete()

        whenever(repository.accountsCount()) doReturn 1
        whenever(userManager.d2) doReturn mock()
        whenever(userManager.d2.dataStoreModule()) doReturn mock()
        whenever(userManager.d2.dataStoreModule().localDataStore()) doReturn mock()
        whenever(userManager.d2.dataStoreModule().localDataStore().value(PIN)) doReturn mock()

        presenter.logOut()

        verify(workManagerController).cancelAllWork()
        verify(preferences).setValue(SESSION_LOCKED, false)
        verify(userManager.d2.dataStoreModule().localDataStore().value(PIN)).blockingDeleteIfExist()
        verify(filterManager).clearAllFilters()
        verify(view).goToLogin(1, false)
    }

    @Test
    fun `Should block session`() {
        presenter.blockSession()

        verify(workManagerController).cancelAllWork()
        verify(view).back()
    }

    @Test
    fun `Should clear disposable when activity is paused`() {
        presenter.onDetach()

        val disposableSize = presenter.disposable.size()

        assertTrue(disposableSize == 0)
    }

    @Test
    fun `Should open drawer when menu is clicked`() {
        presenter.onMenuClick()

        verify(view).openDrawer(any())
    }

    @Test
    fun `should return to home section when user taps back in a different section`() {
        val filterProcessor: FlowableProcessor<FilterManager> = PublishProcessor.create()
        val filterManagerFlowable = Flowable.just(filterManager).startWith(filterProcessor)
        val periodRequest: FlowableProcessor<kotlin.Pair<PeriodRequest, Filters?>> =
            BehaviorProcessor.create()
        whenever(filterManager.asFlowable()) doReturn filterManagerFlowable
        whenever(filterManager.periodRequest) doReturn periodRequest
        whenever(filterManager.ouTreeFlowable()) doReturn Flowable.just(true)

        presenter.onNavigateBackToHome()

        verify(view).goToHome()
    }

    @Test
    fun `Should track event when clicking on SyncManager`() {
        presenter.onClickSyncManager()

        verify(matomoAnalyticsController).trackEvent(any(), any(), any())
    }

    @Test
    fun `Should go to delete account`() {
        val randomFile = File("random")
        whenever(view.obtainFileView()) doReturn randomFile
        whenever(userManager.d2) doReturn mock()
        whenever(userManager.d2.userModule()) doReturn mock()
        whenever(userManager.d2.userModule().accountManager()) doReturn mock()
        whenever(view.obtainFileView()) doReturn randomFile
        whenever(repository.accountsCount()) doReturn 1

        presenter.onDeleteAccount()

        verify(view).showProgressDeleteNotification()
        verify(deleteUserData).wipeCacheAndPreferences(randomFile)
        verify(userManager.d2?.userModule()?.accountManager())?.deleteCurrentAccount()
        verify(view).cancelNotifications()
        verify(view).goToLogin(1, true)
    }

    @Test
    fun `Should go to manage account`() {
        val firstRandomUserAccount =
            DatabaseAccount.builder()
                .username("random")
                .serverUrl("https://www.random.com/")
                .encrypted(false)
                .databaseName("none")
                .databaseCreationDate("16/2/2012")
                .build()
        val secondRandomUserAccount =
            DatabaseAccount.builder()
                .username("random")
                .serverUrl("https://www.random.com/")
                .encrypted(false)
                .databaseName("none")
                .databaseCreationDate("16/2/2012")
                .build()

        val randomFile = File("random")

        whenever(view.obtainFileView()) doReturn randomFile
        whenever(userManager.d2) doReturn mock()
        whenever(userManager.d2.userModule()) doReturn mock()
        whenever(userManager.d2.userModule().accountManager()) doReturn mock()
        whenever(userManager.d2.userModule().accountManager().getAccounts()) doReturn listOf(
            firstRandomUserAccount,
            secondRandomUserAccount,
        )
        whenever(repository.accountsCount()) doReturn 2

        presenter.onDeleteAccount()

        verify(deleteUserData).wipeCacheAndPreferences(randomFile)
        verify(userManager.d2?.userModule()?.accountManager())?.deleteCurrentAccount()
        verify(view).showProgressDeleteNotification()
        verify(view).cancelNotifications()
        verify(view).goToLogin(2, true)
    }

    @Test
    fun `Should track server first time`() {
        val serverVersion = "2.38"
        whenever(repository.getServerVersion()) doReturn Single.just(systemInfo())
        whenever(preferences.getString(DHIS2, "")) doReturn ""

        presenter.trackDhis2Server()

        verify(matomoAnalyticsController).trackEvent(HOME, SERVER_ACTION, serverVersion)
        verify(preferences).setValue(DHIS2, serverVersion)
    }

    @Test
    fun `Should track server when there is an update`() {
        val oldVersion = "2.37"
        val newVersion = "2.38"
        whenever(repository.getServerVersion()) doReturn Single.just(systemInfo())
        whenever(preferences.getString(DHIS2, "")) doReturn oldVersion

        presenter.trackDhis2Server()

        verify(matomoAnalyticsController).trackEvent(HOME, SERVER_ACTION, newVersion)
        verify(preferences).setValue(DHIS2, newVersion)
    }

    @Test
    fun `Should not track server`() {
        whenever(repository.getServerVersion()) doReturn Single.just(systemInfo(""))
        whenever(preferences.getString(DHIS2, "")) doReturn ""

        presenter.trackDhis2Server()

        verifyNoMoreInteractions(matomoAnalyticsController)
    }

    private fun presenterMocks() {
        // UserModule
        whenever(repository.user()) doReturn Single.just(createUser())

        // categoryModule
        whenever(repository.defaultCatCombo()) doReturn Single.just(createCategoryCombo())
        whenever(repository.defaultCatOptCombo()) doReturn Single.just(createCategoryOptionCombo())

        val oldVersion = "2.37"
        whenever(repository.getServerVersion()) doReturn Single.just(systemInfo())
        whenever(preferences.getString(DHIS2, "")) doReturn oldVersion
    }

    private fun systemInfo(server: String = "2.38") = SystemInfo.builder()
        .systemName("random")
        .contextPath("random too")
        .dateFormat("dd/mm/yyyy")
        .serverDate(Date())
        .version(server)
        .build()

    private fun createUser(): User {
        return User.builder()
            .uid("userUid")
            .firstName("test_name")
            .surname("test_surName")
            .build()
    }

    private fun createCategoryCombo(): CategoryCombo {
        return CategoryCombo.builder()
            .uid("uid")
            .build()
    }

    private fun createCategoryOptionCombo(): CategoryOptionCombo {
        return CategoryOptionCombo.builder()
            .uid("uid")
            .build()
    }
}
