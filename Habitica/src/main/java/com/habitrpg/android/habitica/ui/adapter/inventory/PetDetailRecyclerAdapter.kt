package com.habitrpg.android.habitica.ui.adapter.inventory

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.facebook.drawee.view.SimpleDraweeView
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.events.commands.FeedCommand
import com.habitrpg.android.habitica.extensions.addCloseButton
import com.habitrpg.android.habitica.extensions.inflate
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.models.inventory.*
import com.habitrpg.android.habitica.models.user.OwnedItem
import com.habitrpg.android.habitica.models.user.OwnedMount
import com.habitrpg.android.habitica.models.user.OwnedPet
import com.habitrpg.android.habitica.ui.helpers.DataBindingUtils
import com.habitrpg.android.habitica.ui.helpers.bindView
import com.habitrpg.android.habitica.ui.menu.BottomSheetMenu
import com.habitrpg.android.habitica.ui.menu.BottomSheetMenuItem
import com.habitrpg.android.habitica.ui.views.dialogs.HabiticaAlertDialog
import com.habitrpg.android.habitica.ui.views.dialogs.PetSuggestHatchDialog
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter
import io.realm.RealmResults
import org.greenrobot.eventbus.EventBus

class PetDetailRecyclerAdapter(data: OrderedRealmCollection<Pet>?, autoUpdate: Boolean) : RealmRecyclerViewAdapter<Pet, PetDetailRecyclerAdapter.PetViewHolder>(data, autoUpdate) {

    var itemType: String? = null
    var context: Context? = null
    private var existingMounts: RealmResults<Mount>? = null
    private var ownedPets: Map<String, OwnedPet>? = null
    private var ownedMounts: Map<String, OwnedMount>? = null
    private var ownedItems: Map<String, OwnedItem>? = null
    private val equipEvents = PublishSubject.create<String>()

    fun getEquipFlowable(): Flowable<String> {
        return equipEvents.toFlowable(BackpressureStrategy.DROP)
    }

    var animalIngredientsRetriever: ((Animal) -> Pair<Egg?, HatchingPotion?>)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PetViewHolder {
        return PetViewHolder(parent.inflate(R.layout.pet_detail_item))
    }

    override fun onBindViewHolder(holder: PetViewHolder, position: Int) {
        data?.let {
            holder.bind(it[position], ownedPets?.get(it[position]?.key ?: ""))
        }
    }

    fun setExistingMounts(existingMounts: RealmResults<Mount>) {
        this.existingMounts = existingMounts
        notifyDataSetChanged()
    }

    fun setOwnedMounts(ownedMounts: Map<String, OwnedMount>) {
        this.ownedMounts = ownedMounts
        notifyDataSetChanged()
    }

    fun setOwnedPets(ownedPets: Map<String, OwnedPet>) {
        this.ownedPets = ownedPets
        notifyDataSetChanged()
    }

    fun setOwnedItems(ownedItems: Map<String, OwnedItem>) {
        this.ownedItems = ownedItems
        notifyDataSetChanged()
    }

