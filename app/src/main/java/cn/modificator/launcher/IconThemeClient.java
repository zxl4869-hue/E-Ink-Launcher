package cn.modificator.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.model.IconCategoryResolver;
import cn.modificator.launcher.model.AppDataCenter;

/**
 * Downloads remote icon themes and reports package-specific missing icons.
 *
 * Server layout expected by the client:
 *   /eink-launcher/icons/index.json
 *   /eink-launcher/icons/themes/{themeId}/manifest.json
 *   /eink-launcher/icons/missing
 */
public final class IconThemeClient {

  private static final String TAG = "IconThemeClient";
  private static final String PREFS = "iconThemeClient";
  private static final String KEY_CACHED_THEMES = "cachedThemes";
  private static final String KEY_LAST_CHECK_TIME = "lastCheckTime";
  private static final String KEY_LAST_REPORT_PREFIX = "lastReport.";
  private static final String INDEX_URL_PRIMARY =
      "http://154.17.15.193/eink-launcher/icons/index.json";
  private static final String INDEX_URL_BACKUP =
      "https://app.ink-dock.com/eink-launcher/icons/index.json";
  private static final String MISSING_URL_PRIMARY =
      "http://154.17.15.193/eink-launcher/icons/missing";
  private static final String MISSING_URL_BACKUP =
      "https://app.ink-dock.com/eink-launcher/icons/missing";
  private static final long CHECK_INTERVAL_MS = 60L * 60L * 1000L;
  private static final long REPORT_INTERVAL_DAYS = 1L;
  private static final int CONNECT_TIMEOUT_MS = 10000;
  private static final int READ_TIMEOUT_MS = 30000;
  private static final int MAX_MISSING_REPORTS_PER_RUN = 20;

  private IconThemeClient() {
  }

  public interface SyncCallback {
    void onComplete(boolean changed, String message);
  }

  public static List<ThemeInfo> getKnownThemes(Context context) {
    List<ThemeInfo> themes = new ArrayList<>();
    themes.add(new ThemeInfo(Config.ICON_THEME_BUILTIN,
        context.getString(R.string.icon_theme_builtin), "builtin", ""));
    themes.add(new ThemeInfo(Config.ICON_THEME_LOCAL,
        context.getString(R.string.icon_theme_local), "local", ""));

    try {
      JSONArray cached = new JSONArray(prefs(context).getString(KEY_CACHED_THEMES, "[]"));
      Set<String> ids = new HashSet<>();
      ids.add(Config.ICON_THEME_BUILTIN);
      ids.add(Config.ICON_THEME_LOCAL);
      for (int i = 0; i < cached.length(); i++) {
        JSONObject item = cached.optJSONObject(i);
        if (item == null) continue;
        ThemeInfo theme = parseThemeInfo(item);
        if (theme == null || ids.contains(theme.id)) continue;
        themes.add(theme);
        ids.add(theme.id);
      }
    } catch (JSONException ignored) {
    }
    return themes;
  }

  public static String getThemeName(Context context, String themeId) {
    for (ThemeInfo theme : getKnownThemes(context)) {
      if (theme.id.equals(themeId)) return theme.name;
    }
    if (TextUtils.isEmpty(themeId)) return context.getString(R.string.icon_theme_builtin);
    return themeId;
  }

  public static String getNextThemeId(Context context, String currentThemeId) {
    List<ThemeInfo> themes = getKnownThemes(context);
    if (themes.isEmpty()) return Config.ICON_THEME_BUILTIN;
    int current = -1;
    for (int i = 0; i < themes.size(); i++) {
      if (themes.get(i).id.equals(currentThemeId)) {
        current = i;
        break;
      }
    }
    return themes.get((current + 1 + themes.size()) % themes.size()).id;
  }

  public static boolean isRemoteTheme(String themeId) {
    return !TextUtils.isEmpty(themeId)
        && !Config.ICON_THEME_BUILTIN.equals(themeId)
        && !Config.ICON_THEME_LOCAL.equals(themeId);
  }

