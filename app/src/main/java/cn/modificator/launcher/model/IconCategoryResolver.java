package cn.modificator.launcher.model;

import android.text.TextUtils;

import java.util.Locale;
import java.util.regex.Pattern;

import cn.modificator.launcher.R;

/**
 * Centralized icon matching rules.
 *
 * Remote themes and local icon folders both use these virtual package names for
 * generic category icons, so the display path and the missing-icon upload path
 * always agree on the same fallback.
 */
public final class IconCategoryResolver {

  public static final String KEY_CATEGORY_PHONE = "eink.category.phone";
  public static final String KEY_CATEGORY_MESSAGE = "eink.category.message";
  public static final String KEY_CATEGORY_CAMERA = "eink.category.camera";
  public static final String KEY_CATEGORY_GALLERY = "eink.category.gallery";
  public static final String KEY_CATEGORY_SETTINGS = "eink.category.settings";
  public static final String KEY_CATEGORY_FILES = "eink.category.files";
  public static final String KEY_CATEGORY_RECORDER = "eink.category.recorder";
  public static final String KEY_CATEGORY_CONTACTS = "eink.category.contacts";
  public static final String KEY_CATEGORY_MUSIC = "eink.category.music";
  public static final String KEY_CATEGORY_BROWSER = "eink.category.browser";
  public static final String KEY_CATEGORY_READER = "eink.category.reader";
  public static final String KEY_CATEGORY_TOOLS = "eink.category.tools";
  public static final String KEY_CATEGORY_DEFAULT = "eink.category.default";

  public static final String KEY_SYSTEM_WIFI_ON = "eink.system.wifi.on";
  public static final String KEY_SYSTEM_WIFI_OFF = "eink.system.wifi.off";
  public static final String KEY_SYSTEM_LOCK = "eink.system.lock";
  public static final String KEY_SYSTEM_ROTATE_SCREEN = "eink.system.rotate_screen";
  public static final String KEY_SYSTEM_QR_SCAN = "eink.system.qr_scan";

  private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
  private static final Pattern PHONE_PATTERN = pattern("电话|拨号|通话|dialer|phone|telecom");
  private static final Pattern MESSAGE_PATTERN = pattern("信息|短信|短消息|message|messaging|mms|sms");
  private static final Pattern CAMERA_PATTERN = pattern("相机|拍照|camera|quickcamera");
  private static final Pattern GALLERY_PATTERN = pattern("图库|相册|照片|图片|gallery|photo|album");
  private static final Pattern SETTINGS_PATTERN =
      pattern("设置|setting|settings|einksettings|墨水屏设置|水墨屏设置");
  private static final Pattern FILE_PATTERN =
      pattern("文件|文档|file|files|documentsui|manager|storage|download");
  private static final Pattern RECORDER_PATTERN =
      pattern("录音|录音机|record|recorder|soundrecorder");
  private static final Pattern CONTACTS_PATTERN = pattern("通讯录|联系人|contact|contacts");
  private static final Pattern MUSIC_PATTERN = pattern("音乐|音频|music|player|audio");
  private static final Pattern BROWSER_PATTERN = pattern(
      "浏览器|浏览|网页|上网|browser|chrome|firefox|fennec|mozilla|via|einkbro|edge|emm(x|x)|opera|brave|vivaldi|quark|ucmobile|mtt|baidu\\.browser|kiwibrowser|duckduckgo|webview");
  private static final Pattern READING_PATTERN = pattern(
      "阅读|读书|书城|小说|电子书|reader|readera|weread|legado|kindle|kobo|ireader|duokan|moonreader|fbreader|librera|koreader|pocketbook|qidian|shuqi|dragon\\.read|epub|ebook|book");
  private static final Pattern TOOL_PATTERN = pattern(
      "工具|助手|管理|服务|终端|调试|权限|传输|同步|utility|tool|tools|manager|admin|service|terminal|debug|shell|shizuku|stk|sim|stopapp|black|clash|localsend|vpn|proxy|proxyhandler|terminal_test");

  private IconCategoryResolver() {
  }

  public static String getCategoryKey(String pkg, CharSequence label) {
    String text = buildMatchText(pkg, label);
    if (isWeReadPackage(pkg)) return KEY_CATEGORY_READER;
    if (matches(PHONE_PATTERN, text)) return KEY_CATEGORY_PHONE;
    if (matches(MESSAGE_PATTERN, text)) return KEY_CATEGORY_MESSAGE;
    if (matches(CAMERA_PATTERN, text)) return KEY_CATEGORY_CAMERA;
    if (matches(GALLERY_PATTERN, text)) return KEY_CATEGORY_GALLERY;
    if (matches(SETTINGS_PATTERN, text)) return KEY_CATEGORY_SETTINGS;
    if (matches(FILE_PATTERN, text)) return KEY_CATEGORY_FILES;
    if (matches(RECORDER_PATTERN, text)) return KEY_CATEGORY_RECORDER;
    if (matches(CONTACTS_PATTERN, text)) return KEY_CATEGORY_CONTACTS;
    if (matches(MUSIC_PATTERN, text)) return KEY_CATEGORY_MUSIC;
    if (matches(BROWSER_PATTERN, text)) return KEY_CATEGORY_BROWSER;
    if (isReadingPackage(pkg) || matches(READING_PATTERN, text)) return KEY_CATEGORY_READER;
    if (matches(TOOL_PATTERN, text)) return KEY_CATEGORY_TOOLS;
    return KEY_CATEGORY_DEFAULT;
  }

