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

package com.radioacoustick.opennec2.viewer.ui.graphs;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

/**
 * Help class for preparing simulation data for displaying frequency sweep graphs
 */
public class GraphsHelper {

	/**
	 * A method for preparing frequency poins data along the X-axis.
	 * The nec2++ resulting frequency list is presented in Hertz.
	 * It also always contains a default value of around 300 MHz (1 meter wavelength),
	 * hard-coded by the nec2++ engine. This value must be filtered and excluded.
	 */
	private static ArrayList<Float> formatFrequencies(float[] frequencies, int len) {
		ArrayList<Float> freqMHZ_list = new ArrayList<>();
		for (int i = 0; i < len; i++) {
			float mhz = frequencies[i] / 1_000_000f;

			// Scenario 1: Calculation without sweeping (exactly 2 points, specified by the FR card and default)
			if (len == 2 && i == 1) {
				// If the second point is the default ~300 MHz, skip it
				if (mhz >= 299.0f && mhz <= 301.0f) {
					continue;
				}
			}
			// Scenario 2: Full sweep (more than 2 points)
			if (len > 2 && i == len - 1) {
				float mhzPrev1 = frequencies[i - 1] / 1_000_000f;
				float mhzPrev2 = frequencies[i - 2] / 1_000_000f;

				// Get the average step of the frequency sweep
				float normalStep = mhzPrev1 - mhzPrev2;
				float lastStep = mhz - mhzPrev1;

				// If the step before the last point has changed abnormally, this is the default point,
				// the system tail of the NEC ++ engine. We skip it.
				if (Math.abs(lastStep - normalStep) > (normalStep * 0.1f)) {
					continue;
				}
			}
			freqMHZ_list.add(mhz);
		}
		return freqMHZ_list;
	}

	/**
	 * Method for preparing data for the X and Y axes for displaying a single graph
	 */
	public static void displayGraph(MaterialChartRenderer chartRenderer, float[] frequencies, float[] values, String label, boolean isSWR) {
		List<Entry> entries = new ArrayList<>();
		int len = frequencies.length;
		ArrayList<Float> freqMHZ = formatFrequencies(frequencies, len);
		for (int i = 0; i < freqMHZ.size(); i++) {
				 entries.add(new Entry(freqMHZ.get(i), values[i]));
		}
		chartRenderer.displayData(entries, label, isSWR);
	}

	/**
	 * Overloaded method for preparing data for the X and Y axes for displaying a dual graph.
	 */
	public static void displayGraph(MaterialChartRenderer chartRenderer, float[] frequencies, float[] values_R, float[] values_X, String labelR, String labelX) {
		List<Entry> entriesR = new ArrayList<>();
		List<Entry> entriesX = new ArrayList<>();
		int len = frequencies.length;
		ArrayList<Float> freqMHZ = formatFrequencies(frequencies, len);
		for (int i = 0; i < freqMHZ.size(); i++) {
			entriesR.add(new Entry(freqMHZ.get(i), values_R[i]));
			entriesX.add(new Entry(freqMHZ.get(i), values_X[i]));
		}
		chartRenderer.displayDualData(entriesR, entriesX, labelR, labelX);
	}
}
