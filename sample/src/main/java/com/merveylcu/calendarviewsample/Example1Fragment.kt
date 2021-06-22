package com.merveylcu.calendarviewsample

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import com.merveylcu.calendarview.model.CalendarDay
import com.merveylcu.calendarview.model.DayOwner
import com.merveylcu.calendarview.model.InDateStyle
import com.merveylcu.calendarview.ui.DayBinder
import com.merveylcu.calendarview.ui.ViewContainer
import com.merveylcu.calendarview.utils.next
import com.merveylcu.calendarview.utils.previous
import com.merveylcu.calendarview.utils.yearMonth
import com.merveylcu.calendarviewsample.databinding.Example1CalendarDayBinding
import com.merveylcu.calendarviewsample.databinding.Example1FragmentBinding
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

class Example1Fragment : BaseFragment(R.layout.example_1_fragment), HasToolbar {

    override val toolbar: Toolbar?
        get() = null

    override val titleRes: Int = R.string.example_1_title

    private lateinit var binding: Example1FragmentBinding

    private val selectedDates = mutableSetOf<LocalDate>()
    private val today = LocalDate.now()
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = Example1FragmentBinding.bind(view)
        val daysOfWeek = daysOfWeekFromLocale()
        binding.legendLayout.root.children.forEachIndexed { index, v ->
            (v as TextView).apply {
                text = daysOfWeek[index].getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .toUpperCase(Locale.getDefault())
                setTextColorRes(R.color.example_1_white_light)
            }
        }

        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(10)
        val endMonth = currentMonth.plusMonths(10)
        binding.exOneCalendar.setup(startMonth, endMonth, daysOfWeek.first())
        binding.exOneCalendar.scrollToMonth(currentMonth)

        class DayViewContainer(view: View) : ViewContainer(view) {
            lateinit var day: CalendarDay
            val textView = Example1CalendarDayBinding.bind(view).exOneDayText

            init {
                view.setOnClickListener {
                    if (day.owner == DayOwner.THIS_MONTH) {
                        if (selectedDates.contains(day.date)) {
                            selectedDates.remove(day.date)
                        } else {
                            selectedDates.add(day.date)
                        }
                        binding.exOneCalendar.notifyDayChanged(day)
                    }
                }
            }
        }

        binding.exOneCalendar.dayBinder = object : DayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.day = day
                val textView = container.textView
                textView.text = day.date.dayOfMonth.toString()
                if (day.owner == DayOwner.THIS_MONTH) {
                    when {
                        selectedDates.contains(day.date) -> {
                            textView.setTextColorRes(R.color.example_1_bg)
                            textView.setBackgroundResource(R.drawable.example_1_selected_bg)
                        }
                        today == day.date -> {
                            textView.setTextColorRes(R.color.example_1_white)
                            textView.setBackgroundResource(R.drawable.example_1_today_bg)
                        }
                        else -> {
                            textView.setTextColorRes(R.color.example_1_white)
                            textView.background = null
                        }
                    }
                } else {
                    textView.setTextColorRes(R.color.example_1_white_light)
                    textView.background = null
                }
            }
        }

        binding.exOneCalendar.monthScrollListener = {
            if (binding.exOneCalendar.maxRowCount == 6) {
                val year = it.yearMonth.year.toString()
                val month = monthTitleFormatter.format(it.yearMonth)
                binding.exFiveMonthYearText.text = "$month $year"
            } else {
                val firstDate = it.weekDays.first().first().date
                val lastDate = it.weekDays.last().last().date
                if (firstDate.yearMonth == lastDate.yearMonth) {
                    val year = firstDate.yearMonth.year.toString()
                    val month = monthTitleFormatter.format(firstDate)
                    binding.exFiveMonthYearText.text = "$month $year"
                } else {
                    val month =
                        "${monthTitleFormatter.format(firstDate)} - ${monthTitleFormatter.format(lastDate)}"
                    val year = if (firstDate.year == lastDate.year) {
                        firstDate.yearMonth.year.toString()
                    } else {
                        "${firstDate.yearMonth.year} - ${lastDate.yearMonth.year}"
                    }
                    binding.exFiveMonthYearText.text = "$month $year"
                }
            }
        }

        binding.weekModeCheckBox.setOnCheckedChangeListener { _, monthToWeek ->
            //val firstDate = binding.exOneCalendar.findFirstVisibleDay()?.date ?: return@setOnCheckedChangeListener
            val lastDate = binding.exOneCalendar.findLastVisibleDay()?.date ?: return@setOnCheckedChangeListener

            val oneWeekHeight = binding.exOneCalendar.daySize.height
            val oneMonthHeight = oneWeekHeight * 6

            val oldHeight = if (monthToWeek) oneMonthHeight else oneWeekHeight
            val newHeight = if (monthToWeek) oneWeekHeight else oneMonthHeight

            val animator = ValueAnimator.ofInt(oldHeight, newHeight)
            animator.addUpdateListener {
                binding.exOneCalendar.updateLayoutParams {
                    height = it.animatedValue as Int
                }
            }

            animator.doOnStart {
                if (!monthToWeek) {
                    binding.exOneCalendar.updateMonthConfiguration(
                        inDateStyle = InDateStyle.ALL_MONTHS,
                        maxRowCount = 6,
                        hasBoundaries = true
                    )
                }
            }
            animator.doOnEnd {
                if (monthToWeek) {
                    binding.exOneCalendar.updateMonthConfiguration(
                        inDateStyle = InDateStyle.FIRST_MONTH,
                        maxRowCount = 1,
                        hasBoundaries = false
                    )
                }

                if (monthToWeek) {
                    binding.exOneCalendar.scrollToDate(today)
                } else {
                    if (today.yearMonth == lastDate.yearMonth) {
                        binding.exOneCalendar.scrollToMonth(today.yearMonth)
                    } else {
                        binding.exOneCalendar.scrollToMonth(minOf(today.yearMonth.next, endMonth))
                    }
                }
            }
            animator.duration = 250
            animator.start()
        }

        binding.exFiveNextMonthImage.setOnClickListener {
            if (binding.exOneCalendar.maxRowCount == 6) {
                binding.exOneCalendar.findFirstVisibleMonth()?.let {
                    binding.exOneCalendar.smoothScrollToMonth(it.yearMonth.next)
                }
            } else {
                binding.exOneCalendar.findLastVisibleDay()?.let {
                    binding.exOneCalendar.smoothScrollToDate(it.date.plusDays(1))
                }
            }
        }

        binding.exFivePreviousMonthImage.setOnClickListener {
            if (binding.exOneCalendar.maxRowCount == 6) {
                binding.exOneCalendar.findFirstVisibleMonth()?.let {
                    binding.exOneCalendar.smoothScrollToMonth(it.yearMonth.previous)
                }
            } else {
                binding.exOneCalendar.findFirstVisibleDay()?.let {
                    binding.exOneCalendar.smoothScrollToDate(it.date.minusDays(7))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().window.statusBarColor = requireContext().getColorCompat(R.color.example_1_bg_light)
    }

    override fun onStop() {
        super.onStop()
        requireActivity().window.statusBarColor = requireContext().getColorCompat(R.color.colorPrimaryDark)
    }
}
