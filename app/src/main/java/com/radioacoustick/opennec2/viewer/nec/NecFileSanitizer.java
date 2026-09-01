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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for converting a non-standard NEC file to a standard format
 * to avoid parsing and simulation errors on the nec2++ side.
 */
public class NecFileSanitizer {

	// -------------------------------------------------------------
	// Removes strings unnecessary for the simulation,
	// filters and replaces non-standard characters,
	// reads and processes SY cards of the 4NEC2 format
	// -------------------------------------------------------------

	private final Map<String, Double> variables = new HashMap<>();
	private static final Pattern AWG_PATTERN = Pattern.compile("#(\\d{1,2})");

	// Standard NEC-2 control cards
	private static final Set<String> NEC_CARDS = new HashSet<>(Arrays.asList(
		 "GW", "GA", "GE", "GH", "GR", "GS", "GC", "SP", "SM", // Geometry
		 "FR", "EX", "LD", "GN", "TL", "NT", "EK", "NE", "NH", // Settings and physics
		 "RP", "XQ", "EN"                                      // Output and completion
	));

	/**
	 * Function to convert non-standard NEC file to standard format.
	 *
	 * @param source Raw source text of the NEC-file
	 * @return Standard NEC text prepared for the nec2++ engine
	 */
	public String sanitizeForEngine(String source) {
		if (source == null || source.trim().isEmpty()) {
			return "";
		}

		// 1. Clearing invisible characters and NBSP
		String rawNecText = source.replace('\u00A0', ' ')
			 .replace("\u1680", " ")
			 .replace("\u2000", " ")
			 .replace("\u2008", " ")
			 .replace("\u202F", " ");

		boolean hasGround = false;
		boolean frProcessed = false;
		boolean rpProcessed = false;
		boolean isOriginalControlCards = M_Application.getSettings().isOriginalControlCards();

		// 2. Clearing all comments
		StringBuilder withoutComments = new StringBuilder();
		String[] rawLines = rawNecText.split("\\r?\\n");
		for (String rawLine : rawLines) {
			String line = removeComments(rawLine).trim();
			if (line.isEmpty() || isCommentCard(line)) continue;
			withoutComments.append(line).append("\n");
		}

		// 3. Converting a single-line file to a multi-line format, where each card starts on a new line,
		// and then splitting the file into an array of card-lines.
		StringBuilder result = new StringBuilder();
		variables.clear();
		String normalizedText = normalizeNecStructure(withoutComments.toString());
		String[] normalizedLines = normalizedText.split("\\r?\\n");

		for (String line : normalizedLines) {

			String upperLine = line.toUpperCase(Locale.US);

			// 4.Processing SY cards
			if (upperLine.startsWith("SY ")) {
				parseSymbol(line.substring(3).trim());
				continue;
			}

			// 5. Calculation and substitution of variables for the current line
			String processedLine = evaluateAndReplaceLine(line);
			upperLine = processedLine.toUpperCase(Locale.US);

			// 6. Checking the presence of ground (GN)
			if (upperLine.startsWith("GN")) {
				hasGround = parseGroundPresence(processedLine);
				result.append(processedLine).append("\n");
				continue;
			}

			// 7. FR and RP processing with the switch off
			if (!isOriginalControlCards) {
				if (upperLine.startsWith("FR")) {
					if (frProcessed) continue; // Skipping duplicate FRs
					processedLine = NecHelper.generateDefaultFrCard(processedLine);
					frProcessed = true;
				} else if (upperLine.startsWith("RP")) {
					if (rpProcessed) continue; // Skipping duplicate RPs
					processedLine = NecHelper.generateDefaultRpCard(hasGround);
					rpProcessed = true;
				}
			}

			result.append(processedLine).append("\n");
		}

		// 8. Final text cleaning
		String sanitized = sanitizeNecText(result.toString());

		// 9. Checking and adding missing RP/EN cards if they were not in the file
		return NecHelper.ensureRequiredCards(sanitized, hasGround);
	}

	/**
	 * Checking whether the GN card specifies a real or ideal ground.
	 *
	 * @param gnLine Processed GN card line (for example: "GN 0 0 0 0 4 0.001")
	 * @return true if IPERF >= 0; false if IPERF == -1 (free space)
	 */
	private boolean parseGroundPresence(String gnLine) {
		String[] tokens = gnLine.trim().split("\\s+");
		// Minimum format: GN IPERF (at least 2 tokens)
		if (tokens.length >= 2) {
			try {
				// Read the 1st parameter (IPERF / Ground Type)
				int iperf = (int) Float.parseFloat(tokens[1]);
				return iperf >= 0;
			} catch (NumberFormatException e) {
				// If parsing fails, we assume that there is ground (for safety)
				return true;
			}
		}
		return false;
	}


