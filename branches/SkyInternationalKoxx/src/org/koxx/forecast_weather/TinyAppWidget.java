/*
 * Copyright (C) 2009 Jeff Sharkey, http://jsharkey.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.koxx.forecast_weather;

import org.koxx.forecast_weather.R;
import org.koxx.forecast_weather.ForecastProvider.AppWidgets;
import org.koxx.forecast_weather.ForecastProvider.AppWidgetsColumns;
import org.koxx.forecast_weather.ForecastProvider.ForecastsColumns;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Definition of a tiny-sized forecast widget. Passes any requested updates to
 * {@link UpdateService} to perform on background thread and prevent ANR.
 */
public class TinyAppWidget extends AppWidgetProvider {
	private static final String TAG = "TinyAppWidget";

	private static final String[] PROJECTION_APPWIDGETS = new String[] { AppWidgetsColumns.TEMP_UNIT,
			AppWidgetsColumns.CURRENT_TEMP, 
			AppWidgetsColumns.SKIN, };

	private static final int COL_TEMP_UNIT = 0;
	private static final int COL_CURRENT_TEMP = 1;
	private static final int COL_SKIN = 2;
	

	private static final String[] PROJECTION_FORECASTS = new String[] { ForecastsColumns.TEMP_HIGH,
			ForecastsColumns.TEMP_LOW, ForecastsColumns.ICON_URL, };

	private static final int COL_TEMP_HIGH = 0;
	private static final int COL_TEMP_LOW = 1;
	private static final int COL_ICON_URL = 2;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// If no specific widgets requested, collect list of all
		if (appWidgetIds == null) {
			appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, TinyAppWidget.class));
		}

		// Request update for these widgets and launch updater service
		UpdateService.requestUpdate(appWidgetIds);
		context.startService(new Intent(context, UpdateService.class));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		ContentResolver resolver = context.getContentResolver();
		for (int appWidgetId : appWidgetIds) {
			Log.d(TAG, "Deleting appWidgetId=" + appWidgetId);
			Uri appWidgetUri = ContentUris.withAppendedId(AppWidgets.CONTENT_URI, appWidgetId);
			resolver.delete(appWidgetUri, null, null);
		}
	}

	/**
	 * Build an update for the given tiny widget. Should only be called from a
	 * service or thread to prevent ANR during database queries.
	 */
	public static RemoteViews buildUpdate(Context context, Uri appWidgetUri) {
		Log.d(TAG, "Building tiny widget update");

		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_tiny);

		boolean daytime = ForecastUtils.isDaytime();
		boolean forecastFilled = false;

		ContentResolver resolver = context.getContentResolver();
		Resources res = context.getResources();

		String temp_unit_str = "";
		int current_temp = 0;

		String skinName = "";
		boolean useSkin = false;
		
		Cursor cursor = null;

		// Pull out desired temperature units
		try {
			cursor = resolver.query(appWidgetUri, PROJECTION_APPWIDGETS, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				temp_unit_str = cursor.getString(COL_TEMP_UNIT);

				current_temp = cursor.getInt(COL_CURRENT_TEMP);

				skinName = cursor.getString(COL_SKIN);

				if (skinName.equals(""))
					useSkin = false;
				else
					useSkin = true;
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		// Find the forecast nearest now and build update using it
		try {
			Uri forecastAtUri = Uri.withAppendedPath(appWidgetUri, AppWidgets.TWIG_FORECAST_AT);
			Uri forecastAtNowUri = Uri.withAppendedPath(forecastAtUri, Long.toString(System.currentTimeMillis()));
			cursor = resolver.query(forecastAtNowUri, PROJECTION_FORECASTS, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {

				String icon_url = cursor.getString(COL_ICON_URL);

				Bitmap iconResource = ForecastUtils.getIconBitmapForForecast(context, icon_url, daytime, useSkin, skinName);

				int tempHigh = cursor.getInt(COL_TEMP_HIGH);
				int tempLow = cursor.getInt(COL_TEMP_LOW);

				views.setImageViewBitmap(R.id.icon, iconResource);

				views.setTextViewText(R.id.current_temp, ((Integer) current_temp).toString() + temp_unit_str);

				if (tempHigh == Integer.MIN_VALUE || tempLow == Integer.MIN_VALUE) {
					views.setViewVisibility(R.id.temp_block2, View.GONE);
				} else {
					views.setViewVisibility(R.id.temp_block2, View.VISIBLE);
					views.setTextViewText(R.id.high, ((Integer) tempHigh).toString() + temp_unit_str);
					views.setTextViewText(R.id.low, ((Integer) tempLow).toString() + temp_unit_str);
				}

				forecastFilled = true;
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		// If not filled correctly, show error message and hide other fields
		if (!forecastFilled) {
			views = new RemoteViews(context.getPackageName(), R.layout.widget_loading);
			views.setTextViewText(R.id.loading, res.getString(R.string.widget_error));
		}

		// Connect click intent to launch details
		Intent detailIntent = new Intent(context, DetailsActivity.class);
		detailIntent.setData(appWidgetUri);

		PendingIntent pending = PendingIntent.getActivity(context, 0, detailIntent, 0);

		views.setOnClickPendingIntent(R.id.widget, pending);

		return views;
	}
}
