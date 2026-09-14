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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Base class for storing simulation results
 */
public class NecResult {

	// --- RadiationPattern result fields ---
	public float[] anglesPhi = new float[0];
	public float[] gainsPhi = new float[0];
	public float[] anglesTheta = new float[0];
	public float[] gainsTheta = new float[0];
	public float maxGain;
	public float frontToBack;
	public float phi;
	public float theta;

	// --- Frequency sweep result fields ---
	public float[] frequencies = new float[0];
	public float[] resistance = new float[0];
	public float[] reactance = new float[0];
	public float[] gainsF = new float[0];
	public float[] frontToBackF = new float[0];

	// --- Internal DTO structures with JsonElement for arrays ---
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
		public JsonElement anglesTheta;

		@Keep
		@SerializedName("gainsTheta")
		public JsonElement gainsTheta;

		@Keep
		@SerializedName("anglesPhi")
		public JsonElement anglesPhi;

		@Keep
		@SerializedName("gainsPhi")
		public JsonElement gainsPhi;
	}

	public static class SweepDto {
		@Keep
		@SerializedName("frequencies")
		public JsonElement frequencies;

		@Keep
		@SerializedName("resistance")
		public JsonElement resistance;

		@Keep
		@SerializedName("reactance")
		public JsonElement reactance;

		@Keep
		@SerializedName("gains_f")
		public JsonElement gainsF;

		@Keep
		@SerializedName("front_back")
		public JsonElement frontBackF;
	}

	/**
	 * Parses a JSON string from nec2++ safely without crashing on nulls/NaNs.
	 *
	 * @param jsonString input JSON string from nec2++ service
	 * @param gson       com.google.gson.Gson Object to parse input
	 * @return NecResult Object or null if fatal parse failure
	 */
	public static NecResult parseFromJson(String jsonString, Gson gson) {
		if (jsonString == null || jsonString.trim().isEmpty()) {
			return null;
		}

		try {
			RawJsonResponse raw = gson.fromJson(jsonString, RawJsonResponse.class);
			if (raw == null) return null;

			NecResult result = new NecResult();

			if (raw.pattern != null) {
				result.maxGain = sanitizeFloat(raw.pattern.maxGain);
				result.frontToBack = sanitizeFloat(raw.pattern.frontToBack);
				result.phi = sanitizeFloat(raw.pattern.phi);
				result.theta = sanitizeFloat(raw.pattern.theta);

				// Если хоть один массив содержит null/NaN или сам равен null,
				// parseStrictFloatArray выбросит исключение и унесет вызов в catch -> return null
				result.anglesTheta = parseStrictFloatArray(raw.pattern.anglesTheta);
				result.gainsTheta = parseStrictFloatArray(raw.pattern.gainsTheta);
				result.anglesPhi = parseStrictFloatArray(raw.pattern.anglesPhi);
				result.gainsPhi = parseStrictFloatArray(raw.pattern.gainsPhi);
			}

			if (raw.sweep != null) {
				result.frequencies = parseStrictFloatArray(raw.sweep.frequencies);
				result.resistance = parseStrictFloatArray(raw.sweep.resistance);
				result.reactance = parseStrictFloatArray(raw.sweep.reactance);
				result.gainsF = parseStrictFloatArray(raw.sweep.gainsF);
				result.frontToBackF = parseStrictFloatArray(raw.sweep.frontBackF);
			}

			return result;

		} catch (Exception e) {
			// Любой null внутри данныхбракует весь NecResult
			return null;
		}
	}

	/**
	 * Строгий парсинг массива: бросает исключение при наличии null, NaN или Infinity.
	 */
	private static float[] parseStrictFloatArray(JsonElement element) throws IllegalArgumentException {
		if (element == null || element.isJsonNull() || !element.isJsonArray()) {
			throw new IllegalArgumentException("Array element is null or not a JSON array");
		}

		JsonArray array = element.getAsJsonArray();
		float[] result = new float[array.size()];

		for (int i = 0; i < array.size(); i++) {
			JsonElement item = array.get(i);

			// Если элемент массива null (например [1.0, null, 2.0]) — забраковываем
			if (item == null || item.isJsonNull() || !item.isJsonPrimitive()) {
				throw new IllegalArgumentException("Array contains null or non-primitive item at index " + i);
			}

			float val = item.getAsFloat();

			// Проверяем валидность числа (NaN и Infinity также считаем повреждением данных)
			if (Float.isNaN(val) || Float.isInfinite(val)) {
				throw new IllegalArgumentException("Array contains invalid float (NaN/Infinity) at index " + i);
			}

			result[i] = val;
		}

		return result;
	}

	private static float sanitizeFloat(float val) throws IllegalArgumentException {
		if (Float.isNaN(val) || Float.isInfinite(val)) {
			throw new IllegalArgumentException("Float value is NaN or Infinity");
		}
		return val;
	}
}
