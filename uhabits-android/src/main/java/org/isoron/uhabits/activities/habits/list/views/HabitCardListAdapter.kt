/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.activities.habits.list.views

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.isoron.uhabits.activities.habits.list.MAX_CHECKMARK_COUNT
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitMatcher
import org.isoron.uhabits.core.models.ModelObservable
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.screens.habits.list.HabitCardListCache
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsMenuBehavior
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsSelectionMenuBehavior
import org.isoron.uhabits.core.utils.MidnightTimer
import org.isoron.uhabits.inject.ActivityScope
import java.util.LinkedList
import javax.inject.Inject

/**
 * Provides data that backs a [HabitCardListView].
 *
 * The data is fetched and cached by a [HabitCardListCache]. This adapter
 * also holds a list of items that have been selected. It supports two view
 * types: habit cards and category headers.
 */
@ActivityScope
class HabitCardListAdapter @Inject constructor(
    private val cache: HabitCardListCache,
    private val preferences: Preferences,
    private val midnightTimer: MidnightTimer
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    HabitCardListCache.Listener,
    MidnightTimer.MidnightListener,
    ListHabitsMenuBehavior.Adapter,
    ListHabitsSelectionMenuBehavior.Adapter {

    val observable: ModelObservable = ModelObservable()
    private var listView: HabitCardListView? = null
    val selected: LinkedList<Habit> = LinkedList()

    companion object {
        const val VIEW_TYPE_HABIT = 0
        const val VIEW_TYPE_CATEGORY_HEADER = 1
    }

    /**
     * Represents an item in the flattened display list —
     * either a habit card or a category header.
     */
    sealed class DisplayItem {
        data class HabitItem(val habit: Habit) : DisplayItem()
        data class CategoryHeader(val categoryName: String) : DisplayItem()
    }

    /** The flattened display list, rebuilt whenever the cache changes. */
    private var displayItems: List<DisplayItem> = emptyList()

    override fun atMidnight() {
        cache.refreshAllHabits()
    }

    fun cancelRefresh() {
        cache.cancelTasks()
    }

    fun hasNoHabit(): Boolean {
        return cache.hasNoHabit()
    }

    /**
     * Sets all items as not selected.
     */
    override fun clearSelection() {
        if (selected.isEmpty()) return
        selected.clear()
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    override fun getSelected(): List<Habit> {
        return ArrayList(selected)
    }

    /**
     * Returns the habit at a given display position, or null if
     * that position is a category header.
     */
    @Deprecated("")
    fun getItem(position: Int): Habit? {
        if (position < 0 || position >= displayItems.size) return null
        val item = displayItems[position]
        return if (item is DisplayItem.HabitItem) item.habit else null
    }

    /**
     * Returns the category name at a given display position, or null if
     * that position is a habit card.
     */
    fun getCategoryAt(position: Int): String? {
        if (position < 0 || position >= displayItems.size) return null
        val item = displayItems[position]
        return if (item is DisplayItem.CategoryHeader) item.categoryName else null
    }

    override fun getItemCount(): Int = displayItems.size

    override fun getItemId(position: Int): Long {
        val item = displayItems[position]
        return when (item) {
            is DisplayItem.HabitItem -> item.habit.id!!
            is DisplayItem.CategoryHeader -> -(item.categoryName.hashCode().toLong() + 1)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is DisplayItem.HabitItem -> VIEW_TYPE_HABIT
            is DisplayItem.CategoryHeader -> VIEW_TYPE_CATEGORY_HEADER
        }
    }

    /**
     * Returns whether list of selected items is empty.
     */
    val isSelectionEmpty: Boolean
        get() = selected.isEmpty()
    val isSortable: Boolean
        get() = cache.primaryOrder == HabitList.Order.BY_POSITION

    /**
     * Notify the adapter that it has been attached to a ListView.
     */
    fun onAttached() {
        cache.onAttached()
        midnightTimer.addListener(this)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val item = displayItems[position]) {
            is DisplayItem.HabitItem -> {
                if (listView == null) return
                val habit = item.habit
                val score = cache.getScore(habit.id!!)
                val checkmarks = cache.getCheckmarks(habit.id!!)
                val notes = cache.getNotes(habit.id!!)
                val isSelected = selected.contains(habit)
                listView!!.bindCardView(
                    holder as HabitCardViewHolder, habit, score, checkmarks, notes, isSelected
                )
            }
            is DisplayItem.CategoryHeader -> {
                val view = holder.itemView as CategoryHeaderView
                view.categoryName = item.categoryName
            }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is HabitCardViewHolder) {
            listView!!.attachCardView(holder)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is HabitCardViewHolder) {
            listView!!.detachCardView(holder)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HABIT -> {
                val view = listView!!.createHabitCardView()
                HabitCardViewHolder(view)
            }
            VIEW_TYPE_CATEGORY_HEADER -> {
                val view = CategoryHeaderView(parent.context)
                CategoryHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    /**
     * Notify the adapter that it has been detached from a ListView.
     */
    fun onDetached() {
        cache.onDetached()
        midnightTimer.removeListener(this)
    }

    override fun onItemChanged(position: Int) {
        rebuildDisplayList()
    }

    override fun onItemInserted(position: Int) {
        rebuildDisplayList()
    }

    override fun onItemMoved(oldPosition: Int, newPosition: Int) {
        rebuildDisplayList()
    }

    override fun onItemRemoved(position: Int) {
        rebuildDisplayList()
    }

    override fun onRefreshFinished() {
        rebuildDisplayList()
    }

    /**
     * Removes a list of habits from the adapter.
     *
     * Note that this only has effect on the adapter cache. The database is not
     * modified, and the change is lost when the cache is refreshed.
     */
    override fun performRemove(selected: List<Habit>) {
        for (habit in selected) cache.remove(habit.id!!)
    }

    /**
     * Changes the order of habits on the adapter.
     *
     * Note that this only has effect on the adapter cache. The database is not
     * modified, and the change is lost when the cache is refreshed.
     */
    fun performReorder(from: Int, to: Int) {
        // Map display positions back to cache positions
        val fromCachePos = displayPositionToCachePosition(from) ?: return
        val toCachePos = displayPositionToCachePosition(to) ?: return
        cache.reorder(fromCachePos, toCachePos)
    }

    override fun refresh() {
        cache.refreshAllHabits()
    }

    override fun setFilter(matcher: HabitMatcher) {
        cache.setFilter(matcher)
    }

    /**
     * Sets the HabitCardListView that this adapter will provide data for.
     */
    fun setListView(listView: HabitCardListView?) {
        this.listView = listView
    }

    override var primaryOrder: HabitList.Order
        get() = cache.primaryOrder
        set(value) {
            cache.primaryOrder = value
            preferences.defaultPrimaryOrder = value
        }

    override var secondaryOrder: HabitList.Order
        get() = cache.secondaryOrder
        set(value) {
            cache.secondaryOrder = value
            preferences.defaultSecondaryOrder = value
        }

    /**
     * Selects or deselects the item at a given position.
     */
    fun toggleSelection(position: Int) {
        val h = getItem(position) ?: return
        val k = selected.indexOf(h)
        if (k < 0) selected.add(h) else selected.remove(h)
        notifyDataSetChanged()
    }

    /**
     * Rebuilds the flattened display list from the cache, grouping habits
     * by category with headers between groups.
     */
    private fun rebuildDisplayList() {
        val items = mutableListOf<DisplayItem>()
        val habitCount = cache.habitCount

        // Collect all habits from the cache
        val allHabits = mutableListOf<Habit>()
        for (i in 0 until habitCount) {
            cache.getHabitByPosition(i)?.let { allHabits.add(it) }
        }

        // Group by category
        val uncategorized = allHabits.filter { it.category.isEmpty() }
        val categorized = allHabits.filter { it.category.isNotEmpty() }
            .groupBy { it.category }

        // All known categories (from preferences + from existing habits)
        val habitCategories = categorized.keys
        val prefCategories = preferences.categories
        val allCategories = (habitCategories + prefCategories).sorted()

        // Add uncategorized habits first (no header)
        for (habit in uncategorized) {
            items.add(DisplayItem.HabitItem(habit))
        }

        // Add each category with header
        for (category in allCategories) {
            items.add(DisplayItem.CategoryHeader(category))
            categorized[category]?.forEach { habit ->
                items.add(DisplayItem.HabitItem(habit))
            }
        }

        displayItems = items
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    /**
     * Maps a display position to the corresponding cache position.
     * Returns null if the display position is a category header.
     */
    private fun displayPositionToCachePosition(displayPosition: Int): Int? {
        val item = displayItems.getOrNull(displayPosition) ?: return null
        if (item !is DisplayItem.HabitItem) return null
        // Find this habit's index in the cache
        for (i in 0 until cache.habitCount) {
            if (cache.getHabitByPosition(i) == item.habit) return i
        }
        return null
    }

    init {
        cache.setListener(this)
        cache.setCheckmarkCount(
            MAX_CHECKMARK_COUNT
        )
        cache.secondaryOrder = preferences.defaultSecondaryOrder
        cache.primaryOrder = preferences.defaultPrimaryOrder
        setHasStableIds(true)
    }
}
