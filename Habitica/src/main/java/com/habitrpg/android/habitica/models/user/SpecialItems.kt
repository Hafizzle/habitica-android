package com.habitrpg.android.habitica.models.user

import com.habitrpg.android.habitica.models.BaseObject
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

    val hasSpecialItems: Boolean
        get() {
            return seafoam > 0 || shinySeed > 0 || snowball > 0 || spookySparkles > 0
        }

    fun includeTransformationItems(): MutableList<OwnedItem> {
        val transformationItems: MutableList<OwnedItem> = mutableListOf()
        ownedItems?.toMutableList()?.let { transformationItems.addAll(it) }


        if (snowball > 0) {
            val transformationItem = OwnedItem()
            transformationItem.itemType = "special"
            transformationItem.key = "snowball"
            transformationItem.numberOwned = snowball
            transformationItems.add(transformationItem)
        }
        if (shinySeed > 0) {
            val transformationItem = OwnedItem()
            transformationItem.itemType = "special"
            transformationItem.key = "shinySeed"
            transformationItem.numberOwned = shinySeed
            transformationItems.add(transformationItem)
        }
        if (seafoam > 0) {
            val transformationItem = OwnedItem()
            transformationItem.itemType = "special"
            transformationItem.key = "seafoam"
            transformationItem.numberOwned = seafoam
            transformationItems.add(transformationItem)
        }
        if (spookySparkles > 0) {
            val transformationItem = OwnedItem()
            transformationItem.itemType = "special"
            transformationItem.key = "spookySparkles"
            transformationItem.numberOwned = spookySparkles
            transformationItems.add(transformationItem)
        }
        return transformationItems
    }
}
