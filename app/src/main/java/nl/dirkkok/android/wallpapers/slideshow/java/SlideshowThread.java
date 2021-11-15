package nl.dirkkok.android.wallpapers.slideshow.java;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;

class SlideshowThread extends Thread {
	private final SlideshowWallpaperService.SlideshowEngine m_Engine;
	private long m_BitmapChanged = 0;

	public SlideshowThread(SlideshowWallpaperService.SlideshowEngine engine) {
		m_Engine = engine;
	}

	private void updateBitmap() {
		int width;
		int height;
		WallpaperProvider provider;
		synchronized (m_Engine.m_DrawLock) {
			if (!(m_Engine.m_CurrentBitmap == null || (System.currentTimeMillis() - m_BitmapChanged) > 10 * 1000)) {
				return;
			}
			width = m_Engine.getWidth();
			height = m_Engine.getHeight();
			provider = m_Engine.getProvider();
		}
		try (InputStream inputStream = provider.getNextImage()) {
			Bitmap unscaledBitmap = BitmapFactory.decodeStream(inputStream);
			float scale = Math.max((float) width / unscaledBitmap.getWidth(), (float) height / unscaledBitmap.getHeight());

			Bitmap newBitmap = Bitmap.createScaledBitmap(unscaledBitmap, (int) (unscaledBitmap.getWidth() * scale), (int) (unscaledBitmap.getHeight() * scale), true);
			synchronized (m_Engine.m_DrawLock) {
				if (m_Engine.m_PreviousBitmap != null && !m_Engine.m_PreviousBitmap.isRecycled()) {
					m_Engine.m_PreviousBitmap.recycle();
				}

				m_Engine.m_PreviousBitmap = m_Engine.m_CurrentBitmap;
				m_Engine.m_CurrentBitmap = newBitmap;
				m_Engine.m_PreviousBitmapPaint.setAlpha(1);
			}
			m_BitmapChanged = System.currentTimeMillis();

			synchronized (m_Engine.m_MessageLock) {
				m_Engine.requestRedraw();
				m_Engine.m_MessageLock.notify();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private long calculateMillisToWait() {
		long millisSinceUpdate = System.currentTimeMillis() - m_BitmapChanged;
		final long millisBetweenUpdates = 10_000;
		return millisBetweenUpdates - millisSinceUpdate;
	}

	@Override
	public void run() {
		try {
			while (m_Engine.m_Running) {
				long millisToWait = calculateMillisToWait();
				if (millisToWait > 0) {
					synchronized (m_Engine.m_MessageLock) {
						m_Engine.m_MessageLock.wait(millisToWait);
					}
					if (calculateMillisToWait() > 0) {
						continue;
					}
				}
				updateBitmap();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
