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

package com.radioacoustick.opennec2.viewer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.ui.geometry.GeometryFragment;

/**
 * A host fragment containing the “Input file” and “Geometry” tabs
 */
public class MainHostFragment extends Fragment {

	public MainHostFragment() {
		// Required empty public constructor
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
									 Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View root = inflater.inflate(R.layout.fragment_main_host, container, false);
		ViewPager2 viewPager = root.findViewById(R.id.view_pager_main);
		TabLayout tabLayout = root.findViewById(R.id.tab_layout_main);

		viewPager.setAdapter(new FragmentStateAdapter(this) {
			@NonNull
			@Override
			public Fragment createFragment(int position) {
				if (position == 1) {
					return new GeometryFragment();
				}
				return new NecInputFragment();
			}

			@Override
			public int getItemCount() {
				return 2;
			}
		});
		new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
			switch (position) {
				case 0:
					tab.setText(getString(R.string.input_file));
					break;
				case 1:
					tab.setText(getString(R.string.geometry));
					break;
			}
		}).attach();
		viewPager.setUserInputEnabled(false);
		return root;
	}
}