  public static void checkForUpdatesAsync(final Context context,
                                          final List<ResolveInfo> apps,
                                          final String themeId,
                                          final boolean force,
                                          final SyncCallback callback) {
    final Context appContext = context.getApplicationContext();
    new Thread(new Runnable() {
      @Override
      public void run() {
        boolean changed = false;
        String message = "";
        try {
          changed = checkForUpdates(appContext, apps, themeId, force);
        } catch (Exception e) {
          message = e.getMessage() == null ? e.toString() : e.getMessage();
          Log.w(TAG, "Icon theme check failed", e);
        }
        if (callback != null) {
          callback.onComplete(changed, message);
        }
      }
    }, "IconThemeClient").start();
  }

  public static boolean checkForUpdates(Context context, List<ResolveInfo> apps,
                                        String themeId, boolean force)
      throws IOException, JSONException {
    SharedPreferences preferences = prefs(context);
    long now = System.currentTimeMillis();
    long lastCheck = preferences.getLong(KEY_LAST_CHECK_TIME, 0);
    if (!force && now - lastCheck < CHECK_INTERVAL_MS && hasCachedThemeIcons(context, themeId)) {
      return false;
    }

    List<ThemeInfo> remoteThemes;
    try {
      remoteThemes = fetchAndCacheThemeIndex(context);
    } finally {
      preferences.edit().putLong(KEY_LAST_CHECK_TIME, now).apply();
    }

    boolean changed = downloadThemePreviews(context, remoteThemes);
    if (!isRemoteTheme(themeId)) return changed;
    ThemeInfo theme = findTheme(remoteThemes, themeId);
    if (theme == null) {
      theme = findTheme(getKnownThemes(context), themeId);
    }
    if (theme == null || TextUtils.isEmpty(theme.manifestUrl)) return changed;

    ThemeManifest manifest = fetchThemeManifest(theme);
    changed |= downloadNeededIcons(context, manifest, apps);
    if (new Config(context).isIconAutoReportMissing()) {
      reportMissingIcons(context, manifest, apps);
    }
    return changed;
  }

  public static List<ResolveInfo> queryLauncherApps(Context context) {
    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> result = new ArrayList<>();
    List<ResolveInfo> installed = context.getPackageManager().queryIntentActivities(mainIntent, 0);
    for (ResolveInfo info : installed) {
      if (info == null || info.activityInfo == null) continue;
      if ("cn.modificator.launcher.Launcher".equals(info.activityInfo.name)) continue;
      result.add(info);
    }
    AppDataCenter.appendIconThemeVirtualApps(context, result);
    return result;
  }

  private static List<ThemeInfo> fetchAndCacheThemeIndex(Context context)
      throws IOException, JSONException {
    String usedUrl = INDEX_URL_PRIMARY;
    String body;
    try {
      body = readUrl(usedUrl);
    } catch (IOException e) {
      Log.w(TAG, "Primary icon theme index failed, trying backup", e);
      usedUrl = INDEX_URL_BACKUP;
      body = readUrl(usedUrl);
    }

    List<ThemeInfo> themes = parseThemeIndex(body, usedUrl);
    JSONArray normalized = new JSONArray();
    for (ThemeInfo theme : themes) {
      normalized.put(theme.toJson());
    }
    prefs(context).edit().putString(KEY_CACHED_THEMES, normalized.toString()).apply();
    return themes;
  }

  private static List<ThemeInfo> parseThemeIndex(String body, String indexUrl) throws JSONException {
    List<ThemeInfo> themes = new ArrayList<>();
    JSONObject root = new JSONObject(body);
    JSONArray array = root.optJSONArray("themes");
    if (array == null) return themes;
    for (int i = 0; i < array.length(); i++) {
      JSONObject item = array.optJSONObject(i);
      if (item == null) continue;
      ThemeInfo theme = parseThemeInfo(item, indexUrl);
      if (theme != null && "remote".equals(theme.type)
          && !Config.ICON_THEME_BUILTIN.equals(theme.id)) {
        themes.add(theme);
      }
    }
    return themes;
  }

  private static ThemeInfo parseThemeInfo(JSONObject item) {
    return parseThemeInfo(item, "");
  }

  private static ThemeInfo parseThemeInfo(JSONObject item, String baseUrl) {
    String id = item.optString("id", "");
    if (TextUtils.isEmpty(id)) return null;
    String name = item.optString("name", id);
    String type = item.optString("type", "remote");
    String manifestUrl = item.optString("manifestUrl", "");
    if (TextUtils.isEmpty(manifestUrl)) {
      manifestUrl = item.optString("manifest", "");
    }
    if (!TextUtils.isEmpty(baseUrl) && !TextUtils.isEmpty(manifestUrl)) {
      try {
        manifestUrl = resolveUrl(baseUrl, manifestUrl);
      } catch (IOException ignored) {
      }
    }
    return new ThemeInfo(id, name, type, manifestUrl);
  }

