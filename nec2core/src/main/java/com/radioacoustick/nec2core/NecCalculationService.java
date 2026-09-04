// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Valery Kustarev (https://github.com/radioacoustick)
/*
 * This file is part of nec2core engine for android app.
 *
 * Nec2core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Nec2core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Nec2core. If not, see <https://www.gnu.org/licenses/>.
 */

package com.radioacoustick.nec2core;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Isolated background service executing in dedicated process (:nec_engine_process)
 * for heavy C++ NEC calculations.
 */
@Keep
public class NecCalculationService extends Service {

	private static final String TAG = "NecCalculationService";
	public static final String ACTION_STOP_SERVICE = "com.radioacoustick.nec2core.ACTION_STOP_NEC_SERVICE";
	private static final String CHANNEL_ID = "nec_calculation_channel";
	private static final int NOTIFICATION_ID = 1001;

	static {
		System.loadLibrary("nec2core");
	}

	@Keep
	private native String runNecCalculationNative(String input);

	private final INecService.Stub binder = new INecService.Stub() {

		@Override
		public String runSimulation(String necInputContent) throws RemoteException {
			showNotification();
			try {
				return runNecCalculationNative(necInputContent);
			} finally {
				hideNotification();
			}
		}

		@Override
		public void cancelSimulation() throws RemoteException {
			Log.w(TAG, "Calculation cancel requested. Terminating engine process.");
			killEngineProcess();
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
			Log.w(TAG, "Stop action received from notification intent.");
			killEngineProcess();
			return START_NOT_STICKY;
		}
		return START_NOT_STICKY;
	}

	private void showNotification() {
		// Checking permissions for Android 13+
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
				 != PackageManager.PERMISSION_GRANTED) {
				Log.d(TAG, "Notification skipped: POST_NOTIFICATIONS permission not granted.");
				return;
			}
		}
		try {
			NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, createNotification());
		} catch (SecurityException e) {
			Log.e(TAG, "Failed to post notification", e);
		}
	}

	private void hideNotification() {
		try {
			NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
		} catch (Exception e) {
			Log.e(TAG, "Failed to cancel notification", e);
		}
	}

	private Notification createNotification() {
		Intent stopIntent = new Intent(this, NecCalculationService.class);
		stopIntent.setAction(ACTION_STOP_SERVICE);

		int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
		PendingIntent stopPendingIntent = PendingIntent.getService(
			 this,
			 0,
			 stopIntent,
			 flags
		);

		return new NotificationCompat.Builder(this, CHANNEL_ID)
			 .setContentTitle(getString(R.string.notification_title))
			 .setContentText(getString(R.string.notification_content))
			 .setSmallIcon(R.drawable.ic_nec)
			 .setOngoing(true)
			 .setPriority(NotificationCompat.PRIORITY_DEFAULT)
			 .addAction(R.drawable.ic_cancel, getString(R.string.stop), stopPendingIntent)
			 .build();
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(
				 CHANNEL_ID,
				 getString(R.string.notification_channel_name),
				 NotificationManager.IMPORTANCE_DEFAULT
			);
			channel.setDescription(getString(R.string.notification_channel_descript));

			NotificationManager manager = getSystemService(NotificationManager.class);
			if (manager != null) {
				manager.createNotificationChannel(channel);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		Log.w(TAG, "Main application UI was closed. Killing calculation process.");
		killEngineProcess();
	}

	private void killEngineProcess() {
		hideNotification();
		Process.killProcess(Process.myPid());
	}

	@Override
	public void onDestroy() {
		hideNotification();
		Log.d(TAG, "NecCalculationService destroyed.");
		super.onDestroy();
	}
}