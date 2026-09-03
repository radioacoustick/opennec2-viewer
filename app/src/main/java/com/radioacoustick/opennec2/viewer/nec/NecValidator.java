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

package com.radioacoustick.opennec2.viewer.nec;

import static com.radioacoustick.opennec2.viewer.ui.utils.UiUtils.getFileNameFromUri;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.radioacoustick.opennec2.viewer.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class of preliminary validation of the source text
 * for the absence of cards that are necessary for the simulation
 */
public class NecValidator {

	/**
	 * Structure of the NEC-text validation result
	 */
	public static class ValidationResult {
		public final boolean isValid;
		public final boolean hasFrCard;
		@StringRes public final int errorMessageResId;
		@Nullable public final String errorArg;

		// Constructor for a successful result
		public ValidationResult(boolean isValid, boolean hasFrCard) {
			this(isValid, hasFrCard, 0, null);
		}

		// Constructor for a result with an error
		public ValidationResult(boolean isValid, boolean hasFrCard, @StringRes int errorMessageResId, @Nullable String errorArg) {
			this.isValid = isValid;
			this.hasFrCard = hasFrCard;
			this.errorMessageResId = errorMessageResId;
			this.errorArg = errorArg;
		}

		/**
		 * Formatting error strings for display in the user interface.
		 */
		@Nullable
		public String getFormattedErrorMessage(Context context) {
			if (errorMessageResId == 0 || context == null) return null;
			if (errorArg != null) {
				return context.getString(errorMessageResId) + " " + errorArg;
			}
			return context.getString(errorMessageResId);
		}
	}

	/**
	 * Returns the results of checking the NEC-text for the required cards.
	 * Safe to call from ViewModel or background threads (does not require Context).
	 *
	 * @param rawText Source NEC-text
	 * @return ValidationResult
	 */
	public static ValidationResult validateNecText(String rawText) {
		if (rawText == null || rawText.trim().isEmpty()) {
			return new ValidationResult(false, false);
		}

		boolean hasGeometryCard = false;
		boolean hasGeCard = false;
		boolean hasExCard = false;
		boolean hasFrCard = false;

		String cardRegex = "(?i)\\b(GW|GA|GH|GR|GS|GE|EX|EN|FR)\\b";
		Matcher matcher = Pattern.compile(cardRegex).matcher(rawText);

		while (matcher.find()) {
			String card = Objects.requireNonNull(matcher.group(1)).toUpperCase(Locale.US);
			switch (card) {
				case "GW": case "GA": case "GH": case "GR": case "GS":
					hasGeometryCard = true;
					break;
				case "GE":
					hasGeCard = true;
					break;
				case "EX":
					hasExCard = true;
					break;
				case "FR":
					hasFrCard = true;
					break;
			}
		}

		if (!hasGeometryCard) {
			return new ValidationResult(false, false, R.string.message_warning_missing_card, "GW/GA");
		}
		if (!hasGeCard) {
			return new ValidationResult(false, false, R.string.message_warning_missing_card, "GE");
		}
		if (!hasExCard) {
			return new ValidationResult(false, false, R.string.message_warning_missing_card, "EX");
		}

		return new ValidationResult(true, hasFrCard);
	}

	/**
	 * Quickly check the file URI before reading.
	 */
	public static boolean isNecFile(Context context, Uri uri) {
		if (uri == null || context == null) return false;

		// 1. Check by file extension (if name available)
		String fileName = getFileNameFromUri(context, uri);
		if (fileName != null) {
			String lower = fileName.toLowerCase(Locale.US);
			if (!lower.endsWith(".nec")) {
				return false;
			}
		}

		// 2. Content check (first few kilobytes)
		return containsNecHeaderCards(context, uri);
	}

	/**
	 * Scan the first N lines of a file for NEC base cards.
	 */
	private static boolean containsNecHeaderCards(Context context, Uri uri) {
		if (context == null || uri == null) return false;

		final int MAX_LINES_TO_CHECK = 40;
		int checkedLines = 0;

		try (InputStream is = context.getContentResolver().openInputStream(uri);
			  BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

			String line;
			while ((line = reader.readLine()) != null && checkedLines < MAX_LINES_TO_CHECK) {
				String trimmed = line.trim().toUpperCase(Locale.US);
				checkedLines++;

				if (trimmed.isEmpty()) continue;

				// Valid NEC file start or element markers
				if (trimmed.startsWith("CM ") || trimmed.startsWith("CE") ||
					 trimmed.startsWith("SY ") || trimmed.startsWith("GW ") ||
					 trimmed.startsWith("GA ") || trimmed.startsWith("GH ") ||
					 trimmed.startsWith("GE")   || trimmed.startsWith("EN")) {
					return true;
				}
			}
		} catch (Exception e) {
			return false;
		}

		return false;
	}

}