	/**
	 * Restores the standard line-by-line structure of a NEC2 file
	 * if line breaks are in random places or are absent
	 */
	private static String normalizeNecStructure(String rawText) {

		if (rawText == null || rawText.trim().isEmpty()) {
			return "";
		}

		// 1. Replace any line breaks, tabs, and multiple spaces with a single space.
		String singleLine = rawText.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();

		// 2. List of basic NEC2 control cards
		String cardTypes = "CM|CE|SY|GW|GA|GH|GR|GS|GE|GN|EK|EX|FR|LD|TL|NT|CP|RP|XQ|EN";

		// Regular expression: Searches for 2-letter card tags as separate words
		Pattern pattern = Pattern.compile("(?i)\\b(" + cardTypes + ")\\b");
		Matcher matcher = pattern.matcher(singleLine);

		StringBuilder sb = new StringBuilder();
		int lastMatchEnd = 0;

		while (matcher.find()) {
			// Take the text-fragment between the previous and current cards and form it as a separate line
			String segment = singleLine.substring(lastMatchEnd, matcher.start()).trim();
			if (!segment.isEmpty()) {
				sb.append(segment).append("\n");
			}
			lastMatchEnd = matcher.start();
		}

		// Add the last remainder (for example, the parameters of the EN card or the EN itself)
		if (lastMatchEnd < singleLine.length()) {
			sb.append(singleLine.substring(lastMatchEnd).trim());
		}

		return sb.toString().trim();
	}

	/**
	 * Method for checking whether a card is a comment (CM and CE)
	 */
	private boolean isCommentCard(String line) {
		String upper = line.toUpperCase(Locale.US);
		return upper.startsWith("CM ") || upper.equals("CM") || upper.startsWith("CMD") ||
			 upper.startsWith("CE ") || upper.equals("CE") || upper.startsWith("' ");
	}

	/**
	 * Method for removing comments starting with an apostrophe
	 */
	private String removeComments(String line) {
		int commentIdx = line.indexOf('\'');
		if (commentIdx != -1) {
			return line.substring(0, commentIdx);
		}
		return line;
	}

	/**
	 * Parsing a variable card (SY) into a symbol-value pair and store it in the variables set
	 */
	private void parseSymbol(String expression) {
		String[] parts = expression.split("=");
		if (parts.length != 2) return;

		String varName = parts[0].trim();
		String mathExpr = parts[1].trim();

		double value = evaluateMath(mathExpr);
		variables.put(varName, value);
	}

	/**
	 * Substitutes numeric values instead of symbols into standard cards
	 */
	private String evaluateAndReplaceLine(String line) {
		// 1. Convert AWG (#14) gauges to radius in meters
		if (line.contains("#")) {
			line = convertAwgToRadius(line);
		}

		// Splits a line into an array of tokens by spaces. If the line is empty, return
		String[] tokens = line.trim().split("\\s+");
		if (tokens.length == 0 || tokens[0].isEmpty()) {
			return line;
		}

		StringBuilder lineBuilder = new StringBuilder();
		int startIndex = 0;

		// 2. Check if the first token is a standard NEC card
		String firstTokenUpper = tokens[0].toUpperCase(Locale.US);
		if (NEC_CARDS.contains(firstTokenUpper)) {
			lineBuilder.append(firstTokenUpper); // Keep the card name unchanged
			startIndex = 1; // Looking for mathematics and variables only in arguments
		}

		// 3. Process the remaining string parameters
		for (int i = startIndex; i < tokens.length; i++) {
			if (lineBuilder.length() > 0) {
				lineBuilder.append(" ");
			}

			String token = tokens[i];
			// Checking whether a token is a text representing a variable or a mathematical expression
			if (containsVariableOrMath(token)) {
				// Substitute the variable or the expression for the numerical value
				double val = evaluateMath(token);
				lineBuilder.append(String.format(Locale.US, "%.6f", val));
			} else {
				// Substitute the scientific value for the simple value
				lineBuilder.append(normalizeScientificNotation(token));
			}
		}

		return lineBuilder.toString();
	}

	/**
	 * Converts scientific notation like 4.e-3 or 5E-3 to 0.004 / 0.005
	 */
	private String normalizeScientificNotation(String token) {
		try {
			if (token.toLowerCase(Locale.US).contains("e")) {
				BigDecimal bd = new BigDecimal(token);
				return bd.toPlainString();
			}
		} catch (Exception ignored) {
		}
		return token;
	}

