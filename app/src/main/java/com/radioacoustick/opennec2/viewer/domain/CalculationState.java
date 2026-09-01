package com.radioacoustick.opennec2.viewer.domain;

/**
 * Enumerating the states of the simulation engine
 */
public enum CalculationState {
	IDLE,        // Idle (simulation has not been started)
	RUNNING,     // The C++ core performs antenna simulation
	SUCCESS,     // The simulation was completed successfully, the results are ready.
	ERROR,       // An error occurred
	CANCELLED    // The simulation was cancelled by the user
}
