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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.Gson;
import com.radioacoustick.opennec2.viewer.nec.NecResult;

/**
 * View Model for storing the results of a successful simulation of the NEC model
 * and tracking the state of the simulation engine
 */
public class NecResultViewModel extends ViewModel {

	// Stores the latest simulation results
	private final MutableLiveData<NecResult> necResultLiveData = new MutableLiveData<>();
	// Stores the state of the simulation engine
	private final MutableLiveData<CalculationState> calculationState = new MutableLiveData<>(CalculationState.IDLE);

	// A one-time event indicating successful completion of a simulation with a calculation result
	private final SingleLiveEvent<Void> successEvent = new SingleLiveEvent<>();
	// One-time event to transmit error text
	private final SingleLiveEvent<String> errorEvent = new SingleLiveEvent<>();
	// A one-time event indicating if the user canceled the simulation
	private final SingleLiveEvent<Void> cancelEvent = new SingleLiveEvent<>();

	public LiveData<CalculationState> getCalculationState() {
		return calculationState;
	}
	public LiveData<Void> getSuccessEvent() { return successEvent; }
	public LiveData<String> getErrorEvent() { return errorEvent; }
	public LiveData<Void> getCancelEvent() { return cancelEvent; }

	/**
	 * Called just before a JNI method is called.
	 */
	public void onCalculationStarted() {
		calculationState.postValue(CalculationState.RUNNING);
	}

	/**
	 * Called when data is successfully returned from nec2++
	 */
	public void onCalculationSuccess(String resultJson) {
		// Saving the calculated data...
		Gson gson = new Gson();
		NecResult result = NecResult.parseFromJson(resultJson, gson);
		necResultLiveData.postValue(result);
		calculationState.postValue(CalculationState.SUCCESS);
		successEvent.postValue(null);
	}

	/**
	 * Called when an error occurs in C++ or an exception occurs in Java.
	 */
	public void onCalculationFailed(String errorMessage) {
		calculationState.postValue(CalculationState.ERROR);
		errorEvent.postValue(errorMessage);
	}

	/**
	 * Called if the simulation is canceled by the user.
	 */
	public void onCalculationCanceled() {
		calculationState.postValue(CalculationState.CANCELLED);
		cancelEvent.postValue(null);
	}

	/**
	 * Clearing state (e.g. when loading a new file)
	 */
	public void clearResult() {
		necResultLiveData.postValue(null);
		calculationState.postValue(CalculationState.IDLE);
	}


	/**
	 * Method for checking whether the simulation process is running
	 */
	public boolean isSimulationRunning() {
		return calculationState.getValue() == CalculationState.RUNNING;
	}

	public LiveData<NecResult> getNecResult() {
		return necResultLiveData;
	}

}
