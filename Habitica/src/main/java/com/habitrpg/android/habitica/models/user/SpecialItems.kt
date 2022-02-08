package com.habitrpg.android.habitica.models.user

import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.models.BaseObject
import com.habitrpg.android.habitica.models.Skill
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.rxjava3.core.Flowable
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass(embedded = true)
open class SpecialItems : RealmObject(), BaseObject {
    var ownedItems: RealmList<OwnedItem>? = null
    var seafoam: Int = 0
    var shinySeed: Int = 0
    var snowball: Int = 0
    var spookySparkles: Int = 0

    fun getTransformationItems(): RealmList<OwnedItem> {
        val ownItems: RealmList<OwnedItem> = RealmList()
        val transformationItems = ArrayList<String>()
        if (snowball > 0) {
            transformationItems.add("snowball")
        }
        if (shinySeed > 0) {
            transformationItems.add("shinySeed")
        }
        if (seafoam > 0) {
            transformationItems.add("seafoam")
        }
        if (spookySparkles > 0) {
            transformationItems.add("spookySparkles")
        }

        if (transformationItems.size == 0) {
            transformationItems.add("")
        }
        RxJavaBridge.toV3Flowable(
            realm.where(Skill::class.java)
                .`in`("key", transformationItems.toTypedArray())
                .findAll()
                .asFlowable()
                .filter { it.isLoaded }
        ).subscribe({ skills ->
            for (skill in skills) run {
                var item = OwnedItem()
                item.key = skill.key
                item.itemType = "special"
                item.numberOwned = getOwnedCount(skill.key)

                ownItems.add(item)
            }
        }, RxErrorHandler.handleEmptyError())
        return ownItems
    }

    val hasSpecialItems: Boolean
        get() {
            return seafoam > 0 || shinySeed > 0 || snowball > 0 || spookySparkles > 0
        }

    private fun getOwnedCount(key: String): Int {
        return when (key) {
            "snowball" -> snowball
            "shinySeed" -> shinySeed
            "seafoam" -> seafoam
            "spookySparkles" -> spookySparkles
            else -> 0
        } ?: 0
    }
}
