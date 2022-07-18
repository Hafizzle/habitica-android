package com.habitrpg.android.habitica.ui.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.perf.FirebasePerformance
import com.habitrpg.android.habitica.BuildConfig
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.components.UserComponent
import com.habitrpg.android.habitica.data.ApiClient
import com.habitrpg.android.habitica.data.InventoryRepository
import com.habitrpg.android.habitica.data.TaskRepository
import com.habitrpg.android.habitica.data.local.UserQuestStatus
import com.habitrpg.android.habitica.databinding.ActivityMainBinding
import com.habitrpg.android.habitica.extensions.dpToPx
import com.habitrpg.android.habitica.extensions.getThemeColor
import com.habitrpg.android.habitica.extensions.hideKeyboard
import com.habitrpg.android.habitica.extensions.isUsingNightModeResources
import com.habitrpg.android.habitica.extensions.observeOnce
import com.habitrpg.android.habitica.extensions.subscribeWithErrorHandler
import com.habitrpg.android.habitica.extensions.updateStatusBarColor
import com.habitrpg.android.habitica.helpers.AmplitudeManager
import com.habitrpg.android.habitica.helpers.AppConfigManager
import com.habitrpg.android.habitica.helpers.MainNavigationController
import com.habitrpg.android.habitica.helpers.NotificationOpenHandler
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.helpers.SoundManager
import com.habitrpg.android.habitica.interactors.CheckClassSelectionUseCase
import com.habitrpg.android.habitica.interactors.DisplayItemDropUseCase
import com.habitrpg.android.habitica.interactors.NotifyUserUseCase
import com.habitrpg.android.habitica.models.TutorialStep
import com.habitrpg.android.habitica.models.responses.MaintenanceResponse
import com.habitrpg.android.habitica.models.responses.TaskScoringResult
import com.habitrpg.android.habitica.models.user.User
import com.habitrpg.android.habitica.ui.AvatarView
import com.habitrpg.android.habitica.ui.AvatarWithBarsViewModel
import com.habitrpg.android.habitica.ui.TutorialView
import com.habitrpg.android.habitica.ui.fragments.NavigationDrawerFragment
import com.habitrpg.android.habitica.ui.viewmodels.MainActivityViewModel
import com.habitrpg.android.habitica.ui.viewmodels.NotificationsViewModel
import com.habitrpg.android.habitica.ui.views.SnackbarActivity
import com.habitrpg.android.habitica.ui.views.dialogs.QuestCompletedDialog
import com.habitrpg.android.habitica.ui.views.yesterdailies.YesterdailyDialog
import com.habitrpg.android.habitica.widget.AvatarStatsWidgetProvider
import com.habitrpg.android.habitica.widget.DailiesWidgetProvider
import com.habitrpg.android.habitica.widget.HabitButtonWidgetProvider
import com.habitrpg.android.habitica.widget.TodoListWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import kotlin.time.DurationUnit
import kotlin.time.toDuration

var mainActivityCreatedAt: Date? = null

open class MainActivity : BaseActivity(), SnackbarActivity {
    private var launchScreen: String? = null

    @Inject
    internal lateinit var apiClient: ApiClient
    @Inject
    internal lateinit var soundManager: SoundManager
    @Inject
    internal lateinit var checkClassSelectionUseCase: CheckClassSelectionUseCase
    @Inject
    internal lateinit var displayItemDropUseCase: DisplayItemDropUseCase
    @Inject
    internal lateinit var notifyUserUseCase: NotifyUserUseCase
    @Inject
    internal lateinit var taskRepository: TaskRepository
    @Inject
    internal lateinit var inventoryRepository: InventoryRepository
    @Inject
    internal lateinit var appConfigManager: AppConfigManager

    lateinit var binding: ActivityMainBinding

    val snackbarContainer: ViewGroup
        get() = binding.snackbarContainer

    private var avatarInHeader: AvatarWithBarsViewModel? = null
    val notificationsViewModel: NotificationsViewModel by viewModels()
    val viewModel: MainActivityViewModel by viewModels()
    private var sideAvatarView: AvatarView? = null
    private var drawerFragment: NavigationDrawerFragment? = null
    var drawerToggle: ActionBarDrawerToggle? = null
    private var resumeFromActivity = false
    private var userQuestStatus = UserQuestStatus.NO_QUEST
    private var lastNotificationOpen: Long? = null

