/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saggitt.omega.smartspace

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.saggitt.omega.smartspace.eventprovider.*
import com.saggitt.omega.smartspace.weather.FakeDataProvider
import com.saggitt.omega.util.isVisible
import com.saggitt.omega.util.omegaPrefs

class EventProvidersAdapter(private val context: Context) :
    RecyclerView.Adapter<EventProvidersAdapter.Holder>() {

    private val prefs = context.omegaPrefs
    private val allProviders = ArrayList<ProviderItem>()
    private val handler = MAIN_EXECUTOR.handler

    private var dividerIndex = 0

    private val adapterItems = ArrayList<Item>()
    private val currentSpecs = ArrayList<String>()
    private val otherItems = ArrayList<ProviderItem>()
    private val divider = DividerItem()
    private var isDragging = false

    var itemTouchHelper: ItemTouchHelper? = null

    init {
        allProviders.addAll(getEventProviders(context).map { ProviderItem(ProviderInfo(it)) })
        currentSpecs.addAll(prefs.eventProviders.getAll())

        fillItems()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return when (viewType) {
            TYPE_HEADER -> createHolder(parent, R.layout.adapter_item_text, ::HeaderHolder)
            TYPE_ITEM -> createHolder(parent, R.layout.event_provider_dialog_item, ::ProviderHolder)
            TYPE_DIVIDER -> createHolder(
                parent,
                R.layout.event_providers_divider_item,
                ::DividerHolder
            )
            else -> throw IllegalArgumentException(
                "type must be either TYPE_TEXT, " +
                        "TYPE_PROVIDER or TYPE_DIVIDER"
            )
        }
    }

    override fun getItemCount() = adapterItems.count()

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(adapterItems[position])
    }

    override fun getItemViewType(position: Int): Int {
        return adapterItems[position].type
    }

    fun saveSpecs(): ArrayList<String> {
        val newSpecs = ArrayList<String>()
        val iterator = adapterItems.iterator()

        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item is ProviderItem) {
                newSpecs.add(item.info.name)
                Log.d("SmartspaceEventProvider", "adding item ${item.info.name}")
            }
            if (item is DividerItem) break
        }
        return newSpecs
    }

    private fun fillItems() {
        otherItems.clear()
        otherItems.addAll(allProviders)

        adapterItems.clear()
        adapterItems.add(HeaderItem())
        currentSpecs.forEach {
            val item = getAndRemoveOther(it)
            if (item != null) {
                adapterItems.add(item)
            }
        }
        dividerIndex = adapterItems.count()
        adapterItems.add(divider)
        adapterItems.addAll(otherItems)
    }

    private fun getAndRemoveOther(s: String): ProviderItem? {
        val iterator = otherItems.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.info.name == s) {
                iterator.remove()
                return item
            }
        }
        return null
    }

    private fun move(from: Int, to: Int): Boolean {
        if (to == from) return true
        move(from, to, adapterItems)
        dividerIndex = adapterItems.indexOf(divider)
        return true
    }

    private fun <T> move(from: Int, to: Int, list: MutableList<T>) {
        list.add(to, list.removeAt(from))
        notifyItemMoved(from, to)
    }

    private inline fun createHolder(parent: ViewGroup, resource: Int, creator: (View) -> Holder):
            Holder = creator(LayoutInflater.from(parent.context).inflate(resource, parent, false))

    abstract class Item {

        abstract val isStatic: Boolean
        abstract val type: Int
    }

    class HeaderItem : Item() {

        override val isStatic = true
        override val type = TYPE_HEADER
    }

    open class ProviderItem(val info: ProviderInfo) : Item() {

        override val isStatic = false
        override val type = TYPE_ITEM
    }

    class DividerItem : Item() {

        override val isStatic = true
        override val type = TYPE_DIVIDER
    }

    abstract class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        open fun bind(item: Item) {

        }
    }

    inner class ProviderInfo(val name: String) {

        val displayName = OmegaSmartSpaceController.getDisplayName(context, name)
    }

    class HeaderHolder(itemView: View) : Holder(itemView) {

        init {
            itemView.findViewById<TextView>(android.R.id.text1).apply {
                setText(R.string.enabled_events)
                setTextColor(context.omegaPrefs.accentColor)
            }
        }
    }

    inner class ProviderHolder(itemView: View) : Holder(itemView), View.OnClickListener,
        View.OnTouchListener {

        val title: TextView = itemView.findViewById(android.R.id.title)
        val summary: TextView = itemView.findViewById(android.R.id.summary)
        private val dragHandle: View = itemView.findViewById(R.id.drag_handle)
        private val packItem
            get() = adapterItems[bindingAdapterPosition] as? ProviderItem
                ?: throw IllegalArgumentException("item must be ProviderItem")

        init {
            itemView.setOnClickListener(this)
            dragHandle.setOnTouchListener(this)
        }

        override fun bind(item: Item) {
            val packItem = item as? ProviderItem
                ?: throw IllegalArgumentException("item must be ProviderItem")
            title.text = packItem.info.displayName
            itemView.isClickable = !packItem.isStatic
            dragHandle.isVisible = !packItem.isStatic
        }

        override fun onClick(v: View) {
            val item = packItem
            if (bindingAdapterPosition > dividerIndex) {
                adapterItems.removeAt(bindingAdapterPosition)
                adapterItems.add(1, item)
                notifyItemMoved(bindingAdapterPosition, 1)
                dividerIndex++
            } else {
                adapterItems.removeAt(bindingAdapterPosition)
                adapterItems.add(dividerIndex, item)
                notifyItemMoved(bindingAdapterPosition, dividerIndex)
                dividerIndex--
            }
            notifyItemChanged(dividerIndex)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (v == dragHandle && event.actionMasked == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(this)
            }
            return false
        }
    }

    inner class DividerHolder(itemView: View) : Holder(itemView) {

        val text: TextView = itemView.findViewById(android.R.id.text1)

        init {
            text.setTextColor(text.context.omegaPrefs.accentColor)
        }

        override fun bind(item: Item) {
            super.bind(item)
            if (isDragging || dividerIndex == adapterItems.size - 1) {
                text.setText(R.string.drag_to_disable_packs)
            } else {
                text.setText(R.string.drag_to_enable_packs)
            }
        }
    }

    inner class TouchHelperCallback : ItemTouchHelper.Callback() {

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
            handler.post { notifyItemChanged(dividerIndex) }
        }

        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return target.bindingAdapterPosition in 1..dividerIndex
        }

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val item = adapterItems[viewHolder.bindingAdapterPosition]
            val dragFlags = if (item.isStatic) 0 else ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

        }
    }

    companion object {

        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        const val TYPE_DIVIDER = 2

        fun getEventProviders(context: Context): List<String> {
            val list = ArrayList<String>()
            list.add(CalendarEventProvider::class.java.name)
            list.add(SmartSpaceDataWidget::class.java.name)
            list.add(NowPlayingProvider::class.java.name)
            list.add(NotificationUnreadProvider::class.java.name)
            list.add(BatteryStatusProvider::class.java.name)
            list.add(AlarmEventProvider::class.java.name)
            list.add(PersonalityProvider::class.java.name)
            if (context.omegaPrefs.showDebugInfo)
                list.add(FakeDataProvider::class.java.name)
            return list
        }
    }
}
