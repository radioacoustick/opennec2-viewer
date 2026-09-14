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

import android.util.Log;

import com.radioacoustick.opennec2.viewer.M_Application;
import com.radioacoustick.opennec2.viewer.math.Custom4Nec2Functions;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

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

	private final Map<String, Float> variables = new HashMap<>();
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

		try {
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

			// 3. Converting a single-line file to a multi-line format
			StringBuilder result = new StringBuilder();
			variables.clear();
			String normalizedText = normalizeNecStructure(withoutComments.toString());
			String[] normalizedLines = normalizedText.split("\\r?\\n");

			for (String line : normalizedLines) {
				String upperLine = line.toUpperCase(Locale.US);

				// 4. Processing SY cards
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

		} catch (Exception e) {
			// Protection against any unhandled parsing failures
			Log.e("NecFileSanitizer", "Error sanitizing NEC file", e);
			return "";
		}
	}

	/**
	 * Checking whether the GN card specifies a real or ideal ground.
	 *
	 * @param gnLine Processed GN card line (for example: "GN 0 0 0 0 4 0.001")
	 * @return true if IPERF >= 0; false if IPERF == -1 (free space)
	 */
	private boolean parseGroundPresence(String gnLine) {
		String[] tokens = gnLine.trim().split("\\s+");
		if (tokens.length >= 2) {
			try {
				int iperf = (int) Float.parseFloat(tokens[1]);
				return iperf >= 0;
			} catch (NumberFormatException e) {
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

		String singleLine = rawText.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
		String cardTypes = "CM|CE|SY|GW|GA|GH|GR|GS|GE|GN|EK|EX|FR|LD|TL|NT|CP|RP|XQ|EN";

		Pattern pattern = Pattern.compile("(?i)\\b(" + cardTypes + ")\\b");
		Matcher matcher = pattern.matcher(singleLine);

		StringBuilder sb = new StringBuilder();
		int lastMatchEnd = 0;

		while (matcher.find()) {
			String segment = singleLine.substring(lastMatchEnd, matcher.start()).trim();
			if (!segment.isEmpty()) {
				sb.append(segment).append("\n");
			}
			lastMatchEnd = matcher.start();
		}

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
	 * Parsing a variable card (SY) into a symbol-value pair using exp4j.
	 */
	private void parseSymbol(String expression) {
		String[] parts = expression.split("=", 2);
		if (parts.length != 2) return;

		String rawVarName = parts[0].trim();
		String mathExpr = parts[1].trim();

		// 1. Приводим выражение к безопасному виду (заменяем имена известных переменных)
		String safeMathExpr = sanitizeExpression(mathExpr);

		// 2. Вычисляем значение
		float value = evaluateMath(safeMathExpr);

		// 3. Сохраняем имя переменной с префиксом "_"
		String safeVarName = sanitizeVarName(rawVarName);
		variables.put(safeVarName, value);
	}

	private static String sanitizeVarName(String varName) {
		String trimmed = varName.trim();
		// Если переменная еще не содержит префикс _, добавляем его
		return trimmed.startsWith("_") ? trimmed : "_" + trimmed;
	}

	/**
	 * Преобразует математическое выражение, подставляя префиксы '_'
	 * ко всем известным переменным, чтобы избежать конфликтов с функциями exp4j.
	 */
	private String sanitizeExpression(String expr) {
		if (expr == null || expr.isEmpty()) {
			return expr;
		}

		String sanitized = expr;
		// Заменяем имеющиеся переменные на их безопасные аналоги с "_"
		for (String varName : variables.keySet()) {
			// Убираем префикс '_', чтобы получить оригинальное имя для поиска в тексте
			String originalName = varName.startsWith("_") ? varName.substring(1) : varName;

			// Используем \b для точной замены полного слова (чтобы 'rad' не изменил 'grad')
			sanitized = sanitized.replaceAll("\\b" + Pattern.quote(originalName) + "\\b", varName);
		}

		return sanitized;
	}

	/**
	 * Substitutes numeric values instead of symbols into standard cards
	 */
	private String evaluateAndReplaceLine(String line) {
		if (line == null || line.trim().isEmpty()) {
			return line;
		}

		// 1. Сохраняем и отделяем комментарии 4nec2 (начинаются с апострофа ')
		int commentIdx = line.indexOf('\'');
		String comment = "";
		if (commentIdx != -1) {
			comment = line.substring(commentIdx);
			line = line.substring(0, commentIdx);
		}

		// 2. Обработка AWG калибров
		if (line.contains("#")) {
			line = convertAwgToRadius(line);
		}

		// 3. Нейтрализация микро-хаков 4nec2 вида "1e-300" / "1e-100" до парсинга
		line = line.replaceAll("(?i)1e-\\d+", "0.0001");

		String[] tokens = line.trim().split("\\s+");
		if (tokens.length == 0 || tokens[0].isEmpty()) {
			return line;
		}

		StringBuilder lineBuilder = new StringBuilder();
		int startIndex = 0;

		String firstTokenUpper = tokens[0].toUpperCase(Locale.US);
		if (NEC_CARDS.contains(firstTokenUpper)) {
			lineBuilder.append(firstTokenUpper);
			startIndex = 1;
		}

		for (int i = startIndex; i < tokens.length; i++) {
			if (lineBuilder.length() > 0) {
				lineBuilder.append(" ");
			}

			String token = tokens[i];

			if (isNumeric(token)) {
				lineBuilder.append(formatSafeFloatNumber(token));
			} else {
				try {
					// Вычисление математического выражения с возвратом float
					String safeToken = sanitizeExpression(token);
					float val = evaluateMath(safeToken);
					lineBuilder.append(formatFloatForNec(val));
				} catch (Exception e) {
					// Если это не выражение, обрабатываем как числовой/текстовый токен
					lineBuilder.append(formatSafeFloatNumber(token));
				}
			}
		}

		return lineBuilder + (comment.isEmpty() ? "" : " " + comment);
	}

	private boolean isNumeric(String str) {
		try {
			Float.parseFloat(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Форматирование float в синтаксис NEC без экспоненциального хвоста нулей.
	 */
	private String formatFloatForNec(float val) {
		if (Float.isNaN(val) || Float.isInfinite(val)) {
			return "0.0";
		}

		if (Math.abs(val) < 1e-7f) {
			return "0.0";
		}

		String formatted = String.format(Locale.US, "%.6f", val);
		return removeTrailingZerosFromNumber(formatted);
	}

	/**
	 * Безопасное чтение float из токена.
	 */
	private String formatSafeFloatNumber(String token) {
		try {
			float parsed = Float.parseFloat(token);
			return formatFloatForNec(parsed);
		} catch (NumberFormatException e) {
			return token;
		}
	}

	/**
	 * Converts AWG (#14) gauges to radius in meters
	 */
	private String convertAwgToRadius(String line) {
		Matcher matcher = AWG_PATTERN.matcher(line);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String fullMatch = matcher.group(0);
			if (fullMatch == null) continue;
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
	 * Evaluates a mathematical expression using exp4j with support for registered SY variables.
	 */
	private float evaluateMath(String expr) throws IllegalArgumentException {
		if (expr == null || expr.trim().isEmpty()) {
			return 0.0f;
		}

		// Объединяем глобальные Float-константы и пользовательские Float-переменные SY
		Map<String, Float> allVariablesFloat = new HashMap<>(Custom4Nec2Functions.BUILTIN_CONSTANTS);
		allVariablesFloat.putAll(this.variables);

		// exp4j требует Map<String, Double> только на время расчета выражения
		Map<String, Double> exp4jVariables = new HashMap<>();
		for (Map.Entry<String, Float> entry : allVariablesFloat.entrySet()) {
			exp4jVariables.put(entry.getKey(), entry.getValue().doubleValue());
		}

		Expression expression = new ExpressionBuilder(expr)
			 .functions(Custom4Nec2Functions.ALL_4NEC2_FUNCTIONS)
			 .variables(exp4jVariables.keySet())
			 .build();

		expression.setVariables(exp4jVariables);

		float result = (float) expression.evaluate();

		if (Float.isNaN(result) || Float.isInfinite(result)) {
			throw new IllegalArgumentException("Invalid float math result (NaN/Infinity) for: " + expr);
		}

		// Фильтрация underflow под точность float
		if (Math.abs(result) < 1e-7f) {
			return 0.0f;
		}

		return result;
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

		String[] lines = source.split("\\r?\\n");
		StringBuilder sb = new StringBuilder();

		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) continue;

			trimmed = trimmed.replace(",", " ").replace("\t", " ");
			trimmed = trimmed.replaceAll("([0-9.])(?=-[0-9.])", "$1 ");

			String[] tokens = trimmed.split("\\s+");
			StringBuilder lineBuilder = new StringBuilder();

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