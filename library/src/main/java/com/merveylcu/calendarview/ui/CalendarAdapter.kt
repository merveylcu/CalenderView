package com.merveylcu.calendarview.ui

import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.merveylcu.calendarview.CalendarView
import com.merveylcu.calendarview.model.*
import com.merveylcu.calendarview.utils.NO_INDEX
import com.merveylcu.calendarview.utils.getVerticalMargins
import com.merveylcu.calendarview.utils.inflate
import com.merveylcu.calendarview.utils.orZero
import java.time.YearMonth

internal typealias LP = ViewGroup.LayoutParams

internal data class ViewConfig(
    @LayoutRes val dayViewRes: Int,
    @LayoutRes val monthHeaderRes: Int
)

internal class CalendarAdapter(
    private val calView: CalendarView,
    internal var viewConfig: ViewConfig,
    internal var monthConfig: MonthConfig
) : RecyclerView.Adapter<MonthViewHolder>() {

    private val months: List<CalendarMonth>
        get() = monthConfig.months

    var headerViewId = ViewCompat.generateViewId()
    var footerViewId = ViewCompat.generateViewId()

    init {
        setHasStableIds(true)
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                initialLayout = true
            }
        })
    }

    private val isAttached: Boolean
        get() = calView.adapter === this

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        calView.post { notifyMonthScrollListenerIfNeeded() }
    }

    private fun getItem(position: Int): CalendarMonth = months[position]

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    override fun getItemCount(): Int = months.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val context = parent.context
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (viewConfig.monthHeaderRes != 0) {
            val monthHeaderView = rootLayout.inflate(viewConfig.monthHeaderRes)

            if (monthHeaderView.id == View.NO_ID) {
                monthHeaderView.id = headerViewId
            } else {
                headerViewId = monthHeaderView.id
            }
            rootLayout.addView(monthHeaderView)
        }

        @Suppress("UNCHECKED_CAST")
        val dayConfig = DayConfig(
            calView.daySize, viewConfig.dayViewRes,
            calView.dayBinder as DayBinder<ViewContainer>
        )

        val weekHolders = (1..6)
            .map { WeekHolder(createDayHolders(dayConfig)) }
            .onEach { weekHolder -> rootLayout.addView(weekHolder.inflateWeekView(rootLayout)) }

        fun setupRoot(root: ViewGroup) {
            ViewCompat.setPaddingRelative(
                root,
                calView.monthPaddingStart, calView.monthPaddingTop,
                calView.monthPaddingEnd, calView.monthPaddingBottom
            )
            root.layoutParams = ViewGroup.MarginLayoutParams(LP.WRAP_CONTENT, LP.WRAP_CONTENT).apply {
                bottomMargin = calView.monthMarginBottom
                topMargin = calView.monthMarginTop

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    marginStart = calView.monthMarginStart
                    marginEnd = calView.monthMarginEnd
                } else {
                    leftMargin = calView.monthMarginStart
                    rightMargin = calView.monthMarginEnd
                }
            }
        }

        val userRoot = rootLayout.apply { setupRoot(this) }

        @Suppress("UNCHECKED_CAST")
        return MonthViewHolder(
            this,
            userRoot,
            weekHolders,
            calView.monthHeaderBinder as MonthHeaderFooterBinder<ViewContainer>?,
            calView.monthFooterBinder as MonthHeaderFooterBinder<ViewContainer>?
        )
    }

    private fun createDayHolders(dayConfig: DayConfig) = (1..7).map { DayHolder(dayConfig) }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            payloads.forEach {
                holder.reloadDay(it as CalendarDay)
            }
        }
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        holder.bindMonth(getItem(position))
    }

    fun reloadDay(day: CalendarDay) {
        val position = getAdapterPosition(day)
        if (position != NO_INDEX) {
            notifyItemChanged(position, day)
        }
    }

    fun reloadMonth(month: YearMonth) {
        notifyItemChanged(getAdapterPosition(month))
    }

    fun reloadCalendar() {
        notifyItemRangeChanged(0, itemCount)
    }

    private var visibleMonth: CalendarMonth? = null
    private var calWrapsHeight: Boolean? = null
    private var initialLayout = true
    fun notifyMonthScrollListenerIfNeeded() {
        if (!isAttached) return

        if (calView.isAnimating) {
            calView.itemAnimator?.isRunning {
                notifyMonthScrollListenerIfNeeded()
            }
            return
        }
        val visibleItemPos = findFirstVisibleMonthPosition()
        if (visibleItemPos != RecyclerView.NO_POSITION) {
            val visibleMonth = months[visibleItemPos]

            if (visibleMonth != this.visibleMonth) {
                this.visibleMonth = visibleMonth
                calView.monthScrollListener?.invoke(visibleMonth)

                if (calView.scrollMode == ScrollMode.PAGED) {
                    val calWrapsHeight = calWrapsHeight ?: (calView.layoutParams.height == LP.WRAP_CONTENT).also {
                        calWrapsHeight = it
                    }
                    if (!calWrapsHeight) return
                    val visibleVH =
                        calView.findViewHolderForAdapterPosition(visibleItemPos) as? MonthViewHolder ?: return
                    val newHeight = visibleVH.headerView?.height.orZero() +
                            visibleVH.headerView?.getVerticalMargins().orZero() +
                            visibleMonth.weekDays.size * calView.daySize.height +
                            visibleVH.footerView?.height.orZero() +
                            visibleVH.footerView?.getVerticalMargins().orZero()
                    if (calView.height != newHeight) {
                        ValueAnimator.ofInt(calView.height, newHeight).apply {
                            duration = 0
                            addUpdateListener {
                                calView.updateLayoutParams { height = it.animatedValue as Int }
                                visibleVH.itemView.requestLayout()
                            }
                            start()
                        }
                    } else {
                        visibleVH.itemView.requestLayout()
                    }
                    if (initialLayout) initialLayout = false
                }
            }
        }
    }

    internal fun getAdapterPosition(month: YearMonth): Int {
        return months.indexOfFirst { it.yearMonth == month }
    }

    internal fun getAdapterPosition(day: CalendarDay): Int {
        return if (monthConfig.hasBoundaries) {
            val firstMonthIndex = getAdapterPosition(day.positionYearMonth)
            if (firstMonthIndex == NO_INDEX) return NO_INDEX

            val firstCalMonth = months[firstMonthIndex]
            val sameMonths = months.slice(firstMonthIndex until firstMonthIndex + firstCalMonth.numberOfSameMonth)
            val indexWithDateInSameMonth = sameMonths.indexOfFirst { months ->
                months.weekDays.any { weeks -> weeks.any { it == day } }
            }

            if (indexWithDateInSameMonth == NO_INDEX) NO_INDEX else firstMonthIndex + indexWithDateInSameMonth
        } else {
            months.indexOfFirst { months ->
                months.weekDays.any { weeks -> weeks.any { it == day } }
            }
        }
    }

    private val layoutManager: CalendarLayoutManager
        get() = calView.layoutManager as CalendarLayoutManager

    fun findFirstVisibleMonth(): CalendarMonth? = months.getOrNull(findFirstVisibleMonthPosition())

    fun findLastVisibleMonth(): CalendarMonth? = months.getOrNull(findLastVisibleMonthPosition())

    fun findFirstVisibleDay(): CalendarDay? = findVisibleDay(true)

    fun findLastVisibleDay(): CalendarDay? = findVisibleDay(false)

    private fun findFirstVisibleMonthPosition(): Int = findVisibleMonthPosition(true)

    private fun findLastVisibleMonthPosition(): Int = findVisibleMonthPosition(false)

    private fun findVisibleMonthPosition(isFirst: Boolean): Int {
        val visibleItemPos =
            if (isFirst) layoutManager.findFirstVisibleItemPosition() else layoutManager.findLastVisibleItemPosition()

        if (visibleItemPos != RecyclerView.NO_POSITION) {

            // We make sure that the view for the returned position is visible to a reasonable degree.
            val visibleItemPx = Rect().let { rect ->
                val visibleItemView = layoutManager.findViewByPosition(visibleItemPos) ?: return NO_INDEX
                visibleItemView.getGlobalVisibleRect(rect)
                return@let if (calView.isVertical) {
                    rect.bottom - rect.top
                } else {
                    rect.right - rect.left
                }
            }

            if (visibleItemPx <= 7) {
                val nextItemPosition = if (isFirst) visibleItemPos + 1 else visibleItemPos - 1
                return if (months.indices.contains(nextItemPosition)) {
                    nextItemPosition
                } else {
                    visibleItemPos
                }
            }
        }
        return visibleItemPos
    }

    private fun findVisibleDay(isFirst: Boolean): CalendarDay? {
        val visibleIndex = if (isFirst) findFirstVisibleMonthPosition() else findLastVisibleMonthPosition()
        if (visibleIndex == NO_INDEX) return null

        val visibleItemView = layoutManager.findViewByPosition(visibleIndex) ?: return null
        val monthRect = Rect()
        visibleItemView.getGlobalVisibleRect(monthRect)

        val dayRect = Rect()
        return months[visibleIndex].weekDays.flatten()
            .run { if (isFirst) this else reversed() }
            .firstOrNull {
                val dayView = visibleItemView.findViewWithTag<View>(it.date.hashCode()) ?: return@firstOrNull false
                dayView.getGlobalVisibleRect(dayRect)
                dayRect.intersect(monthRect)
            }
    }
}
