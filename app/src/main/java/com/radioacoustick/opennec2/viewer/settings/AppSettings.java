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
import android.os.LocaleList;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;

import com.radioacoustick.opennec2.viewer.R;

/**
 * A class for application settings.
 * Required parameters are saved between application launches.
 */
public class AppSettings {

	//TODO Parameters can be added as needed in future releases.
	/**
	 * Enum for choosing an app design theme
	 */
	public enum AppTheme {
		SYSTEM(R.string.theme_default, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
		LIGHT(R.string.theme_light, AppCompatDelegate.MODE_NIGHT_NO),
		DARK(R.string.theme_dark, AppCompatDelegate.MODE_NIGHT_YES);


		@StringRes
		public final int displayNameResId;
		private final int nightMode;

		AppTheme(@StringRes int displayNameResId, int nightMode) {
			this.displayNameResId = displayNameResId;
			this.nightMode = nightMode;
		}

		public String getDisplayName(Context context) {
			return context.getString(displayNameResId);
		}

		public int getNightMode() {
			return nightMode;
		}

	}

	// TODO Add other languages and translations of string resources
	/**
	 * Enum for choosing an app display language
	 */
	public enum AppLanguage {
		ENGLISH("en", "English");
		private final String code;
		private final String displayName;

		AppLanguage(String code, String displayName) {
			this.code = code;
			this.displayName = displayName;
		}

		public String getCode() {
			return code != null ? code : "en";
		}

		public String getDisplayName() {
			return displayName != null ? displayName : "English";
		}

		public static AppLanguage fromCode(String code) {
			if (code != null) {
				for (AppLanguage lang : values()) {
					if (lang.code.equalsIgnoreCase(code)) {
						return lang;
					}
				}
			}
			return ENGLISH;
		}

		public static AppLanguage detectSystemLanguage() {
			String sysLang;
			sysLang = LocaleList.getDefault().get(0).getLanguage();
			return fromCode(sysLang);
		}
	}

	// Radiation Pattern Resolution Scale Set
	public static final Integer[] RP_RESOLUTION_SCALE_SET = new Integer[]{1, 2, 3, 4, 5, 6, 8, 10};
	private AppTheme appTheme;      // Application theme
	private AppLanguage appLanguage;// Application language
	private float targetFrequency;  // The target frequency at which the radiation pattern is calculated
	private float systemImpedance;  // System impedance (eg 50.0 Ω or 75.0 Ω)
	private float sweepStartFreq;   // Start sweep frequency (MHz)
	private float sweepEndFreq;     // End sweep frequency (MHz)
	private int sweepPoints;        // Number of frequency sweep points
	private int resolScale;         // Radiation pattern resolution scale in degrees
	private boolean isOriginalControlCards;

	public AppSettings() {
		this.appTheme = AppTheme.SYSTEM;
		this.appLanguage = AppLanguage.detectSystemLanguage();
		this.targetFrequency = 298.0f;
		this.systemImpedance = 50.0f;
		this.sweepStartFreq = 144.0f;
		this.sweepEndFreq = 146.0f;
		this.sweepPoints = 11;
		this.resolScale = 3;
		this.isOriginalControlCards = true;
	}

	public AppLanguage getAppLanguage() {
		return appLanguage != null ? appLanguage : AppLanguage.ENGLISH;
	}

	public void setAppLanguage(AppLanguage appLanguage) {
		this.appLanguage = appLanguage;
	}

	public AppTheme getAppTheme() {
		return appTheme != null ? appTheme : AppTheme.SYSTEM;
	}

	public void setAppTheme(AppTheme appTheme) {
		this.appTheme = appTheme;
	}

	public float getSystemImpedance() {
		return systemImpedance;
	}

	public void setSystemImpedance(float systemImpedance) {
		this.systemImpedance = systemImpedance;
	}

	public float getSweepStartFreq() {
		return sweepStartFreq;
	}

	public void setSweepStartFreq(float sweepStartFreq) {
		this.sweepStartFreq = sweepStartFreq;
	}

	public float getSweepEndFreq() {
		return sweepEndFreq;
	}

	public void setSweepEndFreq(float sweepEndFreq) {
		this.sweepEndFreq = sweepEndFreq;
	}

	public int getSweepPoints() {
		return sweepPoints;
	}

	public void setSweepPoints(int sweepPoints) {
		this.sweepPoints = sweepPoints;
	}

	public boolean isOriginalControlCards() {
		return isOriginalControlCards;
	}

	public void setOriginalControlCards(boolean originalControlCards) {
		isOriginalControlCards = originalControlCards;
	}

	public float getTargetFrequency() {
		return targetFrequency;
	}

	public int getResolutionScale() {
		return resolScale;
	}

	public void setTargetFrequency(float targetFrequency) {
		this.targetFrequency = targetFrequency;
	}

	public void setResolutionScale(int resolScale) {
		this.resolScale = resolScale;
	}
}
