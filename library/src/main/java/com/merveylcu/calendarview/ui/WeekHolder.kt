package com.merveylcu.calendarview.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isGone
import com.merveylcu.calendarview.model.CalendarDay

internal class WeekHolder(private val dayHolders: List<DayHolder>) {

    private lateinit var container: LinearLayout

    fun inflateWeekView(parent: LinearLayout): View {
        container = LinearLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            weightSum = dayHolders.count().toFloat()
            for (holder in dayHolders) {
                addView(holder.inflateDayView(this))
            }
        }
        return container
    }

    fun bindWeekView(daysOfWeek: List<CalendarDay>) {
        container.isGone = daysOfWeek.isEmpty()
        dayHolders.forEachIndexed { index, holder ->
            holder.bindDayView(daysOfWeek.getOrNull(index))
        }
    }

    fun reloadDay(day: CalendarDay): Boolean = dayHolders.any { it.reloadViewIfNecessary(day) }
}
