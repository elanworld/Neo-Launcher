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

import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.saggitt.omega.BlankActivity
import com.saggitt.omega.omegaApp
import com.saggitt.omega.util.*

@Keep
class SmartSpaceDataWidget(controller: OmegaSmartSpaceController) :
    OmegaSmartSpaceController.DataProvider(controller) {

    private val prefs = Utilities.getOmegaPrefs(context)
    private val smartspaceWidgetHost = SmartspaceWidgetHost()
    private var smartspaceView: SmartspaceWidgetHostView? = null
    private val widgetIdPref = prefs::smartspaceWidgetId
    private val providerInfo = getSmartspaceWidgetProvider(context)
    private var isWidgetBound = false
    private val pendingIntentTagId =
        context.resources.getIdentifier("pending_intent_tag", "id", "android")

    init {
        bindWidget { }
    }

    private fun createBindOptions(): Bundle {
        val idp = LauncherAppState.getIDP(context)
        val opts = Bundle()
        val size = AppWidgetResizeFrame.getWidgetSizeRanges(
            context,
            idp.numColumns, 1, null
        )
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.left)
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.top)
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.right)
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.bottom)
        return opts
    }

    private fun bindWidget(onSetupComplete: () -> Unit) {
        val widgetManager = AppWidgetManager.getInstance(context)

        var widgetId = widgetIdPref.get()
        val widgetInfo = widgetManager.getAppWidgetInfo(widgetId)
        isWidgetBound = widgetInfo != null && widgetInfo.provider == providerInfo.provider
        val opts: Bundle = createBindOptions()

        val oldWidgetId = widgetId
        if (!isWidgetBound) {
            if (widgetId > -1) {
                // widgetId is already bound and its not the correct provider. reset host.
                smartspaceWidgetHost.deleteHost()
            }

            widgetId = smartspaceWidgetHost.allocateAppWidgetId()
            isWidgetBound = widgetManager.bindAppWidgetIdIfAllowed(
                widgetId, providerInfo.profile, providerInfo.provider, opts
            )
        }

        if (isWidgetBound) {
            smartspaceView = smartspaceWidgetHost.createView(
                context,
                widgetId,
                providerInfo
            ) as SmartspaceWidgetHostView
            smartspaceWidgetHost.startListening()
            onSetupComplete()
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            BlankActivity.startActivityForResult(context, bindIntent, 1028, 0) { resultCode, _ ->
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    bindWidget(onSetupComplete)
                } else {
                    smartspaceWidgetHost.deleteAppWidgetId(widgetId)
                    widgetId = -1
                    widgetIdPref.set(-1)
                    onSetupComplete()
                }
            }
        }

        if (oldWidgetId != widgetId) {
            widgetIdPref.set(widgetId)
        }
    }

    override fun requiresSetup(): Boolean {
        return !isWidgetBound
    }

    override fun startSetup(onFinish: (Boolean) -> Unit) {
        bindWidget {
            onFinish(isWidgetBound)
        }
    }

    override fun stopListening() {
        super.stopListening()

        smartspaceWidgetHost.stopListening()
    }

    private fun updateData(
        weatherIcon: Bitmap?,
        temperature: TextView?,
        cardIcon: Bitmap?,
        title: TextView?,
        subtitle: TextView?,
        subtitle2: TextView?
    ) {
        val weather = parseWeatherData(weatherIcon, temperature)
        val card = if (cardIcon != null && title != null && subtitle != null) {
            val pendingIntent = getPendingIntent(title.parent.parent.parent as? View)
            val ttl =
                title.text.toString() + if (subtitle2 != null) subtitle.text.toString() else ""
            val sub = subtitle2 ?: subtitle
            OmegaSmartSpaceController.CardData(
                cardIcon, ttl, title.ellipsize,
                sub.text.toString(), sub.ellipsize,
                pendingIntent = pendingIntent
            )
        } else {
            null
        }
        updateData(weather, card)
    }

    private fun parseWeatherData(
        weatherIcon: Bitmap?,
        temperatureText: TextView?
    ): OmegaSmartSpaceController.WeatherData? {
        val temperature = temperatureText?.text?.toString()
        return parseWeatherData(weatherIcon, temperature, getPendingIntent(temperatureText))
    }

    private fun getPendingIntent(view: View?): PendingIntent? {
        return view?.getTag(pendingIntentTagId) as? PendingIntent
    }

    inner class SmartspaceWidgetHost : AppWidgetHost(context, 1027) {

        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView {
            return SmartspaceWidgetHostView(context)
        }
    }

    inner class SmartspaceWidgetHostView(context: Context) : AppWidgetHostView(context) {

        @Suppress("UNCHECKED_CAST")
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)

            val childs = getAllChilds()
            val texts =
                childs.filterIsInstance<TextView>().filter { !TextUtils.isEmpty(it.text) }
            val images = childs.filterIsInstance<ImageView>()

            var weatherIconView: ImageView? = null
            var cardIconView: ImageView? = null
            var title: TextView? = null
            var subtitle: TextView? = null
            var subtitle2: TextView? = null
            var temperatureText: TextView? = null
            if (texts.isEmpty()) return
            if (images.isNotEmpty()) {
                weatherIconView = images.last()
                temperatureText = texts.last()
            }
            if (images.size > 1) {
                cardIconView = images.first()
                title = texts[0]
                if (texts.size > 2) {
                    subtitle = texts[1]
                }
                if (texts.size > 3) {
                    subtitle2 = texts[2]
                }
            }
            updateData(
                extractBitmap(weatherIconView),
                temperatureText,
                extractBitmap(cardIconView),
                title,
                subtitle,
                subtitle2
            )
        }
    }

    private fun extractBitmap(imageView: ImageView?): Bitmap? {
        return (imageView?.drawable as? BitmapDrawable)?.bitmap
    }

    companion object {

        private const val TAG = "SmartspaceDataWidget"
        private const val smartspaceComponent =
            "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"

        private val smartspaceProviderComponent =
            ComponentName(Config.GOOGLE_QSB, smartspaceComponent)

        fun getSmartspaceWidgetProvider(context: Context): AppWidgetProviderInfo {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providers =
                appWidgetManager.installedProviders.filter { it.provider == smartspaceProviderComponent }
            val provider = providers.firstOrNull()
            Log.d(TAG, "Provider $provider")
            if (provider != null) {
                return provider
            } else {
                runOnMainThread {
                    val foreground = context.omegaApp.activityHandler.foregroundActivity
                        ?: context
                    if (foreground is AppCompatActivity) {
                        AlertDialog.Builder(foreground)
                            .setTitle(R.string.failed)
                            .setMessage(R.string.smartspace_widget_provider_not_found)
                            .setNegativeButton(android.R.string.cancel, null).create().apply {
                                show()
                                applyAccent()
                            }

                    }
                }
                throw RuntimeException("smartspace widget not found")
            }
        }

        fun parseWeatherData(
            weatherIcon: Bitmap?,
            temperature: String?,
            intent: PendingIntent? = null
        ): OmegaSmartSpaceController.WeatherData? {
            return if (weatherIcon != null && temperature != null) {
                try {
                    val value = temperature.substring(
                        0,
                        temperature.indexOfFirst { (it < '0' || it > '9') && it != '-' }).toInt()
                    OmegaSmartSpaceController.WeatherData(
                        weatherIcon, Temperature(
                            value, when {
                                temperature.contains("C") -> Temperature.Unit.Celsius
                                temperature.contains("F") -> Temperature.Unit.Fahrenheit
                                temperature.contains("K") -> Temperature.Unit.Kelvin
                                else -> throw IllegalArgumentException("only supports C, F and K")
                            }
                        ), pendingIntent = intent
                    )
                } catch (e: NumberFormatException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
            } else {
                null
            }
        }
    }
}
