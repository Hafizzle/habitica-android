package com.habitrpg.android.habitica.models.tasks

import android.os.Parcel
import android.os.Parcelable
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date


open class RemindersItem : RealmObject, Parcelable {
    @PrimaryKey
    var id: String? = null
    var startDate: Date? = null
    var time: Date? = null
    var localTimeString: String? = null

    // Use to store task type before a task is created
    var type: String? = null

    // Use to get local wall clock time
    // (Example: for keeping wall clock time among time zone changes)
    // (ex:5PM CST, 5PM EST) alarm changes)
    fun getLocalWallClockTime(): Date? {
        if (localTimeString != null) {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
            val date: LocalDateTime = LocalDateTime.parse(localTimeString, formatter)
            return Date.from(date.atZone(ZoneId.systemDefault()).toInstant())
        } else {
            return time
        }
    }

    fun setLocalWallClockTime(){

    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(localTimeString)
        dest.writeLong(this.startDate?.time ?: -1)
        dest.writeLong(this.time?.time ?: -1)
    }

    companion object CREATOR : Parcelable.Creator<RemindersItem> {
        override fun createFromParcel(source: Parcel): RemindersItem = RemindersItem(source)

        override fun newArray(size: Int): Array<RemindersItem?> = arrayOfNulls(size)
    }

    constructor(source: Parcel) {
        id = source.readString()
        startDate = Date(source.readLong())
        time = Date(source.readLong())
        localTimeString = source.readString()
    }

    constructor()

    override fun equals(other: Any?): Boolean {
        return if (other is RemindersItem) {
            this.id == other.id
        } else super.equals(other)
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}
