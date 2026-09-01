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

package com.radioacoustick.opennec2.viewer.ui.geometry;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.domain.NecProjectViewModel;

/**
 * Fragment for displaying a 3D model of the antenna
 */
public class GeometryFragment extends Fragment {



	private FilamentViewer filamentViewer;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_geometry, container, false);

		android.view.TextureView textureView = view.findViewById(R.id.antennaTextureView);
		FloatingActionButton fabResetCamera = view.findViewById(R.id.fabResetCamera);
		fabResetCamera.bringToFront();

		// 2. Add a listener that calls FilamentViewer camera reset method.
		fabResetCamera.setOnClickListener(v -> {
			if (filamentViewer != null) {
				filamentViewer.resetCamera();
			}
		});

		// 1. Initializing our 3D Filament helper
		filamentViewer = new FilamentViewer(textureView);
		fabResetCamera.setOnClickListener(v -> filamentViewer.resetCamera());

		NecProjectViewModel necProjectViewModel = new ViewModelProvider(requireActivity()).get(NecProjectViewModel.class);
		necProjectViewModel.getAntennaWires().observe(getViewLifecycleOwner(), wires -> {
			if (filamentViewer != null) {
				filamentViewer.updateAntennaGeometry(wires);
			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (filamentViewer != null) filamentViewer.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (filamentViewer != null) filamentViewer.onPause();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (filamentViewer != null) {
			// Destroy the resources associated with the GeometryFragment and clears the link.
			filamentViewer.onDestroy();
			filamentViewer = null;
		}
	}
}