    inner class PetViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView), View.OnClickListener {
        var animal: Pet? = null
        var ownedPet: OwnedPet? = null

        private val imageView: SimpleDraweeView by bindView(R.id.imageView)
        private val trainedProgressbar: ProgressBar by bindView(R.id.trainedProgressBar)
        private val availableWrapper: ViewGroup by bindView(R.id.items_available_wrapper)
        private val eggAvailableView: ImageView by bindView(R.id.egg_available_view)
        private val potionAvailableView: ImageView by bindView(R.id.potion_available_view)
        private val itemWrapper: ViewGroup by bindView(R.id.item_wrapper)
        private val eggView: SimpleDraweeView by bindView(R.id.egg_view)
        private val hatchingPotionView: SimpleDraweeView by bindView(R.id.hatchingPotion_view)
        private val checkMarkView: ImageView by bindView(R.id.checkmark_view)

        private val isOwned: Boolean
            get() = this.ownedPet?.trained ?: 0 > 0

        private val canRaiseToMount: Boolean
            get() {
                for (mount in existingMounts ?: emptyList<Mount>()) {
                    if (mount.key == animal?.key) {
                        return !(ownedMounts?.get(mount.key)?.owned ?: false)
                    }
                }
                return false
            }

        private val hasEgg: Boolean
            get() {
                return ownedItems?.get(animal?.animal + "-eggs") != null
            }
        private val hasPotion: Boolean
            get() {
                return ownedItems?.get(animal?.color + "-hatchingPotions") != null
            }

        private val canHatch: Boolean
            get() {
                return hasEgg && hasPotion
            }

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(item: Pet, ownedPet: OwnedPet?) {
            this.animal = item
            this.ownedPet = ownedPet
            this.imageView.alpha = 1.0f
            imageView.visibility = View.VISIBLE
            itemWrapper.visibility = View.GONE
            checkMarkView.visibility = View.GONE
            val imageName = "social_Pet-$itemType-${item.color}"
            itemView.setBackgroundResource(R.drawable.layout_rounded_bg_gray_700)
            if (this.ownedPet?.trained ?: 0 > 0) {
                if (this.canRaiseToMount) {
                    this.trainedProgressbar.visibility = View.VISIBLE
                    this.trainedProgressbar.progress = ownedPet?.trained ?: 0
                } else {
                    this.trainedProgressbar.visibility = View.GONE
                }
                availableWrapper.visibility = View.GONE
            } else {
                this.trainedProgressbar.visibility = View.GONE
                this.imageView.alpha = 0.1f
                if (canHatch) {
                    imageView.visibility = View.GONE
                    availableWrapper.visibility = View.GONE
                    itemWrapper.visibility = View.VISIBLE
                    checkMarkView.visibility = View.VISIBLE
                    itemView.setBackgroundResource(R.drawable.layout_rounded_bg_gray_700_brand_border)
                    DataBindingUtils.loadImage(eggView, "Pet_Egg_${item.animal}")
                    DataBindingUtils.loadImage(hatchingPotionView, "Pet_HatchingPotion_${item.color}")
                } else {
                    availableWrapper.visibility = View.VISIBLE
                    eggAvailableView.alpha = if (hasEgg) 1.0f else 0.2f
                    potionAvailableView.alpha = if (hasPotion) 1.0f else 0.2f
                }
            }

            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                trainedProgressbar.progressBackgroundTintMode = PorterDuff.Mode.SRC_OVER
            }
            imageView.background = null
            val trained = ownedPet?.trained ?: 0
            DataBindingUtils.loadImage(imageName) {
                val resources = context?.resources ?: return@loadImage
                val drawable = BitmapDrawable(resources, if (trained  == 0) it.extractAlpha() else it)
                Observable.just(drawable)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(Consumer {
                            imageView.background = drawable
                        }, RxErrorHandler.handleEmptyError())
            }
        }

        override fun onClick(v: View) {
            if (!this.isOwned) {
                showRequirementsDialog()
                return
            }
            val context = context ?: return
            val menu = BottomSheetMenu(context)
            menu.setTitle(animal?.text)
            menu.addMenuItem(BottomSheetMenuItem(itemView.resources.getString(R.string.equip)))
            if (canRaiseToMount) {
                menu.addMenuItem(BottomSheetMenuItem(itemView.resources.getString(R.string.feed)))
            }
            menu.setSelectionRunnable { index ->
                if (index == 0) {
                    animal?.let {
                        equipEvents.onNext(it.key)
                    }
                } else if (index == 1) {
                    val event = FeedCommand()
                    event.usingPet = animal
                    EventBus.getDefault().post(event)
                }
            }
            menu.show()
        }

        private fun showRequirementsDialog() {
            val context = context ?: return
            val dialog = PetSuggestHatchDialog(context)
            animal?.let {
                val ingredients = animalIngredientsRetriever?.invoke(it)
                dialog.configure(it, ingredients?.first, ingredients?.second, canHatch)
            }
            dialog.show()
        }
    }
}
