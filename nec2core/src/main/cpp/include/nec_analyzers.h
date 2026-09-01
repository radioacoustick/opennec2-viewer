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

#ifndef NEC_ANALYZERS_H
#define NEC_ANALYZERS_H

#include <vector>
#include "nec_context.h"

/**
 * Far Field Analysis Data Class (Radiation Pattern)
 */
class RadiationPatternAnalyzer {
public:
    /**
     * Arrays of angles and gains in the vertical and horizontal planes
     */
    struct PatternData {
        std::vector<float> anglesPhi; // Horizontal plane, Theta = 90°
        std::vector<float> gainsPhi;
        std::vector<float> anglesTheta; // Vertical plane, Phi = 0°
        std::vector<float> gainsTheta;
        float max_gain;
        float front_to_back;
        float phi; // Phi angle of maximum gain
        float theta; // Theta angle of maximum gain
    };

    /**
     * Getting the last index of the simulation results
     * @param context Container for an nec2++ simulation
     * @return Last index of the simulation results
     */
    static int getLastFrequencyIndex(nec_context &context);

    /**
    * Far Field Analysis (Radiation Pattern).
    *
    * @param context Container for an nec2++ simulation
    * @param freq_index Last index of the simulation results
    * @return [PatternData](#RadiationPatternAnalyzer::PatternData) structure containing far-field radiation results.
    */
    static PatternData analyzePattern(nec_context &context, int freq_index);

    /**
     * Get back lobe gain
     * @param context Container for an nec2++ simulation
     * @param freq_index Index of the simulation results
     * @return
     */
    static float getBackGain(nec_context &context, int freq_index);
private:
    /**
     * Calculating the angular distance between two directions on a sphere (in degrees)
     * @param t1_deg Theta of the first direction
     * @param p1_deg Phi of the first direction
     * @param t2_deg Theta of the second direction
     * @param p2_deg Phi of the second direction
     * @return
     */
    static float getAngularDistance(float t1_deg, float p1_deg, float t2_deg, float p2_deg);

    /**
     * Getting indices of the radiation pattern matrix for maximum gain
     * @param context
     * @param freq_index
     * @param best_theta_index
     * @param best_phi_index
     */
    static void getBestRpIndices(nec_context &context, int freq_index, int *best_theta_index, int *best_phi_index);
};

/**
 * Near Field and Frequency Response Analysis (SWR, Impedance) data class
 */
class FrequencySweeper {
public:
    /**
     * Frequency sweep points and corresponding values.
     */
    struct SweepData {
        std::vector<float> frequencies;
        std::vector<float> resistance;
        std::vector<float> reactance;
        std::vector<float> gains_f;
        std::vector<float> front_back;
    };

    /**
     * Near Field and Frequency Response Analysis (SWR, Impedance)
     *
     * @param context Container for an nec2++ simulation
     * @return [SweepData](#RadiationPatternAnalyzer::SweepData)
     * structure containing sweep frequency points and corresponding values.
     */
    static SweepData sweep(nec_context &context);
};

#endif // NEC_ANALYZERS_H
