package com.localplayer.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Shared behaviour: widgets only react to launcher callbacks; all real updates come from [WidgetController]. */
abstract class BaseWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) = WidgetController.sync(context)
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = WidgetController.sync(context)
    override fun onAppWidgetOptionsChanged(context: Context, m: AppWidgetManager, id: Int, newOptions: android.os.Bundle) = WidgetController.sync(context)
    override fun onDisabled(context: Context) = WidgetController.sync(context)
}

/** 2x1: artwork, title, artist, previous / play-pause / next. */
class CompactWidgetProvider : BaseWidgetProvider()

/** 2x2: large artwork, title, artist, previous / play-pause / next. (No progress bar: it would need periodic updates.) */
class ExpandedWidgetProvider : BaseWidgetProvider()
