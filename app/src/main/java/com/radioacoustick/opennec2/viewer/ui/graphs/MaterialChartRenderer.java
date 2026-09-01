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

package com.radioacoustick.opennec2.viewer.ui.graphs;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

import androidx.annotation.AttrRes;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.radioacoustick.opennec2.viewer.R;

import java.util.List;

/**
 * A helper class for configuring MPAndroidChart widgets
 * and displaying charts.
 */
public class MaterialChartRenderer {

	private final LineChart chart;
	private final int colorPrimary;
	private final int colorSecondary;
	private final int colorOnSurface;
	private final int colorOutline;

	public MaterialChartRenderer(LineChart chart) {
		this.chart = chart;
		Context context = chart.getContext();

		// Extract dynamic Material 3 colors from the current app theme to apply them to a MPAndroidChart widget
		this.colorPrimary = resolveThemeColor(context, androidx.appcompat.R.attr.colorPrimary);
		this.colorSecondary = resolveThemeColor(context, com.google.android.material.R.attr.colorSecondary);
		this.colorOnSurface = resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface);
		this.colorOutline = resolveThemeColor(context, com.google.android.material.R.attr.colorOutline);

		chart.setNoDataText(context.getString(R.string.no_graph_data));
		chart.setNoDataTextColor(colorOnSurface);

		setupChartStyle();
	}

	/**
	 * Method of graph stylization
	 */
	private void setupChartStyle() {
		chart.getDescription().setEnabled(false);
		chart.setDrawGridBackground(false);
		chart.setTouchEnabled(true);
		chart.setDragEnabled(true);
		chart.setScaleEnabled(true);
		chart.setPinchZoom(true);

		// Setting the X-Axis (Frequency)
		XAxis xAxis = chart.getXAxis();
		xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
		xAxis.setTextColor(colorOnSurface);
		xAxis.setGridColor(colorOutline);
		xAxis.setDrawAxisLine(true);

		// Adjusting the left Y-axis (e.g. VSWR)
		YAxis leftAxis = chart.getAxisLeft();
		leftAxis.setTextColor(colorOnSurface);
		leftAxis.setGridColor(colorOutline);
		leftAxis.setDrawAxisLine(true);

		// Disable the right Y axis
		chart.getAxisRight().setEnabled(false);

		// Stylization of the legend
		chart.getLegend().setTextColor(colorOnSurface);
	}

	/**
	 * Single Graph Display Method
	 *
	 * @param entries Data set for the graph
	 * @param label Graph's label
	 * @param isSWR Is this the SWR graph?
	 */
	public void displayData(List<Entry> entries, String label, boolean isSWR) {
		LineDataSet dataSet = new LineDataSet(entries, label);

		// Styling the graph line
		dataSet.setColor(colorPrimary);
		dataSet.setLineWidth(2.5f);
		dataSet.setDrawCircles(entries.size() < 20); // Draws markers only if there are few dots
		dataSet.setCircleColor(colorPrimary);
		dataSet.setCircleRadius(4f);
		dataSet.setDrawValues(false); // We don't write numbers above each point.


		// If this is a VSWR graph, then we limit the minimum value on the Y axis to one.
		if (isSWR) {
			YAxis leftAxis = chart.getAxisLeft();
			leftAxis.setAxisMinimum(1.0f);
			leftAxis.resetAxisMinimum();
			leftAxis.setAxisMinimum(1.0f);
		}

		// Line smoothing (cubic spline)
		dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

		LineData lineData = new LineData(dataSet);
		chart.setData(lineData);
		chart.invalidate();
	}

	/**
	 * Dual graph display method (eg impedance)
	 *
	 * @param rEntries Data set for the graph
	 * @param xEntries Data set for the graph
	 * @param labelR Graph's label
	 * @param labelX Graph's label
	 */
	public void displayDualData(List<Entry> rEntries, List<Entry> xEntries, String labelR, String labelX) {
		// 1. Create the first data set: Active Resistance (R)
		LineDataSet rDataSet = new LineDataSet(rEntries, labelR);
		rDataSet.setColor(colorPrimary);
		rDataSet.setLineWidth(2.5f);
		rDataSet.setDrawCircles(rEntries.size() < 20);
		rDataSet.setCircleColor(colorPrimary);
		rDataSet.setCircleRadius(4f);
		rDataSet.setDrawValues(false);
		rDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

		// 2.Create a second data set: Reactance (X)
		LineDataSet xDataSet = new LineDataSet(xEntries, labelX);
		// Use a secondary theme color to differentiate the graphs
		xDataSet.setColor(colorSecondary);
		xDataSet.setLineWidth(2.5f);
		xDataSet.setDrawCircles(xEntries.size() < 20);
		xDataSet.setCircleColor(colorSecondary);
		xDataSet.setCircleRadius(4f);
		xDataSet.setDrawValues(false);
		xDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

		// Optional: Make the reactance line dashed to make the graph easier to read for color-blind people.
		xDataSet.enableDashedLine(10f, 10f, 0f);

		chart.getAxisLeft().setDrawZeroLine(true); // Selects a horizontal line at level 0
		chart.getAxisLeft().setZeroLineColor(Color.GRAY);
		chart.getAxisLeft().setZeroLineWidth(1f);

		// 3. Combines both datasets into one LineData
		LineData lineData = new LineData(rDataSet, xDataSet);

		// 4. Send to chart and redraw
		chart.setData(lineData);
		chart.invalidate();
	}

	/**
	 * Method for getting Material 3 colors of the current app theme
	 */
	private int resolveThemeColor(Context context, @AttrRes int attrRes) {
		TypedValue typedValue = new TypedValue();
		context.getTheme().resolveAttribute(attrRes, typedValue, true);
		return typedValue.data;
	}
}
