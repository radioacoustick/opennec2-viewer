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

package com.radioacoustick.opennec2.viewer;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.radioacoustick.opennec2.viewer.settings.AppSettings;
import com.radioacoustick.opennec2.viewer.settings.FloatInputSaveHelper;
import com.radioacoustick.opennec2.viewer.ui.utils.EdgeToEdgeHelper;

public class SettingsActivity extends AppCompatActivity {

	private AppSettings settings;
	private TextView tvLanguageValue, tvThemeValue;
	private SwitchCompat switch_original_control;
	private TextInputEditText edt_target_freq;
	private TextInputEditText edt_start_freq;
	private TextInputEditText edt_stop_freq;
	private TextInputEditText edt_steps;
	private TextInputEditText edt_impedance;
	private AutoCompleteTextView spinner_resol_scale;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdgeHelper.enableEdgeToEdge(this);
		setContentView(R.layout.activity_settings);
		settings = M_Application.getSettings();
		initViews();
		setupToolbar();
		updateUI();
		setupClickListeners();
	}

	private void initViews() {
		tvLanguageValue = findViewById(R.id.tvLanguageValue);
		tvThemeValue = findViewById(R.id.tvThemeValue);
		edt_target_freq = findViewById(R.id.edt_target_freq);
		switch_original_control = findViewById(R.id.switch_settings_original_control);
		spinner_resol_scale = findViewById(R.id.spinner_resol_scale);
		edt_start_freq = findViewById(R.id.edt_start_freq);
		edt_stop_freq = findViewById(R.id.edt_stop_freq);
		edt_steps = findViewById(R.id.edt_steps);
		edt_impedance = findViewById(R.id.edt_impedance);
	}

	private void setupToolbar() {
		MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
		setSupportActionBar(toolbar);

		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setDisplayShowHomeEnabled(true);
			getSupportActionBar().setTitle(R.string.action_settings);
		}
		toolbar.setNavigationOnClickListener(v -> finish());
	}

	/**
	 * Filling the UI with current values ​​from AppSettings
	 */
	private void updateUI() {
		switch_original_control.setChecked(settings.isOriginalControlCards());

		ArrayAdapter<Integer> adapter = new ArrayAdapter<>(
			 this,
			 android.R.layout.simple_list_item_1,
			 AppSettings.RP_RESOLUTION_SCALE_SET
		);
		spinner_resol_scale.setAdapter(adapter);
		int currentScale = M_Application.getSettings().getResolutionScale();
		spinner_resol_scale.setText(String.valueOf(currentScale), false);

		tvLanguageValue.setText(settings.getAppLanguage().getDisplayName());
		tvThemeValue.setText(settings.getAppTheme().getDisplayName(this));

	}

	/**
	 * Defining click listeners for interface elements
	 */
	private void setupClickListeners() {
		findViewById(R.id.itemTheme).setOnClickListener(v -> {
			showThemeDialog();
		});

		findViewById(R.id.itemLanguage).setOnClickListener(v -> {
			AppSettings.AppLanguage[] languages = AppSettings.AppLanguage.values();
			String[] displayNames = new String[languages.length];
			for (int i = 0; i < languages.length; i++) {
				displayNames[i] = languages[i].getDisplayName();
			}

			showSingleChoiceDialog(R.string.language, displayNames, settings.getAppLanguage().ordinal(), (dialog, which) -> {
				// 1. Update and save the application UI language
				AppSettings.AppLanguage selectedLang = languages[which];
				settings.setAppLanguage(selectedLang);
				M_Application.saveSettings(settings);
				// 2. Instantly switch the language
				LocaleListCompat appLocales = LocaleListCompat.forLanguageTags(selectedLang.getCode());
				AppCompatDelegate.setApplicationLocales(appLocales);

				dialog.dismiss();
			});
		});

		switch_original_control.setOnCheckedChangeListener((buttonView, isChecked) -> {
			AppSettings settings = M_Application.getSettings();
			settings.setOriginalControlCards(isChecked);
			M_Application.saveSettings(settings);
		});

		spinner_resol_scale.setOnItemClickListener((parent, view, position, id) -> {
			Integer selectedScale = (Integer) parent.getItemAtPosition(position);
			AppSettings settings = M_Application.getSettings();
			settings.setResolutionScale(selectedScale);
			M_Application.saveSettings(settings);
		});

		FloatInputSaveHelper.bind(
			 edt_target_freq,
			 M_Application.getSettings().getTargetFrequency(),
			 val -> {
				 AppSettings settings = M_Application.getSettings();
				 settings.setTargetFrequency(val);
				 M_Application.saveSettings(settings);
			 }
		);

		FloatInputSaveHelper.bind(
			 edt_start_freq,
			 M_Application.getSettings().getSweepStartFreq(),
			 val -> {
				 AppSettings settings = M_Application.getSettings();
				 settings.setSweepStartFreq(val);
				 M_Application.saveSettings(settings);
			 }
		);

		FloatInputSaveHelper.bind(
			 edt_stop_freq,
			 M_Application.getSettings().getSweepEndFreq(),
			 val -> {
				 AppSettings settings = M_Application.getSettings();
				 settings.setSweepEndFreq(val);
				 M_Application.saveSettings(settings);
			 }
		);

		FloatInputSaveHelper.bind(
			 edt_steps,
			 M_Application.getSettings().getSweepPoints(),
			 val -> {
				 AppSettings settings = M_Application.getSettings();
				 settings.setSweepPoints((int) val);
				 M_Application.saveSettings(settings);
			 }
		);

		FloatInputSaveHelper.bind(
			 edt_impedance,
			 M_Application.getSettings().getSystemImpedance(),
			 val -> {
				 AppSettings settings = M_Application.getSettings();
				 settings.setSystemImpedance(val);
				 M_Application.saveSettings(settings);
			 }
		);
	}

	@SuppressLint("WrongConstant")
	private void showThemeDialog() {
		String[] themes = new String[]{
			 getString(R.string.theme_default),
			 getString(R.string.theme_light),
			 getString(R.string.theme_dark)
		};

		int checkedItem = settings.getAppTheme().ordinal();

		new MaterialAlertDialogBuilder(this)
			 .setTitle(R.string.theme_select)
			 .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
				 AppSettings.AppTheme selectedTheme = AppSettings.AppTheme.values()[which];

				 if (settings.getAppTheme() != selectedTheme) {
					 // 1. Update and save the theme
					 settings.setAppTheme(selectedTheme);
					 M_Application.saveSettings(settings);

					 // 2. Updating the text value in the UI
					 updateUI();

					 // 3. Instantly switch the application theme
					 AppCompatDelegate.setDefaultNightMode(selectedTheme.getNightMode());
				 }
				 dialog.dismiss();
			 })
			 .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss())
			 .show();
	}

	private void showSingleChoiceDialog(int titleRes, String[] items, int checkedItem, AlertDialog.OnClickListener listener) {
		new AlertDialog.Builder(this)
			 .setTitle(titleRes)
			 .setSingleChoiceItems(items, checkedItem, listener)
			 .setNegativeButton(android.R.string.cancel, null)
			 .show();
	}
}
