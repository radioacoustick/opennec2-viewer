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

import com.radioacoustick.opennec2.viewer.M_Application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A class for modifying the NEC source code.
 * Adds required and missing cards necessary for the simulation.
 */
public class NecHelper {

	/**
	 * Adds a FR card to the NEC-source text to simulate antenna parameter on single frequency
	 *
	 * @param baseNecText  Source NEC-text
	 * @param frequencyMHz Simulation frequency (MHz)
	 * @return Source text with embedded FR-card
	 */
	public static String injectSingleFrCard(String baseNecText, float frequencyMHz) {
		return injectSweepFrCard(baseNecText, frequencyMHz, frequencyMHz, 1);
	}

	/**
	 * Adds a FR card to the NEC-source text to simulate antenna parameter graphs based on frequency sweep
	 *
	 * @param baseNecText Source NEC-text
	 * @param start       Start sweep frequency (MHz)
	 * @param end         End sweep frequency (MHz)
	 * @param steps       Number of frequency sweep points
	 * @return Source text with embedded FR-card
	 */
	public static String injectSweepFrCard(String baseNecText, float start, float end, int steps) {
		if (baseNecText == null || baseNecText.trim().isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		String[] lines = baseNecText.split("\\r?\\n");

		// Protection against division by 0 when steps = 1
		float step = (steps > 1) ? (end - start) / (steps - 1) : 0.0f;

		//Correct FR card format for NEC2
		String frCard = String.format(java.util.Locale.US, "FR 0 %d 0 0 %.4f %.4f", steps, start, step);

		boolean frInserted = false;

		for (String line : lines) {
			String trimmed = line.trim().toUpperCase(java.util.Locale.US);

			// Skipping existing FR cards
			if (trimmed.startsWith("FR ") || trimmed.equals("FR")) {
				continue;
			}

			// Insert a new FR card before the execution (XQ, RP) or closing (EN) cards
			if (!frInserted && (trimmed.startsWith("XQ") || trimmed.startsWith("RP") || trimmed.startsWith("EN"))) {
				sb.append(frCard).append("\n");
				frInserted = true;
			}

			sb.append(line).append("\n");
		}

		// If the file does not contain XQ/RP/EN, add FR to the end
		if (!frInserted) {
			sb.append(frCard).append("\n");
		}
		return NecFileSanitizer.removeTrailingZerosFromNumber(sb.toString());
	}

	/**
	 * Checks for the presence of RP and EN cards in the NEC source text.
	 * If they are missing, they are added.
	 * The RP card is generated based on the ground type, if specified in the text.
	 * If not specified, it is generated for free space.
	 *
	 * @param baseNecText Source NEC-text
	 * @return Source text with embedded RP and EN cards
	 */
	public static String ensureRequiredCards(String baseNecText, boolean hasGround) {
		if (baseNecText == null || baseNecText.trim().isEmpty()) {
			return "RP 0 361 361 1000 0 0 1 1\nEN\n";
		}

		String[] lines = baseNecText.split("\\r?\\n");

		boolean hasRp = false;
		boolean hasEn = false;

		// Analyze the existing lines
		for (String line : lines) {
			String trimmed = line.trim().toUpperCase(Locale.US);

			if (trimmed.startsWith("RP ") || trimmed.equals("RP")) {
				hasRp = true;
			} else if (trimmed.equals("EN") || trimmed.startsWith("EN ")) {
				hasEn = true;
			}
		}

		// Collects the resulting list of strings
		List<String> resultLines = new ArrayList<>();

		for (String line : lines) {
			String trimmed = line.trim().toUpperCase(Locale.US);

			// If we reach the EN card but we don't have RP, we insert RP BEFORE EN
			if (!hasRp && (trimmed.equals("EN") || trimmed.startsWith("EN "))) {
				resultLines.add(generateDefaultRpCard(hasGround));
				hasRp = true; // Note that the RP is already inserted
			}

			resultLines.add(line);
		}

		// If the RP is still not added (for example, there was no EN card in the file), it adds
		if (!hasRp) {
			resultLines.add(generateDefaultRpCard(hasGround));
		}

		// If EN was missing, be sure to close the file to avoid parsing errors on the nec2++ side.
		if (!hasEn) {
			resultLines.add("EN");
		}

		return String.join("\n", resultLines) + "\n";
	}

	/**
	 * Generation of 3D RP (Radiation Pattern) map based on resolution scale (resolScale).
	 *
	 * @param hasGround ground presence flag
	 * @return Generated RP map string in NEC2 standard
	 */
	public static String generateDefaultRpCard(boolean hasGround) {

		int step = M_Application.getSettings().getResolutionScale();

		// Calculating the number of steps to completely cover a 3D sphere,
		// taking into account the presence of soil
		int n_theta = hasGround ? (180 / step) + 1 : (360 / step) + 1;
		int n_phi = (360 / step) + 1;

		int imode = 0;
		int xndv = 1000;

		int theta_start = hasGround ? -90 : -180;    // Starting angle Theta
		int phi_start = 0;    // Starting angle Phi (0°)

		// Formatting the RP card
		return String.format(
			 Locale.US,
			 "RP %d %d %d %d %d %d %d %d",
			 imode, n_theta, n_phi, xndv, theta_start, phi_start, step, step
		);
	}

	/**
	 * Extracts the frequency value from the FR card string and generates a FR default card for a single frequency.
	 * If the FR card from the file is not used, the frequency is taken from the application settings.
	 *
	 * @param inputFrLine FR card line (for example: "FR 0 10 0 0 435 2")
	 * @return Correct FR card format for NEC2 for single frequency
	 */
	public static String generateDefaultFrCard(String inputFrLine) {
		String defaultFreqToken = String.valueOf(M_Application.getSettings().getTargetFrequency());
		String freqToken = defaultFreqToken;
		boolean useOriginalControlCards = M_Application.getSettings().isOriginalControlCards();
		if (inputFrLine != null && !inputFrLine.trim().isEmpty()) {
			String[] tokens = inputFrLine.trim().split("\\s+");
			// FR structure: [0]=FR, [1]=IFRQ, [2]=NFRQ, [3]=IMOD, [4]=NMOD, [5]=FMHZ
			if (tokens.length > 5 && "FR".equalsIgnoreCase(tokens[0])) {
				freqToken = useOriginalControlCards ? tokens[5].trim() : defaultFreqToken;
			}
		}
		return String.format(Locale.US, "FR 0 1 0 0 %s 0", freqToken);
	}
}
