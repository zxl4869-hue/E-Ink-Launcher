package cn.modificator.launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class AppUpdateClient {
  static final String MANIFEST_URL_PRIMARY = "http://154.17.15.193/eink-launcher/update.json";
  static final String MANIFEST_URL_BACKUP = "https://app.ink-dock.com/eink-launcher/update.json";

  private static final int CONNECT_TIMEOUT_MS = 10000;
  private static final int READ_TIMEOUT_MS = 30000;

  private AppUpdateClient() {
  }

  static UpdateInfo fetchUpdateInfo() throws IOException, JSONException {
    String body;
    String usedUrl;
    try {
      usedUrl = MANIFEST_URL_PRIMARY;
      body = readUrl(usedUrl);
    } catch (IOException e) {
      Log.w("AppUpdateClient", "Primary update URL failed, trying backup...", e);
      usedUrl = MANIFEST_URL_BACKUP;
      body = readUrl(usedUrl);
    }
    JSONObject json = new JSONObject(body);
    UpdateInfo info = new UpdateInfo();
    info.versionCode = json.optInt("versionCode", 0);
    info.versionName = json.optString("versionName", "");
    info.apkUrl = resolveUrl(usedUrl, json.optString("apkUrl", ""));
    info.notes = json.optString("notes", "");
    info.sha256 = json.optString("sha256", "");
    if (info.versionCode <= 0 || TextUtils.isEmpty(info.apkUrl)) {
      throw new IOException("更新清单缺少 versionCode 或 apkUrl");
    }
    return info;
  }

  static File downloadApk(Context context, UpdateInfo info, ProgressListener listener)
      throws IOException {
    File dir = context.getExternalFilesDir("updates");
    if (dir == null) {
      dir = new File(context.getCacheDir(), "updates");
    }
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("无法创建更新缓存目录");
    }
    File target = new File(dir, "eink-launcher-" + info.versionCode + ".apk");
    File tmp = new File(dir, target.getName() + ".tmp");

    HttpURLConnection connection = (HttpURLConnection) new URL(info.apkUrl).openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setInstanceFollowRedirects(true);
    try {
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("下载失败 HTTP " + code);
      }
      int total = connection.getContentLength();
      InputStream in = new BufferedInputStream(connection.getInputStream());
      FileOutputStream out = new FileOutputStream(tmp);
      try {
        byte[] buffer = new byte[16 * 1024];
        long readTotal = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
          readTotal += read;
          if (listener != null && total > 0) {
            listener.onProgress((int) Math.min(100, readTotal * 100 / total));
          }
        }
        out.flush();
      } finally {
        try {
          out.close();
        } catch (IOException ignored) {
        }
        try {
          in.close();
        } catch (IOException ignored) {
        }
      }
    } finally {
      connection.disconnect();
    }

    if (!TextUtils.isEmpty(info.sha256) && !info.sha256.equalsIgnoreCase(sha256(tmp))) {
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      throw new IOException("APK 校验失败");
    }
    if (target.exists() && !target.delete()) {
      throw new IOException("无法替换旧更新包");
    }
    if (!tmp.renameTo(target)) {
      throw new IOException("无法保存更新包");
    }
    return target;
  }

  static int getCurrentVersionCode(Context context) {
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        return (int) info.getLongVersionCode();
      }
      return info.versionCode;
    } catch (PackageManager.NameNotFoundException e) {
      return 0;
    }
  }

  static String getCurrentVersionName(Context context) {
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      return info.versionName == null ? "" : info.versionName;
    } catch (PackageManager.NameNotFoundException e) {
      return "";
    }
  }

  private static String readUrl(String url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setInstanceFollowRedirects(true);
    try {
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("请求失败 HTTP " + code);
      }
      InputStream in = new BufferedInputStream(connection.getInputStream());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
      } finally {
        try {
          in.close();
        } catch (IOException ignored) {
        }
      }
      return out.toString("UTF-8");
    } finally {
      connection.disconnect();
    }
  }

  private static String resolveUrl(String baseUrl, String rawUrl) throws IOException {
    if (TextUtils.isEmpty(rawUrl)) return "";
    URL base = new URL(baseUrl);
    return new URL(base, rawUrl).toString();
  }

  private static String sha256(File file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      InputStream in = new BufferedInputStream(new java.io.FileInputStream(file));
      try {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      } finally {
        try {
          in.close();
        } catch (IOException ignored) {
        }
      }
      byte[] bytes = digest.digest();
      StringBuilder builder = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        builder.append(String.format(Locale.US, "%02x", b & 0xff));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("缺少 SHA-256 支持", e);
    }
  }

  interface ProgressListener {
    void onProgress(int percent);
  }

  static final class UpdateInfo {
    int versionCode;
    String versionName;
    String apkUrl;
    String notes;
    String sha256;
  }
}
