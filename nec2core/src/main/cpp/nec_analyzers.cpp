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

#include "nec_analyzers.h"
#include <cmath>

int RadiationPatternAnalyzer::getLastFrequencyIndex(nec_context &context) {
    // ==================================================================================================
    // Getting the last index of the simulation results.
    // This index is zero-based and dependent on the number of frequency points.
    // It always has an additional base frequency point hardwired by the calculation nec2++ core
    // corresponding to a wavelength of 1 meter. This point is excluded.
    // ==================================================================================================
    int f = 0;
    while (context.get_input_parameters(f) != nullptr) {
        f++;
    }
    return std::max(0, f - 2);

}

RadiationPatternAnalyzer::PatternData RadiationPatternAnalyzer::analyzePattern(nec_context &context, int freq_index) {
    // ======================================================================================
    // Far Field Analysis (Radiation Pattern).
    // ======================================================================================

    PatternData patternData;

    nec_radiation_pattern *rp = context.get_radiation_pattern(freq_index);

    if (rp == nullptr || context.get_gain_max(freq_index) < -900.0f) {
        for (int i = 0; i < 360; i++) {
            patternData.anglesPhi.push_back((float) i);
            patternData.gainsPhi.push_back(0.0f);
        }
        for (int i = 0; i < 180; i++) {
            patternData.anglesTheta.push_back((float) i);
            patternData.gainsTheta.push_back(0.0f);
        }
        patternData.max_gain = 0.0f;
        patternData.front_to_back = 0.0f;
        return patternData;
    }

    float max_gain = (float) context.get_gain_max(freq_index);
    patternData.max_gain = max_gain;

    int n_theta_points = rp->get_ntheta();
    int n_phi_points = rp->get_nphi();

    // Finding the absolute maximum over the entire 3D grid matrix
    int best_theta_index = 0;
    int best_phi_index = 0;
    getBestRpIndices(context, freq_index, &best_theta_index, &best_phi_index);

    float best_phi_angle = rp->get_phi(best_phi_index);
    float best_theta_angle = rp->get_theta(best_theta_index);
    patternData.phi = best_phi_angle;
    patternData.theta = best_theta_angle;

    // ================================================================
    // STEP 1: FORMING THE HORIZONTAL PLANE (PHI)
    // ================================================================
    patternData.anglesPhi.clear();
    patternData.gainsPhi.clear();

    for (int p = 0; p < n_phi_points; p++) {
        float real_phi = rp->get_phi(p);
        patternData.anglesPhi.push_back(real_phi);
        float gain_dbi = (float) context.get_gain(freq_index, best_theta_index, p);
        patternData.gainsPhi.push_back(gain_dbi);
    }

    // ================================================================
    // STEP 2: FORMING THE VERTICAL PLANE (THETA)
    // ================================================================
    patternData.anglesTheta.clear();
    patternData.gainsTheta.clear();

    for (int t = 0; t < n_theta_points; t++) {
        float theta_angle = rp->get_theta(t);
        patternData.anglesTheta.push_back(theta_angle);

        float gain_dbi = (float) context.get_gain(freq_index, t, best_phi_index);
        patternData.gainsTheta.push_back(gain_dbi);
    }

    // ================================================================
    // STEP 3: LOOKING FOR F/B
    // ================================================================

    float back_gain = getBackGain(context, freq_index);
    if (back_gain > -900.0f) {
        patternData.front_to_back = max_gain - back_gain;
    } else {
        patternData.front_to_back = 0.0f;
    }

    // ================================================================
    // STEP 4: NORMALIZING FOR CANVAS VIEW (0.0f - 1.0f)
    // ================================================================
    float min_graph_db = max_gain - 40.0f;

    auto normalize_gain = [max_gain, min_graph_db](float gain) {
        if (gain < min_graph_db) return 0.0f;
        return (gain - min_graph_db) / (max_gain - min_graph_db);
    };

    for (size_t i = 0; i < patternData.gainsPhi.size(); i++) {
        patternData.gainsPhi[i] = normalize_gain(patternData.gainsPhi[i]);
    }

    for (size_t i = 0; i < patternData.gainsTheta.size(); i++) {
        patternData.gainsTheta[i] = normalize_gain(patternData.gainsTheta[i]);
    }

    return patternData;
}

float RadiationPatternAnalyzer::getAngularDistance(float t1_deg, float p1_deg, float t2_deg, float p2_deg) {
    // ======================================================================================
    // Calculating the angular distance between two directions on a sphere (in degrees)
    // ======================================================================================
    float t1 = t1_deg * M_PI / 180.0f;
    float p1 = p1_deg * M_PI / 180.0f;
    float t2 = t2_deg * M_PI / 180.0f;
    float p2 = p2_deg * M_PI / 180.0f;

    float cos_a = std::sin(t1) * std::sin(t2) * std::cos(p1 - p2) + std::cos(t1) * std::cos(t2);
    cos_a = std::clamp(cos_a, -1.0f, 1.0f);

    return std::acos(cos_a) * 180.0f / M_PI;
}

