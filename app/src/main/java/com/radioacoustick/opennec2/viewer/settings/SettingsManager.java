// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Valery Kustarev (https://github.com/radioacoustick)
/*
 * This file is part of Open NEC2 Viewer.
 *
 * Open NEC2 Viewer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open NEC2 Viewer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open NEC2 Viewer. If not, see <https://www.gnu.org/licenses/>.
 */

package com.radioacoustick.opennec2.viewer.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;

/**
 * Class for managing, saving, and reading application settings
 */
public class SettingsManager {

	private static final String PREF_NAME = "opennec2_prefs";
	private static final String KEY_APP_SETTINGS = "key_app_settings_json";

	// Cache the app settings object in memory for instant access
	private static AppSettings currentSettings;

	/**
	 * Method for reading app settings
	 */
	public static AppSettings getSettings(Context context) {
		if (currentSettings == null) {
			currentSettings = loadFromPrefs(context);
		}
		return currentSettings;
	}

	/**
	 * Loading saved application settings
	 */
	public static AppSettings loadFromPrefs(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
		String json = prefs.getString(KEY_APP_SETTINGS, null);

		if (json == null || json.isEmpty()) {
			return new AppSettings();
		}

		try {
			Gson gson = new Gson();
			AppSettings settings = gson.fromJson(json, AppSettings.class);
			return settings != null ? settings : new AppSettings();
		} catch (Exception e) {
			Log.e("SettingsManager", "Error when reading dettings", e);
			// If the JSON data is corrupted or missing, default settings are returned.
			return new AppSettings();
		}
	}

	/**
	 * Saving application settings between application launches
	 */
	public static void saveSettings(Context context, AppSettings settings) {
		currentSettings = settings;
		SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

		Gson gson = new Gson();
		String json = gson.toJson(settings);

		prefs.edit().putString(KEY_APP_SETTINGS, json).apply();
	}
}
