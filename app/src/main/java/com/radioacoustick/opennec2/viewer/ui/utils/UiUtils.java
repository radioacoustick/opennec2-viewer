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

package com.radioacoustick.opennec2.viewer.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.radioacoustick.opennec2.viewer.M_Application;
import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.domain.NecProjectViewModel;
import com.radioacoustick.opennec2.viewer.settings.AppSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * User Interface Control Methods, Custom Class
 */
public class UiUtils {

	/**
	 * Interface for the "showFrequencyInputDialog"
	 */
	public interface FrequencyDialogCallback {
		/**
		 * @param isConfirmed  true, if the user entered the frequency
		 * @param frequencyMHz entered frequency in MHz (if isConfirmed == true)
		 */
		void onResult(boolean isConfirmed, float frequencyMHz);
	}

	private final AppCompatActivity activity;

	public UiUtils(AppCompatActivity activity) {
		this.activity = activity;
	}

	/**
	 * The catching a *.nec file if it was sent using Intent by a third-party application
	 */
	public void handleIncomingIntent(Intent intent, NecProjectViewModel necProjectViewModel) {
		String action = intent.getAction();
		String type = intent.getType();

		if (Intent.ACTION_VIEW.equals(action) && type != null) {
			Uri fileUri = intent.getData();
			if (fileUri != null) {
				readNecFromUri(fileUri, necProjectViewModel);
			}
		} else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
			String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
			if (sharedText != null) {
				// Extract the file name from EXTRA_TITLE or EXTRA_SUBJECT
				String fileName = intent.getStringExtra(Intent.EXTRA_TITLE);
				if (fileName == null) {
					fileName = intent.getStringExtra(Intent.EXTRA_SUBJECT);
				}
				if (fileName == null) {
					fileName = "imported_model.nec"; // default file name
				}
				necProjectViewModel.setRawNecText(fileName, sharedText);
			}
		}
	}

	/**
	 * Reading file contents by URI
	 */
	public void readNecFromUri(Uri uri, NecProjectViewModel necProjectViewModel) {
		StringBuilder stringBuilder = new StringBuilder();
		try (InputStream inputStream = activity.getContentResolver().openInputStream(uri);
			  BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

			String line;
			while ((line = reader.readLine()) != null) {
				stringBuilder.append(line).append("\n");
			}

			String necContent = stringBuilder.toString();
			String fileName = getFileNameFromUri(activity, uri);
			if (fileName != null)
				necProjectViewModel.setRawNecText(fileName, necContent);

		} catch (IOException e) {
			Log.e("readNecFromUri", "Error reading file", e);
		}
	}

	/**
	 * Method for replacing fragments in a container
	 */
	public boolean loadFragment(Fragment fragment) {
		if (fragment != null) {
			activity.getSupportFragmentManager()
				 .beginTransaction()
				 .replace(R.id.fragment_container, fragment)
				 .commit();
			return true;
		}
		return false;
	}

	/**
	 * Method to get the file name from URI to display it in the user interface.
	 */
	public static String getFileNameFromUri(Context context, Uri uri) {
		String result = null;
		if (Objects.equals(uri.getScheme(), "content")) {
			try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
				if (cursor != null && cursor.moveToFirst()) {
					// Looking for the column with the displayed file name
					int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					if (nameIndex != -1) {
						result = cursor.getString(nameIndex);
					}
				}
			} catch (Exception e) {
				Log.w("getFileNameFromUri", e);
			}
		}
		if (result == null) {
			// Fallback if the "file" schema or query fails
			result = uri.getPath();
			assert result != null;
			int cut = result.lastIndexOf('/');
			if (cut != -1) {
				result = result.substring(cut + 1);
			}
		}
		return result;
	}

	/**
	 * Method to get the application version to display it in the user interface.
	 */
	public String getAppVersionName() {
		try {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
				return activity.getPackageManager().getPackageInfo(
					 activity.getPackageName(),
					 PackageManager.PackageInfoFlags.of(0)
				).versionName;
			} else {
				// Old method for backward compatibility
				return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
			}
		} catch (PackageManager.NameNotFoundException e) {
			Log.w("getAppVersionName", e);
			return "1.0.0"; // Default fallback
		}
	}

	/**
	 * Dialog for displaying information “About the app”.
	 */
	public void showAboutDialog() {
		// 1. Get the application version from BuildConfig / Package
		String appVersion = getAppVersionName();
		// 2. Reading and processing an HTML file from assets
		String htmlContent = loadTextFromAssets("about.html");
		htmlContent = htmlContent.replace("%APP_NAME%", activity.getString(R.string.app_name));
		htmlContent = htmlContent.replace("%APP_VERSION%", appVersion);

		TextView textView = new TextView(activity);

		textView.setText(Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY));

		textView.setMovementMethod(LinkMovementMethod.getInstance());

		int padding = (int) (20 * activity.getResources().getDisplayMetrics().density);
		textView.setPadding(padding, padding / 2, padding, 0);
		textView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);

		new MaterialAlertDialogBuilder(activity)
			 .setTitle(activity.getString(R.string.action_about))
			 .setView(textView)
			 .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
			 .setNeutralButton("GPLv3 License", (dialog, which) -> showLicenseBottomSheet())
			 .show();
	}

	/**
	 * Dialog box for displaying the full text of the GPLv3 license.
	 */
	public void showLicenseBottomSheet() {
		// Create a BottomSheetDialog to display the full license text
		BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(activity);

		NestedScrollView scrollView = new NestedScrollView(activity);
		TextView tvLicense = new TextView(activity);

		tvLicense.setText(loadTextFromAssets("gplv3.txt"));

		int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
		tvLicense.setPadding(padding, padding, padding, padding);

		scrollView.addView(tvLicense);
		bottomSheetDialog.setContentView(scrollView);

		bottomSheetDialog.show();
	}

	/**
	 * Showing the snack bar
	 * Call from an Activity without an anchor
	 */
	public static void showSnackbar(Activity activity, String message) {
		showSnackbar(activity, message, null);
	}

	/**
	 * Showing the snack bar
	 * Call from Activity with optional anchor
	 */
	public static void showSnackbar(Activity activity, String message, View anchorLayout) {
		if (activity == null) return;

		View rootView = activity.findViewById(android.R.id.content);
		showSnackbar(rootView, message, anchorLayout);
	}

	/**
	 * Showing the snack bar
	 * Basic method for working with View (for Fragments or other Views)
	 */
	public static void showSnackbar(View rootView, String message, View anchorLayout) {
		if (rootView == null || message == null || message.trim().isEmpty()) {
			return;
		}

		View container = (anchorLayout != null) ? anchorLayout : rootView.findViewById(android.R.id.content);
		if (container == null) {
			container = rootView;
		}

		Snackbar snackbar = Snackbar.make(container, message, Snackbar.LENGTH_SHORT);

		if (anchorLayout != null) {
			snackbar.setAnchorView(anchorLayout);
		}

		snackbar.show();
	}

	/**
	 * Method to read a text file from an “assets” folder.
	 *
	 * @param fileName The name of the source file
	 * @return A string containing the file's content
	 */
	public String loadTextFromAssets(String fileName) {
		String htmlContent = "";
		try (InputStream is = activity.getAssets().open(fileName)) {
			byte[] buffer = new byte[is.available()];
			is.read(buffer);
			return new String(buffer, StandardCharsets.UTF_8);
		} catch (IOException e) {
			Log.w("loadTextFromAssets", e);
			return "";
		}
	}

	/**
	 * Dialog for selecting the calculation frequency in the absence of a FR card
	 *
	 * @param activity Calling method activity
	 * @param callback Callback interface
	 */
	public static void showFrequencyInputDialog(Activity activity, FrequencyDialogCallback callback) {
		Context context = activity.getBaseContext();
		EditText input = new EditText(context);
		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		input.setHint("145.0");

		FrameLayout container = new FrameLayout(context);
		int margin = (int) (16 * context.getResources().getDisplayMetrics().density);
		container.setPadding(margin, 0, margin, 0);
		container.addView(input);

		new MaterialAlertDialogBuilder(context)
			 .setTitle(context.getString(R.string.title_freq_not_found))
			 .setMessage(context.getString(R.string.message_freq_not_found) + ":")
			 .setView(container)
			 .setPositiveButton("OK", (dialog, which) -> {
				 String val = input.getText().toString().trim();
				 if (!val.isEmpty()) {
					 try {
						 float freq = Float.parseFloat(val);
						 //Success: Pass true and the entered frequency
						 callback.onResult(true, freq);
					 } catch (NumberFormatException e) {
						 showSnackbar(activity, context.getString(R.string.message_error_freq_not_found));
						 callback.onResult(false, 0f);
					 }
				 } else {
					 callback.onResult(false, 0f);
				 }
			 })
			 .setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> {
				 // The user refused - passing false
				 callback.onResult(false, 0f);
			 })
			 .setOnCancelListener(dialog -> {
				 // The tap occurred outside of a dialog or via the "Back" button
				 callback.onResult(false, 0f);
			 })
			 .show();
	}
}
