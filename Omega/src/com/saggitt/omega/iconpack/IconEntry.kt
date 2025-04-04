/*
 *  This file is part of Omega Launcher
 *  Copyright (c) 2021   Omega Launcher Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saggitt.omega.iconpack

import com.android.launcher3.util.ComponentKey

data class IconEntry(
    val packPackageName: String,
    val name: String,
    val type: IconType
) {
    var componentKey: ComponentKey? = null

    fun resolveDynamicCalendar(day: Int): IconEntry {
        if (type != IconType.Calendar) throw IllegalStateException("type is not calendar")
        return IconEntry(packPackageName, "$name${day + 1}", IconType.Normal)
    }
}

enum class IconType {
    Normal, Calendar
}
