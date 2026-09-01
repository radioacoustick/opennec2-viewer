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

#include "nec_parser.h"
#include <sstream>
#include <algorithm>
#include <cctype>
#include <nec2core_exception.h>

// ===============================================================================================
// Main method: accepts the entire text of a .nec file and transfer it to the nec2++ core context
// ===============================================================================================
void NecParser::parseFromString(nec_context &context, const std::string &content) {
    std::stringstream stream(content);
    std::string line;
    // -------------------------------------------------------------------
    // The current release of nec2++ lacks a public method for passing a GS card into context.
    // Therefore, GS card must be caught before parsing geometry cards.
    // Pass 1: Find the final GS scale before creating geometry
    // -------------------------------------------------------------------
    float global_scale = 1.0f;
    {
        std::stringstream scan_stream(content);
        std::string scan_line;
        while (std::getline(scan_stream, scan_line)) {
            std::vector<std::string> tokens;
            splitTokens(scan_line, tokens);
            if (tokens.empty()) continue;

            std::string card = tokens[0];
            std::transform(card.begin(), card.end(), card.begin(), ::toupper);

            // The end of geometry, so there is no sense in looking for GS any further
            if (card == "GE") break;

            if (card == "GS") {
                // NEC2 standard format: GS I1 I2 SCALE (tokens[3])
                // Or GS SCALE (tokens[1]) if integer zeros are omitted
                float scale = parseFloat(tokens, 3, 1.0f);
                if (scale <= 0.0f) scale = parseFloat(tokens, 1, 1.0f);

                if (scale > 0.0f) {
                    global_scale *= scale;
                }
            }
        }
    }
    // -------------------------------------------------------------------
    // Pass 2: Basic parsing using the scale found in the first pass
    // -------------------------------------------------------------------
    bool in_geometry = true;
    try {
        while (std::getline(stream, line)) {
            std::vector<std::string> tokens;
            splitTokens(line, tokens);

            if (tokens.empty()) continue;

            std::string card_type = tokens[0];
            std::transform(card_type.begin(), card_type.end(), card_type.begin(), ::toupper);

            if (in_geometry) {
                if (card_type == "GE") {
                    int gpflag = parseInt(tokens, 1, 0);
                    context.geometry_complete(gpflag);
                    in_geometry = false;
                } else if (card_type == "GS") {
                    // Skipping the GS card in the 2nd pass
                    continue;
                } else {
                    // Passing global_scale to the geometric card handler
                    parseGeometryCard(context, card_type, tokens, global_scale);
                }
            } else {
                if (card_type == "EN") break;
                parseControlCard(context, card_type, tokens);
            }
        }
    }
    catch (const std::exception &e) {
        throw nec2core_exception(std::string("Failed to parse NEC input data: ") + e.what());
    }
}

// Reading data from the card as a string and splitting it into a vector of tokens to pass the data into the nec2++ container.
void NecParser::splitTokens(const std::string &line, std::vector<std::string> &tokens) {
    tokens.clear();

    std::string sanitized = line;
    for (char &ch: sanitized) {
        if (ch == ',') ch = ' ';
    }

    std::stringstream ss(sanitized);
    std::string token;

    while (ss >> token) {
        while (!token.empty() && (token.back() == '\r' || token.back() == '\n')) {
            token.pop_back();
        }

        if (!token.empty()) {
            tokens.push_back(token);
        }
    }
}

// Converting a token to an integer
int NecParser::parseInt(const std::vector<std::string> &tokens, size_t index, int defaultValue) {
    if (index >= tokens.size()) {
        return defaultValue;
    }
    try {
        return std::stoi(tokens[index]);
    } catch (...) {
        return defaultValue;
    }
}

// Convert a token to a floating point number
float NecParser::parseFloat(const std::vector<std::string> &tokens, size_t index, float defaultValue) {
    if (index >= tokens.size()) {
        return defaultValue;
    }
    try {
        return std::stof(tokens[index]);
    } catch (...) {
        return defaultValue;
    }
}

