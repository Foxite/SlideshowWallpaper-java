package nl.dirkkok.android.wallpapers.slideshow.java;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.SurfaceHolder;

class DrawThread extends Thread {
	private final SlideshowWallpaperService.SlideshowEngine m_Engine;
	private long m_LastRedraw;
	private long m_MillisToDrawLastFrame = 0;
	boolean m_RedrawRequested;

	private final Paint m_DebugTextPaint = new Paint();

	public DrawThread(SlideshowWallpaperService.SlideshowEngine engine) {
		m_Engine = engine;
	}

	private void drawWallpaper() {
		float xOffset;
		float yOffset;
		synchronized (m_Engine.m_OffsetLock) {
			xOffset = m_Engine.getXOffset();
			yOffset = m_Engine.getYOffset();
		}

		synchronized (m_Engine.m_DrawLock) {
			SurfaceHolder holder = m_Engine.getSurfaceHolder();
			Canvas canvas = null;
			try {
				canvas = holder.lockCanvas();

				// The bitmaps will always have the exact same width or height as the canvas. (Except maybe off-by-one but idc)
				// The other dimension will be larger than the canvas's respective dimension.
				// Therefore one of these parameters will be zero and the other will be negative.
				// By translating the bitmap by a negative number we move it to the left or up.
				// See onVisibilityChanged for an explanation of what m_XOffset and m_YOffset are.
				Matrix currentTransform = new Matrix();
				currentTransform.postTranslate(-xOffset * (m_Engine.m_CurrentBitmap.getWidth() - m_Engine.getWidth()), -yOffset * (m_Engine.m_CurrentBitmap.getHeight() - m_Engine.getHeight()));

				long currentBitmapStart = System.currentTimeMillis();
				canvas.drawBitmap(m_Engine.m_CurrentBitmap, currentTransform, null);
				long currentBitmapEnd = System.currentTimeMillis();
				canvas.drawText(Long.toString(currentBitmapEnd - currentBitmapStart), 0, 50, m_DebugTextPaint);

				if (m_Engine.m_PreviousBitmap != null) {
					int alpha = m_Engine.m_PreviousBitmapPaint.getAlpha();
					// Lerp alpha from 255 to 0 over 1 second
					if (alpha == 255) {
						alpha -= 4; // as below if the time difference is 1/60th of a second
						// TODO reflect actual refresh rate
					} else {
						alpha -= (float) (System.currentTimeMillis() - m_LastRedraw) * 255 / 1000; // In that order to mitigate precision loss
					}
					if (alpha <= 0) {
						m_Engine.m_PreviousBitmap.recycle();
						m_Engine.m_PreviousBitmap = null;
					} else {
						m_Engine.m_PreviousBitmapPaint.setAlpha(alpha);

						Matrix previousTransform = new Matrix();
						previousTransform.postTranslate(-xOffset * (m_Engine.m_PreviousBitmap.getWidth() - m_Engine.getWidth()), -yOffset * (m_Engine.m_PreviousBitmap.getHeight() - m_Engine.getHeight()));

						long previousBitmapStart = System.currentTimeMillis();
						canvas.drawBitmap(m_Engine.m_PreviousBitmap, previousTransform, m_Engine.m_PreviousBitmapPaint);
						long previousBitmapEnd = System.currentTimeMillis();
						canvas.drawText(Long.toString(previousBitmapEnd - previousBitmapStart), 0, 75, m_DebugTextPaint);

						m_RedrawRequested = true;
					}
				}
				m_MillisToDrawLastFrame = System.currentTimeMillis() - m_LastRedraw;
				canvas.drawText(Long.toString(m_MillisToDrawLastFrame), 0, 25, m_DebugTextPaint);
			} finally {
				m_LastRedraw = System.currentTimeMillis();
				if (canvas != null) {
					holder.unlockCanvasAndPost(canvas);
				}
			}
		}
	}

	private long calculateMillisToWait() {
		long millisSinceUpdate = System.currentTimeMillis() - m_LastRedraw;
		final long millisBetweenUpdates = 16;
		return millisBetweenUpdates - millisSinceUpdate - m_MillisToDrawLastFrame;
	}

	@Override
	public void run() {
		try {
			// If the launcher's calls to the engine take too long they end up being "queued",
			// which leads to a very ugly lag in the wallpaper's response to moving around the launcher.
			//
			// This means that we need to make sure that any calls to requestRedraw, pause, and unpause, will never run for long.
			// Unfortunately, drawing the wallpaper takes too long to avoid that problem on old devices. Which is why we do it on a separate thread.
			while (m_Engine.m_Running) {
				long millisToWait = calculateMillisToWait();
				if (millisToWait > 0) {
					// If this synchronized would take too long to execute, then the requestRedraw, pause, and unpause functions above
					// (which execute on the launcher's thread) then they would cause the aforementioned lag.
					synchronized (m_Engine.m_MessageLock) {
						m_Engine.m_MessageLock.wait(millisToWait);

						// Don't redraw more than 60 times per second
						// TODO find out actual refresh rate of display
						if (m_Engine.m_CurrentBitmap == null || m_Engine.m_Paused || !m_RedrawRequested || calculateMillisToWait() > 0) {
							continue;
						}
						m_RedrawRequested = false;
					}
				}
				drawWallpaper();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
