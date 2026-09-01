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

package com.radioacoustick.opennec2.viewer;

import android.annotation.SuppressLint;
import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.radioacoustick.opennec2.viewer.settings.AppSettings;
import com.radioacoustick.opennec2.viewer.settings.SettingsManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class M_Application extends Application {

	// Single instance of application settings
	private volatile AppSettings settings;
	private static M_Application instance;
	private final ExecutorService diskIO = Executors.newSingleThreadExecutor();

	@SuppressLint("WrongConstant")
	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
		// Loading application settings
		settings = SettingsManager.getSettings(this);

		// Setting the theme via AppCompatDelegate
		AppCompatDelegate.setDefaultNightMode(settings.getAppTheme().getNightMode());

		// Setting the language via AppCompatDelegate
		String langCode = settings.getAppLanguage().getCode();
		LocaleListCompat appLocales = LocaleListCompat.forLanguageTags(langCode);

		// Checks if the current locale is different to avoid unnecessary repaints
		if (!AppCompatDelegate.getApplicationLocales().equals(appLocales)) {
			AppCompatDelegate.setApplicationLocales(appLocales);
		}

		// Loading native Filament JNI libraries to display 3D antenna geometry
		com.google.android.filament.Filament.init();
	}

	/**
	 * Getting application settings
	 */
	public static AppSettings getSettings() {
		if (instance != null) {
			if (instance.settings == null) {
				synchronized (M_Application.class) {
					if (instance.settings == null) {
						instance.settings = SettingsManager.getSettings(instance);
					}
				}
			}
			return instance.settings;
		}
		// Fallback in case of a call before Application initialization
		return new AppSettings();
	}

	/**
	 * Saving application settings
	 */
	public static void saveSettings(AppSettings newSettings) {
		if (instance != null) {
			instance.settings = newSettings;
			// Save in a background thread
			instance.diskIO.execute(() -> {
				SettingsManager.saveSettings(instance, newSettings);
			});
		}
	}

}
