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

package com.radioacoustick.opennec2.viewer.ui.utils;

import android.content.Context;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * A class for implementing edge-to-edge support on Android 10+
 */
public class EdgeToEdgeHelper {

	public static void enableEdgeToEdge(ComponentActivity activity) {
		androidx.activity.EdgeToEdge.enable(activity);
	}

	/**
	 * Method for handling indents
	 * taking into account the presence of a toolbar and bottom navigation menu
	 */
	public static void applyInsets(Context context, View target, View toolbar, View bootomNav) {

		// 1. Moves the Toolbar (or AppBarLayout) to the bottom border of the status bar
		if (toolbar != null) {
			ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
				Insets systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
				v.setPadding(
					 v.getPaddingLeft(),
					 systemBarsInsets.top,
					 v.getPaddingRight(),
					 v.getPaddingBottom()
				);
				return windowInsets;
			});
		}

		// 2. Raise BottomNavigationView above system navigation buttons in Android 8 and below
		if (bootomNav != null) {
			ViewCompat.setOnApplyWindowInsetsListener(bootomNav, (v, windowInsets) -> {
				Insets systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
				v.setPadding(
					 v.getPaddingLeft(),
					 v.getPaddingTop(),
					 v.getPaddingRight(),
					 systemBarsInsets.bottom
				);
				return windowInsets;
			});
		}

		// 3. Protecting the side edges of the main container (for waterfall screens and cutouts)
		if (target != null) {
			ViewCompat.setOnApplyWindowInsetsListener(target, (v, windowInsets) -> {
				Insets systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

				// If there is no bottom panel, push the bottom inset into the container itself
				// so that the content does not go under the system buttons.
				int baseBottomInsert = (bootomNav == null || bootomNav.getVisibility() == View.GONE)
					 ? systemBarsInsets.bottom : 0;

				v.setPadding(
					 systemBarsInsets.left,
					 0, // 0 on top, since the Toolbar has already moved
					 systemBarsInsets.right,
					 baseBottomInsert
				);
				return windowInsets;
			});
		}
	}
}