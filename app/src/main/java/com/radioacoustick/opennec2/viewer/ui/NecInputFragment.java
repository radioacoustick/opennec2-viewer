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

package com.radioacoustick.opennec2.viewer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.radioacoustick.opennec2.viewer.M_Application;
import com.radioacoustick.opennec2.viewer.MainActivity;
import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.SettingsActivity;
import com.radioacoustick.opennec2.viewer.domain.NecProjectViewModel;
import com.radioacoustick.opennec2.viewer.domain.NecResultViewModel;
import com.radioacoustick.opennec2.viewer.nec.NecHelper;
import com.radioacoustick.opennec2.viewer.nec.NecValidator;
import com.radioacoustick.opennec2.viewer.settings.AppSettings;
import com.radioacoustick.opennec2.viewer.settings.FloatInputSaveHelper;
import com.radioacoustick.opennec2.viewer.ui.utils.UiUtils;

/**
 * A fragment containing a widget for displaying the NEC file name,
 * file text, and a button to start the far field simulation.
 */
public class NecInputFragment extends Fragment {

	private TextView tvPatternSummary;
	private TextInputEditText etNecInput;
	private View layoutFileHeader;
	private TextView tvFileName;
	private Button btnCalculate;
	private MaterialSwitch switchOriginalCards;
	private MaterialCardView card_pattern_parameters;
	private NecProjectViewModel necProjectViewModel;
	private NecResultViewModel necResultViewModel;

	public NecInputFragment() {
		// Required empty public constructor
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.fragment_nec_input, container, false);
		etNecInput = root.findViewById(R.id.edit_nec_input);
		layoutFileHeader = root.findViewById(R.id.layout_file_header);
		tvFileName = root.findViewById(R.id.tv_file_name);
		View btnClearFile = root.findViewById(R.id.btn_clear_file);
		ImageView ivSettingsIcon = root.findViewById(R.id.iv_settings_icon_);
		btnCalculate = root.findViewById(R.id.btn_calculate_main);
		switchOriginalCards = root.findViewById(R.id.sw_orig_cards);
		tvPatternSummary = root.findViewById(R.id.tv_pattern_summary);
		card_pattern_parameters = root.findViewById(R.id.card_pattern_parameters);

		// Makes text editing impossible. This may be changed in future releases.
		etNecInput.setFocusable(false);
		etNecInput.setFocusableInTouchMode(false);
		etNecInput.setCursorVisible(false);

		// Connecting ViewModels
		necProjectViewModel = new ViewModelProvider(requireActivity()).get(NecProjectViewModel.class);
		necResultViewModel = new ViewModelProvider(requireActivity()).get(NecResultViewModel.class);

		necProjectViewModel.isUseOriginalCards().observe(getViewLifecycleOwner(), isUse -> {
			if (switchOriginalCards != null && switchOriginalCards.isChecked() != isUse) {
				switchOriginalCards.setChecked(isUse);
			}
			int visibility = isUse ? View.GONE : View.VISIBLE;
			card_pattern_parameters.setVisibility(visibility);
		});

		if (switchOriginalCards != null) {
			switchOriginalCards.setOnCheckedChangeListener((buttonView, isChecked) -> {
				necProjectViewModel.setUseOriginalCards(isChecked);
			});
		}

		// Observes if raw text appears and then prepares formatted text
		necProjectViewModel.getRawNecText().observe(getViewLifecycleOwner(), text -> {
			if (text != null) {
				etNecInput.setText(text);
				NecValidator.ValidationResult validation = NecValidator.validateNecText(requireContext(), text);
				if (validation.isValid) {
					necProjectViewModel.processCleanedNecText(text);
				} else {
					UiUtils.showSnackbar(requireView(), validation.errorMessage, null);
				}
			}
		});

		// Observe the file is loaded or deleted
		necProjectViewModel.getFileName().observe(getViewLifecycleOwner(), fileName -> {
			if (fileName != null && !fileName.isEmpty()) {
				tvFileName.setText(fileName);
				layoutFileHeader.setVisibility(View.VISIBLE);
			} else {
				layoutFileHeader.setVisibility(View.GONE);
			}
		});

		necResultViewModel.getCalculationState().observe(getViewLifecycleOwner(), state -> updateProjectState());
		necProjectViewModel.getAntennaWires().observe(getViewLifecycleOwner(), wires -> updateProjectState());

		btnClearFile.setOnClickListener(v -> {
			necProjectViewModel.clearNecProject();
		});

		btnCalculate.setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				String cleanText = necProjectViewModel.getCleanedNecText().getValue();
				if (cleanText != null && !cleanText.isEmpty()) {
					NecValidator.ValidationResult validation = NecValidator.validateNecText(requireContext(), cleanText);
					// The frequency value selection dialog is shown if there is no FR card in the file.
					if (!validation.hasFrCard) {
						UiUtils.showFrequencyInputDialog(requireActivity(), (isConfirmed, frequencyMHz) -> {
							if (isConfirmed) {
								String preparedText = NecHelper.injectSingleFrCard(cleanText, frequencyMHz);
								((MainActivity) requireActivity()).runNecCalculation(preparedText);
							} else {
								necResultViewModel.onCalculationCanceled();
							}
						});
					} else {
						((MainActivity) requireActivity()).runNecCalculation(cleanText);
					}
					// Starting a new NEC2 calculation

				}
			}
		});

		ivSettingsIcon.setOnClickListener(v -> {
			Intent settingsActivity = new Intent(requireContext(), SettingsActivity.class);
			startActivity(settingsActivity);
		});

		etNecInput.addTextChangedListener(new TextWatcher() {
			private boolean isReplacing = false;

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (isReplacing) return;

				String str = s.toString();
				if (str.contains("\t")) {
					isReplacing = true;
					// If the text separators are tabs, replace all tabs with space for correct display in the widget.
					String replaced = str.replace("\t", " ");
					etNecInput.setText(replaced);
					etNecInput.setSelection(replaced.length());
					isReplacing = false;
				}
			}
		});

		return root;
	}

	@Override
	public void onResume() {
		super.onResume();
		updateSummaryText();
	}

	/**
	 * Service method.
	 * If there is no geometry, clears old results.
	 * If there is no geometry or a simulation is in progress, the button is disabled.
	 */
	private void updateProjectState() {
		boolean hasGeometry = necProjectViewModel.getAntennaWires().getValue() != null;
		boolean isCalculating = necResultViewModel.isSimulationRunning();

		// The start simulation button is active ONLY if the geometry is loaded AND the calculation is NOT currently running.
		btnCalculate.setEnabled(hasGeometry && !isCalculating);
		// Clearing results if the file is not loaded
		if (!hasGeometry)
			necResultViewModel.clearResult();
	}

	/**
	 * The method for updating the summary text
	 * that is shown on the collapsed radiation pattern parameters card
	 */
	private void updateSummaryText() {
		AppSettings settings = M_Application.getSettings();
		String targetFrequency = FloatInputSaveHelper.FLOAT_FORMAT.format(settings.getTargetFrequency());
		String Scale = FloatInputSaveHelper.FLOAT_FORMAT.format(settings.getResolutionScale());

		String summary = String.format("Fo: %s MHz | Scale: %s°", targetFrequency, Scale);
		tvPatternSummary.setText(summary);
	}

}