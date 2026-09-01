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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.core.app.NotificationCompat;

/**
 * Isolated service for performing background calculations NEC (Numerical Electromagnetics Code).
 */
@Keep
public class NecCalculationService extends Service {

	private static final String TAG = "NecCalculationService";
	public static final String ACTION_STOP_SERVICE = "com.radioacoustick.nec2core.ACTION_STOP_NEC_SERVICE";
	private static final String CHANNEL_ID = "nec_channel";
	private static final int NOTIFICATION_ID = 1001;

	static {
		System.loadLibrary("nec2core");
	}

	@Keep
	private native String runNecCalculationNative(String input);

	// TODO: native void cancelNecCalculationNative();

	private final INecService.Stub binder = new INecService.Stub() {
		@Override
		public String runSimulation(String necInputContent) throws RemoteException {
			return runNecCalculationNative(necInputContent);
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
			Log.w(TAG, "Stop action received from notification. Killing process " + Process.myPid());
			stopServiceAndKillProcess();
			return START_NOT_STICKY;
		}

		// Showing Foreground Notification when starting the service
		startForegroundServiceInternal();

		return START_STICKY;
	}

	private void startForegroundServiceInternal() {
		Notification notification = createNotification();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(
				 NOTIFICATION_ID,
				 notification,
				 ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
			);
		} else {
			startForeground(NOTIFICATION_ID, notification);
		}
	}

	private Notification createNotification() {
		// Intent for the force stop button in the notification
		Intent stopIntent = new Intent(this, NecCalculationService.class);
		stopIntent.setAction(ACTION_STOP_SERVICE);

		int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
		pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

		PendingIntent stopPendingIntent = PendingIntent.getService(
			 this,
			 0,
			 stopIntent,
			 pendingFlags
		);

		return new NotificationCompat.Builder(this, CHANNEL_ID)
			 .setContentTitle(getString(R.string.notification_title))
			 .setContentText(getText(R.string.notification_content))
			 .setSmallIcon(R.drawable.ic_nec)
			 .setOngoing(true)
			 .setPriority(NotificationCompat.PRIORITY_LOW)
			 // Adding a Force Close Button
			 .addAction(
				  R.drawable.ic_cancel,
				  getString(R.string.stop),
				  stopPendingIntent
			 )
			 .build();
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(
				 CHANNEL_ID,
				 getString(R.string.notification_channel_name),
				 NotificationManager.IMPORTANCE_LOW
			);
			channel.setDescription(getString(R.string.notification_channel_descript));

			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				manager.createNotificationChannel(channel);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Launch Foreground when bound to the UI process
		startForegroundServiceInternal();
		return binder;
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		Log.w(TAG, "Application closed. Terminating C++ process: " + Process.myPid());
		stopServiceAndKillProcess();
	}

	private void stopServiceAndKillProcess() {
		stopForeground(STOP_FOREGROUND_REMOVE);
		stopSelf();

		// Since the service is isolated in a separate process (:nec_engine_process),
		// killProcess immediately terminates the running C++ code.
		Process.killProcess(Process.myPid());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "NecCalculationService has been destroyed.");
	}
}