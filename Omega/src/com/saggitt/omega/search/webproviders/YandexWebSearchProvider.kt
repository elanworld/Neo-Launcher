/*
 *  Copyright (c) 2020 Omega Launcher
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.saggitt.omega.search.webproviders

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.android.launcher3.R
import com.saggitt.omega.search.WebSearchProvider
import com.saggitt.omega.util.locale

class YandexWebSearchProvider(context: Context) : WebSearchProvider(context) {
    override val iconRes: Int
        get() = R.drawable.ic_yandex
    override val icon: Drawable
        get() = ResourcesCompat.getDrawable(context.resources, iconRes, null)!!

    override val packageName: String
        get() = "https://yandex.com/search/?text=%s"

    override val suggestionsUrl: String
        get() = "https://suggest.yandex.com/suggest-ff.cgi?part=%s&uil=" + context.locale.language

    override val name: String
        get() = context.getString(R.string.web_search_yandex)
}