  private static ThemeInfo findTheme(List<ThemeInfo> themes, String themeId) {
    if (themes == null) return null;
    for (ThemeInfo theme : themes) {
      if (theme.id.equals(themeId)) return theme;
    }
    return null;
  }

  private static ThemeManifest fetchThemeManifest(ThemeInfo theme)
      throws IOException, JSONException {
    String body = readUrl(theme.manifestUrl);
    JSONObject root = new JSONObject(body);
    ThemeManifest manifest = new ThemeManifest();
    manifest.id = root.optString("id", theme.id);
    manifest.name = root.optString("name", theme.name);
    manifest.manifestUrl = theme.manifestUrl;
    manifest.missingReportUrl = root.optString("missingReportUrl", "");
    if (TextUtils.isEmpty(manifest.missingReportUrl)) {
      manifest.missingReportUrl = root.optString("reportMissingUrl", "");
    }
    if (!TextUtils.isEmpty(manifest.missingReportUrl)) {
      manifest.missingReportUrl = resolveUrl(theme.manifestUrl, manifest.missingReportUrl);
    }

    String iconBaseUrl = root.optString("iconsBaseUrl", "");
    if (TextUtils.isEmpty(iconBaseUrl)) iconBaseUrl = theme.manifestUrl;
    else iconBaseUrl = resolveUrl(theme.manifestUrl, iconBaseUrl);

    parseIconSection(root.optJSONObject("icons"), iconBaseUrl, manifest.allIcons,
        manifest.packageIcons);
    parseIconSection(root.optJSONObject("packages"), iconBaseUrl, manifest.allIcons,
        manifest.packageIcons);
    parseIconSection(root.optJSONObject("packageIcons"), iconBaseUrl, manifest.allIcons,
        manifest.packageIcons);
    parseIconSection(root.optJSONObject("categories"), iconBaseUrl, manifest.allIcons,
        manifest.categoryIcons);
    parseIconSection(root.optJSONObject("categoryIcons"), iconBaseUrl, manifest.allIcons,
        manifest.categoryIcons);
    parseDefaultIcon(root, iconBaseUrl, manifest);
    return manifest;
  }

  private static void parseIconSection(JSONObject section, String baseUrl,
                                       Map<String, RemoteIcon> allIcons,
                                       Map<String, RemoteIcon> typedIcons) {
    if (section == null) return;
    Iterator<String> keys = section.keys();
    while (keys.hasNext()) {
      String rawKey = keys.next();
      String key = IconCategoryResolver.normalizeIconKey(rawKey);
      RemoteIcon icon = parseIconEntry(section.opt(rawKey), baseUrl);
      if (TextUtils.isEmpty(key) || icon == null) continue;
      allIcons.put(key, icon);
      typedIcons.put(key, icon);
    }
  }

  private static void parseDefaultIcon(JSONObject root, String baseUrl,
                                       ThemeManifest manifest) {
    RemoteIcon icon = parseIconEntry(root.opt("defaultIcon"), baseUrl);
    if (icon == null) icon = parseIconEntry(root.opt("default"), baseUrl);
    if (icon == null) return;
    manifest.allIcons.put(IconCategoryResolver.KEY_CATEGORY_DEFAULT, icon);
    manifest.categoryIcons.put(IconCategoryResolver.KEY_CATEGORY_DEFAULT, icon);
  }

  private static RemoteIcon parseIconEntry(Object entry, String baseUrl) {
    if (entry == null || JSONObject.NULL.equals(entry)) return null;
    String url = "";
    String sha256 = "";
    if (entry instanceof String) {
      url = (String) entry;
    } else if (entry instanceof JSONObject) {
      JSONObject object = (JSONObject) entry;
      url = object.optString("url", "");
      if (TextUtils.isEmpty(url)) url = object.optString("file", "");
      if (TextUtils.isEmpty(url)) url = object.optString("path", "");
      sha256 = object.optString("sha256", "");
    }
    if (TextUtils.isEmpty(url)) return null;
    try {
      return new RemoteIcon(resolveUrl(baseUrl, url), sha256);
    } catch (IOException e) {
      return null;
    }
  }

