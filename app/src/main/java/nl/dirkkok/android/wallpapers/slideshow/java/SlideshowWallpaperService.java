package nl.dirkkok.android.wallpapers.slideshow.java;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.Environment;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;

public class SlideshowWallpaperService extends WallpaperService {
	private final WallpaperProvider m_Provider;

	public SlideshowWallpaperService() {
		m_Provider = new FilesystemWallpaperProvider(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Wallpapers"));
	}

	@Override
	public Engine onCreateEngine() {
		return new SlideshowEngine();
	}

	class SlideshowEngine extends WallpaperService.Engine {
		DrawThread m_DrawThread;
		SlideshowThread m_SlideshowThread;

		final Object m_MessageLock = new Object();
		boolean m_Running = true;
		boolean m_Paused = false;

		final Object m_OffsetLock = new Object();
		private float m_XOffset = 0;
		private float m_YOffset = 0;

		final Object m_DrawLock = new Object();
		private int m_Width = 0;
		private int m_Height = 0;
		Bitmap m_CurrentBitmap = null;
		Bitmap m_PreviousBitmap = null;
		Paint m_PreviousBitmapPaint; // The current bitmap is always drawn using alpha = 1. The previous bitmap, if there is one, will be drawn on top of it using this alpha value.

		public SlideshowEngine() {
			super();
			m_PreviousBitmapPaint = new Paint();
			m_PreviousBitmapPaint.setAlpha(0);
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			if (visible) {
				unpause();
			} else {
				pause();
			}
		}

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
			// The offset is provided by the launcher and it is a number between 0 and 1 indicating where the user is.
			// For example, if you have a horizontal page-based homescreen, and there are 3 pages, then m_XOffset: page 1 is 0, page 2 is 0.5, page 3 is 1.
			// For vertical launchers I suspect it's the same thing but for m_YOffset.
			synchronized (m_OffsetLock) {
				m_XOffset = xOffset;
				m_YOffset = yOffset;
			}
			requestRedraw();
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			Log.d("SLIDESHOW", String.format("onSurfaceChanged %d %d %d", format, width, height)); // TODO request update from SlideshowThread
			synchronized (m_DrawLock) {
				m_Width = width;
				m_Height = height;
			}

			if (m_SlideshowThread == null) {
				m_SlideshowThread = new SlideshowThread(this);
				m_SlideshowThread.start();
			}
			requestRedraw();
		}

		@Override
		public void onDestroy() {
			endLoop();
		}

		// The following methods are thread-safe.
		public void endLoop() {
			synchronized (m_MessageLock) {
				m_Running = false;
				m_MessageLock.notifyAll();
			}
			synchronized (m_DrawLock) {
				// Make sure this method doesn't return while the draw thread is drawing
			}
		}

		public void requestRedraw() {
			synchronized (m_MessageLock) {
				if (m_DrawThread != null) {
					m_DrawThread.m_RedrawRequested = true;
					m_MessageLock.notifyAll();
				}
			}
		}

		public void pause() {
			synchronized (m_MessageLock) {
				m_Paused = true;
				m_MessageLock.notifyAll();
			}
		}

		public void unpause() {
			synchronized (m_MessageLock) {
				m_Paused = false;
				requestRedraw();
			}
		}

		public float getXOffset() {
			return m_XOffset;
		}
		public float getYOffset() {
			return m_YOffset;
		}

		private void fixWidthAndHeight() {
			if (m_Width == 0 || m_Height == 0) {
				synchronized (m_DrawLock) {
					m_Width = getSurfaceHolder().getSurfaceFrame().width();
					m_Height = getSurfaceHolder().getSurfaceFrame().height();
					Log.e("SLIDESHOW", "Fixed width and height " + m_Width + " " + m_Height);
				}
			}
		}
		public int getWidth() {
			fixWidthAndHeight();
			return m_Width;
		}
		public int getHeight() {
			fixWidthAndHeight();
			return m_Height;
		}

		public WallpaperProvider getProvider() {
			return m_Provider;
		}
	}
}
