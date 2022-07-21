package com.habitrpg.android.habitica.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.components.UserComponent
import com.habitrpg.android.habitica.data.InventoryRepository
import com.habitrpg.android.habitica.databinding.ActivityArmoireBinding
import com.habitrpg.android.habitica.extensions.dpToPx
import com.habitrpg.android.habitica.extensions.observeOnce
import com.habitrpg.android.habitica.helpers.AdHandler
import com.habitrpg.android.habitica.helpers.AdType
import com.habitrpg.android.habitica.helpers.Animations
import com.habitrpg.android.habitica.helpers.AppConfigManager
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.ui.helpers.loadImage
import com.habitrpg.android.habitica.ui.viewmodels.MainUserViewModel
import com.habitrpg.android.habitica.ui.views.ads.AdButton
import com.habitrpg.android.habitica.ui.views.dialogs.HabiticaBottomSheetDialog
import com.plattysoft.leonids.ParticleSystem
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

class ArmoireActivity : BaseActivity() {

    private var equipmentKey: String? = null
    private var gold: Double? = null
    private var hasAnimatedChanges: Boolean = false
    private lateinit var binding: ActivityArmoireBinding

    @Inject
    internal lateinit var inventoryRepository: InventoryRepository
    @Inject
    internal lateinit var appConfigManager: AppConfigManager
    @Inject
    lateinit var userViewModel: MainUserViewModel

    override fun getLayoutResId(): Int = R.layout.activity_armoire

    override fun injectActivity(component: UserComponent?) {
        component?.inject(this)
    }

    override fun getContentView(): View {
        binding = ActivityArmoireBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.goldView.currency = "gold"
        binding.goldView.animationDuration = 1000
        binding.goldView.animationDelay = 500
        binding.goldView.minForAbbrevation = 1000000
        binding.goldView.decimals = 0

        userViewModel.user.observeOnce(this) { user ->
            gold = user?.stats?.gp
            val remaining = inventoryRepository.getArmoireRemainingCount()
            binding.equipmentCountView.text = getString(R.string.equipment_remaining, remaining)
            binding.noEquipmentView.visibility = if (remaining > 0) View.GONE else View.VISIBLE
        }

        if (appConfigManager.enableArmoireAds()) {
            val handler = AdHandler(this, AdType.ARMOIRE) {
                if (!it) {
                    return@AdHandler
                }
                Log.d("AdHandler", "Giving Armoire")
                val user = userViewModel.user.value ?: return@AdHandler
                val currentGold = user.stats?.gp ?: return@AdHandler
                compositeSubscription.add(
                    userRepository.updateUser("stats.gp", currentGold + 100)
                        .flatMap { inventoryRepository.buyItem(user, "armoire", 100.0, 1) }
                        .subscribe({
                            configure(
                                it.armoire["type"] ?: "",
                                it.armoire["dropKey"] ?: "",
                                it.armoire["dropText"] ?: "",
                                it.armoire["value"] ?: ""
                            )
                            binding.adButton.state = AdButton.State.UNAVAILABLE
                            binding.adButton.visibility = View.INVISIBLE
                            hasAnimatedChanges = false
                            gold = null
                        }, RxErrorHandler.handleEmptyError())
                )
            }
            handler.prepare {
                if (it && binding.adButton.state == AdButton.State.LOADING) {
                    binding.adButton.state = AdButton.State.READY
                } else if (!it) {
                    binding.adButton.visibility = View.INVISIBLE
                }
            }
            binding.adButton.updateForAdType(AdType.ARMOIRE, lifecycleScope)
            binding.adButton.setOnClickListener {
                binding.adButton.state = AdButton.State.LOADING
                handler.show()
            }
        } else {
            binding.adButton.visibility = View.GONE
        }

        binding.closeButton.setOnClickListener {
            finish()
        }
        binding.equipButton.setOnClickListener {
            equipmentKey?.let { it1 -> inventoryRepository.equip("equipped", it1).subscribe({}, RxErrorHandler.handleEmptyError()) }
            finish()
        }
        binding.dropRateButton.setOnClickListener {
            showDropRateDialog()
        }
        intent.extras?.let {
            val args = ArmoireActivityArgs.fromBundle(it)
            equipmentKey = args.key
            configure(args.type, args.key, args.text, args.value)
        }
    }

    override fun onResume() {
        super.onResume()
        startAnimation()
    }

    private fun startAnimation() {
        val gold = gold?.toInt()
        if (hasAnimatedChanges) return
        if (gold != null) {
            binding.goldView.value = (gold).toDouble()
            binding.goldView.value = (gold - 100).toDouble()
        }

        val container = binding.confettiAnchor
        container.postDelayed(
            {
                createParticles(container, R.drawable.confetti_blue)
                createParticles(container, R.drawable.confetti_red)
                createParticles(container, R.drawable.confetti_yellow)
                createParticles(container, R.drawable.confetti_purple)
            }, 500
        )

        binding.iconView.startAnimation(Animations.bobbingAnimation())
        binding.titleView.alpha = 0f
        binding.subtitleView.alpha = 0f

        lifecycleScope.launch {
            if (binding.iconWrapper.isAttachedToWindow) {
                Animations.circularReveal(binding.iconWrapper, 300)
            }
        }

        binding.leftSparkView.startAnimating()
        binding.rightSparkView.startAnimating()

        binding.titleView.animate().apply {
            alpha(1f)
            duration = 300
            startDelay = 600
            start()
        }
        binding.subtitleView.animate().apply {
            alpha(1f)
            duration = 300
            startDelay = 900
            start()
        }

        hasAnimatedChanges = true
    }

    private fun createParticles(container: FrameLayout, resource: Int) {
        ParticleSystem(
            container,
            30,
            ContextCompat.getDrawable(this, resource),
            6000
        )
            .setRotationSpeed(144f)
            .setScaleRange(1.0f, 1.6f)
            .setSpeedByComponentsRange(-0.15f, 0.15f, 0.15f, 0.45f)
            .setFadeOut(200, AccelerateInterpolator())
            .emitWithGravity(binding.confettiAnchor, Gravity.TOP, 15, 2000)
    }

    fun configure(type: String, key: String, text: String, value: String? = "") {
        binding.titleView.text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.equipButton.visibility = if (type == "gear") View.VISIBLE else View.GONE
        when (type) {
            "gear" -> {
                binding.subtitleView.text = getString(R.string.armoireEquipment_new)
                binding.iconView.loadImage("shop_$key")
            }
            "food" -> {
                binding.subtitleView.text = getString(R.string.armoireFood_new)
                binding.iconView.loadImage("Pet_Food_$key")
            }
            else -> {
                @SuppressLint("SetTextI18n")
                binding.titleView.text = "+${value} ${binding.titleView.text}"
                binding.subtitleView.text = getString(R.string.armoireExp)
                binding.iconView.setImageResource(R.drawable.armoire_experience)
                val layoutParams = RelativeLayout.LayoutParams(108.dpToPx(this), 122.dpToPx(this))
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
                binding.iconView.layoutParams = layoutParams
            }
        }
    }

    private fun showDropRateDialog() {
        val dialog = HabiticaBottomSheetDialog(this)
        dialog.setContentView(R.layout.armoire_drop_rate_dialog)
        dialog.show()
    }
}
