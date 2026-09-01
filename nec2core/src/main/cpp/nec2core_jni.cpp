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

#include <jni.h>
#include <string>
#include <clocale>
#include <json.hpp>
#include <sstream>
#include "nec_parser.h"
#include "nec_analyzers.h"
#include "nec2core_exception.h"

extern "C"
JNIEXPORT jstring JNICALL
/**
 * Native JNI function for calculating the electromagnetic model of an antenna.
 * <p>
 * Runs in the background process of the NecCalculationService.
 * Calls the C++ nec2++ kernel, calculates the radiation pattern,
 * input impedance and other antenna parameters, returning the results as a JSON string.
 *</p>
 * @param env A pointer to a JNI interface context (JNIEnv*).
 * @param thiz A reference to the instance of the NecCalculationService Java class that called the method.
 * @param nec_data_string NEC2 Geometry and Control Command Input String (Content of the NEC-file)
 *
 * @return A string with the calculation results (or execution error) to pass back to the Java layer.
 */
Java_com_radioacoustick_nec2core_NecCalculationService_runNecCalculationNative(
        JNIEnv *env, jobject thiz, jstring nec_input) {

    // Solving the NDK locale issue for GN cards
    std::setlocale(LC_NUMERIC, "C");

    // Generating input data
    const char *native_string = env->GetStringUTFChars(nec_input, nullptr);
    if (!native_string) return nullptr;
    std::string nec_content(native_string);
    env->ReleaseStringUTFChars(nec_input, native_string);

    try {
        // ================================================================
        // 1.Preparing and running the main calculation in the NEC2 core using nec2++
        // ================================================================
        nec_context context;
        context.initialize();

        // Parsing the input data line by line and filling the nec_content with cards (including geometry)
        NecParser::parseFromString(context, nec_content);

        // Initialize the calculation matrices
        context.calc_prepare();

        // Forcefully launching the electrodynamics calculation core!
        // Passing 0 (by default, calculation without outputting raw log to file)
        context.xq_card(0);

        // Collecting results from the context into data structures after the calculation is completed
        int last_freq_index = RadiationPatternAnalyzer::getLastFrequencyIndex(context);
        auto pattern = RadiationPatternAnalyzer::analyzePattern(context, last_freq_index);
        auto sweep = FrequencySweeper::sweep(context);

        // ================================================================
        // 2. Packing data into JSON
        // ================================================================
        using json = nlohmann::json;
        json json_output;

        // Packing RadiationPattern

        json_output["pattern"]["max_gain"] = pattern.max_gain;
        json_output["pattern"]["front_to_back"] = pattern.front_to_back;
        json_output["pattern"]["phi"] = pattern.phi;
        json_output["pattern"]["theta"] = pattern.theta;

        json json_anglesTheta = json::array();
        for (const auto &val: pattern.anglesTheta) {
            json_anglesTheta.push_back(val);
        }
        json_output["pattern"]["anglesTheta"] = json_anglesTheta;

        json json_gainsTheta = json::array();
        for (const auto &val: pattern.gainsTheta) {
            json_gainsTheta.push_back(val);
        }
        json_output["pattern"]["gainsTheta"] = json_gainsTheta;

        json json_anglesPhi = json::array();
        for (const auto &val: pattern.anglesPhi) {
            json_anglesPhi.push_back(val);
        }
        json_output["pattern"]["anglesPhi"] = json_anglesPhi;

        json json_gainsPhi = json::array();
        for (const auto &val: pattern.gainsPhi) {
            json_gainsPhi.push_back(val);
        }
        json_output["pattern"]["gainsPhi"] = json_gainsPhi;

        // Packing Sweep

        json json_frequencies = json::array();
        for (const auto &val: sweep.frequencies) {
            json_frequencies.push_back(val);
        }
        json_output["sweep"]["frequencies"] = json_frequencies;

        json json_resistance = json::array();
        for (const auto &val: sweep.resistance) {
            json_resistance.push_back(val);
        }
        json_output["sweep"]["resistance"] = json_resistance;

        json json_reactance = json::array();
        for (const auto &val: sweep.reactance) {
            json_reactance.push_back(val);
        }
        json_output["sweep"]["reactance"] = json_reactance;

        json json_gains_f = json::array();
        for (const auto &val: sweep.gains_f) {
            json_gains_f.push_back(val);
        }
        json_output["sweep"]["gains_f"] = json_gains_f;

        json json_front_back = json::array();
        for (const auto &val: sweep.front_back) {
            json_front_back.push_back(val);
        }
        json_output["sweep"]["front_back"] = json_front_back;

        // 3. Convert to std::string and return to Java
        std::string json_string = json_output.dump();
        return env->NewStringUTF(json_string.c_str());
    }
    catch (const nec_exception &e) {
        throwJavaException(env, e);
        return nullptr;
    }
    catch (const nec2core_exception &e) {
        throwJavaException(env, e);
        return nullptr;
    }
    catch (const std::exception &e) {
        throwJavaException(env, e);
        return nullptr;
    }
}