  public static int getBuiltinIconRes(String key) {
    if (KEY_SYSTEM_WIFI_ON.equals(key) || KEY_SYSTEM_WIFI_OFF.equals(key)) {
      return R.drawable.ic_line_wifi;
    }
    if (KEY_SYSTEM_LOCK.equals(key)) return R.drawable.ic_line_lock;
    if (KEY_SYSTEM_ROTATE_SCREEN.equals(key)) return R.drawable.ic_line_rotate;
    if (KEY_SYSTEM_QR_SCAN.equals(key)) return R.drawable.ic_line_qr_scan;
    if (KEY_CATEGORY_PHONE.equals(key)) return R.drawable.ic_line_phone;
    if (KEY_CATEGORY_MESSAGE.equals(key)) return R.drawable.ic_line_message;
    if (KEY_CATEGORY_CAMERA.equals(key)) return R.drawable.ic_line_camera;
    if (KEY_CATEGORY_GALLERY.equals(key)) return R.drawable.ic_line_gallery;
    if (KEY_CATEGORY_SETTINGS.equals(key)) return R.drawable.ic_line_settings;
    if (KEY_CATEGORY_FILES.equals(key)) return R.drawable.ic_line_file;
    if (KEY_CATEGORY_RECORDER.equals(key)) return R.drawable.ic_line_mic;
    if (KEY_CATEGORY_CONTACTS.equals(key)) return R.drawable.ic_line_contacts;
    if (KEY_CATEGORY_MUSIC.equals(key)) return R.drawable.ic_line_music;
    if (KEY_CATEGORY_BROWSER.equals(key)) return R.drawable.ic_line_browser;
    if (KEY_CATEGORY_READER.equals(key)) return R.drawable.ic_line_book;
    if (KEY_CATEGORY_TOOLS.equals(key)) return R.drawable.ic_line_tool;
    return R.drawable.ic_line_app;
  }

  public static int getBuiltinIconResForApp(String pkg, String categoryKey) {
    if (isWeReadPackage(pkg)) return R.drawable.ic_line_weread;
    return getBuiltinIconRes(categoryKey);
  }

  public static String normalizeIconKey(String key) {
    if (key == null) return "";
    String normalized = key.trim();
    if (TextUtils.isEmpty(normalized)) return "";
    if ("E-ink_Launcher.Lock".equalsIgnoreCase(normalized)) return KEY_SYSTEM_LOCK;
    if ("E-ink_Launcher.Rotate".equalsIgnoreCase(normalized)) return KEY_SYSTEM_ROTATE_SCREEN;
    if ("E-ink_Launcher.QrScan".equalsIgnoreCase(normalized)) return KEY_SYSTEM_QR_SCAN;
    if ("E-ink_Launcher.WifiOn".equalsIgnoreCase(normalized)) return KEY_SYSTEM_WIFI_ON;
    if ("E-ink_Launcher.WifiOff".equalsIgnoreCase(normalized)) return KEY_SYSTEM_WIFI_OFF;
    return normalized;
  }

  public static String safeFileNameForKey(String key) {
    String normalized = normalizeIconKey(key);
    if (TextUtils.isEmpty(normalized)) return "";
    return normalized.replace('/', '_').replace('\\', '_').replace(':', '_') + ".png";
  }

  private static String buildMatchText(String pkg, CharSequence label) {
    StringBuilder builder = new StringBuilder();
    if (label != null) {
      builder.append(label);
    }
    builder.append(' ');
    if (pkg != null) {
      builder.append(pkg);
    }
    return builder.toString().toLowerCase(Locale.ROOT);
  }

  private static Pattern pattern(String regex) {
    return Pattern.compile(regex, REGEX_FLAGS);
  }

  private static boolean matches(Pattern pattern, String text) {
    return text != null && pattern.matcher(text).find();
  }

  private static boolean isWeReadPackage(String pkg) {
    return pkg != null && pkg.toLowerCase(Locale.ROOT).startsWith("com.tencent.weread");
  }

  private static boolean isReadingPackage(String pkg) {
    if (pkg == null) return false;
    String p = pkg.toLowerCase(Locale.ROOT);
    if (p.startsWith("com.tencent.weread")) return true;
    if (p.startsWith("io.legado.app")) return true;
    switch (p) {
      case "com.amazon.kindle":
      case "com.google.android.apps.books":
      case "com.kobobooks.android":
      case "com.chaozh.ireader":
      case "com.chaozh.ireaderfree":
      case "com.qq.reader":
      case "com.duokan.reader":
      case "com.netease.snailread":
      case "com.luojilab.player":
      case "com.flyersoft.moonreader":
      case "com.flyersoft.moonreaderp":
      case "org.readera":
      case "org.geometerplus.zlibrary.ui.android":
      case "com.foobnix.pdf.reader":
      case "com.foobnix.pro.pdf.reader":
      case "org.koreader.launcher":
      case "org.koreader.launcher.fdroid":
      case "com.obreey.reader":
      case "com.jd.app.reader":
      case "com.dangdang.reader":
      case "com.shuqi.controller":
      case "com.dragon.read":
      case "com.qidian.qdreader":
      case "com.kmxs.reader":
        return true;
      default:
        return false;
    }
  }
}
