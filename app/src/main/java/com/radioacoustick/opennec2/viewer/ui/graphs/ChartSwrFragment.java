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

package com.radioacoustick.opennec2.viewer.ui.graphs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.radioacoustick.opennec2.viewer.M_Application;
import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.domain.NecResultViewModel;
import com.radioacoustick.opennec2.viewer.math.AntennaMath;

/**
 * A fragment containing a MPAndroidChart widget for displaying VSWR graph
 */
public class ChartSwrFragment extends Fragment {

	private MaterialChartRenderer chartRenderer;

	public ChartSwrFragment() {
		// Required empty public constructor
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
									 Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View root = inflater.inflate(R.layout.fragment_chart_swr, container, false);
		LineChart chartSWR = root.findViewById(R.id.swr_line_chart);
		chartRenderer = new MaterialChartRenderer(chartSWR);
		NecResultViewModel viewModel = new ViewModelProvider(requireActivity()).get(NecResultViewModel.class);
		viewModel.getNecResult().observe(getViewLifecycleOwner(), result -> {
			if (result != null && result.frequencies != null) {
				float z0 = M_Application.getSettings().getSystemImpedance();
				float[] swrValues = AntennaMath.calculateSwr(result.resistance, result.reactance, z0);
				GraphsHelper.displayGraph(chartRenderer, result.frequencies, swrValues, getString(R.string.swr), true);
			}
		});

		return root;
	}

}