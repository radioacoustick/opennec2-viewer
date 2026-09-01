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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;
import com.google.android.filament.android.UiHelper;
import com.radioacoustick.opennec2.viewer.R;
import com.radioacoustick.opennec2.viewer.nec.Wire;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Class for implementing a 3D model of an antenna using Google Filament.
 */
@Keep
public class FilamentViewer {

	private final TextureView textureView;
	private UiHelper uiHelper;
	private Engine engine;
	private Renderer renderer;
	private Scene scene;
	private View view;
	private Camera camera;
	private boolean isDrawing = false;
	private SwapChain swapChain;

	//The target point where the camera is looking (center of the scene)
	private float targetX = 0.0f;
	private float targetY = 0.0f;
	private float targetZ = 0.0f;
	// Auxiliary variables for two-finger panning
	private float lastPanX;
	private float lastPanY;
	// Coordinates of the last touch with one finger
	private float lastX = 0.0f;
	private float lastY = 0.0f;

	private float rotationX = 0.0f; // Angle of rotation around the horizontal axis
	private float rotationY = 0.0f; // Angle of rotation around the vertical axis
	private float radius = 10.0f;   // Distance from camera to antenna

	private android.view.ScaleGestureDetector scaleGestureDetector;

	private int antennaEntity = 0;
	private VertexBuffer vertexBuffer3D;
	private IndexBuffer indexBuffer3D;
	private int axisEntity = 0;
	private VertexBuffer vertexBufferAxis;
	private IndexBuffer indexBufferAxis;

	private com.google.android.filament.MaterialInstance antennaMaterialInstance;
	private com.google.android.filament.MaterialInstance axisMaterialInstance;
	private com.google.android.filament.Material antennaMaterial;


	/**
	 * The constructor initializes the basic Filament components for the given SurfaceView.
	 *
	 * @param textureView TextureView, which is where 3D rendering is performed.
	 */
	public FilamentViewer(TextureView textureView) {
		this.textureView = textureView;
		setupFilament();
	}

	/**
	 * Performs initial initialization of the Filament engine, scene, camera, UiHelper and connects the touch gesture handler.
	 */
	private void setupFilament() {
		engine = Engine.create();
		renderer = engine.createRenderer();
		scene = engine.createScene();
		view = engine.createView();
		camera = engine.createCamera(engine.getEntityManager().create());

		view.setScene(scene);
		view.setCamera(camera);

		view.setPostProcessingEnabled(false);

		loadMaterial();
		updateColorsFromTheme();

		uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
		uiHelper.setOpaque(true);
		uiHelper.setRenderCallback(new UiHelper.RendererCallback() {
			@Override
			public void onNativeWindowChanged(Surface surface) {
				if (swapChain != null) {
					engine.destroySwapChain(swapChain);
				}
				if (surface != null) {
					swapChain = engine.createSwapChain(surface);
				}
			}

			@Override
			public void onDetachedFromSurface() {
				if (swapChain != null) {
					engine.destroySwapChain(swapChain);
					swapChain = null;
				}
			}

			@Override
			public void onResized(int width, int height) {
				if (width <= 0 || height <= 0) return;
				view.setViewport(new Viewport(0, 0, width, height));
				double aspect = (double) width / height;
				camera.setProjection(45.0, aspect, 0.01, 10000.0, Camera.Fov.VERTICAL);
				updateCameraPosition();
			}
		});

		uiHelper.attachTo(textureView);
		setupTouchListener();

		createCoordinateAxes(1.0f, 0.05f);
	}

	/**
	 * Updates the scene background and antenna material colors to match the current application theme.
	 */
	public void updateColorsFromTheme() {
		if (engine == null || scene == null) return;

		Context context = textureView.getContext();

		TypedValue backgroundValue = new TypedValue();
		context.getTheme().resolveAttribute(R.attr.filamentBackgroundColor, backgroundValue, true);
		int backgroundColor = backgroundValue.data;

		float bgR = Color.red(backgroundColor) / 255.0f;
		float bgG = Color.green(backgroundColor) / 255.0f;
		float bgB = Color.blue(backgroundColor) / 255.0f;

		com.google.android.filament.Skybox skybox = new com.google.android.filament.Skybox.Builder()
			 .color(bgR, bgG, bgB, 1.0f)
			 .build(engine);
		scene.setSkybox(skybox);

		TypedValue primaryValue = new TypedValue();
		context.getTheme().resolveAttribute(R.attr.filamentAntennaColor, primaryValue, true);
		int primaryColor = primaryValue.data;

		if (antennaMaterialInstance != null) {
			antennaMaterialInstance.setParameter("baseColor",
				 Color.red(primaryColor) / 255.0f,
				 Color.green(primaryColor) / 255.0f,
				 Color.blue(primaryColor) / 255.0f,
				 1.0f
			);
		}
	}

