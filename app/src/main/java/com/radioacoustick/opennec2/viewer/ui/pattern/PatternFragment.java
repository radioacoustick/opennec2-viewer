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

package com.radioacoustick.opennec2.viewer.ui.pattern;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.domain.NecProjectViewModel;
import com.radioacoustick.opennec2.viewer.domain.NecResultViewModel;
import com.radioacoustick.opennec2.viewer.math.AntennaMath;
import com.radioacoustick.opennec2.viewer.nec.NecResult;

/**
 * An UI fragment containing a PolarPatternView widget
 * for displaying Radiation Pattern and the main antenna parameters
 */
public class PatternFragment extends Fragment {

	private PolarPatternView polarPatternView;
	private TextView tvMaxGain;
	private TextView tvF2b;
	private TextView tvFreq;
	private TextView tvHpbw_h;
	private TextView tvHpbw_v;
	private TextView tvPhi;
	private TextView tvTheta;
	private String fileName;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_pattern, container, false);

		polarPatternView = view.findViewById(R.id.polar_view);
		tvMaxGain = view.findViewById(R.id.tv_max_gain);
		tvF2b = view.findViewById(R.id.tv_f2b);
		tvFreq = view.findViewById(R.id.tv_freq);
		tvHpbw_h = view.findViewById(R.id.tv_hpbw_h);
		tvHpbw_v = view.findViewById(R.id.tv_hpbw_v);
		tvPhi = view.findViewById(R.id.tv_phi);
		tvTheta = view.findViewById(R.id.tv_theta);

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);


		NecProjectViewModel necProjectViewModel = new ViewModelProvider(requireActivity()).get(NecProjectViewModel.class);
		necProjectViewModel.getFileName().observe(getViewLifecycleOwner(), fileName -> {
			if (fileName != null)
				this.fileName = fileName;
		});

		NecResultViewModel viewModel = new ViewModelProvider(requireActivity()).get(NecResultViewModel.class);
		// Observe the NecResult changing
		viewModel.getNecResult().observe(getViewLifecycleOwner(), result -> {
			if (result != null) {
				polarPatternView.setPatternData(
					 fileName,
					 result.anglesPhi, result.gainsPhi,     // Horizontal plane (Azimuth)
					 result.anglesTheta, result.gainsTheta,  // Vertical plane (elevation)
					 result.maxGain
				);
				displayCardData(result);
			}
		});
	}

	/**
	 * Method for displaying basic antenna parameters in cards at the corners of the main widget
	 *
	 * @param result NecResult Object
	 */
	private void displayCardData(NecResult result) {
		try {
			// Displays the Maximum Antenna Gain value
			String maxGain = String.format(java.util.Locale.US, "%.1f", result.maxGain) + " dBi";
			tvMaxGain.setText(maxGain);
		} catch (Exception ignored) {
		}

		try {
			// Displays the Front to Back value
			String frontToBack = String.format(java.util.Locale.US, "%.1f", result.frontToBack) + " dB";
			tvF2b.setText(frontToBack);
		} catch (Exception ignored) {
		}

		try {
			// Displays the last calculation frequency from the sweep frequency set.
			// This set ends with a default frequency corresponding to a wavelength of 1 meter,
			// hardcoded into the nec2++ engine. This frequency is excluded.
			float mhzFreq = result.frequencies[result.frequencies.length - 2] / 1_000_000f;
			String displayFreq = String.format(java.util.Locale.US, "%.1f", mhzFreq) + " MHz";
			tvFreq.setText(displayFreq);
		} catch (Exception ignored) {
		}

		try {
			// Displays HPBW in the horizontal plane.
			float hpbw_h = AntennaMath.calculateHPBW(result.anglesPhi, result.gainsPhi);
			String displayHpbw_h = String.format(java.util.Locale.US, "%.0f", hpbw_h) + "°";
			tvHpbw_h.setText(displayHpbw_h);
		} catch (Exception ignored) {
		}

		try {
			// Displays the HPBW in the vertical plane
			float hpbw_v = AntennaMath.calculateHPBW(result.anglesTheta, result.gainsTheta);
			String displayHpbw_v = String.format(java.util.Locale.US, "%.0f", hpbw_v) + "°";
			tvHpbw_v.setText(displayHpbw_v);
		} catch (Exception ignored) {
		}

		try {
			// Displays the Phi angle of maximum gain
			String maxPhi = "Phi: " + String.format(java.util.Locale.US, "%.1f", result.phi) + "°";
			tvPhi.setText(maxPhi);
		} catch (Exception ignored) {
		}

		try {
			// Displays the Theta angle of maximum gain.
			// The zero value is the direction to the zenith
			String maxTheta = "Theta: " + String.format(java.util.Locale.US, "%.1f", result.theta) + "°";
			tvTheta.setText(maxTheta);
		} catch (Exception ignored) {
		}

	}

}
