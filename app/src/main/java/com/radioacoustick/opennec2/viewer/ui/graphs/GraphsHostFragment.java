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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.radioacoustick.opennec2.viewer.M_Application;
import com.radioacoustick.opennec2.viewer.MainActivity;
import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.SettingsActivity;
import com.radioacoustick.opennec2.viewer.domain.NecProjectViewModel;
import com.radioacoustick.opennec2.viewer.domain.NecResultViewModel;
import com.radioacoustick.opennec2.viewer.nec.NecHelper;
import com.radioacoustick.opennec2.viewer.settings.AppSettings;
import com.radioacoustick.opennec2.viewer.settings.FloatInputSaveHelper;

/**
 * A host fragment that contains tabs with frequency sweep graphs
 * and input fields for sweep parameters
 */
public class GraphsHostFragment extends Fragment {

	private TextView tvSweepSummary;
	private Button btnCalculate;
	private MaterialSwitch switchOriginalCards;
	private NecProjectViewModel necProjectViewModel;
	private NecResultViewModel necResultViewModel;
	private MaterialCardView card_sweep_parameters;


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.fragment_graphs, container, false);
		necProjectViewModel = new ViewModelProvider(requireActivity()).get(NecProjectViewModel.class);
		necResultViewModel = new ViewModelProvider(requireActivity()).get(NecResultViewModel.class);

		ViewPager2 viewPager = root.findViewById(R.id.view_pager);
		TabLayout tabLayout = root.findViewById(R.id.tab_layout);
		tvSweepSummary = root.findViewById(R.id.tv_sweep_summary);
		ImageView ivSettingsIcon = root.findViewById(R.id.iv_settings_icon);
		btnCalculate = root.findViewById(R.id.btn_calculate);
		switchOriginalCards = root.findViewById(R.id.sw_orig_cards_sweep);
		card_sweep_parameters = root.findViewById(R.id.card_pattern_parameters);

		viewPager.setAdapter(new FragmentStateAdapter(this) {
			@NonNull
			@Override
			public Fragment createFragment(int position) {
				return switch (position) {
					case 1 -> new ChartImpedanceFragment();
					case 2 -> new ChartGainFragment();
					case 3 -> new ChartFrontBackFragment();
					default -> new ChartSwrFragment();
				};
			}

			@Override
			public int getItemCount() {
				return 4; // Total number of tabs
			}
		});

		btnCalculate.setOnClickListener(v -> {
			AppSettings settings = M_Application.getSettings();
			if (settings.isOriginalControlCards()){
				performFrequencySweep();
			} else {
				try {
					float start = settings.getSweepStartFreq();
					float end = settings.getSweepEndFreq();
					int steps = settings.getSweepPoints();
					// Run the simulation
					performFrequencySweep(start, end, steps);
				} catch (NumberFormatException e) {
					Log.e("GraphsHostFragment", "onCreateView", e);
				}
			}

		});
		ivSettingsIcon.setOnClickListener(v -> {
			Intent settingsActivity = new Intent(requireContext(), SettingsActivity.class);
			startActivity(settingsActivity);
		});

		new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
			switch (position) {
				case 0:
					tab.setText(getString(R.string.swr));
					break;
				case 1:
					tab.setText(getString(R.string.impedance));
					break;
				case 2:
					tab.setText(getString(R.string.gain));
					break;
				case 3:
					tab.setText(getString(R.string.front_back));
					break;
			}
		}).attach();

		necProjectViewModel.isUseOriginalCards().observe(getViewLifecycleOwner(), isUse -> {
			if (switchOriginalCards != null && switchOriginalCards.isChecked() != isUse) {
				switchOriginalCards.setChecked(isUse);
			}
			int visibility = isUse ? View.GONE : View.VISIBLE;
			card_sweep_parameters.setVisibility(visibility);
		});

		if (switchOriginalCards != null) {
			switchOriginalCards.setOnCheckedChangeListener((buttonView, isChecked) -> {
				necProjectViewModel.setUseOriginalCards(isChecked);
			});
		}

		necProjectViewModel.getAntennaWires().observe(getViewLifecycleOwner(), wires -> updateButtonState());
		necResultViewModel.getCalculationState().observe(getViewLifecycleOwner(), state -> updateButtonState());

		return root;
	}

	@Override
	public void onResume() {
		super.onResume();
		updateSummaryText();
	}

	/**
	 * Method for starting a simulation after inserting an FR card
	 * with custom frequency sweep parameters into the source NEC-text
	 *
	 * @param startFreq Start sweep frequency (MHz)
	 * @param endFreq   End sweep frequency (MHz)
	 * @param steps     Number of frequency sweep points
	 */
	private void performFrequencySweep(float startFreq, float endFreq, int steps) {
		// 1. Get the source formatted text
		String baseNecText = necProjectViewModel.getCleanedNecText().getValue();

		if (baseNecText != null && !baseNecText.trim().isEmpty()) {
			// 2. Modify the text by replacing/adding an FR card
			String finalNecInput = NecHelper.injectSweepFrCard(baseNecText, startFreq, endFreq, steps);

			// 3. Run simulation
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).runNecCalculation(finalNecInput);
			}
		}
	}

	/**
	 * Method for starting a simulation with default FR card
	 */
	private void performFrequencySweep() {
		// 1. Get the source formatted text
		String baseNecText = necProjectViewModel.getCleanedNecText().getValue();
		if (getActivity() instanceof MainActivity) {
			((MainActivity) getActivity()).runNecCalculation(baseNecText);
		}
	}

	/**
	 * Service method.
	 * If there is no geometry or a simulation is in progress, the button is disabled.
	 */
	private void updateButtonState() {
		boolean hasGeometry = necProjectViewModel.getAntennaWires().getValue() != null;
		boolean isCalculating = necResultViewModel.isSimulationRunning();

		btnCalculate.setEnabled(hasGeometry && !isCalculating);
	}

	/**
	 * The method for updating the summary text
	 * that is shown on the collapsed sweep parameters card
	 */
	private void updateSummaryText() {
		AppSettings settings = M_Application.getSettings();
		String start = FloatInputSaveHelper.FLOAT_FORMAT.format(settings.getSweepStartFreq());
		String stop = FloatInputSaveHelper.FLOAT_FORMAT.format(settings.getSweepEndFreq());
		String pts = FloatInputSaveHelper.FLOAT_FORMAT.format(settings.getSweepPoints());
		String zo = FloatInputSaveHelper.FLOAT_FORMAT.format(settings.getSystemImpedance());

		String summary = String.format("%s – %s MHz (%s pts) | Zo: %s Ω", start, stop, pts, zo);
		tvSweepSummary.setText(summary);
	}
}