    val isAppBarExpanded: Boolean
        get() = binding.appbar.height - binding.appbar.bottom == 0

    override fun getLayoutResId(): Int {
        return R.layout.activity_main
    }

    override fun getContentView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    private var launchTrace: com.google.firebase.perf.metrics.Trace? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG) {
            mainActivityCreatedAt = Date()
        }
        try {
            launchTrace = FirebasePerformance.getInstance().newTrace("MainActivityLaunch")
        } catch (e: Exception) {
            // pass
        }
        launchTrace?.start()
        super.onCreate(savedInstanceState)

        if (!viewModel.isAuthenticated) {
            val intent = Intent(this, IntroActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        }

        setupToolbar(binding.toolbar)

        avatarInHeader = AvatarWithBarsViewModel(this, binding.avatarWithBars, viewModel.userViewModel)
        sideAvatarView = AvatarView(this, showBackground = true, showMount = false, showPet = false)

        viewModel.user.observe(this) {
            setUserData(it)
        }
        compositeSubscription.add(
            userRepository.getUserQuestStatus().subscribeWithErrorHandler {
                userQuestStatus = it
            }
        )

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawerFragment = supportFragmentManager.findFragmentById(R.id.navigation_drawer) as? NavigationDrawerFragment
        drawerFragment?.setUp(R.id.navigation_drawer, drawerLayout, notificationsViewModel)

        drawerToggle = object : ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        ) {}
        // Set the drawer toggle as the DrawerListener
        drawerToggle?.let { drawerLayout.addDrawerListener(it) }
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            private var isOpeningDrawer: Boolean? = null

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (!isUsingNightModeResources()) {
                    if (slideOffset < 0.5f && isOpeningDrawer == null) {
                        window.updateStatusBarColor(getThemeColor(R.attr.colorPrimaryDark), false)
                        isOpeningDrawer = true
                    } else if (slideOffset > 0.5f && isOpeningDrawer == null) {
                        window.updateStatusBarColor(getThemeColor(R.attr.headerBackgroundColor), true)
                        isOpeningDrawer = false
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                hideKeyboard()
                if (!isUsingNightModeResources()) {
                    window.updateStatusBarColor(getThemeColor(R.attr.colorPrimaryDark), false)
                }
                isOpeningDrawer = null
                drawerFragment?.updatePromo()
            }

            override fun onDrawerClosed(drawerView: View) {
                if (!isUsingNightModeResources()) {
                    window.updateStatusBarColor(getThemeColor(R.attr.headerBackgroundColor), true)
                }
                isOpeningDrawer = null
            }

            override fun onDrawerStateChanged(newState: Int) {
            }
        })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        setupNotifications()
        setupBottomnavigationLayoutListener()

        viewModel.onCreate()
    }

    override fun setTitle(title: CharSequence?) {
        binding.toolbarTitle.text = title
    }

    override fun setTitle(titleId: Int) {
        binding.toolbarTitle.text = getString(titleId)
    }

    private fun updateToolbarTitle(destination: NavDestination, arguments: Bundle?) {
        viewModel.getToolbarTitle(destination.id, destination.label, arguments?.getString("type")) {
            title = it
        }
        drawerFragment?.setSelection(destination.id, null, false)
    }

    override fun onSupportNavigateUp(): Boolean {
        hideKeyboard()
        onBackPressed()
        return true
    }

    private fun setupNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "default"
            val channel = NotificationChannel(
                channelId,
                "Habitica Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setupBottomnavigationLayoutListener() {
        binding.bottomNavigation.viewTreeObserver.addOnGlobalLayoutListener {
            if (binding.bottomNavigation.visibility == View.VISIBLE) {
                snackbarContainer.setPadding(0, 0, 0, binding.bottomNavigation.barHeight + 12.dpToPx(this))
            } else {
                snackbarContainer.setPadding(0, 0, 0, 0)
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle?.syncState()

        launchScreen = viewModel.launchScreen
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (drawerToggle?.onOptionsItemSelected(item) == true) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun injectActivity(component: UserComponent?) {
        component?.inject(this)
    }

    override fun onResume() {
        super.onResume()

        viewModel.onResume()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navigationController = navHostFragment.navController
        MainNavigationController.setup(navigationController)
        navigationController.addOnDestinationChangedListener { _, destination, arguments -> updateToolbarTitle(destination, arguments) }

        if (launchScreen == "/party") {
            viewModel.user.observeOnce(this) {
                if (viewModel.userViewModel.isUserInParty) {
                    MainNavigationController.navigate(R.id.partyFragment)
                }
            }
        }
        launchScreen = null

        if (!resumeFromActivity) {
            retrieveUser()
            this.checkMaintenance()
        }
        resumeFromActivity = false

        if ((intent.hasExtra("notificationIdentifier") || intent.hasExtra("openURL")) && lastNotificationOpen != intent.getLongExtra("notificationTimeStamp", 0)) {
            lastNotificationOpen = intent.getLongExtra("notificationTimeStamp", 0)
            val identifier = intent.getStringExtra("notificationIdentifier") ?: ""
            if (intent.hasExtra("sendAnalytics")) {
                val additionalData = HashMap<String, Any>()
                additionalData["identifier"] = identifier
                AmplitudeManager.sendEvent(
                    "open notification",
                    AmplitudeManager.EVENT_CATEGORY_BEHAVIOUR,
                    AmplitudeManager.EVENT_HITTYPE_EVENT,
                    additionalData
                )
            }
            retrieveUser(true)
            NotificationOpenHandler.handleOpenedByNotification(identifier, intent)
        }

        launchTrace?.stop()
        launchTrace = null

        if (binding.toolbarTitle.text?.isNotBlank() != true) {
            navigationController.currentDestination?.let { updateToolbarTitle(it, null) }
        }
    }

    override fun onPause() {
        updateWidgets()
        super.onPause()
    }

    override fun startActivity(intent: Intent?) {
        resumeFromActivity = true
        super.startActivity(intent)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        resumeFromActivity = true
        super.startActivity(intent, options)
    }

    private fun updateWidgets() {
        updateWidget(AvatarStatsWidgetProvider::class.java)
        updateWidget(TodoListWidgetProvider::class.java)
        updateWidget(DailiesWidgetProvider::class.java)
        updateWidget(HabitButtonWidgetProvider::class.java)
    }

    private fun updateWidget(widgetClass: Class<*>) {
        val intent = Intent(this, widgetClass)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, widgetClass))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    fun navigate(transitionId: Int) {
        findNavController(R.id.nav_host_fragment).navigate(transitionId)
    }

    private fun setUserData(user: User?) {
        if (user != null) {
            val preferences = user.preferences

            preferences?.language?.let { apiClient.setLanguageCode(it) }
            if (preferences?.language != viewModel.preferenceLanguage) {
                viewModel.preferenceLanguage = preferences?.language
            }
            preferences?.sound?.let { soundManager.soundTheme = it }

            displayDeathDialogIfNeeded()
            YesterdailyDialog.showDialogIfNeeded(this, user.id, userRepository, taskRepository)

            if (user.flags?.verifiedUsername == false && isActivityVisible) {
                val intent = Intent(this, VerifyUsernameActivity::class.java)
                startActivity(intent)
            }

            val quest = user.party?.quest
            if (quest?.completed?.isNotBlank() == true) {
                compositeSubscription.add(
                    inventoryRepository.getQuestContent(user.party?.quest?.completed ?: "").firstElement().subscribe(
                        {
                            QuestCompletedDialog.showWithQuest(this, it)

                            viewModel.updateUser("party.quest.completed", "")
                        },
                        RxErrorHandler.handleEmptyError()
                    )
                )
            }

            if (user.flags?.welcomed == false) {
                viewModel.updateUser("flags.welcomed", true)
            }

            val title = binding.toolbarTitle.text
            if (title.isBlank()) {
                viewModel.getToolbarTitle(0, null, null) { newTitle ->
                    this.title = newTitle
                }
            }
        }
    }

    override fun onBackPressed() {
        if (drawerFragment?.isDrawerOpen == true) {
            drawerFragment?.closeDrawer()
        } else {
            try {
                super.onBackPressed()
            } catch (ignored: Exception) {
            }
        }
    }

    // region Events

    public override fun onDestroy() {
        userRepository.close()
        inventoryRepository.close()
        super.onDestroy()
    }
    // endregion

    internal fun displayTaskScoringResponse(data: TaskScoringResult?) {
        if (viewModel.user.value != null && data != null) {
            val damageValue = when (userQuestStatus) {
                UserQuestStatus.QUEST_BOSS -> data.questDamage
                else -> 0.0
            }
            compositeSubscription.add(
                notifyUserUseCase.observable(
                    NotifyUserUseCase.RequestValues(
                        this, snackbarContainer,
                        viewModel.user.value, data.experienceDelta, data.healthDelta, data.goldDelta, data.manaDelta, damageValue, data.hasLeveledUp, data.level
                    )
                )
                    .subscribe({ }, RxErrorHandler.handleEmptyError())
            )
        }

        val showItemsFound = userQuestStatus == UserQuestStatus.QUEST_COLLECT
        compositeSubscription.add(
            displayItemDropUseCase.observable(DisplayItemDropUseCase.RequestValues(data, this, snackbarContainer, showItemsFound))
                .subscribe({ }, RxErrorHandler.handleEmptyError())
        )
    }

    private var lastDeathDialogDisplay = 0L

    private fun displayDeathDialogIfNeeded() {
        if (!viewModel.userViewModel.isUserFainted) {
            return
        }

        val now = Date().time
        if (!this.isFinishing && now - lastDeathDialogDisplay > 60000) {
            lastDeathDialogDisplay = now
            MainNavigationController.navigate(R.id.deathActivity)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            drawerFragment?.openDrawer()
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    protected fun retrieveUser(forced: Boolean = false) {
        viewModel.retrieveUser(forced)
    }

    fun displayTutorialStep(step: TutorialStep, texts: List<String>, canBeDeferred: Boolean) {
        val view = TutorialView(this, step, viewModel)
        view.setTutorialTexts(texts)
        view.setCanBeDeferred(canBeDeferred)
        binding.overlayFrameLayout.children.forEach {
            if (it is TutorialView) {
                binding.overlayFrameLayout.removeView(it)
            }
        }
        binding.overlayFrameLayout.addView(view)
        viewModel.logTutorialStatus(step, false)
    }

    private fun checkMaintenance() {
        viewModel.ifNeedsMaintenance { maintenanceResponse ->
            if (maintenanceResponse.activeMaintenance) {
                val intent = createMaintenanceIntent(maintenanceResponse, false)
                startActivity(intent)
            } else {
                if (maintenanceResponse.minBuild != null) {
                    try {
                        val packageInfo = packageManager.getPackageInfo(packageName, 0)
                        @Suppress("DEPRECATION")
                        if (packageInfo.versionCode < maintenanceResponse.minBuild) {
                            val intent = createMaintenanceIntent(maintenanceResponse, true)
                            startActivity(intent)
                        }
                    } catch (e: PackageManager.NameNotFoundException) {
                        RxErrorHandler.reportError(e)
                    }
                }
            }
        }
    }

    private fun createMaintenanceIntent(
        maintenanceResponse: MaintenanceResponse,
        isDeprecationNotice: Boolean
    ): Intent {
        val intent = Intent(this, MaintenanceActivity::class.java)
        val data = Bundle()
        data.putString("title", maintenanceResponse.title)
        data.putString("imageUrl", maintenanceResponse.imageUrl)
        data.putString("description", maintenanceResponse.description)
        data.putBoolean("deprecationNotice", isDeprecationNotice)
        intent.putExtras(data)
        return intent
    }

    override fun snackbarContainer(): ViewGroup {
        return snackbarContainer
    }

    private var errorJob: Job? = null

    override fun showConnectionProblem(title: String?, message: String) {
        if (title != null) {
            super.showConnectionProblem(title, message)
        } else {
            if (errorJob?.isCancelled == false) {
                // a new error resets the timer to hide the error message
                errorJob?.cancel()
            }
            binding.connectionIssueView.visibility = View.VISIBLE
            binding.connectionIssueTextview.text = message
            errorJob = lifecycleScope.launch(Dispatchers.Main) {
                delay(1.toDuration(DurationUnit.MINUTES))
                binding.connectionIssueView.visibility = View.GONE
            }
        }
    }

    override fun hideConnectionProblem() {
        if (errorJob?.isCancelled == false) {
            errorJob?.cancel()
        }
        lifecycleScope.launch(Dispatchers.Main) {
            if (binding.connectionIssueView.visibility == View.VISIBLE) {
                binding.connectionIssueView.visibility = View.GONE
            }
        }
    }
}
