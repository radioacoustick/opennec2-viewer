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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.radioacoustick.nec2core.INecService;
import com.radioacoustick.nec2core.NecCalculationService;
import com.radioacoustick.opennec2.viewer.domain.CalculationState;
import com.radioacoustick.opennec2.viewer.domain.NecProjectViewModel;
import com.radioacoustick.opennec2.viewer.domain.NecResultViewModel;
import com.radioacoustick.opennec2.viewer.nec.NecGeometryParser;
import com.radioacoustick.opennec2.viewer.nec.NecValidator;
import com.radioacoustick.opennec2.viewer.nec.Wire;
import com.radioacoustick.opennec2.viewer.ui.MainHostFragment;
import com.radioacoustick.opennec2.viewer.ui.graphs.GraphsHostFragment;
import com.radioacoustick.opennec2.viewer.ui.pattern.PatternFragment;
import com.radioacoustick.opennec2.viewer.ui.utils.EdgeToEdgeHelper;
import com.radioacoustick.opennec2.viewer.ui.utils.UiUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

	private CircularProgressIndicator progressIndicator;
	private MenuItem cancelMenuItem;
	private NecProjectViewModel necProjectViewModel;
	private NecResultViewModel necResultViewModel;
	private BottomNavigationView bottomNavigationView;
	private static final String TAG = "MainActivity";

	private INecService necService;
	private UiUtils uiUtils;
	private boolean isBound = false;

	// Background thread pool
	// First thread is for dispatching heavy IPC tasks to avoid freezing the UI
	// The second thread is intended to forcefully stop the simulation
	private final ExecutorService executor = Executors.newFixedThreadPool(2);
	private Runnable pendingCalculationTask = null;

	private final ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			necService = INecService.Stub.asInterface(service);
			isBound = true;
			Log.d(TAG, "Connection with C++ service established");

			// If the calculation was waiting for the service to reconnect, this executes it
			if (pendingCalculationTask != null) {
				Runnable task = pendingCalculationTask;
				pendingCalculationTask = null;
				executor.execute(task);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			cleanupDeadServiceConnection();
			Log.w(TAG, "The C++ service process crashed or terminated unexpectedly.");
		}
	};

	/**
	 * Reading a NEC-file from a device after it is opened by the System File Explorer
	 */
	ActivityResultLauncher<String[]> openNecFileLauncher = registerForActivityResult(
		 new ActivityResultContracts.OpenDocument(),
		 uri -> {
			 if (uri != null) {
				 if (!NecValidator.isNecFile(this, uri)) {
					 UiUtils.showSnackbar(this, getString(R.string.message_error_invalid_file_format));
				 } else {
					 uiUtils.readNecFromUri(uri, necProjectViewModel);
				 }
			 }
		 }
	);

	/**
	 * Launching System Explorer to manually select the .nec file
	 */
	private void openFilePicker() {
		String[] mimeTypes = new String[]{
			 "text/plain",
			 "application/octet-stream",
			 "text/*"
		};
		openNecFileLauncher.launch(mimeTypes);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		EdgeToEdgeHelper.enableEdgeToEdge(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		necProjectViewModel = new ViewModelProvider(this).get(NecProjectViewModel.class);
		necResultViewModel = new ViewModelProvider(this).get(NecResultViewModel.class);
		uiUtils = new UiUtils(this);

		initViews();
		uiUtils.handleIncomingIntent(getIntent(), necProjectViewModel);

		if (savedInstanceState == null) {
			uiUtils.loadFragment(new MainHostFragment());
		}
	}

	private void initViews() {
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ViewGroup contentRoot = findViewById(android.R.id.content);
		bottomNavigationView = findViewById(R.id.bottom_navigation);
		progressIndicator = findViewById(R.id.progress_bar);

		View mainLayout = contentRoot.getChildAt(0);
		EdgeToEdgeHelper.applyInsets(getApplicationContext(), mainLayout, toolbar, bottomNavigationView);

		bottomNavigationView.setOnItemSelectedListener(item -> {
			Fragment selectedFragment = null;
			int id = item.getItemId();

			if (id == R.id.nav_geometry) {
				selectedFragment = new MainHostFragment();
			} else if (id == R.id.nav_pattern) {
				selectedFragment = new PatternFragment();
			} else if (id == R.id.nav_charts) {
				selectedFragment = new GraphsHostFragment();
			}
			return uiUtils.loadFragment(selectedFragment);
		});

		necResultViewModel.getCalculationState().observe(this, state -> {
			boolean isRunning = (state == CalculationState.RUNNING);
			progressIndicator.setVisibility(isRunning ? View.VISIBLE : View.GONE);
			if (cancelMenuItem != null) {
				cancelMenuItem.setVisible(isRunning);
			}
			if (isRunning) {
				getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			} else {
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		});

		necResultViewModel.getSuccessEvent().observe(this, unused -> {
			int id = bottomNavigationView.getSelectedItemId();
			if (id == R.id.nav_geometry) {
				new MaterialAlertDialogBuilder(this)
					 .setTitle(getString(R.string.calculation_completed))
					 .setMessage(getString(R.string.message_simulation_success_))
					 .setPositiveButton(getString(R.string.show_radiation_pattern), (dialog, which) -> {
						 bottomNavigationView.setSelectedItemId(R.id.nav_pattern);
					 })
					 .setNegativeButton(getString(R.string.close), null)
					 .show();
			}
		});

		necResultViewModel.getErrorEvent().observe(this, errorMessage -> {
			if (errorMessage != null && !errorMessage.isEmpty()) {
				new MaterialAlertDialogBuilder(this)
					 .setTitle(getString(R.string.title_simulation_error_message))
					 .setMessage(errorMessage)
					 .setPositiveButton("OK", null)
					 .show();
			}
		});

		necResultViewModel.getCancelEvent().observe(this, event -> {
			UiUtils.showSnackbar(this, getString(R.string.calculation_canceled));
		});

		// Observes if formatted text appears, then updates the antenna geometry
		necProjectViewModel.getCleanedNecText().observe(this, text -> {
			if (text != null && !text.isEmpty()) {
				Wire[] wires = NecGeometryParser.parseWires(text);
				necProjectViewModel.updateGeometry(wires);
			}
		});

		// Observe the file name changing
		necProjectViewModel.getFileChangedEvent().observe(this, fileName -> {
			if (fileName != null && !fileName.isEmpty()) {
				necResultViewModel.clearResult();
				bottomNavigationView.setSelectedItemId(R.id.nav_geometry);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		cancelMenuItem = menu.findItem(R.id.action_stop_nec_engine);
		if (cancelMenuItem != null) {
			cancelMenuItem.setVisible(necResultViewModel.isSimulationRunning());
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.action_about) {
			uiUtils.showAboutDialog();
			return true;
		} else if (itemId == R.id.action_open_file) {
			openFilePicker();
			return true;
		} else if (itemId == R.id.action_settings) {
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		} else if (itemId == R.id.action_stop_nec_engine) {
			forceStopService();
			return true;
		} else if (itemId == R.id.action_exit) {
			exitApplication();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStart() {
		super.onStart();
		bindNecService();
	}

	private void bindNecService() {
		if (!isBound) {
			Intent intent = new Intent(this, NecCalculationService.class);
			bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (isBound) {
			unbindService(serviceConnection);
			isBound = false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (necProjectViewModel != null) {
			necProjectViewModel.updateSettings();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (executor != null && !executor.isShutdown()) {
			executor.shutdown();
		}

		if (isBound) {
			unbindService(serviceConnection);
			isBound = false;
		}

		if (isFinishing()) {
			stopService(new Intent(this, NecCalculationService.class));
		}
	}

	/**
	 * Public method for starting a new NEC2 calculation from UI
	 *
	 * @param necInput Cleaned source code, standard NEC text format
	 */
	public void runNecCalculation(String necInput) {
		// Clear previous calculation results if any
		necResultViewModel.clearResult();

		// Simulation task
		Runnable calculationTask = () -> {
			// 1. Showing the start of the calculation in the UI
			runOnUiThread(() -> {
				necResultViewModel.onCalculationStarted();
				UiUtils.showSnackbar(this, getString(R.string.message_simulation_start));
			});

			try {
				// 2. Interprocess AIDL call (blocks the current background executor thread)
				String resultJson = necService.runSimulation(necInput);

				// 3. Return the result to the main UI thread
				runOnUiThread(() -> necResultViewModel.onCalculationSuccess(resultJson));

			} catch (DeadObjectException e) {
				Log.e(TAG, "IPC error: necpp service process was killed", e);
				handleServiceDisconnectInUi(getString(R.string.message_service) + ":\n" + getString(R.string.calculation_canceled));

			} catch (RemoteException e) {
				Log.e(TAG, "IPC communication error with necpp service", e);
				handleServiceDisconnectInUi(getString(R.string.message_error_service) + ":\n" + e.getLocalizedMessage());

			} catch (IllegalArgumentException e) {
				Log.e(TAG, "Error in nec2++ core", e);
				runOnUiThread(() -> necResultViewModel.onCalculationFailed(getString(R.string.message_error_nec) + ":\n" + e.getMessage()));

			} catch (IllegalStateException e) {
				Log.e(TAG, "Error in nec2core", e);
				runOnUiThread(() -> necResultViewModel.onCalculationFailed(getString(R.string.message_error_nec2core) + ":\n" + e.getMessage()));

			} catch (Exception e) {
				Log.e(TAG, "An unexpected exception occurred", e);
				runOnUiThread(() -> necResultViewModel.onCalculationFailed(getString(R.string.message_error_unknown) + ":\n" + e.getMessage()));
			}
		};

		// Scenario A: The service is alive and Binder is active - start the simulation task immediately
		if (isBound && necService != null && necService.asBinder().isBinderAlive()) {
			executor.execute(calculationTask);
			return;
		}

		// Scenario B: The process was killed (via cancel/killProcess) - reconnect
		Log.i(TAG, "Service is not connected or binder is dead. Rebinding before calculation...");

		cleanupDeadServiceConnection();

		// The task is saved for calling in onServiceConnected
		pendingCalculationTask = calculationTask;

		Intent intent = new Intent(this, NecCalculationService.class);
		boolean success = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		if (!success) {
			pendingCalculationTask = null;
			UiUtils.showSnackbar(this, getString(R.string.message_service_not_ready));
		}
	}

	/**
	 * Helper method to safely reset a dead connection
	 */
	private void cleanupDeadServiceConnection() {
		if (isBound) {
			try {
				unbindService(serviceConnection);
			} catch (Exception ignored) {
			}
			isBound = false;
		}
		necService = null;
	}

	/**
	 * Helper method for sending IPC error messages to the UI thread
	 *
	 * @param errorMessage Error message
	 */
	private void handleServiceDisconnectInUi(String errorMessage) {
		runOnUiThread(() -> {
			cleanupDeadServiceConnection();
			necResultViewModel.onCalculationFailed(errorMessage);
		});
	}

	/**
	 * Forcefully Stopping the Simulation Service
	 */
	private void forceStopService() {
		if (necService != null && necResultViewModel.isSimulationRunning()) {
			executor.execute(() -> {
				try {
					Log.d(TAG, "Sending cancel signal to C++ service via AIDL...");
					necService.cancelSimulation();
				} catch (RemoteException e) {
					Log.e(TAG, "Service process was already killed or unreachable", e);
				} finally {
					runOnUiThread(() -> necResultViewModel.onCalculationCanceled());
				}
			});
		}
	}

	/**
	 * Completely exit the application and stop service
	 */
	private void exitApplication() {
		if (isBound) {
			unbindService(serviceConnection);
			isBound = false;
		}
		stopService(new Intent(this, NecCalculationService.class));
		finishAndRemoveTask();
	}
}