	/**
	 * Configures a touch listener for SurfaceView,
	 * allowing one-finger rotation of the 3D scene and
	 * two-finger zooming (changing the camera distance).
	 */
	@SuppressLint("ClickableViewAccessibility")
	private void setupTouchListener() {
		scaleGestureDetector = new android.view.ScaleGestureDetector(
			 textureView.getContext(),
			 new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
				 @Override
				 public boolean onScale(@NonNull android.view.ScaleGestureDetector detector) {
					 float scaleFactor = detector.getScaleFactor();

					 // Changing the camera distance
					 radius /= scaleFactor;

					 // Limiting the radius
					 if (radius < 1.0f) radius = 1.0f;
					 if (radius > 40.0f) radius = 40.0f;

					 // Reset the panning reference point to the current focus of the scale gesture
					 // to prevent the scene from jerking when zooming and panning simultaneously.
					 lastPanX = detector.getFocusX();
					 lastPanY = detector.getFocusY();

					 updateCameraPosition();
					 return true;
				 }
			 }
		);
		textureView.setOnTouchListener((v, event) -> {
			scaleGestureDetector.onTouchEvent(event);

			int pointerCount = event.getPointerCount();

			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					lastX = event.getX();
					lastY = event.getY();
					break;

				case MotionEvent.ACTION_POINTER_DOWN:
					if (pointerCount == 2) {
						lastPanX = (event.getX(0) + event.getX(1)) / 2.0f;
						lastPanY = (event.getY(0) + event.getY(1)) / 2.0f;
					}
					break;

				case MotionEvent.ACTION_POINTER_UP:
					// When releasing the second finger,
					// update the lastX/lastY of the remaining finger to prevent a rotation jump
					int actionIndex = event.getActionIndex();
					int remainingIndex = (actionIndex == 0) ? 1 : 0;
					if (pointerCount == 2) {
						lastX = event.getX(remainingIndex);
						lastY = event.getY(remainingIndex);
					}
					break;

				case MotionEvent.ACTION_MOVE:
					if (pointerCount == 1 && !scaleGestureDetector.isInProgress()) {
						// Rotate the scene with one finger
						float deltaX = event.getX() - lastX;
						float deltaY = event.getY() - lastY;

						rotationY += deltaX * 0.5f;
						rotationX += deltaY * 0.5f;

						// Vertical limitation
						rotationX = Math.max(-89.0f, Math.min(89.0f, rotationX));

						lastX = event.getX();
						lastY = event.getY();

						updateCameraPosition();

					} else if (pointerCount == 2) {
						// Two-finger panning
						float currentPanX = (event.getX(0) + event.getX(1)) / 2.0f;
						float currentPanY = (event.getY(0) + event.getY(1)) / 2.0f;

						float deltaPanX = currentPanX - lastPanX;
						float deltaPanY = currentPanY - lastPanY;

						// Distance-dependent sensitivity coefficient
						float factor = radius * 0.0015f;

						double radY = Math.toRadians(rotationY);
						targetX -= (float) ((deltaPanX * Math.cos(radY) - deltaPanY * Math.sin(radY)) * factor);
						targetY += deltaPanY * factor;
						targetZ -= (float) ((deltaPanX * Math.sin(radY) + deltaPanY * Math.cos(radY)) * factor);

						lastPanX = currentPanX;
						lastPanY = currentPanY;

						updateCameraPosition();
					}
					break;
			}
			return true;
		});
	}

	/**
	 * Animation frame processor synchronized with the screen refresh rate (VSync).
	 * <p>
	 * On each frame, requests the next rendering step via {@link Choreographer},
	 * checks the readiness of Filament components and renders
	 * the current 3D scene in {@link com.google.android.filament.SwapChain SwapChain}.
	 */
	private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
		@Override
		public void doFrame(long frameTimeNanos) {
			if (!isDrawing) return;
			Choreographer.getInstance().postFrameCallback(this);

			if (renderer != null && view != null && swapChain != null) {
				if (renderer.beginFrame(swapChain, frameTimeNanos)) {
					renderer.render(view);
					renderer.endFrame();
				}
			}
		}
	};

	/**
	 * Starts the frame rendering cycle through {@link Choreographer}.
	 * Called when the container (GeometryFragment) enters the Resume state.
	 */
	public void onResume() {
		isDrawing = true;
		Choreographer.getInstance().postFrameCallback(frameCallback);
	}

	/**
	 * Pauses the frame rendering loop.
	 * Called when the container (GeometryFragment) enters the Pause state.
	 */
	public void onPause() {
		isDrawing = false;
		// Stop the rendering loop and disable UiHelper
		Choreographer.getInstance().removeFrameCallback(frameCallback);
	}

	/**
	 * Releases all native Filament resources (scene objects, buffers, materials, and engine).
	 * Must be called when the container (GeometryFragment) is destroyed.
	 */
	public void onDestroy() {

		isDrawing = false;

		// 1. Disable UiHelper
		if (uiHelper != null) {
			uiHelper.detach();
		}

		// 2.Freeing up antenna resources (Entity, VertexBuffer, IndexBuffer)
		clearAntennaResources();

		// 3. Freeing up the coordinate axes resources (Entity, VertexBuffer, IndexBuffer)
		clearAxesResources();

		// 4. Freeing the materials (first the instances, then the base materials)
		if (antennaMaterialInstance != null) {
			engine.destroyMaterialInstance(antennaMaterialInstance);
			antennaMaterialInstance = null;
		}
		if (axisMaterialInstance != null) {
			engine.destroyMaterialInstance(axisMaterialInstance);
			axisMaterialInstance = null;
		}
		if (antennaMaterial != null) {
			engine.destroyMaterial(antennaMaterial);
			antennaMaterial = null;
		}

		// 5. Destroying scene objects and views
		if (scene != null) {
			engine.destroyScene(scene);
			scene = null;
		}
		if (view != null) {
			engine.destroyView(view);
			view = null;
		}

		// 6. Destroying the camera components and the renderer
		if (camera != null) {
			engine.destroyCameraComponent(camera.getEntity());
			EntityManager.get().destroy(camera.getEntity());
			camera = null;
		}

		if (renderer != null) {
			engine.destroyRenderer(renderer);
			renderer = null;
		}

		// 7. Destroy SwapChain if it remains
		if (swapChain != null) {
			engine.destroySwapChain(swapChain);
			swapChain = null;
		}

		// 8. The final step is to destroy the Filament engine itself.
		if (engine != null) {
			engine.destroy();
			engine = null;
		}
	}

	/**
	 * Resets the camera orientation to the coordinate center and viewed from above the Z-axis.
	 */
	public void resetCamera() {
		rotationX = 0.0f;
		rotationY = 0.0f;
		targetX = 0.0f;
		targetY = 0.0f;
		targetZ = 0.0f;
		updateCameraPosition();
	}

	/**
	 * Recalculates the camera coordinates in the spherical system based on the rotation angles
	 * (rotationX, rotationY) and radius, and then updates the lookAt matrix.
	 */
	private void updateCameraPosition() {
		if (camera == null) return;

		// Calculate the position of the eye relative to the target point
		double radX = Math.toRadians(rotationX);
		double radY = Math.toRadians(rotationY);

		float eyeX = (float) (targetX + radius * Math.cos(radX) * Math.sin(radY));
		float eyeY = (float) (targetY + radius * Math.sin(radX));
		float eyeZ = (float) (targetZ + radius * Math.cos(radX) * Math.cos(radY));

		// Direct the camera from the eye to the offset target point (targetX, targetY, targetZ)
		camera.lookAt(
			 eyeX, eyeY, eyeZ,
			 targetX, targetY, targetZ,
			 0.0f, 1.0f, 0.0f
		);
	}

	/**
	 * Loads the compiled Filament material from the file `color.filamat`
	 * into assets and creates an instance of it for application to 3D objects.
	 */
	private void loadMaterial() {
		try {
			java.io.InputStream is = textureView.getContext().getAssets().open("color.filmat");
			byte[] buffer = new byte[is.available()];
			if (is.read(buffer) > 0) {
				antennaMaterial = new com.google.android.filament.Material.Builder()
					 .payload(ByteBuffer.wrap(buffer), buffer.length)
					 .build(engine);

				if (antennaMaterialInstance == null) {
					antennaMaterialInstance = antennaMaterial.createInstance();
				}
				if (axisMaterialInstance == null) {
					axisMaterialInstance = antennaMaterial.createInstance();
					// For the axes, we don't take into account the color from the theme - we set it to pure white (1,1,1,1)
					axisMaterialInstance.setParameter("baseColor", 1.0f, 1.0f, 1.0f, 1.0f);
				}
			}
			is.close();
		} catch (Exception e) {
			Log.e("FilamentViewer", "Error when loading filament material", e);
		}
	}

	/**
	 * Generates and displays 3D coordinate axes (X - red, Y - green, Z - blue) on the scene,
	 * along with 3D letter labels (X, Y, Z) at the ends.
	 *
	 * @param size Length of coordinate axes.
	 * @param rad  Base radius (thickness) of axis lines.
	 */
	public void createCoordinateAxes(float size, float rad) {
		clearAxesResources();

		// 1. Geometry of letters (segments)
		float[][] letterXLines = {
			 {-0.5f, -0.5f,  0.5f,  0.5f},
			 {-0.5f,  0.5f,  0.5f, -0.5f}
		};

		float[][] letterYLines = {
			 {-0.5f,  0.5f,  0.0f,  0.0f},
			 { 0.5f,  0.5f,  0.0f,  0.0f},
			 { 0.0f,  0.0f,  0.0f, -0.5f}
		};

		float[][] letterZLines = {
			 {-0.5f,  0.5f,  0.5f,  0.5f},
			 { 0.5f,  0.5f, -0.5f, -0.5f},
			 {-0.5f, -0.5f,  0.5f, -0.5f}
		};

		float letterScale = size * 0.12f;
		float letterOffset = size * 1.15f;
		float letterThick = rad * 0.8f;

		int letterLinesCount = letterXLines.length + letterYLines.length + letterZLines.length; // 8
		int letterVerticesCount = letterLinesCount * 4; // 32
		int letterIndicesCount = letterLinesCount * 6;  // 48

		int totalVertices = 24 + letterVerticesCount;  // 56 Vertices
		int totalIndices = 108 + letterIndicesCount;   // 156 Indices

		// 6 floats per vertex: Position(3) + Color(3)
		FloatBuffer vertexData = ByteBuffer.allocateDirect(totalVertices * 6 * Float.BYTES)
			 .order(ByteOrder.nativeOrder())
			 .asFloatBuffer();

		ShortBuffer indexData = ByteBuffer.allocateDirect(totalIndices * Short.BYTES)
			 .order(ByteOrder.nativeOrder())
			 .asShortBuffer();

		float r = 0.5f * rad;

		float[][] axisConfigs = {
			 {size, 0, 0,  0, r, 0,  0, 0, r,  1.0f, 0.0f, 0.0f},
			 {0, size, 0,  r, 0, 0,  0, 0, r,  0.0f, 1.0f, 0.0f},
			 {0, 0, size,  r, 0, 0,  0, r, 0,  0.0f, 0.0f, 1.0f}
		};

		short vIndex = 0;

		// --- 1. Generation of 3 coordinate axes ---
		for (float[] cfg : axisConfigs) {
			float targetX = cfg[0], targetY = cfg[1], targetZ = cfg[2];

			float ux = cfg[3], uy = cfg[4], uz = cfg[5];
			float vx = cfg[6], vy = cfg[7], vz = cfg[8];
			float cr = cfg[9], cg = cfg[10], cb = cfg[11];

			// Start face (4 vertices x 6 float = 24 float)
			vertexData.put(0 + ux + vx).put(0 + uy + vy).put(0 + uz + vz).put(cr).put(cg).put(cb);
			vertexData.put(0 - ux + vx).put(0 - uy + vy).put(0 - uz + vz).put(cr).put(cg).put(cb);
			vertexData.put(0 - ux - vx).put(0 - uy - vy).put(0 - uz - vz).put(cr).put(cg).put(cb);
			vertexData.put(0 + ux - vx).put(0 + uy - vy).put(0 + uz - vz).put(cr).put(cg).put(cb);

			// End face (4 vertices x 6 float = 24 float)
			vertexData.put(targetX + ux + vx).put(targetY + uy + vy).put(targetZ + uz + vz).put(cr).put(cg).put(cb);
			vertexData.put(targetX - ux + vx).put(targetY - uy + vy).put(targetZ - uz + vz).put(cr).put(cg).put(cb);
			vertexData.put(targetX - ux - vx).put(targetY - uy - vy).put(targetZ - uz - vz).put(cr).put(cg).put(cb);
			vertexData.put(targetX + ux - vx).put(targetY + uy - vy).put(targetZ + uz - vz).put(cr).put(cg).put(cb);

			// Lateral face indices
			for (int i = 0; i < 4; i++) {
				int next = (i + 1) % 4;
				short b1 = (short) (vIndex + i);
				short b2 = (short) (vIndex + next);
				short e1 = (short) (vIndex + 4 + i);
				short e2 = (short) (vIndex + 4 + next);

				indexData.put(b1).put(b2).put(e1);
				indexData.put(b2).put(e2).put(e1);
			}

			// Start/End faces indices
			indexData.put((short) (vIndex)).put((short) (vIndex + 2)).put((short) (vIndex + 1));
			indexData.put((short) (vIndex)).put((short) (vIndex + 3)).put((short) (vIndex + 2));
			indexData.put((short) (vIndex + 4)).put((short) (vIndex + 5)).put((short) (vIndex + 6));
			indexData.put((short) (vIndex + 4)).put((short) (vIndex + 6)).put((short) (vIndex + 7));

			vIndex += 8;
		}

		// --- 2. Generation of letters X, Y, Z ---
		Object[] letterConfigs = {
			 new Object[]{letterXLines, new float[]{letterOffset, 0, 0}, 0, new float[]{1.0f, 0.0f, 0.0f}},
			 new Object[]{letterYLines, new float[]{0, letterOffset, 0}, 1, new float[]{0.0f, 1.0f, 0.0f}},
			 new Object[]{letterZLines, new float[]{0, 0, letterOffset}, 2, new float[]{0.0f, 0.0f, 1.0f}}
		};

		float halfThick = letterThick * 0.5f;

		for (Object obj : letterConfigs) {
			Object[] cfg = (Object[]) obj;
			float[][] lines = (float[][]) cfg[0];
			float[] center = (float[]) cfg[1];
			int axisType = (Integer) cfg[2];
			float[] color = (float[]) cfg[3];

			for (float[] line : lines) {
				float x1 = line[0] * letterScale;
				float y1 = line[1] * letterScale;
				float x2 = line[2] * letterScale;
				float y2 = line[3] * letterScale;

				float dx = x2 - x1;
				float dy = y2 - y1;
				float len = (float) Math.hypot(dx, dy);
				if (len == 0) continue;

				float nx = (-dy / len) * halfThick;
				float ny = (dx / len) * halfThick;

				float p1x, p1y, p1z, p2x, p2y, p2z;
				float offsetNx, offsetNy, offsetNz;

				if (axisType == 0) {
					p1x = center[0]; p1y = center[1] + x1; p1z = center[2] + y1;
					p2x = center[0]; p2y = center[1] + x2; p2z = center[2] + y2;
					offsetNx = 0; offsetNy = nx; offsetNz = ny;
				} else if (axisType == 1) {
					p1x = center[0] + x1; p1y = center[1]; p1z = center[2] + y1;
					p2x = center[0] + x2; p2y = center[1]; p2z = center[2] + y2;
					offsetNx = nx; offsetNy = 0; offsetNz = ny;
				} else {
					p1x = center[0] + x1; p1y = center[1] + y1; p1z = center[2];
					p2x = center[0] + x2; p2y = center[1] + y2; p2z = center[2];
					offsetNx = nx; offsetNy = ny; offsetNz = 0;
				}

				// 4 vertices of the stroke strip (each with 6 floats: XYZ + RGB)
				vertexData.put(p1x - offsetNx).put(p1y - offsetNy).put(p1z - offsetNz).put(color[0]).put(color[1]).put(color[2]);
				vertexData.put(p1x + offsetNx).put(p1y + offsetNy).put(p1z + offsetNz).put(color[0]).put(color[1]).put(color[2]);
				vertexData.put(p2x + offsetNx).put(p2y + offsetNy).put(p2z + offsetNz).put(color[0]).put(color[1]).put(color[2]);
				vertexData.put(p2x - offsetNx).put(p2y - offsetNy).put(p2z - offsetNz).put(color[0]).put(color[1]).put(color[2]);

				indexData.put(vIndex);
				indexData.put((short) (vIndex + 1));
				indexData.put((short) (vIndex + 2));

				indexData.put(vIndex);
				indexData.put((short) (vIndex + 2));
				indexData.put((short) (vIndex + 3));

				vIndex += 4;
			}
		}

		vertexData.flip();
		indexData.flip();

		vertexBufferAxis = new VertexBuffer.Builder()
			 .vertexCount(totalVertices)
			 .bufferCount(1)
			 .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 6 * Float.BYTES)
			 .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT3, 3 * Float.BYTES, 6 * Float.BYTES)
			 .build(engine);
		vertexBufferAxis.setBufferAt(engine, 0, vertexData);

		indexBufferAxis = new IndexBuffer.Builder()
			 .indexCount(totalIndices)
			 .bufferType(IndexBuffer.Builder.IndexType.USHORT)
			 .build(engine);
		indexBufferAxis.setBuffer(engine, indexData);

		axisEntity = EntityManager.get().create();
		com.google.android.filament.Box dummyBox =
			 new com.google.android.filament.Box(0.0f, 0.0f, 0.0f, size * 2.0f, size * 2.0f, size * 2.0f);

		new RenderableManager.Builder(1)
			 .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBufferAxis, indexBufferAxis, 0, totalIndices)
			 .boundingBox(dummyBox)
			 .culling(false)
			 .material(0, axisMaterialInstance)
			 .build(engine, axisEntity);

		scene.addEntity(axisEntity);
	}

	/**
	 * Updates the 3D geometry of an antenna in the scene based on an array of wires (Wire).
	 * Recalculates scales, creates vertex/index buffers, and adjusts coordinate axes.
	 *
	 * @param wires An array of Wire objects describing the geometry of the antenna wires.
	 */
	public void updateAntennaGeometry(Wire[] wires) {

		clearAntennaResources();

		if (wires == null || wires.length == 0) {
			createCoordinateAxes(1.0f, 0.05f);
			radius = 10.0f;
			resetCamera();
			return;
		}

		// 1. We take (0,0,0) into account when searching for the dimensions of the entire scene
		float minX = 0.0f, maxX = 0.0f;
		float minY = 0.0f, maxY = 0.0f;
		float minZ = 0.0f, maxZ = 0.0f;

		for (Wire wire : wires) {
			minX = Math.min(minX, Math.min(wire.x1, wire.x2));
			maxX = Math.max(maxX, Math.max(wire.x1, wire.x2));
			minY = Math.min(minY, Math.min(wire.y1, wire.y2));
			maxY = Math.max(maxY, Math.max(wire.y1, wire.y2));
			minZ = Math.min(minZ, Math.min(wire.z1, wire.z2));
			maxZ = Math.max(maxZ, Math.max(wire.z1, wire.z2));
		}

		float sizeX = maxX - minX;
		float sizeY = maxY - minY;
		float sizeZ = maxZ - minZ;
		float maxDimension = Math.max(sizeX, Math.max(sizeY, sizeZ));

		if (maxDimension <= 0.0f) maxDimension = 0.5f;

		// The center of the camera's viewpoint takes into account both the antenna and (0,0,0)
		targetX = (minX + maxX) / 2.0f;
		targetY = (minY + maxY) / 2.0f;
		targetZ = (minZ + maxZ) / 2.0f;

		// Increases the camera radius so that both the axes at (0,0,0) and the antenna itself fit into the frame.
		radius = maxDimension * 3.0f;

		// The size of the axes relative to the overall scene
		float calculatedAxisSize = maxDimension * 0.25f;
		float axisThickness = (wires[0].radius <= 0) ? maxDimension * 0.008f : wires[0].radius;

		// We draw the axes strictly at (0,0,0)
		createCoordinateAxes(calculatedAxisSize, axisThickness);

		// One wire: 8 vertices of the prism + 2 central points at the ends = 10 vertices.
		// Indices: 24 (side faces) + 12 (starting end) + 12 (final end) = 48 indices per wire.
		int vertsPerWire = 10;
		int indicesPerWire = 48;

		int totalVertices = wires.length * vertsPerWire;
		int totalIndices = wires.length * indicesPerWire;

		FloatBuffer vertexData = ByteBuffer.allocateDirect(totalVertices * 6 * Float.BYTES)
			 .order(ByteOrder.nativeOrder())
			 .asFloatBuffer();

		ShortBuffer indexData = ByteBuffer.allocateDirect(totalIndices * 2)
			 .order(ByteOrder.nativeOrder())
			 .asShortBuffer();

		short vIndex = 0;

		for (Wire wire : wires) {
			float dx = wire.x2 - wire.x1;
			float dy = wire.y2 - wire.y1;
			float dz = wire.z2 - wire.z1;
			float lenDir = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

			if (lenDir == 0.0f) continue;

			float dirX = dx / lenDir;
			float dirY = dy / lenDir;
			float dirZ = dz / lenDir;

			// Selects an auxiliary vector that is not collinear to the wire direction
			float upX = (Math.abs(dirX) < 0.9f) ? 1.0f : 0.0f;
			float upY = (Math.abs(dirX) < 0.9f) ? 0.0f : 1.0f;
			float upZ = 0.0f;

			// U = dir x Up
			float ux = dirY * upZ - dirZ * upY;
			float uy = dirZ * upX - dirX * upZ;
			float uz = dirX * upY - dirY * upX;
			float lenU = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
			ux /= lenU; uy /= lenU; uz /= lenU;

			// V = dir x U
			float vx = dirY * uz - dirZ * uy;
			float vy = dirZ * ux - dirX * uz;
			float vz = dirX * uy - dirY * ux;

			float r = wire.radius;
			if (r <= 0.0f) r = maxDimension * 0.005f;

			ux *= r; uy *= r; uz *= r;
			vx *= r; vy *= r; vz *= r;

			// [0..3] Vertices around the Start point (wire1)
			vertexData.put(wire.x1 + ux).put(wire.y1 + uy).put(wire.z1 + uz).put(1.0f).put(1.0f).put(1.0f);
			vertexData.put(wire.x1 + vx).put(wire.y1 + vy).put(wire.z1 + vz).put(1.0f).put(1.0f).put(1.0f);
			vertexData.put(wire.x1 - ux).put(wire.y1 - uy).put(wire.z1 - uz).put(1.0f).put(1.0f).put(1.0f);
			vertexData.put(wire.x1 - vx).put(wire.y1 - vy).put(wire.z1 - vz).put(1.0f).put(1.0f).put(1.0f);

			// [4..7] Vertices around the End point (wire2)
			vertexData.put(wire.x2 + ux).put(wire.y2 + uy).put(wire.z2 + uz).put(1.0f).put(1.0f).put(1.0f);
			vertexData.put(wire.x2 + vx).put(wire.y2 + vy).put(wire.z2 + vz).put(1.0f).put(1.0f).put(1.0f);
			vertexData.put(wire.x2 - ux).put(wire.y2 - uy).put(wire.z2 - uz).put(1.0f).put(1.0f).put(1.0f);
			vertexData.put(wire.x2 - vx).put(wire.y2 - vy).put(wire.z2 - vz).put(1.0f).put(1.0f).put(1.0f);

			// [8] Center of the starting end, [9] Center of the ending end
			// Moving them outwards a tiny distance (by the value of the radius r) to round off the joint.
			float lenWire = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float capOffsetX = (lenWire > 0) ? (dx / lenWire) * r : 0;
			float capOffsetY = (lenWire > 0) ? (dy / lenWire) * r : 0;
			float capOffsetZ = (lenWire > 0) ? (dz / lenWire) * r : 0;

			vertexData.put(wire.x1 - capOffsetX).put(wire.y1 - capOffsetY).put(wire.z1 - capOffsetZ).put(1.0f).put(1.0f).put(1.0f);
			vertexData.put(wire.x2 + capOffsetX).put(wire.y2 + capOffsetY).put(wire.z2 + capOffsetZ).put(1.0f).put(1.0f).put(1.0f);

			// 1. Indices of the side faces of the tube (24 indices)
			for (int i = 0; i < 4; i++) {
				int next = (i + 1) % 4;
				short b1 = (short) (vIndex + i);
				short b2 = (short) (vIndex + next);
				short e1 = (short) (vIndex + 4 + i);
				short e2 = (short) (vIndex + 4 + next);

				indexData.put(b1).put(b2).put(e1);
				indexData.put(b2).put(e2).put(e1);
			}

			// 2.End Cap (Pyramid to Vertex 8) -> 4 Triangles (12 Indexes)
			short capStartCenter = (short) (vIndex + 8);
			for (int i = 0; i < 4; i++) {
				int next = (i + 1) % 4;
				indexData.put((short) (vIndex + next));
				indexData.put((short) (vIndex + i));
				indexData.put(capStartCenter);
			}

			// 3. End Cap (Pyramid to Vertex 9) -> 4 Triangles (12 Indexes)
			short capEndCenter = (short) (vIndex + 9);
			for (int i = 0; i < 4; i++) {
				int next = (i + 1) % 4;
				indexData.put((short) (vIndex + 4 + i));
				indexData.put((short) (vIndex + 4 + next));
				indexData.put(capEndCenter);
			}

			vIndex += (short) vertsPerWire;
		}

		vertexData.flip();
		indexData.flip();

		//The rest of the Filament buffer work (unchanged)
		vertexBuffer3D = new VertexBuffer.Builder()
			 .vertexCount(totalVertices)
			 .bufferCount(1)
			 .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 6 * Float.BYTES)
			 .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT3, 3 * Float.BYTES, 6 * Float.BYTES)
			 .build(engine);
		vertexBuffer3D.setBufferAt(engine, 0, vertexData);

		indexBuffer3D = new IndexBuffer.Builder()
			 .indexCount(totalIndices)
			 .bufferType(IndexBuffer.Builder.IndexType.USHORT)
			 .build(engine);
		indexBuffer3D.setBuffer(engine, indexData);

		antennaEntity = EntityManager.get().create();
		com.google.android.filament.Box dummyBox =
			 new com.google.android.filament.Box(targetX, targetY, targetZ, maxDimension * 2.0f, maxDimension * 2.0f, maxDimension * 2.0f);

		new RenderableManager.Builder(1)
			 .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer3D, indexBuffer3D, 0, totalIndices)
			 .boundingBox(dummyBox)
			 .culling(false)
			 .material(0, antennaMaterialInstance)
			 .build(engine, antennaEntity);

		scene.addEntity(antennaEntity);

		// Update the camera position with the new target/radius
		updateCameraPosition();
	}

	/**
	 * Frees native Filament resources associated with rendering the coordinate axes.
	 * (Entity, VertexBuffer, IndexBuffer), and removes the object from the scene.
	 */
	private void clearAxesResources() {
		if (axisEntity != 0) {
			scene.removeEntity(axisEntity);
			engine.destroyEntity(axisEntity);
			EntityManager.get().destroy(axisEntity);
			axisEntity = 0;
		}
		if (vertexBufferAxis != null) {
			engine.destroyVertexBuffer(vertexBufferAxis);
			vertexBufferAxis = null;
		}
		if (indexBufferAxis != null) {
			engine.destroyIndexBuffer(indexBufferAxis);
			indexBufferAxis = null;
		}
	}

	/**
	 * Frees native Filament resources associated with antenna geometry
	 * (Entity, VertexBuffer, IndexBuffer), and removes the object from the scene.
	 */
	private void clearAntennaResources() {
		if (antennaEntity != 0) {
			scene.removeEntity(antennaEntity);
			engine.destroyEntity(antennaEntity);
			EntityManager.get().destroy(antennaEntity);
			antennaEntity = 0;
		}
		if (vertexBuffer3D != null) {
			engine.destroyVertexBuffer(vertexBuffer3D);
			vertexBuffer3D = null;
		}
		if (indexBuffer3D != null) {
			engine.destroyIndexBuffer(indexBuffer3D);
			indexBuffer3D = null;
		}
	}
}