void NecParser::parseGeometryCard(nec_context &context, const std::string &type, const std::vector<std::string> &tokens, float scale) {
    // GW - Wire
    if (type == "GW") {
        try {
            int tag_id = parseInt(tokens, 1, 0);
            int segment_count = parseInt(tokens, 2, 1);

            double xw1 = parseFloat(tokens, 3, 0.0f) * scale;
            double yw1 = parseFloat(tokens, 4, 0.0f) * scale;
            double zw1 = parseFloat(tokens, 5, 0.0f) * scale;

            double xw2 = parseFloat(tokens, 6, 0.0f) * scale;
            double yw2 = parseFloat(tokens, 7, 0.0f) * scale;
            double zw2 = parseFloat(tokens, 8, 0.0f) * scale;

            double rad = parseFloat(tokens, 9, 0.001f) * scale;

            double rdel = parseFloat(tokens, 10, 1.0f);
            double rrad = parseFloat(tokens, 11, 1.0f);

            context.wire(tag_id, segment_count, xw1, yw1, zw1, xw2, yw2, zw2, rad, rdel, rrad);
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse GW-card: ") + e.what());
        }
    }
        // SP - Surface Segment
    else if (type == "SP") {
        try {
            int ns = parseInt(tokens, 1, 0);
            float x1 = parseFloat(tokens, 2, 0.0f) * scale;
            float y1 = parseFloat(tokens, 3, 0.0f) * scale;
            float z1 = parseFloat(tokens, 4, 0.0f) * scale;

            float x2 = parseFloat(tokens, 5, 0.0f) * scale;
            float y2 = parseFloat(tokens, 6, 0.0f) * scale;
            float z2 = parseFloat(tokens, 7, 0.0f) * scale;

            context.sp_card(ns, x1, y1, z1, x2, y2, z2);
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse SP-card: ") + e.what());
        }
    }
        // SC card
    else if (type == "SC") {
        try {
            int i2 = (tokens.size() > 0) ? parseInt(tokens, 1) : 0;
            nec_float x3 = (tokens.size() > 1) ? parseFloat(tokens, 2) : 0.0f;
            nec_float y3 = (tokens.size() > 2) ? parseFloat(tokens, 3) : 0.0f;
            nec_float z3 = (tokens.size() > 3) ? parseFloat(tokens, 4) : 0.0f;
            nec_float x4 = (tokens.size() > 4) ? parseFloat(tokens, 5) : 0.0f;
            nec_float y4 = (tokens.size() > 5) ? parseFloat(tokens, 6) : 0.0f;
            nec_float z4 = (tokens.size() > 6) ? parseFloat(tokens, 7) : 0.0f;

            context.sc_card(i2, x3, y3, z3, x4, y4, z4);
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse SC-card: ") + e.what());
        }
    }
        // GX - Reflection
    else if (type == "GX") {
        try {
            context.gx_card(parseInt(tokens, 1), parseInt(tokens, 2));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse GX-card: ") + e.what());
        }
    }
        // GM / NX - Move/Transform Structure
    else if (type == "GM") {
        try {
            context.move(
                    parseFloat(tokens, 3), parseFloat(tokens, 4), parseFloat(tokens, 5), // rox, roy, roz
                    parseFloat(tokens, 6), parseFloat(tokens, 7), parseFloat(tokens, 8), // xs, ys, zs
                    parseInt(tokens, 1),                                                 // its
                    parseInt(tokens, 2),                                                 // nrpt
                    parseInt(tokens, 9)                                                  // itgi
            );
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse GM-card: ") + e.what());
        }
    }
        // GA - Arc
    else if (type == "GA") {
        try {
            context.arc(
                    parseInt(tokens, 1), parseInt(tokens, 2),
                    parseFloat(tokens, 3), parseFloat(tokens, 4), parseFloat(tokens, 5),
                    parseFloat(tokens, 6));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse GA-card: ") + e.what());
        }
    }
        // GH - Helix
    else if (type == "GH") {
        try {
            context.helix(
                    parseInt(tokens, 1), parseInt(tokens, 2), parseFloat(tokens, 3),
                    parseFloat(tokens, 4), parseFloat(tokens, 5), parseFloat(tokens, 6),
                    parseFloat(tokens, 7), parseFloat(tokens, 8), parseFloat(tokens, 9));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse GH-card: ") + e.what());
        }
    }
}

