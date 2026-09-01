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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.radioacoustick.nec2core.INecService;
import com.radioacoustick.nec2core.NecCalculationService;
import com.radioacoustick.opennec2.viewer.domain.NecProjectViewModel;
import com.radioacoustick.opennec2.viewer.domain.NecResultViewModel;
import com.radioacoustick.opennec2.viewer.nec.NecGeometryParser;
import com.radioacoustick.opennec2.viewer.nec.NecValidator;
import com.radioacoustick.opennec2.viewer.nec.Wire;
import com.radioacoustick.opennec2.viewer.ui.utils.EdgeToEdgeHelper;
import com.radioacoustick.opennec2.viewer.ui.MainHostFragment;
import com.radioacoustick.opennec2.viewer.ui.utils.UiUtils;
import com.radioacoustick.opennec2.viewer.ui.graphs.GraphsHostFragment;
import com.radioacoustick.opennec2.viewer.ui.pattern.PatternFragment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

	private CircularProgressIndicator progressIndicator;
	private NecProjectViewModel necProjectViewModel;
	private NecResultViewModel necResultViewModel;
	private BottomNavigationView bottomNavigationView;
	private static final String TAG = "MainActivity";

	private INecService necService;
	private UiUtils uiUtils;
	private boolean isBound = false;

	// Background thread pool for dispatching heavy IPC tasks to avoid freezing the UI
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private final ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			necService = INecService.Stub.asInterface(service);
			isBound = true;
			Log.d(TAG, "Connection with C++ service established");
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			necService = null;
			isBound = false;
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
					 return;
				 }
				 uiUtils.readNecFromUri(uri, necProjectViewModel);
			 }
		 }
	);

	/**
	 * When the application is first launched, it asks for permission
	 * to display notifications that are necessary
	 * for the nec2++ modeling service to function correctly.
	 */
	private final ActivityResultLauncher<String> requestPermissionLauncher =
		 registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
			 if (isGranted) {
				 // Permission granted! Now we can safely launch the service.
				 startNecService();
			 } else {
				 UiUtils.showSnackbar(this, getString(R.string.message_calculation_not_possible));
			 }
		 });

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
		necResultViewModel = new ViewModelProvider(MainActivity.this).get(NecResultViewModel.class);
		uiUtils = new UiUtils(this);

		initViews();
		uiUtils.handleIncomingIntent(getIntent(), necProjectViewModel);

		uiUtils.loadFragment(new MainHostFragment());
	}

	private void initViews() {
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ViewGroup contentRoot = findViewById(android.R.id.content);
		bottomNavigationView = findViewById(R.id.bottom_navigation);
		progressIndicator = findViewById(R.id.progress_bar);

		View mainLayout = contentRoot.getChildAt(0);
		EdgeToEdgeHelper.applyInsets(getApplicationContext(), mainLayout, toolbar, bottomNavigationView);

		// Switching fragments via Bottom Navigation
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
			progressIndicator.setVisibility(necResultViewModel.isSimulationRunning() ? View.VISIBLE : View.GONE);
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
				// Clearing old calculation results when loading a new file
				necResultViewModel.clearResult();
				bottomNavigationView.setSelectedItemId(R.id.nav_geometry);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == R.id.action_about) {
			uiUtils.showAboutDialog();
			return true;
		}
		if (item.getItemId() == R.id.action_open_file) {
			openFilePicker();
			return true;
		}
		if (item.getItemId() == R.id.action_settings) {
			//uiUtils.showThemeSelectionDialog();
			Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
			startActivity(settingsActivity);
			return true;
		}
		if (item.getItemId() == R.id.action_start_nec_engine) {
			Intent intent = new Intent(this, NecCalculationService.class);

			// 1. Running the service in Foreground mode
			ContextCompat.startForegroundService(this, intent);

			// 2. AIDL Interface Binding
			if (!isBound) {
				bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
			} else {
				UiUtils.showSnackbar(this, getString(R.string.message_service_already_running));
			}
			return true;
		}
		if (item.getItemId() == R.id.action_exit) {
			exitApplication();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStart() {
		super.onStart();
		String postNotificationsPermission = "android.permission.POST_NOTIFICATIONS";
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (ContextCompat.checkSelfPermission(this, postNotificationsPermission)
				 == PackageManager.PERMISSION_GRANTED) {
				startNecService();
			} else {
				// Request permission for tray notifications
				requestPermissionLauncher.launch(postNotificationsPermission);
			}
		} else {
			startNecService();
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

		// 1. Terminate the local thread pool executor
		if (executor != null && !executor.isShutdown()) {
			executor.shutdown();
		}

		// 2. Be sure to unbind ServiceConnection to avoid memory leaks.
		if (isBound) {
			unbindService(serviceConnection);
			isBound = false;
		}

		// 3. Stop the service ONLY if the user actually closes the Activity, and not just rotates the screen.
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
		if (!isBound || necService == null) {
			UiUtils.showSnackbar(findViewById(android.R.id.content), getString(R.string.message_service_not_ready), findViewById(R.id.btn_calculate_main));
			return;
		}

		necResultViewModel.clearResult();
		necResultViewModel.onCalculationStarted();
		UiUtils.showSnackbar(findViewById(android.R.id.content), getString(R.string.message_simulation_start), findViewById(R.id.btn_calculate_main));

		// IPC call is started in background thread!
		executor.execute(() -> {
			try {
				// Calling the AIDL method (executed in the :nec_engine process)
				String resultJson = necService.runSimulation(necInput);
				// Return the result to the main UI thread
				necResultViewModel.onCalculationSuccess(resultJson);

			} catch (RemoteException e) {
				Log.e(TAG, "IPC communication error with necpp service", e);
				necResultViewModel.onCalculationFailed(getString(R.string.message_error_service) + ":\n" + e.getLocalizedMessage());
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "Error in nec2++ core", e);
				necResultViewModel.onCalculationFailed(getString(R.string.message_error_nec) + ":\n" + e.getMessage());
			} catch (IllegalStateException e) {
				Log.e(TAG, "Error in nec2core", e);
				necResultViewModel.onCalculationFailed(getString(R.string.message_error_nec2core) + ":\n" + e.getMessage());
			} catch (Exception e) {
				Log.e(TAG, "An unexpected exception occurred", e);
				necResultViewModel.onCalculationFailed(getString(R.string.message_error_unknown) + ":\n" + e.getMessage());
			}
		});
	}

	/**
	 * Start the nec2++ service in an independent process and bind to it.
	 */
	private void startNecService() {
		Intent intent = new Intent(this, NecCalculationService.class);
		ContextCompat.startForegroundService(this, intent);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
	}

	/**
	 * Completely exit the application and stop service
	 */
	private void exitApplication() {

		if (isBound) {
			unbindService(serviceConnection);
			isBound = false;
		}

		Intent stopServiceIntent = new Intent(this, NecCalculationService.class);
		stopService(stopServiceIntent);

		finishAndRemoveTask();

		System.exit(0);
	}

}