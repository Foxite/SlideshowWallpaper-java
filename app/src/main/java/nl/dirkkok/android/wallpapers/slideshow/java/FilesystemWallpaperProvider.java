package nl.dirkkok.android.wallpapers.slideshow.java;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class FilesystemWallpaperProvider implements WallpaperProvider {
	private final File m_Folder;
	private int m_Index = 0;

	public FilesystemWallpaperProvider(File folder) {
		if (!folder.isDirectory()) {
			throw new IllegalArgumentException("Path indicated by {file} must be a directory");
		}
		m_Folder = folder;
	}

	public InputStream getNextImage() {
		File[] files = m_Folder.listFiles(File::isFile);
		assert files != null; // listFiles returns null if you're not calling it on a folder. but we check if it's a directory in the constructor
		assert files.length > 0;
		if (m_Index > files.length) {
			m_Index = 0;
		}
		File selected = files[this.m_Index];
		assert selected.exists();
		assert selected.isFile();
		assert selected.canRead();
		m_Index++;
		try {
			return new FileInputStream(selected);
		} catch (FileNotFoundException ex) {
			throw new AssertionError("What the fuck", ex);
		}
	}
}
