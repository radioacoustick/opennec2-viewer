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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * A helper class for binding TextInputEditText elements to float parameters.
 * Automates formatting, range validation, and value saving.
 */
public class FloatInputSaveHelper {

	// Formatter: '#' signs show fractional part only if there is one (up to 4 characters)
	public static final DecimalFormat FLOAT_FORMAT;

	static {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
		symbols.setDecimalSeparator('.'); // Ensures a point instead of a comma
		FLOAT_FORMAT = new DecimalFormat("0.####", symbols);
	}

	public interface OnValueSaveListener {
		void onSave(float value);
	}

	/**
	 * Binds autosaving of the TextInputEditText value without range restriction.
	 *
	 * @param editText     TextInputEditText input field
	 * @param initialValue Initial value to display
	 * @param listener     Listener called when a valid number change occurs
	 */
	public static void bind(TextInputEditText editText, float initialValue, OnValueSaveListener listener) {
		bind(editText, initialValue, -Float.MAX_VALUE, Float.MAX_VALUE, listener);
	}

	/**
	 * Binds autosaving of the TextInputEditText value with validation of the minimum and maximum values.
	 *
	 * @param editText     TextInputEditText input field
	 * @param initialValue Initial value to display
	 * @param minValue     Minimum allowable value
	 * @param maxValue     Maximum allowable value
	 * @param listener     Listener called when a valid number change occurs
	 */
	public static void bind(TextInputEditText editText, float initialValue, float minValue, float maxValue, OnValueSaveListener listener) {

		if (initialValue != 0f) {
			editText.setText(FLOAT_FORMAT.format(initialValue));
		} else {
			editText.setText("0");
		}

		Runnable processSave = () -> {
			String text = editText.getText() != null ? editText.getText().toString().trim() : "";
			if (!text.isEmpty() && !text.equals(".")) {
				try {
					float val = Float.parseFloat(text);
					if (val >= minValue && val <= maxValue) {
						listener.onSave(val);
					}
				} catch (NumberFormatException ignored) {
					// Ignore intermediate input states
				}
			}
		};

		// 1. Tracking text input
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				processSave.run();
			}
		});

		// 2. Tracking loss of focus
		editText.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) processSave.run();
		});

		// 3. Handling a Keyboard Action (IME Action)
		editText.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
				processSave.run();
				editText.clearFocus();
			}
			return false;
		});
	}
}
