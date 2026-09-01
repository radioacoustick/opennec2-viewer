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

package com.radioacoustick.opennec2.viewer.math;

/**
 * Helper class for calculating antenna parameters not available from the nec2++ engine
 */
public class AntennaMath {

	/**
	 * The method calculates the half-power width of the main lobe (HPBW) in degrees
	 * for a normalized array of gains (from 0.0 to 1.0, where the peak is 1.0).
	 *
	 * @param angles Array of angles (in degrees)
	 * @param gains  Normalized array of gain values (from 0.0 to 1.0, where maximum = 1.0)
	 * @return HPBW in degrees, or -1.0f in case of error
	 */
	public static float calculateHPBW(float[] angles, float[] gains) {
		if (angles == null || gains == null || angles.length != gains.length || angles.length < 2) {
			return -1.0f;
		}

		float dynamicRange = 40.0f;
		int n = gains.length;

		// Step 1. Find the absolute maximum index (it should be equal to 1.0f)
		int maxIdx = 0;
		float maxGain = gains[0];
		for (int i = 1; i < n; i++) {
			if (gains[i] > maxGain) {
				maxGain = gains[i];
				maxIdx = i;
			}
		}

		// Step 2. Set the target half power level.
		// If the gains array is linear power: target = 0.5f
		// If the gains array is the normalized field voltage: target = 0.707f
		float targetGain = 1.0f - (3.0f / dynamicRange);
		;

		// Step 3. Looking for the left boundary, moving to the left of the maximum (circular bypass)
		int leftIdx = -1;
		for (int i = 1; i < n; i++) {
			int curr = (maxIdx - i + n) % n;
			if (gains[curr] < targetGain) {
				leftIdx = curr;
				break;
			}
		}

		// Step 4. Looking for the right boundary, moving to the right of the maximum (circular bypass)
		int rightIdx = -1;
		for (int i = 1; i < n; i++) {
			int curr = (maxIdx + i) % n;
			if (gains[curr] < targetGain) {
				rightIdx = curr;
				break;
			}
		}

		if (leftIdx == -1 || rightIdx == -1) {
			return 360.0f;
		}

		// Step 5: Linear Interpolation of Angles for Professional Accuracy
		float leftAngle = interpolateAngle(leftIdx, (leftIdx + 1) % n, targetGain, angles, gains, n);
		float rightAngle = interpolateAngle(rightIdx, (rightIdx - 1 + n) % n, targetGain, angles, gains, n);

		// Step 6. Calculating the angular difference taking into account the circular shift
		float hpbw = rightAngle - leftAngle;

		if (hpbw < 0) {
			hpbw += 360.0f;
		}
		if (hpbw > 360.0f) {
			hpbw -= 360.0f;
		}

		return hpbw;
	}

	/**
	 * An auxiliary method for interpolating the exact angle between two grid points
	 */
	private static float interpolateAngle(int idxUnder, int idxOver, float target, float[] angles, float[] gains, int n) {
		float g1 = gains[idxUnder]; // Value below target (e.g. 0.45)
		float g2 = gains[idxOver];  // Value above target (e.g. 0.55)

		float a1 = angles[idxUnder];
		float a2 = angles[idxOver];

		// Processing a transition through a junction of an array of angles (for example, -180 and 180)
		float angleDiff = a2 - a1;
		if (angleDiff > 180.0f) {
			a2 -= 360.0f;
		} else if (angleDiff < -180.0f) {
			a2 += 360.0f;
		}

		if (Math.abs(g2 - g1) < 0.0001f) {
			return a1;
		}

		float t = (target - g1) / (g2 - g1);
		float interpolated = a1 + t * (a2 - a1);

		// Normalizing the resulting angle back to the circular range
		if (interpolated < -180.0f) interpolated += 360.0f;
		if (interpolated > 180.0f) interpolated -= 360.0f;

		return interpolated;
	}

	/**
	 * The method calculates an array of VSWR values for a given array of impedance values
	 *
	 * @param rValues Resistance values array
	 * @param xValues Reactance values array
	 * @param z0      Load/Source Resistance (System Impedance)
	 * @return VSWR values array
	 */
	public static float[] calculateSwr(float[] rValues, float[] xValues, float z0) {

		if (rValues == null || xValues == null || rValues.length != xValues.length) {
			return new float[0];
		}

		int length = rValues.length;
		float[] swrValues = new float[length];
		float z0_sq = z0 * z0;

		for (int i = 0; i < length; i++) {
			float r = rValues[i];
			float x = xValues[i];

			float x_sq = x * x;
			// Calculation of the numerator (zn) and denominator (zd) of the reflection coefficient
			float zn = (r - z0) * (r - z0) + x_sq;
			float zd = (r + z0) * (r + z0) + x_sq;

			float swr = 99.0f; // Default value for infinite VSWR

			if (zd > 0.001f) {
				float reflectionCoeff = (float) Math.sqrt(zn / zd);
				if (reflectionCoeff < 0.999f) {
					swr = (1.0f + reflectionCoeff) / (1.0f - reflectionCoeff);
				}
			}

			swrValues[i] = swr;
		}

		return swrValues;
	}
}
