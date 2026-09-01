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

package com.radioacoustick.opennec2.viewer.ui.pattern;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.radioacoustick.opennec2.viewer.R;

import java.util.Locale;

/**
 * Widget class for drawing antenna radiation pattern in polar coordinate system
 */
public class PolarPatternView extends View {

	private String fileName = "";
	private float[] anglesPhi;
	private float[] gainsPhi;
	private float[] anglesTheta;
	private float[] gainsTheta;
	private float maxGain;

	private float centerX;
	private float centerY;
	private float maxRadius;
	private int horizontalPlaneColor = Color.RED;
	private int verticalPlaneColor = Color.BLUE;
	private int gridColor = Color.GRAY;
	private int textColor = Color.BLACK;
	private int legendTextColor = Color.LTGRAY;

	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
		setColor(textColor);
		setTextSize(dpToPx(10f));
		setTextAlign(Paint.Align.CENTER);
	}};

	private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
		setColor(gridColor);
		setStyle(Paint.Style.STROKE);
		setStrokeWidth(dpToPx(1f));
	}};

	// The main line of the graph for the horizontal plane (Phi)
	private final Paint graphPhiPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
		setColor(horizontalPlaneColor);
		setStyle(Paint.Style.STROKE);
		setStrokeWidth(6f);
	}};

	// The main line of the graph for the vertical plane (Theta)
	private final Paint graphThetaPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
		setColor(verticalPlaneColor);
		setStyle(Paint.Style.STROKE);
		setStrokeWidth(6f);
		// Hachures the Theta graph,
		// so that the graphs are easily distinguishable even on black and white screens.
		setPathEffect(new android.graphics.DashPathEffect(new float[]{5, 4}, 0));
	}};

	private final Paint legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
		setColor(legendTextColor);
		setTextSize(dpToPx(11f));
		setTextAlign(Paint.Align.LEFT);
	}};

	private final Paint legendLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
		setStrokeWidth(dpToPx(2f));
		setStyle(Paint.Style.STROKE);
	}};

	// An auxiliary method for scaling sizes to fit screen density
	private float dpToPx(float dp) {
		return dp * getResources().getDisplayMetrics().density;
	}

	public PolarPatternView(Context context) {
		super(context);
		initColors(context, null, 0);
	}

	public PolarPatternView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initColors(context, attrs, 0);
	}

	public PolarPatternView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initColors(context, attrs, defStyleAttr);
	}

	/**
	 * Method for initializing the primary colors of graphs and text
	 */
	private void initColors(Context context, AttributeSet attrs, int defStyleAttr) {
		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(
				 attrs,
				 R.styleable.PolarPatternView,
				 defStyleAttr,
				 0
			);

			try {
				horizontalPlaneColor = a.getColor(R.styleable.PolarPatternView_horizontalPlaneColor, Color.RED);
				verticalPlaneColor = a.getColor(R.styleable.PolarPatternView_verticalPlaneColor, Color.BLUE);
				gridColor = a.getColor(R.styleable.PolarPatternView_gridColor, Color.GRAY);
				textColor = a.getColor(R.styleable.PolarPatternView_textColor, Color.BLACK);
				legendTextColor = a.getColor(R.styleable.PolarPatternView_legendTextColor, Color.LTGRAY);
			} finally {
				a.recycle();
			}
		}
		graphPhiPaint.setColor(horizontalPlaneColor);
		graphThetaPaint.setColor(verticalPlaneColor);
		gridPaint.setColor(gridColor);
		textPaint.setColor(textColor);
		legendTextPaint.setColor(legendTextColor);
		invalidate();
	}

	/**
	 * Method for initializing data for drawing graphs
	 */
	public void setPatternData(String fileName, float[] anglesPhi, float[] gainsPhi, float[] anglesTheta, float[] gainsTheta, float maxGain) {
		this.fileName = fileName;
		this.anglesPhi = anglesPhi;
		this.gainsPhi = gainsPhi;
		this.anglesTheta = anglesTheta;
		this.gainsTheta = gainsTheta;
		this.maxGain = maxGain;
		invalidate();
	}

	/**
	 * Method for cleaning graphs
	 */
	public void clear() {
		this.anglesPhi = null;
		this.gainsPhi = null;
		this.anglesTheta = null;
		this.gainsTheta = null;
		invalidate();
	}


	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);

		float width = getWidth();
		float height = getHeight();
		this.centerX = width / 2;
		this.centerY = height / 2;
		this.maxRadius = Math.min(centerX, centerY) * 0.85f; // indent from the edge

		float minGain = maxGain - 40.0f; // dynamic range 40 dB
		float dbStep = 10.0f; // Grid step in dB

		// Draws a radial grid and labels
		drawRadialGrid(canvas);
		drawAngularLabels(canvas);

		// Drawing the Horizontal plane Radiation Pattern Curve
		if (anglesPhi != null && gainsPhi != null) {
			Path pathPhi = generatePolarPath(anglesPhi, gainsPhi, true);
			canvas.drawPath(pathPhi, graphPhiPaint);
		}

		// Drawing the Vertical plane Radiation Pattern Curve
		if (anglesTheta != null && gainsTheta != null) {
			Path pathTheta = generatePolarPath(anglesTheta, gainsTheta, false);
			canvas.drawPath(pathTheta, graphThetaPaint);
		}

		// Draws a circular grid and labels
		drawDbCirclesAndLabels(canvas, maxGain, minGain, dbStep);

		// Draws a legend
		drawLegend(canvas, centerX, centerY, maxRadius);

	}

	/**
	 * Method for generating and drawing the Radiation Pattern path
	 */
	private Path generatePolarPath(float[] angles, float[] normalizedGains, boolean isPhi) {
		Path path = new Path();

		for (int i = 0; i < angles.length; i++) {
			// Setting the orientation of the graphs
			// If isPhi = true, add 90 degrees (for Phi)
			// If false, leave the angle as is (for Theta)
			float targetAngle = isPhi ? (angles[i]) : angles[i] - 90;

			float angleRad = (float) Math.toRadians(targetAngle);
			float radius = normalizedGains[i] * maxRadius;

			float x = centerX + radius * (float) Math.cos(angleRad);
			float y = centerY + radius * (float) Math.sin(angleRad);

			if (i == 0) {
				path.moveTo(x, y);
			} else {
				path.lineTo(x, y);
			}
		}
		path.close();
		return path;
	}

	/**
	 * Method for drawing a circular grid and its labels
	 */
	private void drawDbCirclesAndLabels(Canvas canvas, float maxGain, float minGain, float dbStep) {
		float currentDb = maxGain;

		// Text alignment for the dB scale
		textPaint.setTextAlign(Paint.Align.LEFT);

		while (currentDb >= minGain) {
			// 1. Normalize the dB value to a range from 0.0 (center) to 1.0 (outer edge)
			float normalizedRadius = (currentDb - minGain) / (maxGain - minGain);

			// 2. Converts the normalized value to actual screen pixels
			float r = normalizedRadius * maxRadius;

			if (r > 0) {
				// 3. Draw a grid circle
				canvas.drawCircle(centerX, centerY, r, gridPaint);

				// 4. Formatting the dB level label
				String labelText = String.format(Locale.US, "%.0f", currentDb);

				// Setting the label text position relative to the grid
				float textY = centerY - r + dpToPx(10f);
				float textX = centerX + dpToPx(4f);

				canvas.drawText(labelText, textX, textY, textPaint);
			}

			currentDb -= dbStep;
		}

		// Sets the center alignment for the rest of the text.
		textPaint.setTextAlign(Paint.Align.CENTER);
	}

	/**
	 * Radial grid rendering method
	 */
	private void drawRadialGrid(@NonNull Canvas canvas) {
		// Main Axial crosshairs (0°, 90°, 180°, 270°)
		canvas.drawLine(centerX - maxRadius, centerY, centerX + maxRadius, centerY, gridPaint);
		canvas.drawLine(centerX, centerY - maxRadius, centerX, centerY + maxRadius, gridPaint);

		// Additional rays of angles
		drawRadialLine(canvas, centerX, centerY, maxRadius, 30);
		drawRadialLine(canvas, centerX, centerY, maxRadius, 60);
		drawRadialLine(canvas, centerX, centerY, maxRadius, 120);
		drawRadialLine(canvas, centerX, centerY, maxRadius, 150);
	}

	/**
	 * Method for drawing labels for a radial grid
	 */
	private void drawAngularLabels(Canvas canvas) {
		float labelRadius = maxRadius + dpToPx(12f); // Offsetting the labels outward from the outer circle

		// Draws labels every 30 degrees
		for (int angle = 0; angle < 360; angle += 30) {
			// Convert the angle to radians, taking into account that 0 degrees is at the top (zenith)
			double angleRad = Math.toRadians(angle - 90);

			float x = centerX + labelRadius * (float) Math.cos(angleRad);
			// Offset Y by half the font height to perfectly center the text vertically
			float y = centerY + labelRadius * (float) Math.sin(angleRad) + (textPaint.getTextSize() / 3);

			// Formats text according to a scale from 0 to 180 and from -15 to -165
			int displayAngle = (angle <= 180) ? angle : angle - 360;

			String labelText;
			switch (displayAngle) {
				case 0:
					labelText = "0  Z";
					break;
				case 90:
					labelText = "90  XY";
					break;
				case -90:
					labelText = "-90";
					break;
				case 180:
					labelText = "-180";
					break;
				default:
					labelText = String.valueOf(displayAngle);
					break;
			}

			canvas.drawText(labelText, x, y, textPaint);
		}
	}

	/**
	 * Method for drawing labels for a radial grid
	 */
	// Auxiliary method for calculating grid rays
	private void drawRadialLine(Canvas canvas, float centerX, float centerY, float maxRadius, int angleDeg) {
		float cos = (float) Math.cos(Math.toRadians(angleDeg));
		float sin = (float) Math.sin(Math.toRadians(angleDeg));
		canvas.drawLine(centerX - maxRadius * cos, centerY - maxRadius * sin,
			 centerX + maxRadius * cos, centerY + maxRadius * sin, gridPaint);
	}

	/**
	 * Draws the file name in the upper left corner and the legend in the lower left corner.
	 */
	private void drawLegend(Canvas canvas, float centerX, float centerY, float maxRadius) {
		// Calculate the base X-coordinate of the left edge
		float startX = centerX - maxRadius + dpToPx(8f);

		if (fileName != null) {
			// 1. Display the file name in the upper left corner
			if (!fileName.isEmpty()) {
				// Rising above the top edge of the graph
				float topY = centerY - maxRadius - dpToPx(12f);
				if (topY < dpToPx(16f)) {
					topY = dpToPx(16f);
				}

				String displayText = fileName;
				float maxTextWidth = maxRadius - 50.0f;
				// If the file name is longer than the available width, cut it off and add “…”
				if (legendTextPaint.measureText(fileName) > maxTextWidth) {
					// Calculates how many characters fit, taking into account the ellipsis “…”
					float dotsWidth = legendTextPaint.measureText("…");
					int count = legendTextPaint.breakText(fileName, true, maxTextWidth - dotsWidth, null);
					displayText = fileName.substring(0, count) + "…";
				}

				legendTextPaint.setFakeBoldText(true);
				canvas.drawText(displayText, startX, topY, legendTextPaint);
				legendTextPaint.setFakeBoldText(false);
			}
		}

		// 2. Drawing the graph legend in the lower left corner
		float startY = centerY + maxRadius + dpToPx(16f);

		float lineLength = dpToPx(16f); // Color line length
		float textOffset = dpToPx(6f);  // Indent from line to text
		float rowHeight = dpToPx(16f);  // Line spacing

		// --- Line 1: Horizontal plane ---
		legendLinePaint.setColor(horizontalPlaneColor);
		legendLinePaint.setPathEffect(null);

		canvas.drawLine(startX, startY, startX + lineLength, startY, legendLinePaint);
		canvas.drawText("Horizontal plane", startX + lineLength + textOffset, startY + dpToPx(3.5f), legendTextPaint);

		// --- Line 2: Vertical plane ---
		float nextY = startY + rowHeight;
		legendLinePaint.setColor(verticalPlaneColor);
		legendLinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dpToPx(4f), dpToPx(2f)}, 0));

		canvas.drawLine(startX, nextY, startX + lineLength, nextY, legendLinePaint);
		canvas.drawText("Vertical plane", startX + lineLength + textOffset, nextY + dpToPx(3.5f), legendTextPaint);

		legendLinePaint.setPathEffect(null);
	}
}