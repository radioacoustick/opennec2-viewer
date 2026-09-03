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

package com.radioacoustick.opennec2.viewer.domain;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.radioacoustick.opennec2.viewer.M_Application;
import com.radioacoustick.opennec2.viewer.nec.NecFileSanitizer;
import com.radioacoustick.opennec2.viewer.nec.NecValidator;
import com.radioacoustick.opennec2.viewer.nec.Wire;
import com.radioacoustick.opennec2.viewer.settings.AppSettings;

import java.util.Objects;

/**
 * View Model for storing the necessary parameters of the NEC project
 */
public class NecProjectViewModel extends ViewModel {

	private final MutableLiveData<String> rawNecText = new MutableLiveData<>();
	private final MutableLiveData<Wire[]> antennaWires = new MutableLiveData<>();
	private final MutableLiveData<String> fileName = new MutableLiveData<>();

	private final SingleLiveEvent<String> fileChangedEvent = new SingleLiveEvent<>();

	private final MutableLiveData<Boolean> useOriginalCards = new MutableLiveData<>(
		 M_Application.getSettings().isOriginalControlCards()
	);
	private final MediatorLiveData<String> cleanedNecText = new MediatorLiveData<>();

	public NecProjectViewModel() {
		cleanedNecText.addSource(rawNecText, text -> reformatCleanText());
		cleanedNecText.addSource(useOriginalCards, isOriginal -> reformatCleanText());
	}

	public LiveData<String> getRawNecText() {
		return rawNecText;
	}

	public void setRawNecText(String newFileName, String text) {
		boolean isValidNewName = newFileName != null && !newFileName.trim().isEmpty();
		if (!isValidNewName) {
			return;
		}

		String currentName = this.fileName.getValue();
		String currentText = this.rawNecText.getValue();

		// Checks for changes in the name or the contents of a file.
		boolean isNameChanged = !newFileName.equals(currentName);
		boolean isTextChanged = !Objects.equals(text, currentText);

		if (isNameChanged || isTextChanged) {
			fileName.setValue(newFileName);
			rawNecText.setValue(text);

			if (isNameChanged) {
				fileChangedEvent.setValue(newFileName);
			}
		}
	}

	/**
	 * Returns a one-time file change event (passes the file name).
	 */
	public LiveData<String> getFileChangedEvent() {
		return fileChangedEvent;
	}

	public LiveData<String> getCleanedNecText() {
		return cleanedNecText;
	}

	public LiveData<Wire[]> getAntennaWires() {
		return antennaWires;
	}

	public void updateGeometry(Wire[] wires) {
		antennaWires.setValue(wires);
	}

	public LiveData<Boolean> isUseOriginalCards() {
		return useOriginalCards;
	}

	public LiveData<String> getFileName() {
		return fileName;
	}

	public void setUseOriginalCards(boolean isOriginal) {
		AppSettings settings = M_Application.getSettings();
		settings.setOriginalControlCards(isOriginal);
		M_Application.saveSettings(settings);
		useOriginalCards.setValue(isOriginal);
	}

	/**
	 * Cleaning the antenna geometry object
	 */
	public void clearGeometry() {
		antennaWires.setValue(null);
	}

	/**
	 * Complete NEC project data cleanup
	 */
	public void clearNecProject() {
		fileName.setValue("");
		rawNecText.setValue("");
		cleanedNecText.setValue("");
		clearGeometry();
	}

	/**
	 * Updates the state if setting changes occurred in another Activity
	 */
	public void updateSettings() {
		AppSettings currentSettings = M_Application.getSettings();
		useOriginalCards.setValue(currentSettings.isOriginalControlCards());
	}

	/**
	 * Formatting the input rawNecText
	 */
	public void processCleanedNecText(String inputRawText) {
		NecFileSanitizer sanitizer = new NecFileSanitizer();
		cleanedNecText.setValue(sanitizer.sanitizeForEngine(inputRawText));
	}

	/**
	 * Formatting when changing text or the useOriginalCards flag
	 */
	private void reformatCleanText() {
		String text = rawNecText.getValue();
		if (text == null || text.trim().isEmpty()) {
			cleanedNecText.setValue("");
			return;
		}

		NecValidator.ValidationResult validationResult = NecValidator.validateNecText(text);
		if (!validationResult.isValid) {
			cleanedNecText.setValue("");
			clearGeometry();
			return;
		}

		if (validationResult.hasFrCard) {
			processCleanedNecText(text);
		}
	}
}
