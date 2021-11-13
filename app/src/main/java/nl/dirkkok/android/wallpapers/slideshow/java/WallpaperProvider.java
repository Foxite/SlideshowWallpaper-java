package nl.dirkkok.android.wallpapers.slideshow.java;

import java.io.InputStream;

public interface WallpaperProvider {
	InputStream getNextImage();
}
