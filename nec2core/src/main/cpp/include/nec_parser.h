// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Valery Kustarev (https://github.com/radioacoustick)
/*
 * This file is part of nec2core engine for android app.
 *
 * Nec2core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Nec2core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Nec2core. If not, see <https://www.gnu.org/licenses/>.
 */

#ifndef NEC_PARSER_H
#define NEC_PARSER_H

#include "nec_structures.h"
#include <string>
#include <vector>
#include "nec_context.h"

/**
 *
 */
class NecParser {
public:
    /**
     * Single public method of the NecParser class: accepts the entire sanitized text of a .nec file in the standard format
     * and pass the data into the nec2++ simulation container
     *
     * @param context Container for an nec2++ simulation
     * @param content NEC2 Geometry and Control Command Input String (text of a .nec file)
     */
    static void parseFromString(nec_context &context, const std::string &content);

private:
    /**
     * Reading data from the NEC-card as a string and splitting it into a vector of NEC-card values
     * to pass the data into the nec2++ container.
     *
     * @param line Single line of input NEC-text (a NEC-card content)
     * @param tokens Strings vector of a NEC-card values
     */
    static void splitTokens(const std::string &line, std::vector<std::string> &tokens);

    /**
     * Parsing the antenna geometry card (for example, GW)
     * and filling the corresponding NEC2++ container field with the card data
     *
     * @param context Container for an nec2++ simulation
     * @param type NEC-card identifier (e.g. GW)
     * @param tokens Strings vector of a NEC-card values
     */
    static void parseGeometryCard(nec_context &context, const std::string &type, const std::vector<std::string> &tokens, float scale);

    /**
     * Parsing the NEC control card (for example, FR)
     * and filling the corresponding NEC2++ container field with the card data
     *
     * @param context Container for an nec2++ simulation
     * @param type NEC-card identifier (e.g. FR)
     * @param tokens Strings vector of a NEC-card values
     */
    static void parseControlCard(nec_context &context, const std::string &type, const std::vector<std::string> &tokens);

    /**
     * Convert a string token to a floating point number
     */
    static int parseInt(const std::vector<std::string> &tokens, size_t index, int defaultValue = 0);

    /**
     * Convert a string token to an integer
     */
    static float parseFloat(const std::vector<std::string> &tokens, size_t index, float defaultValue = 0.0f);
};

#endif // NEC_PARSER_H