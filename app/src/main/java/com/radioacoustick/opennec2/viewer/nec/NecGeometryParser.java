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

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Class for extracting the antenna geometric structure from the input NEC text
 * to show the antenna geometry before the simulation starts.
 */
public class NecGeometryParser {

	/**
	 * Parses the NEC input text, finds all GW cards, and returns a Wire[] array.
	 *
	 * @param necContent Full text of the NEC file
	 * @return Array of Wire objects (may be empty if there are no GW cards)
	 */
	public static Wire[] parseWires(String necContent) {
		if (necContent == null || necContent.isEmpty()) {
			return new Wire[0];
		}

		List<Wire> wiresList = new ArrayList<>();

		try (Scanner scanner = new Scanner(necContent)) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine().trim();

				if (line.isEmpty() || line.toUpperCase().startsWith("CM") || line.toUpperCase().startsWith("CE")) {
					continue;
				}

				if (line.toUpperCase().startsWith("GW")) {
					Wire wire = parseGwLine(line);
					if (wire != null) {
						wiresList.add(wire);
					}
				}
			}
		}

		return wiresList.toArray(new Wire[0]);
	}

	/**
	 * Analyzes one specific line of GW cards.
	 */
	private static Wire parseGwLine(String line) {
		String[] tokens = line.split(" ");

		if (tokens.length < 10) {
			return null;
		}

		try {
			int tag = Integer.parseInt(tokens[1]);
			int segments = Integer.parseInt(tokens[2]);

			float x1 = Float.parseFloat(tokens[3]);
			float y1 = Float.parseFloat(tokens[4]);
			float z1 = Float.parseFloat(tokens[5]);

			float x2 = Float.parseFloat(tokens[6]);
			float y2 = Float.parseFloat(tokens[7]);
			float z2 = Float.parseFloat(tokens[8]);

			float radius = Float.parseFloat(tokens[9]);

			if (segments <= 0 || radius <= 0.0f) {
				return null;
			}

			return new Wire(tag, segments, x1, y1, z1, x2, y2, z2, radius);

		} catch (NumberFormatException e) {
			return null;
		}
	}
}