void NecParser::parseControlCard(nec_context &context, const std::string &type, const std::vector<std::string> &tokens) {
    // FR - Frequency parameters
    if (type == "FR") {
        try {
            int ifrq = parseInt(tokens, 1, 0);
            int nfreq = parseInt(tokens, 2, 1);


            // The frequency is exactly at the 5th token index!
            float frequency = parseFloat(tokens, 5, 299.7925f);
            float delta_freq = parseFloat(tokens, 6, 0.0f);

            // Passing to context
            context.fr_card(ifrq, nfreq, frequency, delta_freq);
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse FR-card: ") + e.what());
        }
    }
        // LD - RLC load elements
    else if (type == "LD") {
        try {
            context.ld_card(
                    parseInt(tokens, 1), parseInt(tokens, 2), parseInt(tokens, 3), parseInt(tokens, 4),
                    parseFloat(tokens, 5), parseFloat(tokens, 6), parseFloat(tokens, 7));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse LD-card: ") + e.what());
        }
    }
        // GN - Ground parameters
    else if (type == "GN") {
        try {
            int ground_type = parseInt(tokens, 1, 0);
            int rad_wire_count = parseInt(tokens, 2, 0);

            // Move on to vector indices 5 and 6, where the soil parameters are located.
            nec_float dielectric = (nec_float) parseFloat(tokens, 5, 1.0f);
            nec_float conductivity = (nec_float) parseFloat(tokens, 6, 0.0f);

            // Assign zero values to the remaining parameters that are not in the card.
            nec_float tmp3 = 0.0f;
            nec_float tmp4 = 0.0f;
            nec_float tmp5 = 0.0f;
            nec_float tmp6 = 0.0f;

            // Calling a core method with the correct number of arguments
            context.gn_card(ground_type, rad_wire_count,
                            dielectric, conductivity,
                            tmp3, tmp4, tmp5, tmp6);
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse GN-card: ") + e.what());
        }
    }
        // EX - Excitation
    else if (type == "EX") {
        try {
            int ex_type_int = parseInt(tokens, 1);
            excitation_type ex_type = static_cast<excitation_type>(ex_type_int);
            context.ex_card(
                    ex_type, parseInt(tokens, 2), parseInt(tokens, 3), parseInt(tokens, 4),
                    parseFloat(tokens, 5), parseFloat(tokens, 6), parseFloat(tokens, 7),
                    parseFloat(tokens, 8), parseFloat(tokens, 9), parseFloat(tokens, 10));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse EX-card: ") + e.what());
        }
    }
        // TL - Transmission Line
    else if (type == "TL") {
        try {
            context.tl_card(
                    parseInt(tokens, 1), parseInt(tokens, 2), parseInt(tokens, 3), parseInt(tokens, 4),
                    parseFloat(tokens, 5), parseFloat(tokens, 6), parseFloat(tokens, 7),
                    parseFloat(tokens, 8), parseFloat(tokens, 9), parseFloat(tokens, 10));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse TL-card: ") + e.what());
        }
    }
        // NT - Network
    else if (type == "NT") {
        try {
            context.nt_card(
                    parseInt(tokens, 1), parseInt(tokens, 2), parseInt(tokens, 3), parseInt(tokens, 4),
                    parseFloat(tokens, 5), parseFloat(tokens, 6), parseFloat(tokens, 7),
                    parseFloat(tokens, 8), parseFloat(tokens, 9), parseFloat(tokens, 10));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse NT-card: ") + e.what());
        }
    }
        // XQ - Execute
    else if (type == "XQ") {
        try {
            context.xq_card(parseInt(tokens, 1, 0));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse XQ-card: ") + e.what());
        }
    }
        // GD - Additional ground parameters
    else if (type == "GD") {
        try {
            context.gd_card(
                    parseFloat(tokens, 1), parseFloat(tokens, 2),
                    parseFloat(tokens, 3), parseFloat(tokens, 4));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse GD-card: ") + e.what());
        }
    }
        // RP - Radiation Pattern Request
    else if (type == "RP") {
        try {
            // 1. Required fields
            int calc_mode = parseInt(tokens, 1, 0);
            int n_theta = parseInt(tokens, 2, 1);
            int n_phi = parseInt(tokens, 3, 1);

            // 2. Unpacking the XNDS Composite Flag (Token 4)
            int xnds = parseInt(tokens, 4, 0);
            int output_format = xnds / 1000;
            int normalization = (xnds / 100) % 10;
            int D = (xnds / 10) % 10;
            int A = xnds % 10;

            // 3. Reading optional fields (if they are not in tokens, default values are taken)
            float th0 = parseFloat(tokens, 5, 0.0f);
            float ph0 = parseFloat(tokens, 6, 0.0f);
            float d_th = parseFloat(tokens, 7, 0.0f);
            float d_ph = parseFloat(tokens, 8, 0.0f);
            float distance = parseFloat(tokens, 9, 0.0f);
            float g_norm = parseFloat(tokens, 10, 0.0f);

            // 4. Passing parsed values to the nec2++ kernel
            context.rp_card(calc_mode, n_theta, n_phi, output_format, normalization, D, A,
                            th0, ph0, d_th, d_ph, distance, g_norm);
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse RP-card: ") + e.what());
        }
    }
        // PT - Segment current print control
    else if (type == "PT") {
        try {
            context.pt_card(parseInt(tokens, 1), parseInt(tokens, 2), parseInt(tokens, 3), parseInt(tokens, 4));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse PT-card: ") + e.what());
        }
    }
        // PQ - Control of charge printing
    else if (type == "PQ") {
        try {
            context.pq_card(parseInt(tokens, 1), parseInt(tokens, 2), parseInt(tokens, 3), parseInt(tokens, 4));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse PQ-card: ") + e.what());
        }
    }
        // KH - Limit of numerical integration of a matrix
    else if (type == "KH") {
        try {
            context.kh_card(parseFloat(tokens, 1));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse KH-card: ") + e.what());
        }
    }
        // NE - Near E-Field calculations
    else if (type == "NE") {
        try {
            context.ne_card(
                    parseInt(tokens, 1), parseInt(tokens, 2), parseInt(tokens, 3), parseInt(tokens, 4),
                    parseFloat(tokens, 5), parseFloat(tokens, 6), parseFloat(tokens, 7),
                    parseFloat(tokens, 8), parseFloat(tokens, 9), parseFloat(tokens, 10));
        }
        catch (const std::exception &e) {
            throw nec2core_exception(std::string("Failed to parse NE-card: ") + e.what());
        }
    }
}