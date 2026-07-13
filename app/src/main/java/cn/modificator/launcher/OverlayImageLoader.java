package cn.modificator.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.Random;

public class OverlayImageLoader {

  private static final String TAG = "OverlayImageLoader";
  private static final Random random = new Random();

  private OverlayImageLoader() {}

  public static Bitmap loadRandomOverlay(String dirPath) {
    return loadOverlay(dirPath, true);
  }

  public static Bitmap loadOverlay(String dirPath, boolean randomPick) {
    if (dirPath == null || dirPath.isEmpty()) return null;
    File dir = new File(dirPath);
    if (!dir.exists()) {
      if (!dir.mkdirs()) return null;
      Log.i(TAG, "Created overlay directory: " + dirPath);
    }
    if (!dir.isDirectory()) return null;

    File[] files = listOverlayFiles(dir);
    if (files == null || files.length == 0) return null;
    File chosen = randomPick ? files[random.nextInt(files.length)] : newestFile(files);
    try {
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
      Bitmap bitmap = BitmapFactory.decodeFile(chosen.getAbsolutePath(), opts);
      if (bitmap == null) return null;
      Log.d(TAG, "Loaded overlay: " + chosen.getName()
          + " (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
      return bitmap;
    } catch (Exception e) {
      Log.w(TAG, "Failed to load overlay: " + chosen.getAbsolutePath(), e);
      return null;
    }
  }

  private static File[] listOverlayFiles(File dir) {
    return dir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File file) {
        if (!file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
            || name.endsWith(".jpg")
            || name.endsWith(".jpeg")
            || name.endsWith(".webp");
      }
    });
  }

  private static File newestFile(File[] files) {
    File newest = files[0];
    for (File file : files) {
      if (file.lastModified() > newest.lastModified()) {
        newest = file;
      }
    }
    return newest;
  }
}
