package com.habitrpg.android.habitica.prefs

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.DialogPreference
import com.habitrpg.android.habitica.extensions.parseToZonedDateTimeDefault
import java.time.format.DateTimeFormatter

class TimePreference(ctxt: Context, attrs: AttributeSet?) : DialogPreference(ctxt, attrs) {
    private var timeval: String? = null

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index)!!
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        timeval = getPersistedString(defaultValue?.toString() ?: "19:00")
        summary = timeval ?: ""
    }

    val lastHour: Int
        get() = timeval.parseToZonedDateTimeDefault().hour
    val lastMinute: Int
        get() = timeval.parseToZonedDateTimeDefault().minute
    var text: String?
        get() = timeval
        set(text) {
            val wasBlocking = shouldDisableDependents()
            timeval = text
            persistString(text)
            val isBlocking = shouldDisableDependents()
            if (isBlocking != wasBlocking) {
                notifyDependencyChange(isBlocking)
            }
        }

    override fun setSummary(summary: CharSequence?) {
        val time = timeval.parseToZonedDateTimeDefault()
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        super.setSummary(time.format(formatter))
    }

    init {
        positiveButtonText = "Set"
        negativeButtonText = "Cancel"
    }
}
