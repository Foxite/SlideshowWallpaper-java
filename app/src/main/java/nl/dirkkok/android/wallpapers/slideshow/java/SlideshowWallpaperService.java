package nl.dirkkok.android.wallpapers.slideshow.java;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Environment;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class SlideshowWallpaperService extends WallpaperService {
	private final WallpaperProvider m_Provider;

	public SlideshowWallpaperService() {
		m_Provider = new FilesystemWallpaperProvider(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Wallpapers"));
	}

	@Override
	public Engine onCreateEngine() {
		return new SlideshowEngine();
	}

	private class SlideshowEngine extends WallpaperService.Engine {
		private final WallpaperDrawThread m_DrawThread;
		private float m_XOffset = 0;
		private float m_YOffset = 0;
		private Bitmap m_CurrentBitmap = null;
		private Date m_BitmapChanged = null;
		private Date m_LastRedraw = null;

		public SlideshowEngine() {
			super();
			m_DrawThread = new WallpaperDrawThread();
			m_DrawThread.start();
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			if (visible) {
				m_DrawThread.unpause();
				m_DrawThread.requestRedraw();
			} else {
				m_DrawThread.pause();
			}
		}

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
			m_XOffset = xOffset;
			m_YOffset = yOffset;
			m_DrawThread.requestRedraw();
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			Log.d("SLIDESHOW", String.format("onSurfaceChanged %d %d %d", format, width, height));
		}

		private class WallpaperDrawThread extends Thread {
			private final Object m_DrawLock = new Object();
			private volatile boolean m_KeepRunning = true; // This is volatile because I couldn't figure out a good way to synchronize access to it.
			private boolean m_Paused = false;
			private boolean m_RedrawRequested; // I was able to do it for this one, so it's not volatile.

			public void endLoop() {
				synchronized (m_DrawLock) {
					m_KeepRunning = false;
					m_DrawLock.notify();
				}
			}

			public void requestRedraw() {
				synchronized (m_DrawLock) {
					m_RedrawRequested = true;
					m_DrawLock.notify();
				}
			}

			public void pause() {
				synchronized (m_DrawLock) {
					m_Paused = true;
					m_DrawLock.notify();
				}
			}

			public void unpause() {
				synchronized (m_DrawLock) {
					m_Paused = false;
					m_DrawLock.notify();
				}
			}

			private void updateBitmap(float canvasWidth, float canvasHeight) {
				if (m_CurrentBitmap == null || m_BitmapChanged == null) { // || (new Date().getTime() - m_BitmapChanged.getTime()) > 60 * 1000
					m_BitmapChanged = new Date();
					try (InputStream inputStream = m_Provider.getNextImage()) {
						Bitmap unscaledBitmap = BitmapFactory.decodeStream(inputStream);
						float scale = Math.max(canvasWidth / unscaledBitmap.getWidth(), canvasHeight / unscaledBitmap.getHeight());
						m_CurrentBitmap = Bitmap.createScaledBitmap(unscaledBitmap, (int) (unscaledBitmap.getWidth() * scale), (int) (unscaledBitmap.getHeight() * scale), true);
					} catch (IOException e) {
						throw new RuntimeException(e);
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
					// Unfortunately, drawing the wallpaper takes way too long to avoid that problem. Which is why we do it on a separate thread.
					while (m_KeepRunning) {
						// If this synchronized would take too long to execute, then the requestRedraw, pause, and unpause functions above
						// (which execute on the launcher's thread) then they would cause the aforementioned lag.
						synchronized (m_DrawLock) {
							m_DrawLock.wait();

							if (m_Paused || !m_RedrawRequested || (m_LastRedraw != null && (new Date().getTime() - m_LastRedraw.getTime()) < 1000 / 60)) {
								continue;
							}
							m_RedrawRequested = false;
						}
						m_LastRedraw = new Date();

						SurfaceHolder holder = getSurfaceHolder();
						Canvas canvas = null;
						try {
							canvas = holder.lockCanvas();
							Matrix transform = new Matrix();
							Paint paint = new Paint();

							updateBitmap(canvas.getWidth(), canvas.getHeight());

							// Scale to fill
							//float scale = Math.max((float) canvas.getWidth() / m_CurrentBitmap.getWidth(), (float) canvas.getHeight() / m_CurrentBitmap.getHeight());
							//transform.postScale(scale, scale);

							// Offset
							transform.postTranslate(-m_XOffset * (m_CurrentBitmap.getWidth() - canvas.getWidth()), -m_YOffset * (m_CurrentBitmap.getHeight() - canvas.getHeight()));

							long start = System.currentTimeMillis();
//							canvas.drawBitmap(
//								m_CurrentBitmap,
//								new Rect(0, 0, m_CurrentBitmap.getWidth(), m_CurrentBitmap.getHeight()),
//								new Rect(0, 0, canvas.getWidth(), canvas.getHeight()),
//								paint
//							); // not faster
							canvas.drawBitmap(m_CurrentBitmap, transform, paint); // 99% of time
							Log.d("SLIDESHOW", Long.toString(System.currentTimeMillis() - start));
						} finally {
							if (canvas != null) {
								holder.unlockCanvasAndPost(canvas);
							}
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}