float RadiationPatternAnalyzer::getBackGain(nec_context &context, int freq_index) {
    // ======================================================================================
    // Get back lobe gain (dB)
    // ======================================================================================
    nec_radiation_pattern *rp = context.get_radiation_pattern(freq_index);
    if (rp == nullptr || context.get_gain_max(freq_index) < -900.0f) {
        return -999.0f;
    }
    int n_theta_points = rp->get_ntheta();
    int n_phi_points = rp->get_nphi();

    bool has_ground = (rp->get_theta(n_theta_points - 1) <= 90.01f);
    int best_theta_index = 0;
    int best_phi_index = 0;
    getBestRpIndices(context, freq_index, &best_theta_index, &best_phi_index);
    float best_phi_angle = rp->get_phi(best_phi_index);
    float best_theta_angle = rp->get_theta(best_theta_index);

    // --- Calculates the coordinates of the opposite vector ---
    float target_phi_deg = std::fmod(best_phi_angle + 180.0f, 360.0f);
    if (target_phi_deg < 0.0f) target_phi_deg += 360.0f;
    float target_theta_deg = has_ground ? best_theta_angle : (180.0f - best_theta_angle);

    // --- Find the closest matrix point to target_theta / target_phi ---
    float min_angular_distance = 360.0f;
    float back_gain = -999.0f;

    for (int t = 0; t < n_theta_points; t++) {
        float current_theta = rp->get_theta(t);

        for (int p = 0; p < n_phi_points; p++) {
            float current_phi = rp->get_phi(p);

            // Distance from the ideal opposite vector to the current matrix point
            float dist = getAngularDistance(target_theta_deg, target_phi_deg, current_theta, current_phi);

            if (dist < min_angular_distance) {
                min_angular_distance = dist;
                back_gain = (float) context.get_gain(freq_index, t, p);
            }
        }
    }
    return back_gain;
}

void RadiationPatternAnalyzer::getBestRpIndices(nec_context &context, int freq_index, int *best_theta_index, int *best_phi_index) {
    // ======================================================================================
    // Getting indices of the radiation pattern matrix for maximum gain
    // ======================================================================================
    nec_radiation_pattern *rp = context.get_radiation_pattern(freq_index);
    if (rp != nullptr && context.get_gain_max(freq_index) > -900.0f) {
        int n_theta_points = rp->get_ntheta();
        int n_phi_points = rp->get_nphi();

        // Finding the absolute maximum over the entire 3D grid matrix

        float max_found_gain = -999.0f;

        for (int p = 0; p < n_phi_points; p++) {
            for (int t = 0; t < n_theta_points; t++) {
                float current_theta = rp->get_theta(t);

                // Ignore negative duplicate Theta angles when searching for the main lobe
                if (current_theta < 0.0f) continue;

                float current_gain = (float) context.get_gain(freq_index, t, p);
                if (current_gain > max_found_gain) {
                    max_found_gain = current_gain;
                    *best_theta_index = t;
                    *best_phi_index = p;
                }
            }
        }
    }
}

FrequencySweeper::SweepData FrequencySweeper::sweep(nec_context &context) {
    // ======================================================================================
    // Near Field and Frequency Response Analysis (SWR, Impedance) data class implementation
    // ======================================================================================

    FrequencySweeper::SweepData data;
    int f = 0;

    while (true) {
        // ============================================================
        // 1. Get the parameters of the current frequency point
        // ============================================================
        auto *ipt = context.get_input_parameters(f);
        if (ipt == nullptr) {
            break; // The frequency points have run out
        }

        // ============================================================
        // 2. Read R, X and Max Gain for the current frequency index f
        // ============================================================
        float r = (float) context.get_impedance_real(f);
        float x = (float) context.get_impedance_imag(f);
        float maxGain = (float) context.get_gain_max(f);

        if (r <= -990.0f) {
            r = 0.0f;
            x = 0.0f;
        }

        float freq_mhz = (float) ipt->get_frequency();

        // ============================================================
        // 3. Front-to-back calculation for the frequency index f
        // ============================================================
        float fb_ratio = 0.0f;
        float back_gain = RadiationPatternAnalyzer::getBackGain(context, f);
        if (back_gain > -900.0f) {
            fb_ratio = maxGain - back_gain;
        }

        // ============================================================
        // 4. Filling in the output data structures
        // ============================================================
        data.frequencies.push_back(freq_mhz);
        data.resistance.push_back(r);
        data.reactance.push_back(x);
        data.gains_f.push_back(maxGain);
        data.front_back.push_back(fb_ratio);

        f++;
    }

    return data;
}