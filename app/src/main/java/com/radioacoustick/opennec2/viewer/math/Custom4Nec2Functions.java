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

import net.objecthunter.exp4j.function.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * 4NEC2 Custom Functions Class
 */
public final class Custom4Nec2Functions {

	private Custom4Nec2Functions() {
	}

	/**
	 * sqr(x) = x^2
	 */
	public static final Function SQR = new Function("sqr", 1) {
		@Override
		public double apply(double... args) {
			return args[0] * args[0];
		}
	};

	/**
	 * int(x) / trunc(x) - discarding the fractional part
	 */
	public static final Function INT = new Function("int", 1) {
		@Override
		public double apply(double... args) {
			return (double) ((long) args[0]);
		}
	};

	/**
	 * rad(x) - explicit conversion of degrees to radians
	 */
	public static final Function RAD = new Function("rad", 1) {
		@Override
		public double apply(double... args) {
			return Math.toRadians(args[0]);
		}
	};

	/**
	 * deg(x) - explicit conversion of radians to degrees
	 */
	public static final Function DEG = new Function("deg", 1) {
		@Override
		public double apply(double... args) {
			return Math.toDegrees(args[0]);
		}
	};

	/**
	 * Sine function with the argument in degrees
	 */
	public static final Function SIN_DEG = new Function("sin", 1) {
		@Override
		public double apply(double... args) {
			return Math.sin(Math.toRadians(args[0]));
		}
	};

	/**
	 * Cosine function with the argument in degrees
	 */
	public static final Function COS_DEG = new Function("cos", 1) {
		@Override
		public double apply(double... args) {
			return Math.cos(Math.toRadians(args[0]));
		}
	};

	/**
	 * Tangent function with the argument in degrees
	 */
	public static final Function TAN_DEG = new Function("tan", 1) {
		@Override
		public double apply(double... args) {
			return Math.tan(Math.toRadians(args[0]));
		}
	};

	/**
	 * Arcsine function with the argument in degrees
	 */
	public static final Function ASIN_DEG = new Function("asin", 1) {
		@Override
		public double apply(double... args) {
			return Math.toDegrees(Math.asin(args[0]));
		}
	};

	/**
	 * Arccosine function with the argument in degrees
	 */
	public static final Function ACOS_DEG = new Function("acos", 1) {
		@Override
		public double apply(double... args) {
			return Math.toDegrees(Math.acos(args[0]));
		}
	};

	/**
	 * Arctangent function with the argument in degrees
	 */
	public static final Function ATAN_DEG = new Function("atan", 1) {
		@Override
		public double apply(double... args) {
			return Math.toDegrees(Math.atan(args[0]));
		}
	};

	/**
	 * Arctangent function with the argument in degrees (alternative notation)
	 */
	public static final Function ATN_DEG = new Function("atn", 1) {
		@Override
		public double apply(double... args) {
			return Math.toDegrees(Math.atan(args[0]));
		}
	};

	/**
	 * The arctangent function with two arguments, X and Y.
	 */
	public static final Function ATAN2_DEG = new Function("atan2", 2) {
		@Override
		public double apply(double... args) {
			return Math.toDegrees(Math.atan2(args[0], args[1]));
		}
	};

	public static final Function[] ALL_4NEC2_FUNCTIONS = new Function[]{
		 SQR, INT, RAD, DEG,
		 SIN_DEG, COS_DEG, TAN_DEG,
		 ASIN_DEG, ACOS_DEG, ATAN_DEG, ATN_DEG, ATAN2_DEG
	};

	// --- 4NEC2 System Constants ---

	public static final Map<String, Float> BUILTIN_CONSTANTS = new HashMap<>();

	static {
		BUILTIN_CONSTANTS.put("p", (float) Math.PI);
		BUILTIN_CONSTANTS.put("pi", (float) Math.PI);
		BUILTIN_CONSTANTS.put("c", 299792458.0f);
		BUILTIN_CONSTANTS.put("c0", 299792458.0f);
		BUILTIN_CONSTANTS.put("z0", 376.730313f);
		BUILTIN_CONSTANTS.put("d2r", (float) (Math.PI / 180.0));
		BUILTIN_CONSTANTS.put("dtor", (float) (Math.PI / 180.0));
		BUILTIN_CONSTANTS.put("r2d", (float) (180.0 / Math.PI));
		BUILTIN_CONSTANTS.put("rtod", (float) (180.0 / Math.PI));
	}
}