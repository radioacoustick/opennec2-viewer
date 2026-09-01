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

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

/**
 * Base class for storing simulation results
 */
public class NecResult {

	// --- RadiationPattern result fields ---
	public float[] anglesPhi;
	public float[] gainsPhi;
	public float[] anglesTheta;
	public float[] gainsTheta;
	public float maxGain;
	public float frontToBack;
	public float phi;
	public float theta;

	// --- Frequency sweep result fields ---
	public float[] frequencies;
	public float[] resistance;
	public float[] reactance;
	public float[] gainsF;
	public float[] frontToBackF;

	// TODO Requires editing when data changes on the nec2core service side
	// --- Internal DTO structures that are completely identical to C++ JSON (nec2core_jni.cpp) ---
	public static class RawJsonResponse {
		@Keep
		@SerializedName("pattern")
		public PatternDto pattern;

		@Keep
		@SerializedName("sweep")
		public SweepDto sweep;
	}

	public static class PatternDto {
		@Keep
		@SerializedName("max_gain")
		public float maxGain;

		@Keep
		@SerializedName("front_to_back")
		public float frontToBack;

		@Keep
		@SerializedName("phi")
		public float phi;

		@Keep
		@SerializedName("theta")
		public float theta;

		@Keep
		@SerializedName("anglesTheta")
		public float[] anglesTheta;

		@Keep
		@SerializedName("gainsTheta")
		public float[] gainsTheta;

		@Keep
		@SerializedName("anglesPhi")
		public float[] anglesPhi;

		@Keep
		@SerializedName("gainsPhi")
		public float[] gainsPhi;
	}

	public static class SweepDto {
		@Keep
		@SerializedName("frequencies")
		public float[] frequencies;

		@Keep
		@SerializedName("resistance")
		public float[] resistance;

		@Keep
		@SerializedName("reactance")
		public float[] reactance;

		@Keep
		@SerializedName("gains_f")
		public float[] gainsF;

		@Keep
		@SerializedName("front_back")
		public float[] frontBackF;
	}

	/**
	 * Parses a JSON string from nec2++ and turns it into a NecResult object
	 *
	 * @param jsonString input JSON string from nec2++ service
	 * @param gson com.google.gson.Gson Object to parse input
	 * @return NecResult Object
	 */
	public static NecResult parseFromJson(String jsonString, com.google.gson.Gson gson) {
		if (jsonString == null || jsonString.isEmpty()) {
			return null;
		}

		RawJsonResponse raw = gson.fromJson(jsonString, RawJsonResponse.class);
		if (raw == null) return null;

		NecResult result = new NecResult();

		if (raw.pattern != null) {
			result.maxGain = raw.pattern.maxGain;
			result.frontToBack = raw.pattern.frontToBack;
			result.phi = raw.pattern.phi;
			result.theta = raw.pattern.theta;
			result.anglesTheta = raw.pattern.anglesTheta;
			result.gainsTheta = raw.pattern.gainsTheta;
			result.anglesPhi = raw.pattern.anglesPhi;
			result.gainsPhi = raw.pattern.gainsPhi;
		}

		if (raw.sweep != null) {
			result.frequencies = raw.sweep.frequencies;
			result.resistance = raw.sweep.resistance;
			result.reactance = raw.sweep.reactance;
			result.gainsF = raw.sweep.gainsF;
			result.frontToBackF = raw.sweep.frontBackF;
		}

		return result;
	}
}