	/**
	 * Converts AWG (#14) gauges to radius in meters
	 */
	private String convertAwgToRadius(String line) {
		Matcher matcher = AWG_PATTERN.matcher(line);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String fullMatch = matcher.group(0);
			if (fullMatch == null) {
				continue;
			}
			try {
				String group1 = matcher.group(1);

				if (group1 != null) {
					int awgNumber = Integer.parseInt(group1);
					double diameterMm = 0.127 * Math.pow(92.0, (36.0 - awgNumber) / 39.0);
					double radiusMeters = (diameterMm / 2.0) / 1000.0;

					String radiusStr = String.format(Locale.US, "%.6f", radiusMeters);
					matcher.appendReplacement(sb, radiusStr);
				} else {
					matcher.appendReplacement(sb, Matcher.quoteReplacement(fullMatch));
				}
			} catch (NumberFormatException e) {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(fullMatch));
			}
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Checking whether a token is a variable or a mathematical expression
	 * composed of variables by matching the text against a given set of variables.
	 */
	private boolean containsVariableOrMath(String token) {
		if (variables.isEmpty()) return false;

		for (String var : variables.keySet()) {
			if (token.contains(var)) return true;
		}
		return false;
	}

	/**
	 * Substitute the variable or the expression for the numerical value
	 */
	private double evaluateMath(String expr) {
		for (Map.Entry<String, Double> entry : variables.entrySet()) {
			expr = expr.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b",
				 String.valueOf(entry.getValue()));
		}

		try {
			return evalSimpleMath(expr);
		} catch (Exception e) {
			return 0.0;
		}
	}

	/**
	 * Substitute the simple mathematical expression for the numerical value
	 */
	private double evalSimpleMath(String expr) {
		expr = expr.replaceAll("\\s+", "");

		try {
			return Double.parseDouble(expr);
		} catch (NumberFormatException ignored) {
		}

		if (expr.contains("/")) {
			int idx = expr.lastIndexOf('/');
			return evalSimpleMath(expr.substring(0, idx)) / evalSimpleMath(expr.substring(idx + 1));
		}
		if (expr.contains("*")) {
			int idx = expr.lastIndexOf('*');
			return evalSimpleMath(expr.substring(0, idx)) * evalSimpleMath(expr.substring(idx + 1));
		}
		if (expr.contains("+")) {
			int idx = expr.lastIndexOf('+');
			return evalSimpleMath(expr.substring(0, idx)) + evalSimpleMath(expr.substring(idx + 1));
		}
		if (expr.contains("-")) {
			int idx = expr.lastIndexOf('-');
			if (idx > 0) {
				return evalSimpleMath(expr.substring(0, idx)) - evalSimpleMath(expr.substring(idx + 1));
			}
		}

		return 0.0;
	}

	/**
	 * The method removes trailing zeros from decimal fractions (145.000000 -> 145, 0.000500 -> 0.0005).
	 * It ignores card names (GW, FR, EX) and text tokens.
	 */
	public static String removeTrailingZerosFromNumber(String token) {
		try {
			BigDecimal bd = new BigDecimal(token);
			return bd.stripTrailingZeros().toPlainString();
		} catch (NumberFormatException e) {
			return token;
		}
	}

	/**
	 * A method for final cleaning and normalization of NEC card text.
	 * This results in a standard format that is recognized by the nec2core engine parser.
	 */
	private String sanitizeNecText(String source) {
		if (source == null || source.isEmpty()) return "";

		// Splits the .nec source file into an array of lines.
		String[] lines = source.split("\\r?\\n");
		StringBuilder sb = new StringBuilder();

		for (String line : lines) {
			String trimmed = line.trim();
			// Removes empty lines
			if (trimmed.isEmpty()) continue;

			// Replaces comma and tab separators with spaces
			trimmed = trimmed.replace(",", " ").replace("\t", " ");
			// Finds the number/dot combination before the minus sign and inserts a space between them
			trimmed = trimmed.replaceAll("([0-9.])(?=-[0-9.])", "$1 ");

			// Splits a string into tokens array on spaces
			String[] tokens = trimmed.split("\\s+");
			StringBuilder lineBuilder = new StringBuilder();

			// Formats numbers, trims leading zeros (12.5000 -> 12.5). Reassembles tokens into a string.
			for (int i = 0; i < tokens.length; i++) {
				String token = tokens[i];

				String cleanToken = removeTrailingZerosFromNumber(token);
				lineBuilder.append(cleanToken);

				if (i < tokens.length - 1) {
					lineBuilder.append(" ");
				}
			}

			sb.append(lineBuilder).append("\n");
		}

		return sb.toString();
	}
}