  private static boolean downloadNeededIcons(Context context, ThemeManifest manifest,
                                             List<ResolveInfo> apps) throws IOException {
    boolean changed = false;
    File dir = IconCache.getThemeDirectory(context, manifest.id);
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("无法创建图标主题缓存目录");
    }

    LinkedHashMap<String, RemoteIcon> needed = new LinkedHashMap<>();
    addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_SYSTEM_WIFI_ON);
    addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_SYSTEM_WIFI_OFF);
    addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_SYSTEM_LOCK);
    addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_SYSTEM_ROTATE_SCREEN);
    addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_SYSTEM_QR_SCAN);
    addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_CATEGORY_DEFAULT);

    PackageManager pm = context.getPackageManager();
    for (ResolveInfo app : missingReportOrder(apps)) {
      if (app == null || app.activityInfo == null) continue;
      String pkg = app.activityInfo.packageName;
      CharSequence label = app.loadLabel(pm);
      addIconIfPresent(needed, manifest, pkg);
      addIconIfPresent(needed, manifest, IconCategoryResolver.getCategoryKey(pkg, label));
    }

    for (Map.Entry<String, RemoteIcon> entry : needed.entrySet()) {
      changed |= downloadIcon(dir, entry.getKey(), entry.getValue());
    }
    return changed;
  }

  private static boolean downloadThemePreviews(Context context, List<ThemeInfo> themes) {
    boolean changed = false;
    if (themes == null) return false;
    for (ThemeInfo theme : themes) {
      if (theme == null || TextUtils.isEmpty(theme.manifestUrl)) continue;
      try {
        ThemeManifest manifest = fetchThemeManifest(theme);
        LinkedHashMap<String, RemoteIcon> needed = new LinkedHashMap<>();
        addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_CATEGORY_READER);
        addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_CATEGORY_BROWSER);
        addIconIfPresent(needed, manifest, IconCategoryResolver.KEY_CATEGORY_DEFAULT);
        File dir = IconCache.getThemeDirectory(context, manifest.id);
        if (!dir.exists() && !dir.mkdirs()) continue;
        for (Map.Entry<String, RemoteIcon> entry : needed.entrySet()) {
          changed |= downloadIcon(dir, entry.getKey(), entry.getValue());
        }
      } catch (Exception e) {
        Log.w(TAG, "Icon theme preview download failed: " + theme.id, e);
      }
    }
    return changed;
  }

  private static void addIconIfPresent(LinkedHashMap<String, RemoteIcon> needed,
                                       ThemeManifest manifest, String key) {
    RemoteIcon icon = manifest.allIcons.get(IconCategoryResolver.normalizeIconKey(key));
    if (icon != null) {
      needed.put(IconCategoryResolver.normalizeIconKey(key), icon);
    }
  }

  private static boolean downloadIcon(File dir, String key, RemoteIcon icon) throws IOException {
    File target = new File(dir, IconCategoryResolver.safeFileNameForKey(key));
    if (target.exists() && (TextUtils.isEmpty(icon.sha256) || icon.sha256.equalsIgnoreCase(sha256(target)))) {
      return false;
    }
    File tmp = new File(dir, target.getName() + ".tmp");
    HttpURLConnection connection = (HttpURLConnection) new URL(icon.url).openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setInstanceFollowRedirects(true);
    try {
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("图标下载失败 HTTP " + code);
      }
      InputStream in = new BufferedInputStream(connection.getInputStream());
      FileOutputStream out = new FileOutputStream(tmp);
      try {
        copy(in, out);
      } finally {
        closeQuietly(out);
        closeQuietly(in);
      }
    } finally {
      connection.disconnect();
    }
    if (!TextUtils.isEmpty(icon.sha256)) {
      String actualSha256 = sha256(tmp);
      if (!icon.sha256.equalsIgnoreCase(actualSha256)) {
        Log.w(TAG, "Icon checksum mismatch: " + key
            + " expected=" + icon.sha256 + " actual=" + actualSha256
            + ", keeping downloaded icon");
      }
    }
    if (target.exists() && !target.delete()) {
      throw new IOException("无法替换旧图标：" + key);
    }
    if (!tmp.renameTo(target)) {
      throw new IOException("无法保存图标：" + key);
    }
    return true;
  }

  private static boolean hasCachedThemeIcons(Context context, String themeId) {
    if (!isRemoteTheme(themeId)) return true;
    File dir = IconCache.getThemeDirectory(context, themeId);
    File[] files = dir.listFiles();
    if (files == null) return false;
    for (File file : files) {
      if (file == null || !file.isFile()) continue;
      String name = file.getName();
      if (name.startsWith(".")) continue;
      if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
          || name.endsWith(".webp")) {
        return true;
      }
    }
    return false;
  }

  private static void reportMissingIcons(Context context, ThemeManifest manifest,
                                         List<ResolveInfo> apps) {
    PackageManager pm = context.getPackageManager();
    int count = 0;
    for (ResolveInfo app : safeApps(apps)) {
      if (count >= MAX_MISSING_REPORTS_PER_RUN) return;
      if (app == null || app.activityInfo == null) continue;
      String pkg = app.activityInfo.packageName;
      String normalizedPkg = IconCategoryResolver.normalizeIconKey(pkg);
      if (TextUtils.isEmpty(pkg) || manifest.allIcons.containsKey(normalizedPkg)) continue;
      if (!shouldReportMissing(context, manifest.id, pkg)) continue;
      CharSequence label = app.loadLabel(pm);
      String category = missingReportCategory(pkg, label);
      try {
        byte[] icon = drawableToPng(loadReportIcon(context, app, pm, pkg));
        postMissingIcon(context, manifest, pkg, label == null ? "" : label.toString(), category, icon);
      } catch (Exception e) {
        Log.w(TAG, "Missing icon report failed: " + pkg, e);
      }
      markMissingReported(context, manifest.id, pkg);
      count++;
    }
  }

  private static List<ResolveInfo> missingReportOrder(List<ResolveInfo> apps) {
    List<ResolveInfo> safe = safeApps(apps);
    List<ResolveInfo> ordered = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (ResolveInfo app : safe) {
      if (app == null || app.activityInfo == null) continue;
      String pkg = app.activityInfo.packageName;
      if (!isPriorityMissingPackage(pkg) || !seen.add(pkg)) continue;
      ordered.add(app);
    }
    for (ResolveInfo app : safe) {
      if (app == null || app.activityInfo == null) continue;
      String pkg = app.activityInfo.packageName;
      if (TextUtils.isEmpty(pkg) || !seen.add(pkg)) continue;
      ordered.add(app);
    }
    return ordered;
  }

  private static boolean isPriorityMissingPackage(String packageName) {
    return AppDataCenter.ROTATE_PACKAGE_NAME.equals(packageName)
        || AppDataCenter.QR_SCAN_PACKAGE_NAME.equals(packageName);
  }

  private static String missingReportCategory(String packageName, CharSequence label) {
    if (isPriorityMissingPackage(packageName)) {
      return IconCategoryResolver.normalizeIconKey(packageName);
    }
    return IconCategoryResolver.getCategoryKey(packageName, label);
  }

  private static Drawable loadReportIcon(Context context, ResolveInfo app, PackageManager pm,
                                         String packageName) {
    int virtualIcon = AppDataCenter.getVirtualIconRes(packageName);
    if (virtualIcon != 0) {
      return context.getResources().getDrawable(virtualIcon);
    }
    return app.loadIcon(pm);
  }

  private static boolean shouldReportMissing(Context context, String themeId, String packageName) {
    long today = System.currentTimeMillis() / (24L * 60L * 60L * 1000L);
    long last = prefs(context).getLong(KEY_LAST_REPORT_PREFIX + themeId + "." + packageName, 0);
    return today - last >= REPORT_INTERVAL_DAYS;
  }

  private static void markMissingReported(Context context, String themeId, String packageName) {
    long today = System.currentTimeMillis() / (24L * 60L * 60L * 1000L);
    prefs(context).edit().putLong(KEY_LAST_REPORT_PREFIX + themeId + "." + packageName, today).apply();
  }

  private static void postMissingIcon(Context context, ThemeManifest manifest, String packageName,
                                      String label, String category, byte[] iconBytes)
      throws IOException {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("themeId", manifest.id);
    fields.put("packageName", packageName);
    fields.put("appName", label);
    fields.put("category", category);
    fields.put("launcherVersionCode", String.valueOf(AppUpdateClient.getCurrentVersionCode(context)));
    fields.put("launcherVersionName", AppUpdateClient.getCurrentVersionName(context));
    fields.put("device", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);

    if (!TextUtils.isEmpty(manifest.missingReportUrl)) {
      postMultipart(manifest.missingReportUrl, fields, "defaultIcon.png", iconBytes);
      return;
    }
    try {
      postMultipart(MISSING_URL_PRIMARY, fields, "defaultIcon.png", iconBytes);
    } catch (IOException e) {
      Log.w(TAG, "Primary missing icon endpoint failed, trying backup", e);
      postMultipart(MISSING_URL_BACKUP, fields, "defaultIcon.png", iconBytes);
    }
  }

  private static void postMultipart(String url, Map<String, String> fields, String fileName,
                                    byte[] fileBytes) throws IOException {
    String boundary = "----EInkIconTheme" + System.currentTimeMillis();
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    OutputStream out = connection.getOutputStream();
    try {
      for (Map.Entry<String, String> field : fields.entrySet()) {
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n");
        write(out, field.getValue() == null ? "" : field.getValue());
        write(out, "\r\n");
      }
      if (fileBytes != null && fileBytes.length > 0) {
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"defaultIcon\"; filename=\"" + fileName + "\"\r\n");
        write(out, "Content-Type: image/png\r\n\r\n");
        out.write(fileBytes);
        write(out, "\r\n");
      }
      write(out, "--" + boundary + "--\r\n");
      out.flush();
    } finally {
      closeQuietly(out);
    }
    try {
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("缺失图标上报失败 HTTP " + code);
      }
    } finally {
      connection.disconnect();
    }
  }

  private static byte[] drawableToPng(Drawable drawable) {
    int width = drawable.getIntrinsicWidth();
    int height = drawable.getIntrinsicHeight();
    if (width <= 0) width = 96;
    if (height <= 0) height = 96;
    int max = Math.max(width, height);
    if (max > 128) {
      float scale = 128f / max;
      width = Math.max(1, Math.round(width * scale));
      height = Math.max(1, Math.round(height * scale));
    }
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, width, height);
    drawable.draw(canvas);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    bitmap.recycle();
    return out.toByteArray();
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
        copy(in, out);
      } finally {
        closeQuietly(in);
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
      InputStream in = new BufferedInputStream(new FileInputStream(file));
      try {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      } finally {
        closeQuietly(in);
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

  private static List<ResolveInfo> safeApps(List<ResolveInfo> apps) {
    return apps == null ? new ArrayList<ResolveInfo>() : apps;
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[16 * 1024];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
  }

  private static void write(OutputStream out, String text) throws IOException {
    out.write(text.getBytes("UTF-8"));
  }

  private static void closeQuietly(java.io.Closeable closeable) {
    if (closeable == null) return;
    try {
      closeable.close();
    } catch (IOException ignored) {
    }
  }

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static final class ThemeInfo {
    public final String id;
    public final String name;
    public final String type;
    public final String manifestUrl;

    ThemeInfo(String id, String name, String type, String manifestUrl) {
      this.id = id;
      this.name = name;
      this.type = TextUtils.isEmpty(type) ? "remote" : type;
      this.manifestUrl = manifestUrl == null ? "" : manifestUrl;
    }

    JSONObject toJson() throws JSONException {
      JSONObject json = new JSONObject();
      json.put("id", id);
      json.put("name", name);
      json.put("type", type);
      json.put("manifestUrl", manifestUrl);
      return json;
    }
  }

  private static final class ThemeManifest {
    String id;
    String name;
    String manifestUrl;
    String missingReportUrl;
    final Map<String, RemoteIcon> allIcons = new HashMap<>();
    final Map<String, RemoteIcon> packageIcons = new HashMap<>();
    final Map<String, RemoteIcon> categoryIcons = new HashMap<>();
  }

  private static final class RemoteIcon {
    final String url;
    final String sha256;

    RemoteIcon(String url, String sha256) {
      this.url = url;
      this.sha256 = sha256 == null ? "" : sha256;
    }
  }
}
