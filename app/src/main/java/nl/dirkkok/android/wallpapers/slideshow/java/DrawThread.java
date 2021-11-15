package nl.dirkkok.android.wallpapers.slideshow.java;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.SurfaceHolder;

class DrawThread extends Thread {
	private final SlideshowWallpaperService.SlideshowEngine m_Engine;
	private long m_LastRedraw;
	boolean m_RedrawRequested;

	public DrawThread(SlideshowWallpaperService.SlideshowEngine engine) {
		m_Engine = engine;
	}

	private void drawWallpaper() {
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
				currentTransform.postTranslate(-m_Engine.getXOffset() * (m_Engine.m_CurrentBitmap.getWidth() - m_Engine.getWidth()), -m_Engine.getYOffset() * (m_Engine.m_CurrentBitmap.getHeight() - m_Engine.getHeight()));

				canvas.drawBitmap(m_Engine.m_CurrentBitmap, currentTransform, null);

				if (m_Engine.m_PreviousBitmap != null) {
					int alpha = m_Engine.m_PreviousBitmapPaint.getAlpha();
					alpha -= (System.currentTimeMillis() - m_LastRedraw) * 255 / 1000; // In that order to mitigate precision loss
					if (alpha < 0) {
						m_Engine.m_PreviousBitmap.recycle();
						m_Engine.m_PreviousBitmap = null;
					} else {
						m_Engine.m_PreviousBitmapPaint.setAlpha(alpha);

						Matrix previousTransform = new Matrix();
						previousTransform.postTranslate(-m_Engine.getXOffset() * (m_Engine.m_PreviousBitmap.getWidth() - m_Engine.getWidth()), -m_Engine.getYOffset() * (m_Engine.m_PreviousBitmap.getHeight() - m_Engine.getHeight()));

						canvas.drawBitmap(m_Engine.m_PreviousBitmap, previousTransform, m_Engine.m_PreviousBitmapPaint);
					}
				}
			} finally {
				if (canvas != null) {
					holder.unlockCanvasAndPost(canvas);
				}
			}
		}
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
				// If this synchronized would take too long to execute, then the requestRedraw, pause, and unpause functions above
				// (which execute on the launcher's thread) then they would cause the aforementioned lag.
				synchronized (m_Engine.m_MessageLock) {
					m_Engine.m_MessageLock.wait();

					// Don't redraw more than 60 times per second
					// TODO find out actual refresh rate of display
					if (m_Engine.m_CurrentBitmap == null || m_Engine.m_Paused || !m_RedrawRequested || (System.currentTimeMillis() - m_LastRedraw) < 1000 / 60) {
						continue;
					}
					m_RedrawRequested = false;
				}
				m_LastRedraw = System.currentTimeMillis();

				drawWallpaper();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
