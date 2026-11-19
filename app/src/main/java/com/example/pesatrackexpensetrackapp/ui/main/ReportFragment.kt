package com.example.pesatrackexpensetrackapp.ui.main

import android.app.DatePickerDialog
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.data.model.FinanceCategory
import com.example.pesatrackexpensetrackapp.data.model.FinanceTransaction
import com.example.pesatrackexpensetrackapp.databinding.FragmentReportBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

class ReportFragment : Fragment() {
    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!
    private val finance: FinanceViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.financeViewModelFactory
    }
    private var period = 1
    private var customStart = startOfDay(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) })
    private var customEnd = startOfDay(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) })
    private var selectedCategoryId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        finance.transactions.observe(viewLifecycleOwner) { render() }
        finance.categories.observe(viewLifecycleOwner) { render() }
        finance.transactionsLoaded.observe(viewLifecycleOwner) { render() }
        finance.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
        render()
    }

    private fun render() {
        val content = _binding?.reportContent ?: return
        content.removeAllViews()
        val context = requireContext()
        val categories = finance.categories.value.orEmpty().filterNot { it.archived }
        val allTransactions = finance.transactions.value.orEmpty()
        val (start, end) = periodRange()
        val previousRange = previousPeriodRange(start, end)
        val expenses = allTransactions.filter { it.type == "EXPENSE" && it.occurredAtMillis in start until end }
        val periodTransactionCount = allTransactions.count { it.occurredAtMillis in start until end }
        val priorExpenses = allTransactions.filter { it.type == "EXPENSE" && it.occurredAtMillis in previousRange.first until previousRange.second }
        val total = expenses.sumOf { it.amountMinor }
        val priorTotal = priorExpenses.sumOf { it.amountMinor }
        val dayCount = ((end - start) / DAY_MILLIS).coerceAtLeast(1L)
        val categoryTotals = expenses.groupBy { it.categoryId }
            .map { (id, items) -> CategorySpend(id, categories.firstOrNull { it.id == id }, items.sumOf { it.amountMinor }) }
            .sortedByDescending { it.amount }
        val title = label("Reports", 26, R.color.text_primary, true)
        content.addView(title)
        content.addView(label("Your spending summary", 13, R.color.text_muted).withTop(4))
        val picker = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, PERIODS)
            setSelection(period)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position == period) return
                    period = position
                    if (position == 5) pickCustomRange() else render()
                }
            }
        }
        content.addView(picker, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(15) })
        if (period == 5) content.addView(label("${dateLabel(start)} – ${dateLabel(end - 1)}  ·  Tap to change dates", 12, R.color.forest_700).apply {
            setOnClickListener { pickCustomRange() }
        }.withTop(2))
        if (finance.transactionsLoaded.value != true) {
            content.addView(ProgressBar(requireContext()).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                gravity = Gravity.CENTER
                topMargin = dp(32)
                bottomMargin = dp(12)
            })
            content.addView(label("Loading your expenses…", 13, R.color.text_muted).apply { gravity = Gravity.CENTER })
            return
        }

        content.addView(sectionTitle("Overview"))
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val change = if (priorTotal == 0L) "No previous period data" else {
            val pct = ((total - priorTotal).toDouble() / priorTotal * 100).toInt()
            "${if (pct > 0) "+" else ""}$pct% vs previous period"
        }
        val top = categoryTotals.firstOrNull()
        val topShare = if (total == 0L || top == null) 0 else (top.amount * 100 / total).toInt()
        val cardRows = listOf(
            listOf("Total expenses" to money(total), "Daily average" to money(total / dayCount)),
            listOf("Top category" to (top?.let { "${it.category?.name ?: "Other"} · $topShare%" } ?: "—"), "Transactions" to periodTransactionCount.toString()),
        )
        cardRows.forEachIndexed { rowIndex, cells ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            cells.forEachIndexed { colIndex, cell ->
                val accent = (rowIndex == 0 && colIndex == 0)
                row.addView(metricCard(cell.first, cell.second, if (accent) change else null), LinearLayout.LayoutParams(0, dp(100), 1f).apply {
                    if (colIndex == 0) marginEnd = dp(7) else marginStart = dp(7)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        content.addView(grid)

        content.addView(sectionTitle("Expenses by category"))
        if (categoryTotals.isEmpty()) content.addView(emptyCard("No expenses in this period", "Add an expense to see your category breakdown."))
        else {
            val chart = DonutChart(context, categoryTotals.map { it.amount }, categoryTotals.map { categoryColor(it.category, it.id) }) { index ->
                categoryTotals.getOrNull(index)?.let { showCategoryDetail(it, expenses, total) }
            }
            content.addView(chart, LinearLayout.LayoutParams(-1, dp(190)))
            categoryTotals.forEach { item ->
                val percent = if (total == 0L) 0 else (item.amount * 100 / total).toInt()
                val categoryName = item.category?.name ?: "Other"
                val row = simpleRow("●   $categoryName", "${money(item.amount)}  ·  $percent%")
                row.setOnClickListener { showCategoryDetail(item, expenses, total) }
                content.addView(row)
            }
        }

        content.addView(sectionTitle("Spending trend"))
        val (labels, currentValues, priorValues) = trendData(allTransactions, start, end, previousRange.first)
        val trend = TrendChart(context, labels, currentValues, priorValues)
        content.addView(trend, LinearLayout.LayoutParams(-1, dp(205)))
        content.addView(label("● Selected period       ┄ Previous period", 11, R.color.text_muted).apply { gravity = Gravity.CENTER }.withTop(7))

        content.addView(sectionTitle("Recent expenses"))
        val recent = expenses.sortedByDescending { it.occurredAtMillis }.take(5)
        if (recent.isEmpty()) content.addView(emptyCard("Nothing to show yet", "Your expenses for this period will appear here."))
        else recent.forEach { transaction ->
            val category = categories.firstOrNull { it.id == transaction.categoryId }
            content.addView(simpleRow("${icon(category?.iconKey)}  ${transaction.title.ifBlank { category?.name ?: "Expense" }}", money(transaction.amountMinor), "${category?.name ?: "Other"}  ·  ${dateLabel(transaction.occurredAtMillis)}"))
        }
        val viewAll = label("View all transactions  →", 13, R.color.forest_700, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(10))
            setOnClickListener { findNavController().navigate(R.id.transactionsFragment) }
        }
        content.addView(viewAll)
    }

    private fun periodRange(): Pair<Long, Long> {
        val now = Calendar.getInstance()
        val start = (now.clone() as Calendar).apply {
            when (period) {
                0 -> { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                1 -> { set(Calendar.DAY_OF_MONTH, 1); clearTime() }
                2 -> { set(Calendar.DAY_OF_MONTH, 1); clearTime(); add(Calendar.MONTH, -1) }
                3 -> { set(Calendar.DAY_OF_MONTH, 1); clearTime(); add(Calendar.MONTH, -2) }
                4 -> { set(Calendar.DAY_OF_YEAR, 1); clearTime() }
                5 -> timeInMillis = customStart
            }
        }
        val end = (now.clone() as Calendar).apply {
            when (period) {
                0 -> { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); clearTime(); add(Calendar.DAY_OF_YEAR, 7); setToTomorrowIfEarlier(this) }
                1 -> { set(Calendar.DAY_OF_MONTH, 1); clearTime(); add(Calendar.MONTH, 1); setToTomorrowIfEarlier(this) }
                2 -> { set(Calendar.DAY_OF_MONTH, 1); clearTime() }
                3 -> { set(Calendar.DAY_OF_MONTH, 1); clearTime(); add(Calendar.MONTH, 1) }
                4 -> { set(Calendar.DAY_OF_YEAR, 1); clearTime(); add(Calendar.YEAR, 1); setToTomorrowIfEarlier(this) }
                5 -> timeInMillis = customEnd
            }
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun previousPeriodRange(start: Long, end: Long): Pair<Long, Long> {
        val length = (end - start).coerceAtLeast(DAY_MILLIS)
        return (start - length) to start
    }

    private fun trendData(transactions: List<FinanceTransaction>, start: Long, end: Long, previousStart: Long): Triple<List<String>, List<Long>, List<Long>> {
        val days = ((end - start) / DAY_MILLIS).coerceAtLeast(1).toInt()
        val monthly = days > 45
        val count = if (monthly) min(12, (days + 29) / 30) else min(14, days)
        val step = if (monthly) (days.toDouble() / count).toInt().coerceAtLeast(1) else 1
        val labels = mutableListOf<String>(); val current = MutableList(count) { 0L }; val previous = MutableList(count) { 0L }
        val sdf = SimpleDateFormat(if (monthly) "MMM" else "d/M", Locale.getDefault())
        repeat(count) { i -> labels += sdf.format(Date(start + i.toLong() * step * DAY_MILLIS)) }
        transactions.filter { it.type == "EXPENSE" }.forEach { t ->
            val offset = t.occurredAtMillis - start
            if (offset >= 0 && t.occurredAtMillis < end) current[((offset / (step * DAY_MILLIS)).toInt()).coerceIn(0, count - 1)] += t.amountMinor
            val priorOffset = t.occurredAtMillis - previousStart
            if (priorOffset >= 0 && t.occurredAtMillis < start) previous[((priorOffset / (step * DAY_MILLIS)).toInt()).coerceIn(0, count - 1)] += t.amountMinor
        }
        return Triple(labels, current, previous)
    }

    private fun pickCustomRange() {
        val startCalendar = Calendar.getInstance().apply { timeInMillis = customStart }
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val pickedStart = Calendar.getInstance().apply { set(year, month, day); clearTime() }
            DatePickerDialog(requireContext(), { _, endYear, endMonth, endDay ->
                val pickedEnd = Calendar.getInstance().apply { set(endYear, endMonth, endDay); clearTime(); add(Calendar.DAY_OF_YEAR, 1) }
                if (pickedEnd.timeInMillis <= pickedStart.timeInMillis) {
                    Toast.makeText(context, "End date must be after start date", Toast.LENGTH_SHORT).show()
                } else {
                    customStart = pickedStart.timeInMillis; customEnd = pickedEnd.timeInMillis; render()
                }
            }, year, month, day).apply { setTitle("Select end date"); datePicker.minDate = pickedStart.timeInMillis }.show()
        }, startCalendar.get(Calendar.YEAR), startCalendar.get(Calendar.MONTH), startCalendar.get(Calendar.DAY_OF_MONTH)).apply { setTitle("Select start date") }.show()
    }

    private fun showCategoryDetail(item: CategorySpend, expenses: List<FinanceTransaction>, total: Long) {
        val relevant = expenses.filter { it.categoryId == item.id }
        val percent = if (total == 0L) 0 else item.amount * 100 / total
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(item.category?.name ?: "Other")
            .setMessage("${money(item.amount)} total · $percent% of expenses\n${relevant.size} transactions\n\nLargest: ${relevant.maxByOrNull { it.amountMinor }?.let { "${it.title} · ${money(it.amountMinor)}" } ?: "—"}")
            .setPositiveButton("Done", null).show()
    }

    private fun metricCard(name: String, value: String, note: String?) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(12), dp(13), dp(10)); setBackgroundResource(R.drawable.bg_card)
        addView(label(name, 11, R.color.text_muted))
        addView(label(value, if (value.length > 16) 14 else 18, R.color.text_primary, true).withTop(7))
        if (note != null) addView(label(note, 9, R.color.forest_700).withTop(3))
    }

    private fun sectionTitle(title: String) = label(title, 17, R.color.text_primary, true).apply { setPadding(0, dp(19), 0, dp(9)) }
    private fun emptyCard(title: String, detail: String) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(18), dp(20), dp(18), dp(20)); setBackgroundResource(R.drawable.bg_card)
        addView(label(title, 14, R.color.text_primary, true)); addView(label(detail, 12, R.color.text_muted).withTop(5))
    }
    private fun simpleRow(title: String, amount: String, subtitle: String? = null) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(12), dp(13), dp(12)); setBackgroundResource(R.drawable.bg_card)
        val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        line.addView(label(title, 13, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(label(amount, 12, R.color.text_primary, true))
        addView(line); if (subtitle != null) addView(label(subtitle, 11, R.color.text_muted).withTop(5))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
    }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(requireContext()).apply {
        text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(requireContext(), color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD); includeFontPadding = false
    }
    private fun TextView.withTop(value: Int): TextView { setPadding(paddingLeft, dp(value), paddingRight, paddingBottom); return this }
    private fun money(minor: Long): String = "KSh " + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-KE")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(minor / 100.0)
    private fun dateLabel(millis: Long) = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    private fun categoryColor(category: FinanceCategory?, id: String): Int {
        val hex = category?.colorHex?.removePrefix("#")
        return try { android.graphics.Color.parseColor("#${hex ?: ""}") } catch (_: Exception) { PALETTE[(id.hashCode().and(Int.MAX_VALUE)) % PALETTE.size] }
    }
    private fun icon(key: String?): String = when (key?.lowercase()) { "food" -> "🍽"; "transport" -> "🚌"; "shopping" -> "🛍"; "home" -> "⌂"; "receipt" -> "▤"; "health" -> "✚"; "entertainment" -> "♪"; "phone" -> "▣"; else -> "•" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun startOfDay(cal: Calendar) = cal.apply { clearTime() }.timeInMillis
    private fun setToTomorrowIfEarlier(candidate: Calendar) {
        val tomorrow = Calendar.getInstance().apply { clearTime(); add(Calendar.DAY_OF_YEAR, 1) }
        if (candidate.after(tomorrow)) candidate.timeInMillis = tomorrow.timeInMillis
    }
    private fun Calendar.clearTime() { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    private data class CategorySpend(val id: String, val category: FinanceCategory?, val amount: Long)

    private class DonutChart(context: android.content.Context, private val amounts: List<Long>, private val colors: List<Int>, private val onSelect: (Int) -> Unit) : View(context) {
        private val paint = Paint(3)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas); if (amounts.isEmpty()) return
            val total = amounts.sum().coerceAtLeast(1L); val size = min(width, height).toFloat() * .78f
            val rect = RectF((width-size)/2, (height-size)/2, (width+size)/2, (height+size)/2)
            var angle = -90f
            amounts.forEachIndexed { i, amount ->
                val sweep = amount.toFloat() / total * 360f
                paint.color = colors[i]; paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .18f; paint.strokeCap = Paint.Cap.BUTT
                canvas.drawArc(rect, angle, sweep, false, paint); angle += sweep
            }
            paint.style = Paint.Style.FILL; paint.color = android.graphics.Color.DKGRAY; paint.textAlign = Paint.Align.CENTER; paint.textSize = 12f * resources.displayMetrics.scaledDensity
            canvas.drawText("${amounts.size} categories", width / 2f, height / 2f + paint.textSize / 3f, paint)
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.action == android.view.MotionEvent.ACTION_UP && amounts.isNotEmpty()) {
                val size = min(width, height).toFloat() * .78f
                val dx = event.x - width / 2f
                val dy = event.y - height / 2f
                val radius = kotlin.math.sqrt(dx * dx + dy * dy)
                if (radius in (size * .30f)..(size * .50f)) {
                    var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0) angle += 360f
                    val total = amounts.sum().coerceAtLeast(1L)
                    var accumulated = 0f
                    amounts.forEachIndexed { index, amount ->
                        accumulated += amount.toFloat() / total * 360f
                        if (angle <= accumulated) { onSelect(index); return true }
                    }
                }
                return true
            }
            return true
        }
    }

    private class TrendChart(context: android.content.Context, private val labels: List<String>, private val values: List<Long>, private val previous: List<Long>) : View(context) {
        private val paint = Paint(3)
        private var selected = -1
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas); if (values.isEmpty()) return
            val left = 48f * resources.displayMetrics.density; val right = width - 12f * resources.displayMetrics.density
            val top = 18f * resources.displayMetrics.density; val bottom = height - 32f * resources.displayMetrics.density
            val maxValue = (values + previous).maxOrNull()?.coerceAtLeast(1L) ?: 1L
            paint.color = 0xffdfe7e2.toInt(); paint.strokeWidth = 1f
            for (i in 0..3) { val y = bottom - (bottom-top)*i/3; canvas.drawLine(left,y,right,y,paint) }
            drawSeries(canvas, previous, left, right, top, bottom, maxValue, 0xff9eaaa3.toInt(), true)
            drawSeries(canvas, values, left, right, top, bottom, maxValue, 0xff21805e.toInt(), false)
            labels.forEachIndexed { i, text -> if (i % maxOf(1, labels.size / 5) == 0) { paint.color = 0xff6d7d76.toInt(); paint.textSize = 10f * resources.displayMetrics.scaledDensity; paint.textAlign = Paint.Align.CENTER; canvas.drawText(text, x(i, values.size, left, right), height - 6f * resources.displayMetrics.density, paint) } }
            if (selected >= 0 && selected < values.size) { val x = x(selected, values.size, left, right); val y = y(values[selected], top, bottom, maxValue); paint.color = 0xff176b4d.toInt(); canvas.drawCircle(x,y,5f*resources.displayMetrics.density,paint); paint.textAlign = Paint.Align.CENTER; paint.textSize=11f*resources.displayMetrics.scaledDensity; canvas.drawText("${labels[selected]} · KSh ${values[selected]/100.0}", x.coerceIn(left+60,right-60), y-10, paint) }
        }
        private fun drawSeries(canvas: Canvas, data: List<Long>, left: Float, right: Float, top: Float, bottom: Float, max: Long, color: Int, dashed: Boolean) {
            if (data.isEmpty()) return
            paint.color=color; paint.style=Paint.Style.STROKE; paint.strokeWidth=2f*resources.displayMetrics.density; paint.pathEffect=if (dashed) android.graphics.DashPathEffect(floatArrayOf(8f,6f),0f) else null
            val path=Path(); data.forEachIndexed { i,v -> if (i==0) path.moveTo(x(i,data.size,left,right),y(v,top,bottom,max)) else path.lineTo(x(i,data.size,left,right),y(v,top,bottom,max)) }; canvas.drawPath(path,paint); paint.pathEffect=null; paint.style=Paint.Style.FILL
        }
        private fun x(i:Int,n:Int,l:Float,r:Float)=if(n<=1)(l+r)/2 else l+(r-l)*i/(n-1)
        private fun y(v:Long,t:Float,b:Float,m:Long)=b-(b-t)*v.toFloat()/m
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean { if(event.action==android.view.MotionEvent.ACTION_UP && values.isNotEmpty()) { val l=48f*resources.displayMetrics.density; val r=width-12f*resources.displayMetrics.density; selected=if(values.size==1)0 else (((event.x-l)/(r-l)*(values.size-1)).toInt()).coerceIn(values.indices); invalidate(); performClick(); return true }; return true }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private val PERIODS = listOf("This Week", "This Month", "Last Month", "Last 3 Months", "This Year", "Custom Date Range")
        private val PALETTE = intArrayOf(0xff45b87b.toInt(),0xff2f80ed.toInt(),0xffeb5757.toInt(),0xfff2994a.toInt(),0xffbb6bd9.toInt(),0xff27ae60.toInt())
    }
}
