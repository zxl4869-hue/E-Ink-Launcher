package cn.modificator.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.DownloadManager;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import androidx.core.content.FileProvider;

import cn.modificator.launcher.autorefresh.AutoRefreshSettings;
import cn.modificator.launcher.ftpservice.FTPReceiver;
import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.legado.LegadoHomeController;
import cn.modificator.launcher.model.AdminReceiver;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.AppSortComparator;
import cn.modificator.launcher.model.ForceRotationService;
import cn.modificator.launcher.model.HomeEntranceService;
import cn.modificator.launcher.model.IconCategoryResolver;
import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.reading.ReadingProgressRepository;
import cn.modificator.launcher.reading.WeReadWallpaperQuoteSource;
import cn.modificator.launcher.model.WifiControl;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.EInkLauncherView;
import cn.modificator.launcher.widgets.LauncherAdapter;
import cn.modificator.launcher.widgets.PageIndicatorView;
import cn.modificator.launcher.wifitransfer.WifiFileTransferService;

/**
 * 主界面 Activity - E-Ink 墨水屏桌面启动器。
 */
public class Launcher extends Activity
    implements AppItemBinder.Callback, EInkLauncherView.OnPageChangeListener,
    SettingFragment.OnSettingChangeListener {

  private static final int REQUEST_DEVICE_ADMIN = 10001;
  private static final int REQUEST_MEDIA_PROJECTION = 10003;
  private static final int REQUEST_RUNTIME_PERMISSIONS = 10004;
  private static final int REQUEST_QR_SCAN = 10005;
  private static final int QR_DOWNLOAD_CONNECT_TIMEOUT_MS = 20000;
  private static final int QR_DOWNLOAD_READ_TIMEOUT_MS = 120000;
  private static final int QR_DOWNLOAD_MAX_RETRIES = 3;
  private static final long QR_SYSTEM_DOWNLOAD_TIMEOUT_MS = 90000L;
  private static final long QR_SYSTEM_APK_DOWNLOAD_TIMEOUT_MS = 600000L;
  private static final long QR_SYSTEM_IMAGE_STALL_TIMEOUT_MS = 12000L;
  private static final long QR_SYSTEM_APK_STALL_TIMEOUT_MS = 45000L;
  private static final long QR_SYSTEM_DOWNLOAD_POLL_MS = 500L;
  private static final int SECTION_APPS = 0;
  private static final int SECTION_DRAWER = 1;
  private static final int SECTION_READING = 2;
  private static final int SECTION_SETTINGS = 3;
  private static final int DRAWER_LANDSCAPE_COLS = 4;
  private static final int DRAWER_LANDSCAPE_ROWS = 3;
  private static final int DRAWER_PORTRAIT_COLS = 3;
  private static final int DRAWER_PORTRAIT_ROWS = 4;
  private static final int SETTINGS_SUB_HOME = 0;
  private static final int SETTINGS_SUB_DESKTOP = 1;
  private static final int SETTINGS_SUB_TRANSFER = 2;
  private static final int SETTINGS_SUB_AUTO_REFRESH = 3;
  private static final int SETTINGS_SUB_UPDATE = 4;
  private static final int SETTINGS_SUB_ICON_THEME = 5;
  private static final int SETTINGS_SUB_LAYOUT = 6;
  private static final int SETTINGS_SUB_DISPLAY = 7;
  private static final int SETTINGS_SUB_ICON = 8;
  private static final int SETTINGS_SUB_ORIENTATION = 9;
  private static final int SETTINGS_SUB_STANDBY = 10;
  private static final int SETTINGS_SUB_PERMISSIONS = 11;
  private static final int SETTINGS_SUB_STANDBY_CLOCK_STYLE = 12;
  private static final int SETTINGS_SUB_STANDBY_RANDOM_WALLPAPER_INTERVAL = 13;
  private static final int SETTINGS_SUB_READING = 14;
  private static final int[] STANDBY_RANDOM_WALLPAPER_INTERVALS =
      new int[]{1, 5, 15, 30, 60, 120, 360, 720};
  private static final int[] WEREAD_SYNC_INTERVALS =
      new int[]{30, 60, 120, 360};
  private static final String PERMISSION_STEP_CAMERA = "camera";
  private static final String PERMISSION_STEP_STORAGE = "storage";
  private static final String PERMISSION_STEP_LOCATION = "location";
  private static final String PERMISSION_STEP_INSTALL = "install";
  private static final String PERMISSION_STEP_USAGE = "usage";
  private static final String PERMISSION_STEP_WRITE_SETTINGS = "write_settings";
  private static final String PERMISSION_STEP_OVERLAY = "overlay";
  private static final String PERMISSION_STEP_ACCESSIBILITY = "accessibility";
  private static final String PERMISSION_STEP_DEVICE_ADMIN = "device_admin";
  private static final String PERMISSION_STEP_SCREEN_CAPTURE = "screen_capture";
  private static String desktopWallpaperCachePath;
  private static long desktopWallpaperCacheModified;
  private static long desktopWallpaperCacheLength;
  private static Bitmap desktopWallpaperCachePortrait;
  private static Bitmap desktopWallpaperCacheLandscape;
  private static Bitmap desktopWallpaperCacheReverseLandscape;

  // ---- Views ----
  private EInkLauncherView launcherView;
  private PageIndicatorView pageIndicator;
  private BatteryView batteryProgress;
  private TextView batteryStatus;
  private View primaryRail;
  private View secondaryRail;
  private ImageView desktopWallpaper;
  private ImageView sideNetworkIcon;
  private TextView textClock;
  private TextView dateClock;
  private TextView lunarClock;
  private boolean lunarFetchInFlight;
  private String lunarFetchDate;
  private TextView appsSectionTitle;
  private TextView readingTimeClock;
  private TextView readingDateClock;
  private View appsPage;
  private View readingPage;
  private View settingsPage;
  private View[] settingsRows;
  private View[] settingsHomeRows;
  private View[] settingsDesktopRows;
  private View[] settingsLayoutRows;
  private View[] settingsDisplayRows;
  private View[] settingsIconRows;
  private View[] settingsOrientationRows;
  private View[] settingsStandbyRows;
  private View[] settingsStandbyClockStyleRows;
  private View[] settingsStandbyRandomWallpaperIntervalRows;
  private View[] settingsTransferRows;
  private View[] settingsAutoRefreshRows;
  private View[] settingsUpdateRows;
  private View[] settingsIconThemeRows;
  private View[] settingsPermissionRows;
  private View[] settingsReadingRows;
  private View settingsHomePage;
  private View settingsDesktopPage;
  private View settingsTransferPage;
  private View settingsAutoRefreshPage;
  private View settingsUpdatePage;
  private View settingsIconThemePage;
  private View settingsPermissionPage;
  private View settingsReadingPage;
  private LinearLayout settingIconThemeList;
  private int settingsPageIndex;
  private int settingsPageCount;
  private int settingsSubPage = SETTINGS_SUB_HOME;
  private TextView navAppsText;
  private TextView navDrawerText;
  private TextView navReadingText;
  private TextView navSettingsText;
  private TextView settingsHomeTitle;
  private TextView settingsDesktopTitle;
  private TextView settingsTransferTitle;
  private TextView settingsAutoRefreshTitle;
  private TextView settingsUpdateTitle;
  private TextView settingsIconThemeTitle;
  private TextView settingsPermissionTitle;
  private TextView settingsReadingTitle;
  private TextView settingDesktopBackLabel;
  private TextView settingIconThemeBackLabel;
  private TextView settingLayoutGroupValue;
  private TextView settingFavoriteAppsValue;
  private TextView settingDisplayGroupValue;
  private TextView settingIconGroupValue;
  private TextView settingOrientationGroupValue;
  private TextView settingStandbyGroupValue;
  private TextView settingColNumValue;
  private TextView settingRowNumValue;
  private TextView settingSortModeValue;
  private TextView settingAppNameLinesValue;
  private TextView settingFontSizeValue;
  private TextView settingDesktopWallpaperValue;
  private TextView settingDividerValue;
  private TextView settingStatusBarValue;
  private TextView settingCustomIconValue;
  private TextView settingIconAutoFillValue;
  private TextView[] settingRotationValues;
  private TextView[] settingStandbyClockStyleValues;
  private TextView[] settingStandbyRandomWallpaperIntervalValues;
  private EditText settingStandbyTextInput;
  private TextView settingStandbyWallpaperValue;
  private TextView settingStandbyRandomWallpaperValue;
  private TextView settingStandbyRandomWallpaperIntervalValue;
  private TextView settingStandbyFullRefreshValue;
  private TextView settingStandbyClockStyleValue;
  private TextView settingBookCoverValue;
  private TextView settingBookCoverRandomValue;
  private TextView settingWereadWallpaperValue;
  private TextView settingWereadWallpaperIntervalValue;
  private TextView settingWifiTransferStatus;
  private TextView settingWifiTransferAddress;
  private ImageView settingWifiTransferQr;
  private TextView settingAutoRefreshStatus;
  private TextView settingAutoRefreshIntervalText;
  private TextView settingAutoRefreshTapStatus;
  private TextView settingAutoRefreshTapIntervalText;
  private TextView settingAutoRefreshDelayText;
  private TextView settingAutoRefreshKeycodeText;
  private TextView settingAutoRefreshSosFrameRefreshValue;
  private TextView settingAutoRefreshShakeRefreshValue;
  private TextView settingAutoRefreshShakeCountValue;
  private TextView settingAutoRefreshShakeAmplitudeValue;
  private TextView settingUpdateCurrentVersion;
  private TextView settingUpdateStatus;
  private TextView settingUpdateNotes;
  private TextView settingWereadSyncValue;
  private TextView settingWereadApiKeyValue;
  private TextView settingWereadSyncIntervalValue;
  private TextView settingPermissionCameraValue;
  private TextView settingPermissionStorageValue;
  private TextView settingPermissionLocationValue;
  private TextView settingPermissionInstallValue;
  private TextView settingPermissionUsageValue;
  private TextView settingPermissionWriteSettingsValue;
  private TextView settingPermissionOverlayValue;
  private TextView settingPermissionAccessibilityValue;
  private TextView settingPermissionDeviceAdminValue;
  private TextView settingPermissionScreenCaptureValue;
  private boolean updateBusy;
  private boolean permissionRequestFlowActive;
  private boolean permissionRequestFlowWaitingSettings;
  private boolean permissionRequestFlowWaitingResult;
  private String permissionRequestFlowStep;

  // ---- Data ----
  private AppDataCenter dataCenter;
  private Config config;
  private Calendar calendar;
  private boolean isChina = true;
  private IconCache iconCache;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private LegadoHomeController legadoHomeController;
  private int currentSection = SECTION_APPS;
  private boolean isSystemApp = false;

  // ---- Device Admin ----
  private DevicePolicyManager policyManager;

  // ---- Receivers ----
  private FTPReceiver ftpReceiver = new FTPReceiver();
  private boolean batteryRegistered;
  private boolean timeRegistered;
  private boolean usbRegistered;
  private boolean ftpRegistered;
  private boolean wifiRegistered;
  private boolean wifiTransferRegistered;
  private float pageTouchDownX;
  private float pageTouchDownY;

  private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateTimeShow();
    }
  };

  private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      handleBatteryChanged(intent);
    }
  };

  private final BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      iconCache.clearAppCache();
      dataCenter.refreshAppList(binder.isDelete());
      checkIconThemeUpdates(true);
    }
  };

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
        iconCache.markDirty();
        refreshIcons();
      }
    }
  };

  private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateNetworkIcon();
      updateWifiTransferStatus();
    }
  };

  private final BroadcastReceiver wifiTransferReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (WifiFileTransferService.ACTION_FAILEDTOSTART.equals(intent.getAction())) {
        Toast.makeText(context, R.string.setting_wifi_transfer_failed, Toast.LENGTH_SHORT).show();
      }
      updateWifiTransferStatus();
    }
  };

  // =========================================================================
  // Lifecycle
  // =========================================================================

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    long t0 = System.currentTimeMillis();
    super.onCreate(savedInstanceState);
    config = new Config(this);

    setContentView(R.layout.launcher_activity);
    Log.d("Perf", "setContentView: " + (System.currentTimeMillis() - t0) + "ms");

    WifiControl.init(this);
    ScreenCaptureManager.getInstance().init(this);
    applyStatusBarVisibility();

    isChina = getResources().getConfiguration().locale.getCountry().equals("CN");

    long t1 = System.currentTimeMillis();
    initViews();
    Log.d("Perf", "initViews: " + (System.currentTimeMillis() - t1) + "ms");

    registerStaticReceivers();
    checkLaunchHomeNotification();
    IconThemeUpdateReceiver.schedule(this);
    checkIconThemeUpdates(false);
    if (config.isStandbyRefreshEnabled()) {
      StandbyMinuteRefreshReceiver.scheduleSoon(this);
    }
    Log.d("Perf", "onCreate total: " + (System.currentTimeMillis() - t0) + "ms");
  }

  @Override
  protected void onResume() {
    long t0 = System.currentTimeMillis();
    super.onResume();
    applyLauncherOrientation(false);
    applySystemUiVisibility();
    registerDynamicReceivers();
    applyDesktopWallpaper();

    long t1 = System.currentTimeMillis();
    refreshIcons();
    Log.d("Perf", "refreshIcons: " + (System.currentTimeMillis() - t1) + "ms");

    checkIconThemeUpdates(false);
    if (legadoHomeController != null) {
      legadoHomeController.refresh(this);
    }
    updateNetworkIcon();
    updateStandbyTextValue();
    updateWifiTransferStatus();
    updateAutoRefreshStatus();
    updateReadingSyncValues();
    updatePermissionValues();
    handlePermissionFlowResume();
    Log.d("Perf", "onResume total: " + (System.currentTimeMillis() - t0) + "ms");
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      applySystemUiVisibility();
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        pageTouchDownX = event.getX();
        pageTouchDownY = event.getY();
        break;
      case MotionEvent.ACTION_UP:
        if (handlePageSwipe(event.getX(), event.getY())) {
          return true;
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        pageTouchDownX = 0f;
        pageTouchDownY = 0f;
        break;
      default:
        break;
    }
    return super.dispatchTouchEvent(event);
  }

  private boolean handlePageSwipe(float upX, float upY) {
    float dx = upX - pageTouchDownX;
    float dy = upY - pageTouchDownY;
    float absDx = Math.abs(dx);
    float absDy = Math.abs(dy);
    View decor = getWindow() == null ? null : getWindow().getDecorView();
    int width = decor == null || decor.getWidth() <= 0
        ? getResources().getDisplayMetrics().widthPixels : decor.getWidth();
    int height = decor == null || decor.getHeight() <= 0
        ? getResources().getDisplayMetrics().heightPixels : decor.getHeight();
    float threshold = Math.max(dp(28), Math.min(width, height) / 12f);
    if (Math.max(absDx, absDy) < threshold) return false;
    if (absDx >= absDy) {
      if (dx < 0) showNextSectionOrPage(); else showPreviousSectionOrPage();
    } else {
      if (dy < 0) showNextSectionOrPage(); else showPreviousSectionOrPage();
    }
    return true;
  }

  @Override
  protected void onPause() {
    super.onPause();
    unregisterDynamicReceivers();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterDynamicReceivers();
    unregisterReceiver(appChangeReceiver);
  }

  // =========================================================================
  // View 初始化
  // =========================================================================

  private void initViews() {
    policyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

    launcherView = findViewById(R.id.mList);
    pageIndicator = findViewById(R.id.pageIndicator);
    batteryProgress = findViewById(R.id.batteryProgress);
    batteryStatus = findViewById(R.id.batteryStatus);
    primaryRail = findViewById(R.id.primaryRail);
    secondaryRail = findViewById(R.id.secondaryRail);
    desktopWallpaper = findViewById(R.id.desktopWallpaper);
    sideNetworkIcon = findViewById(R.id.sideNetworkIcon);
    textClock = findViewById(R.id.textClock);
    dateClock = findViewById(R.id.dateClock);
    lunarClock = findViewById(R.id.lunarClock);
    appsSectionTitle = findViewById(R.id.appsSectionTitle);
    readingTimeClock = findViewById(R.id.readingTimeClock);
    readingDateClock = findViewById(R.id.readingDateClock);
    appsPage = findViewById(R.id.appsPage);
    readingPage = findViewById(R.id.readingPage);
    settingsPage = findViewById(R.id.settingsPage);
    settingsHomePage = findViewById(R.id.settingsHomePage);
    settingsDesktopPage = findViewById(R.id.settingsDesktopPage);
    settingsTransferPage = findViewById(R.id.settingsTransferPage);
    settingsAutoRefreshPage = findViewById(R.id.settingsAutoRefreshPage);
    settingsUpdatePage = findViewById(R.id.settingsUpdatePage);
    settingsIconThemePage = findViewById(R.id.settingsIconThemePage);
    settingsPermissionPage = findViewById(R.id.settingsPermissionPage);
    settingsReadingPage = findViewById(R.id.settingsReadingPage);
    settingsHomeTitle = findViewById(R.id.settingsHomeTitle);
    settingsDesktopTitle = findViewById(R.id.settingsDesktopTitle);
    settingsTransferTitle = findViewById(R.id.settingsTransferTitle);
    settingsAutoRefreshTitle = findViewById(R.id.settingsAutoRefreshTitle);
    settingsUpdateTitle = findViewById(R.id.settingsUpdateTitle);
    settingsIconThemeTitle = findViewById(R.id.settingsIconThemeTitle);
    settingsPermissionTitle = findViewById(R.id.settingsPermissionTitle);
    settingsReadingTitle = findViewById(R.id.settingsReadingTitle);
    settingDesktopBackLabel = findViewById(R.id.settingDesktopBackLabel);
    settingIconThemeBackLabel = findViewById(R.id.settingIconThemeBackLabel);
    settingLayoutGroupValue = findViewById(R.id.settingLayoutGroupValue);
    settingFavoriteAppsValue = findViewById(R.id.settingFavoriteAppsValue);
    settingDisplayGroupValue = findViewById(R.id.settingDisplayGroupValue);
    settingIconGroupValue = findViewById(R.id.settingIconGroupValue);
    settingOrientationGroupValue = findViewById(R.id.settingOrientationGroupValue);
    settingStandbyGroupValue = findViewById(R.id.settingStandbyGroupValue);
    settingsHomeRows = new View[]{
        findViewById(R.id.settingOpenLauncher),
        findViewById(R.id.settingManageApps),
        findViewById(R.id.settingTransfer),
        findViewById(R.id.settingAutoRefresh),
        findViewById(R.id.settingReadingSync),
        findViewById(R.id.settingUpdate),
        findViewById(R.id.settingPermissions),
        findViewById(R.id.settingQrScan),
        findViewById(R.id.settingWifi),
        findViewById(R.id.settingDeviceManager),
        findViewById(R.id.settingHelpAbout),
        findViewById(R.id.settingLock),
    };
    settingsDesktopRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingLayoutGroup),
        findViewById(R.id.settingFavoriteApps),
        findViewById(R.id.settingDisplayGroup),
        findViewById(R.id.settingIconGroup),
        findViewById(R.id.settingOrientationGroup),
        findViewById(R.id.settingStandbyGroup),
    };
    settingsLayoutRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingColNum),
        findViewById(R.id.settingRowNum),
        findViewById(R.id.settingSortMode),
        findViewById(R.id.settingAppNameLines),
    };
    settingsDisplayRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingFontSize),
        findViewById(R.id.settingDesktopWallpaper),
        findViewById(R.id.settingDivider),
        findViewById(R.id.settingStatusBar),
    };
    settingsIconRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingCustomIcon),
        findViewById(R.id.settingIconAutoFill),
    };
    settingsOrientationRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingRotationAuto),
        findViewById(R.id.settingRotationPortrait),
        findViewById(R.id.settingRotationLandscape),
        findViewById(R.id.settingRotationReversePortrait),
        findViewById(R.id.settingRotationReverseLandscape),
    };
    settingsStandbyRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingStandbyText),
        findViewById(R.id.settingStandbyFullRefresh),
        findViewById(R.id.settingStandbyClockStyle),
        findViewById(R.id.settingBookCover),
        findViewById(R.id.settingBookCoverRandom),
        findViewById(R.id.settingWereadWallpaper),
        findViewById(R.id.settingWereadWallpaperInterval),
        findViewById(R.id.settingStandbyWallpaper),
        findViewById(R.id.settingStandbyRandomWallpaper),
        findViewById(R.id.settingStandbyRandomWallpaperInterval),
        findViewById(R.id.settingStandbyPreview),
    };
    settingsStandbyClockStyleRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingStandbyClockClassic),
        findViewById(R.id.settingStandbyClockClassicHollow),
        findViewById(R.id.settingStandbyClockFlipLandscape),
        findViewById(R.id.settingStandbyClockFlipLandscapeLight),
        findViewById(R.id.settingStandbyClockMinimalLandscape),
    };
    settingsStandbyRandomWallpaperIntervalRows = new View[]{
        findViewById(R.id.settingDesktopBack),
        findViewById(R.id.settingStandbyRandomWallpaperInterval1Min),
        findViewById(R.id.settingStandbyRandomWallpaperInterval5Min),
        findViewById(R.id.settingStandbyRandomWallpaperInterval15Min),
        findViewById(R.id.settingStandbyRandomWallpaperInterval30Min),
        findViewById(R.id.settingStandbyRandomWallpaperInterval1Hour),
        findViewById(R.id.settingStandbyRandomWallpaperInterval2Hour),
        findViewById(R.id.settingStandbyRandomWallpaperInterval6Hour),
        findViewById(R.id.settingStandbyRandomWallpaperInterval12Hour),
    };
    settingsTransferRows = new View[]{
        findViewById(R.id.settingTransferBack),
        findViewById(R.id.settingWifiTransferToggle),
    };
    settingsAutoRefreshRows = new View[]{
        findViewById(R.id.settingAutoRefreshBack),
        findViewById(R.id.settingAutoRefreshToggle),
        findViewById(R.id.settingAutoRefreshInterval),
        findViewById(R.id.settingAutoRefreshTapToggle),
        findViewById(R.id.settingAutoRefreshTapInterval),
        findViewById(R.id.settingAutoRefreshDelay),
        findViewById(R.id.settingAutoRefreshKeycode),
        findViewById(R.id.settingAutoRefreshSosFrameRefresh),
        findViewById(R.id.settingAutoRefreshShakeRefresh),
        findViewById(R.id.settingAutoRefreshShakeCount),
        findViewById(R.id.settingAutoRefreshShakeAmplitude),
    };
    settingsUpdateRows = new View[]{
        findViewById(R.id.settingUpdateBack),
        findViewById(R.id.settingUpdateCheck),
        findViewById(R.id.settingUpdateCurrentRow),
    };
    settingsPermissionRows = new View[]{
        findViewById(R.id.settingPermissionsBack),
        findViewById(R.id.settingPermissionRequestAll),
        findViewById(R.id.settingPermissionCamera),
        findViewById(R.id.settingPermissionStorage),
        findViewById(R.id.settingPermissionLocation),
        findViewById(R.id.settingPermissionInstall),
        findViewById(R.id.settingPermissionUsage),
        findViewById(R.id.settingPermissionWriteSettings),
        findViewById(R.id.settingPermissionOverlay),
        findViewById(R.id.settingPermissionAccessibility),
        findViewById(R.id.settingPermissionDeviceAdmin),
        findViewById(R.id.settingPermissionScreenCapture),
    };
    settingsReadingRows = new View[]{
        findViewById(R.id.settingReadingBack),
        findViewById(R.id.settingWereadSyncToggle),
        findViewById(R.id.settingWereadApiKey),
        findViewById(R.id.settingWereadSyncInterval),
        findViewById(R.id.settingWereadSyncNow),
    };
    settingsIconThemeRows = new View[]{
        findViewById(R.id.settingIconThemeBack),
    };
    settingsRows = settingsHomeRows;
    navAppsText = findViewById(R.id.navAppsText);
    navDrawerText = findViewById(R.id.navDrawerText);
    navReadingText = findViewById(R.id.navReadingText);
    navSettingsText = findViewById(R.id.navSettingsText);
    settingColNumValue = findViewById(R.id.settingColNumValue);
    settingRowNumValue = findViewById(R.id.settingRowNumValue);
    settingSortModeValue = findViewById(R.id.settingSortModeValue);
    settingAppNameLinesValue = findViewById(R.id.settingAppNameLinesValue);
    settingFontSizeValue = findViewById(R.id.settingFontSizeValue);
    settingDesktopWallpaperValue = findViewById(R.id.settingDesktopWallpaperValue);
    settingDividerValue = findViewById(R.id.settingDividerValue);
    settingStatusBarValue = findViewById(R.id.settingStatusBarValue);
    settingCustomIconValue = findViewById(R.id.settingCustomIconValue);
    settingIconAutoFillValue = findViewById(R.id.settingIconAutoFillValue);
    settingIconThemeList = findViewById(R.id.settingIconThemeList);
    settingRotationValues = new TextView[]{
        findViewById(R.id.settingRotationAutoValue),
        findViewById(R.id.settingRotationPortraitValue),
        findViewById(R.id.settingRotationLandscapeValue),
        findViewById(R.id.settingRotationReversePortraitValue),
        findViewById(R.id.settingRotationReverseLandscapeValue),
    };
    settingStandbyClockStyleValues = new TextView[]{
        findViewById(R.id.settingStandbyClockClassicValue),
        findViewById(R.id.settingStandbyClockFlipLandscapeValue),
        findViewById(R.id.settingStandbyClockFlipLandscapeLightValue),
        findViewById(R.id.settingStandbyClockClassicHollowValue),
        findViewById(R.id.settingStandbyClockMinimalLandscapeValue),
    };
    settingStandbyRandomWallpaperIntervalValues = new TextView[]{
        findViewById(R.id.settingStandbyRandomWallpaperInterval1MinValue),
        findViewById(R.id.settingStandbyRandomWallpaperInterval5MinValue),
        findViewById(R.id.settingStandbyRandomWallpaperInterval15MinValue),
        findViewById(R.id.settingStandbyRandomWallpaperInterval30MinValue),
        findViewById(R.id.settingStandbyRandomWallpaperInterval1HourValue),
        findViewById(R.id.settingStandbyRandomWallpaperInterval2HourValue),
        findViewById(R.id.settingStandbyRandomWallpaperInterval6HourValue),
        findViewById(R.id.settingStandbyRandomWallpaperInterval12HourValue),
    };
    settingStandbyTextInput = findViewById(R.id.settingStandbyTextInput);
    settingStandbyWallpaperValue = findViewById(R.id.settingStandbyWallpaperValue);
    settingStandbyRandomWallpaperValue = findViewById(R.id.settingStandbyRandomWallpaperValue);
    settingStandbyRandomWallpaperIntervalValue =
        findViewById(R.id.settingStandbyRandomWallpaperIntervalValue);
    settingStandbyFullRefreshValue = findViewById(R.id.settingStandbyFullRefreshValue);
    settingStandbyClockStyleValue = findViewById(R.id.settingStandbyClockStyleValue);
    settingBookCoverValue = findViewById(R.id.settingBookCoverValue);
    settingBookCoverRandomValue = findViewById(R.id.settingBookCoverRandomValue);
    settingWereadWallpaperValue = findViewById(R.id.settingWereadWallpaperValue);
    settingWereadWallpaperIntervalValue = findViewById(R.id.settingWereadWallpaperIntervalValue);
    settingWifiTransferStatus = findViewById(R.id.settingWifiTransferStatus);
    settingWifiTransferAddress = findViewById(R.id.settingWifiTransferAddress);
    settingWifiTransferQr = findViewById(R.id.settingWifiTransferQr);
    settingAutoRefreshStatus = findViewById(R.id.settingAutoRefreshStatus);
    settingAutoRefreshIntervalText = findViewById(R.id.settingAutoRefreshIntervalText);
    settingAutoRefreshTapStatus = findViewById(R.id.settingAutoRefreshTapStatus);
    settingAutoRefreshTapIntervalText = findViewById(R.id.settingAutoRefreshTapIntervalText);
    settingAutoRefreshDelayText = findViewById(R.id.settingAutoRefreshDelayText);
    settingAutoRefreshKeycodeText = findViewById(R.id.settingAutoRefreshKeycodeText);
    settingAutoRefreshSosFrameRefreshValue = findViewById(R.id.settingAutoRefreshSosFrameRefreshValue);
    settingAutoRefreshShakeRefreshValue =
        findViewById(R.id.settingAutoRefreshShakeRefreshValue);
    settingAutoRefreshShakeCountValue =
        findViewById(R.id.settingAutoRefreshShakeCountValue);
    settingAutoRefreshShakeAmplitudeValue =
        findViewById(R.id.settingAutoRefreshShakeAmplitudeValue);
    settingUpdateCurrentVersion = findViewById(R.id.settingUpdateCurrentVersion);
    settingUpdateStatus = findViewById(R.id.settingUpdateStatus);
    settingUpdateNotes = findViewById(R.id.settingUpdateNotes);
    settingWereadSyncValue = findViewById(R.id.settingWereadSyncValue);
    settingWereadApiKeyValue = findViewById(R.id.settingWereadApiKeyValue);
    settingWereadSyncIntervalValue = findViewById(R.id.settingWereadSyncIntervalValue);
    settingPermissionCameraValue = findViewById(R.id.settingPermissionCameraValue);
    settingPermissionStorageValue = findViewById(R.id.settingPermissionStorageValue);
    settingPermissionLocationValue = findViewById(R.id.settingPermissionLocationValue);
    settingPermissionInstallValue = findViewById(R.id.settingPermissionInstallValue);
    settingPermissionUsageValue = findViewById(R.id.settingPermissionUsageValue);
    settingPermissionWriteSettingsValue = findViewById(R.id.settingPermissionWriteSettingsValue);
    settingPermissionOverlayValue = findViewById(R.id.settingPermissionOverlayValue);
    settingPermissionAccessibilityValue = findViewById(R.id.settingPermissionAccessibilityValue);
    settingPermissionDeviceAdminValue = findViewById(R.id.settingPermissionDeviceAdminValue);
    settingPermissionScreenCaptureValue = findViewById(R.id.settingPermissionScreenCaptureValue);
    legadoHomeController = new LegadoHomeController();

    // 配置 Binder、Adapter、View
    iconCache = new IconCache(this);
    iconCache.setOnScanCompleteListener(() -> adapter.refreshDisplay());
    binder = new AppItemBinder(getPackageManager());
    binder.setCallback(this);
    binder.setIconCache(iconCache);
    binder.setHideAppPkg(config.getHideApps());
    adapter = new LauncherAdapter();
    adapter.setBinder(binder);
    adapter.setFontSize(config.getFontSize());
    adapter.setAppNameLines(config.getAppNameLines());
    launcherView.setAdapter(adapter);
    launcherView.setOnPageChangeListener(this);

    // 初始化数据中心
    dataCenter = new AppDataCenter(this);
    dataCenter.setSortMode(config.getSortMode());
    dataCenter.setFavoriteApps(config.getFavoriteApps());
    dataCenter.setHideApps(config.getHideApps());
    ensureFavoriteAppsSeeded();
    dataCenter.setAdapter(adapter);

    applyCurrentGridSize();

    View lastPage = findViewById(R.id.lastPage);
    if (lastPage != null) lastPage.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showPreviousSectionOrPage();
      }
    });
    View nextPage = findViewById(R.id.nextPage);
    if (nextPage != null) nextPage.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showNextSectionOrPage();
      }
    });

    View navApps = findViewById(R.id.navApps);
    if (navApps == null) navApps = findViewById(R.id.navAppsText);
    if (navApps != null) navApps.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSection(SECTION_APPS);
      }
    });
    View navDrawer = findViewById(R.id.navDrawer);
    if (navDrawer == null) navDrawer = findViewById(R.id.navDrawerText);
    if (navDrawer != null) navDrawer.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSection(SECTION_DRAWER);
      }
    });
    View navReading = findViewById(R.id.navReading);
    if (navReading == null) navReading = findViewById(R.id.navReadingText);
    if (navReading != null) navReading.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSection(SECTION_READING);
      }
    });
    View navSettings = findViewById(R.id.navSettings);
    if (navSettings == null) navSettings = findViewById(R.id.navSettingsText);
    if (navSettings != null) navSettings.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSection(SECTION_SETTINGS);
      }
    });

    View sideWifi = findViewById(R.id.sideWifi);
    if (sideWifi != null) sideWifi.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        WifiControl.onClickWifiItem();
        updateNetworkIcon();
      }
    });
    if (sideWifi != null) sideWifi.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        WifiControl.onLongClickWifiItem();
        return true;
      }
    });
    View sideLock = findViewById(R.id.sideLock);
    if (sideLock != null) sideLock.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openStandbyAndLock();
      }
    });
    View settingOpenLauncher = findViewById(R.id.settingOpenLauncher);
    if (settingOpenLauncher != null) settingOpenLauncher.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showDesktopSettings();
      }
    });
    bindClick(R.id.settingTransfer, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showTransferSettings();
      }
    });
    bindClick(R.id.settingAutoRefresh, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showAutoRefreshSettings();
      }
    });
    bindClick(R.id.settingReadingSync, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showReadingSettings();
      }
    });
    bindClick(R.id.settingUpdate, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showUpdateSettings();
      }
    });
    bindClick(R.id.settingPermissions, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showPermissionSettings();
      }
    });
    bindClick(R.id.settingQrScan, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        startQrScan();
      }
    });
    bindClick(R.id.settingDeviceManager, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showDeviceAdminDialog();
      }
    });
    bindClick(R.id.settingHelpAbout, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AboutDialog.getInstance(Launcher.this).show();
      }
    });
    View settingManageApps = findViewById(R.id.settingManageApps);
    if (settingManageApps != null) settingManageApps.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSection(SECTION_DRAWER);
        onEnterManageMode();
      }
    });
    View settingWifi = findViewById(R.id.settingWifi);
    if (settingWifi != null) settingWifi.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        WifiControl.onClickWifiItem();
        updateNetworkIcon();
      }
    });
    bindDesktopSettingsRows();
    bindTransferSettingsRows();
    bindAutoRefreshSettingsRows();
    bindUpdateSettingsRows();
    bindPermissionSettingsRows();
    bindReadingSettingsRows();
    View settingStandbyFullRefresh = findViewById(R.id.settingStandbyFullRefresh);
    if (settingStandbyFullRefresh != null) {
      settingStandbyFullRefresh.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          config.setStandbyFullRefresh(!config.isStandbyFullRefresh());
          updateStandbyFullRefreshValue();
        }
      });
    }
    View settingStandbyClockStyle = findViewById(R.id.settingStandbyClockStyle);
    if (settingStandbyClockStyle != null) {
      settingStandbyClockStyle.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          showStandbyClockStyleSettings();
        }
      });
    }
    bindStandbyClockStyleRow(R.id.settingStandbyClockClassic,
        Config.STANDBY_CLOCK_STYLE_CLASSIC);
    bindStandbyClockStyleRow(R.id.settingStandbyClockClassicHollow,
        Config.STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW);
    bindStandbyClockStyleRow(R.id.settingStandbyClockFlipLandscape,
        Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE);
    bindStandbyClockStyleRow(R.id.settingStandbyClockFlipLandscapeLight,
        Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT);
    bindStandbyClockStyleRow(R.id.settingStandbyClockMinimalLandscape,
        Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE);
    View settingBookCover = findViewById(R.id.settingBookCover);
    if (settingBookCover != null) {
      settingBookCover.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          boolean enabled = !config.isBookCoverEnabled();
          config.setBookCoverEnabled(enabled);
          if (enabled) config.setWereadWallpaperEnabled(false);
          updateBookCoverValue();
          updateWereadWallpaperValue();
        }
      });
    }
    View settingBookCoverRandom = findViewById(R.id.settingBookCoverRandom);
    if (settingBookCoverRandom != null) {
      settingBookCoverRandom.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          config.setBookCoverRandom(!config.isBookCoverRandom());
          updateBookCoverRandomValue();
        }
      });
    }
    View settingWereadWallpaper = findViewById(R.id.settingWereadWallpaper);
    if (settingWereadWallpaper != null) {
      settingWereadWallpaper.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          applyWereadWallpaperMode(!config.isWereadWallpaperEnabled());
        }
      });
    }
    View settingWereadWallpaperInterval = findViewById(R.id.settingWereadWallpaperInterval);
    if (settingWereadWallpaperInterval != null) {
      settingWereadWallpaperInterval.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          applyWereadWallpaperInterval(nextStandbyInterval(
              config.getWereadWallpaperIntervalMinutes()));
        }
      });
    }
    View settingStandbyWallpaper = findViewById(R.id.settingStandbyWallpaper);
    if (settingStandbyWallpaper != null) {
      settingStandbyWallpaper.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          boolean enabled = !config.isStandbyWallpaperEnabled();
          config.setStandbyWallpaperEnabled(enabled);
          if (enabled) config.setWereadWallpaperEnabled(false);
          updateStandbyWallpaperValue();
          updateWereadWallpaperValue();
          StandbyWallpaperUpdater.applyLockText(Launcher.this);
          if (enabled) {
            config.setLastWallpaperUpdateMinute(0);
          }
          StandbyMinuteRefreshReceiver.scheduleRetry(Launcher.this);
        }
      });
    }
    View settingStandbyRandomWallpaper = findViewById(R.id.settingStandbyRandomWallpaper);
    if (settingStandbyRandomWallpaper != null) {
      settingStandbyRandomWallpaper.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          boolean enabled = !config.isStandbyRandomWallpaperEnabled();
          config.setStandbyRandomWallpaperEnabled(enabled);
          if (enabled) {
            config.setWereadWallpaperEnabled(false);
            config.setLastWallpaperUpdateMinute(0);
          }
          updateStandbyWallpaperValue();
          updateStandbyRandomWallpaperValue();
          updateWereadWallpaperValue();
          StandbyMinuteRefreshReceiver.scheduleRetry(Launcher.this);
        }
      });
    }
    View settingStandbyRandomWallpaperInterval =
        findViewById(R.id.settingStandbyRandomWallpaperInterval);
    if (settingStandbyRandomWallpaperInterval != null) {
      settingStandbyRandomWallpaperInterval.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          showStandbyRandomWallpaperIntervalSettings();
        }
      });
    }
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval1Min, 1);
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval5Min, 5);
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval15Min, 15);
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval30Min, 30);
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval1Hour, 60);
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval2Hour, 120);
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval6Hour, 360);
    bindStandbyRandomWallpaperIntervalRow(R.id.settingStandbyRandomWallpaperInterval12Hour, 720);
    View settingStandbyPreview = findViewById(R.id.settingStandbyPreview);
    if (settingStandbyPreview != null) {
      settingStandbyPreview.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          openStandbyPreview();
        }
      });
    }
    View settingLock = findViewById(R.id.settingLock);
    if (settingLock != null) settingLock.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openStandbyAndLock();
      }
    });

    // 管理完成按钮
    findViewById(R.id.deleteFinish).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        exitManageMode(true);
        config.setHideApps(dataCenter.getHideApps());
      }
    });

    // 时间显示
    calendar = Calendar.getInstance();
    applyDesktopWallpaper();
    updateTimeShow();
    updateNetworkIcon();
    updateDesktopSettingValues();
    showSection(SECTION_APPS);

    // 检测系统应用
    try {
      isSystemApp = !isUserApp(getPackageManager().getPackageInfo(getPackageName(), 0));
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
  }

  private void bindDesktopSettingsRows() {
    bindClick(R.id.settingDesktopBack, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showPreviousSettingsLevel();
      }
    });
    bindClick(R.id.settingLayoutGroup, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showLayoutSettings();
      }
    });
    bindClick(R.id.settingFavoriteApps, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        exitManageMode(false);
        showSection(SECTION_DRAWER);
        Toast.makeText(Launcher.this, "长按应用可加入或移出主页", Toast.LENGTH_LONG).show();
      }
    });
    bindClick(R.id.settingDisplayGroup, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showDisplaySettings();
      }
    });
    bindClick(R.id.settingIconGroup, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showIconSettings();
      }
    });
    bindClick(R.id.settingOrientationGroup, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showOrientationSettings();
      }
    });
    bindClick(R.id.settingStandbyGroup, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showStandbySettings();
      }
    });
    bindClick(R.id.settingColNum, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean landscape = isLandscapeOrientation();
        int value = nextBoundedValue(config.getColNum(landscape), 2, 10);
        config.setColNum(landscape, value);
        onColNumChanged(value);
        updateDesktopSettingValues();
      }
    });
    bindClick(R.id.settingRowNum, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean landscape = isLandscapeOrientation();
        int value = nextBoundedValue(config.getRowNum(landscape), 2, 10);
        config.setRowNum(landscape, value);
        onRowNumChanged(value);
        updateDesktopSettingValues();
      }
    });
    bindClick(R.id.settingSortMode, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cycleSortMode();
      }
    });
    bindClick(R.id.settingAppNameLines, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        int lines = config.getAppNameLines();
        int next = lines == 0 ? 1 : lines == 1 ? 2 : lines == 2 ? Integer.MAX_VALUE : 0;
        config.setAppNameLines(next);
        onAppNameLinesChanged(next);
        updateDesktopSettingValues();
      }
    });
    bindClick(R.id.settingFontSize, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        float size = config.getFontSize();
        float next = size >= 30f ? 10f : ((int) size) + 1f;
        config.setFontSize(next);
        onFontSizeChanged(next);
        updateDesktopSettingValues();
      }
    });
    bindClick(R.id.settingDesktopWallpaper, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showDesktopWallpaperDialog();
        updateDesktopSettingValues();
      }
    });
    bindClick(R.id.settingDivider, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean hide = !config.isHideDivider();
        config.setHideDivider(hide);
        onHideDividerChanged(hide);
        updateDesktopSettingValues();
      }
    });
    bindClick(R.id.settingStatusBar, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean show = !config.isShowStatusBar();
        config.setShowStatusBar(show);
        onShowStatusBarChanged(show);
        updateDesktopSettingValues();
      }
    });
    bindClick(R.id.settingCustomIcon, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showIconThemeSettings();
        checkIconThemeUpdates(true);
      }
    });
    bindClick(R.id.settingIconThemeBack, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showIconSettings();
      }
    });
    bindClick(R.id.settingIconAutoFill, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        config.setIconAutoReportMissing(!config.isIconAutoReportMissing());
        updateDesktopSettingValues();
        checkIconThemeUpdates(true);
      }
    });
    bindOrientationRow(R.id.settingRotationAuto, Config.ORIENTATION_AUTO);
    bindOrientationRow(R.id.settingRotationPortrait, Config.ORIENTATION_PORTRAIT);
    bindOrientationRow(R.id.settingRotationLandscape, Config.ORIENTATION_LANDSCAPE);
    bindOrientationRow(R.id.settingRotationReversePortrait, Config.ORIENTATION_REVERSE_PORTRAIT);
    bindOrientationRow(R.id.settingRotationReverseLandscape, Config.ORIENTATION_REVERSE_LANDSCAPE);

    if (settingStandbyTextInput != null) {
      settingStandbyTextInput.setSingleLine(true);
      settingStandbyTextInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
      settingStandbyTextInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
          if (actionId == EditorInfo.IME_ACTION_DONE
              || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            saveStandbyLockText();
            return true;
          }
          return false;
        }
      });
      settingStandbyTextInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          if (!hasFocus) {
            saveStandbyLockText();
          }
        }
      });
    }
    bindClick(R.id.settingStandbyTextSave, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        saveStandbyLockText();
      }
    });
  }

  private void applyIconTheme(String themeId) {
    config.setIconThemeId(themeId);
    iconCache.markDirty();
    refreshIcons();
    updateDesktopSettingValues();
    if (settingsSubPage == SETTINGS_SUB_ICON_THEME) {
      buildIconThemeRows();
      showSettingsPage();
    }
    checkIconThemeUpdates(true);
  }

  private void buildIconThemeRows() {
    if (settingIconThemeList == null) return;
    settingIconThemeList.removeAllViews();

    List<View> rows = new ArrayList<>();
    View back = findViewById(R.id.settingIconThemeBack);
    if (back != null) rows.add(back);

    List<IconThemeClient.ThemeInfo> themes = IconThemeClient.getKnownThemes(this);
    for (IconThemeClient.ThemeInfo theme : themes) {
      View row = createIconThemeRow(theme);
      settingIconThemeList.addView(row);
      rows.add(row);
    }
    settingsIconThemeRows = rows.toArray(new View[rows.size()]);
    if (settingsSubPage == SETTINGS_SUB_ICON_THEME) {
      settingsRows = settingsIconThemeRows;
    }
  }

  private View createIconThemeRow(final IconThemeClient.ThemeInfo theme) {
    LinearLayout row = new LinearLayout(this);
    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
    rowParams.topMargin = dp(6);
    row.setLayoutParams(rowParams);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(10), 0, dp(8), 0);
    row.setBackgroundResource(R.drawable.bg_launcher_row);
    row.setClickable(true);
    row.setFocusable(true);

    TextView label = new TextView(this);
    LinearLayout.LayoutParams labelParams =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
    labelParams.leftMargin = dp(2);
    label.setLayoutParams(labelParams);
    label.setGravity(Gravity.CENTER_VERTICAL);
    label.setIncludeFontPadding(false);
    label.setSingleLine(true);
    label.setEllipsize(TextUtils.TruncateAt.END);
    label.setTextColor(Color.BLACK);
    label.setTextSize(18);
    label.setText(theme.name);
    row.addView(label);

    LinearLayout preview = new LinearLayout(this);
    preview.setLayoutParams(new LinearLayout.LayoutParams(dp(94), dp(34)));
    preview.setGravity(Gravity.CENTER);
    preview.setOrientation(LinearLayout.HORIZONTAL);
    addThemePreviewIcon(preview, theme.id, IconCategoryResolver.KEY_CATEGORY_READER);
    addThemePreviewIcon(preview, theme.id, IconCategoryResolver.KEY_CATEGORY_BROWSER);
    addThemePreviewIcon(preview, theme.id, IconCategoryResolver.KEY_CATEGORY_DEFAULT);
    row.addView(preview);

    TextView selected = new TextView(this);
    selected.setLayoutParams(new LinearLayout.LayoutParams(dp(42),
        LinearLayout.LayoutParams.MATCH_PARENT));
    selected.setGravity(Gravity.CENTER);
    selected.setIncludeFontPadding(false);
    selected.setSingleLine(true);
    selected.setTextColor(Color.BLACK);
    selected.setTextSize(12);
    selected.setText(theme.id.equals(config.getIconThemeId())
        ? getString(R.string.icon_theme_selected) : "");
    row.addView(selected);

    row.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (Config.ICON_THEME_LOCAL.equals(theme.id)) {
          Utils.checkStoragePermission(Launcher.this, new Runnable() {
            @Override
            public void run() {
              applyIconTheme(theme.id);
            }
          });
        } else {
          applyIconTheme(theme.id);
        }
      }
    });
    return row;
  }

  private void addThemePreviewIcon(LinearLayout parent, String themeId, String key) {
    ImageView icon = new ImageView(this);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(26), dp(26));
    params.leftMargin = dp(2);
    params.rightMargin = dp(2);
    icon.setLayoutParams(params);
    icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

    File file = getPreviewIconFile(themeId, key);
    if (file != null && file.exists()) {
      icon.setImageURI(Uri.fromFile(file));
    } else {
      icon.setImageResource(IconCategoryResolver.getBuiltinIconRes(key));
    }
    parent.addView(icon);
  }

  private File getPreviewIconFile(String themeId, String key) {
    if (Config.ICON_THEME_BUILTIN.equals(themeId)) return null;
    File root = Config.ICON_THEME_LOCAL.equals(themeId)
        ? IconCache.getLocalIconDirectory()
        : IconCache.getThemeDirectory(this, themeId);
    File file = new File(root, IconCategoryResolver.safeFileNameForKey(key));
    if (file.exists()) return file;
    File legacyLock = null;
    if (IconCategoryResolver.KEY_SYSTEM_LOCK.equals(key)) {
      legacyLock = new File(root, "E-ink_Launcher.Lock.png");
    }
    return legacyLock != null && legacyLock.exists() ? legacyLock : null;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void checkIconThemeUpdates(boolean force) {
    if (dataCenter == null || config == null) return;
    IconThemeClient.checkForUpdatesAsync(this, dataCenter.getAppsSnapshot(),
        config.getIconThemeId(), force, new IconThemeClient.SyncCallback() {
          @Override
          public void onComplete(final boolean changed, String message) {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                updateDesktopSettingValues();
                if (changed) {
                  iconCache.markDirty();
                  refreshIcons();
                }
                if (settingsSubPage == SETTINGS_SUB_ICON_THEME) {
                  buildIconThemeRows();
                  showSettingsPage();
                }
              }
            });
          }
        });
  }

  private void bindTransferSettingsRows() {
    bindClick(R.id.settingTransferBack, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSettingsHome();
      }
    });
    bindClick(R.id.settingWifiTransferToggle, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleWifiTransferToggle();
      }
    });
  }

  private void bindAutoRefreshSettingsRows() {
    bindClick(R.id.settingAutoRefreshBack, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSettingsHome();
      }
    });
    bindClick(R.id.settingAutoRefreshToggle, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean enabled = !AutoRefreshSettings.isEnabled(Launcher.this);
        AutoRefreshSettings.setEnabled(Launcher.this, enabled);
        updateAutoRefreshStatus();
        if (enabled && !AutoRefreshSettings.isAccessibilityServiceEnabled(Launcher.this)) {
          Toast.makeText(Launcher.this, R.string.setting_auto_refresh_accessibility_off,
              Toast.LENGTH_LONG).show();
          startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
      }
    });
    bindClick(R.id.settingAutoRefreshInterval, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AutoRefreshSettings.setInterval(Launcher.this,
            AutoRefreshSettings.nextInterval(Launcher.this));
        updateAutoRefreshStatus();
      }
    });
    bindClick(R.id.settingAutoRefreshTapToggle, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean enabled = !AutoRefreshSettings.isTapEnabled(Launcher.this);
        AutoRefreshSettings.setTapEnabled(Launcher.this, enabled);
        updateAutoRefreshStatus();
        if (enabled && !AutoRefreshSettings.isAccessibilityServiceEnabled(Launcher.this)) {
          Toast.makeText(Launcher.this, R.string.setting_auto_refresh_accessibility_off,
              Toast.LENGTH_LONG).show();
          startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
      }
    });
    bindClick(R.id.settingAutoRefreshTapInterval, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AutoRefreshSettings.setTapInterval(Launcher.this,
            AutoRefreshSettings.nextTapInterval(Launcher.this));
        updateAutoRefreshStatus();
      }
    });
    bindClick(R.id.settingAutoRefreshDelay, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AutoRefreshSettings.setDelay(Launcher.this,
            AutoRefreshSettings.nextDelay(Launcher.this));
        updateAutoRefreshStatus();
      }
    });
    bindClick(R.id.settingAutoRefreshKeycode, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!AutoRefreshSettings.isAccessibilityServiceEnabled(Launcher.this)) {
          Toast.makeText(Launcher.this, R.string.setting_auto_refresh_accessibility_off,
              Toast.LENGTH_LONG).show();
          startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
          return;
        }
        AutoRefreshSettings.setCaptureNextKey(Launcher.this, true);
        Toast.makeText(Launcher.this, R.string.setting_auto_refresh_capture_key,
            Toast.LENGTH_LONG).show();
      }
    });
    bindClick(R.id.settingAutoRefreshSosFrameRefresh, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean enabled = !AutoRefreshSettings.isSosFrameRefreshEnabled(Launcher.this);
        AutoRefreshSettings.setSosFrameRefreshEnabled(Launcher.this, enabled);
        updateAutoRefreshStatus();
      }
    });
    bindClick(R.id.settingAutoRefreshShakeRefresh, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!AutoRefreshSettings.hasMotionSensor(Launcher.this)) {
          Toast.makeText(Launcher.this, R.string.setting_shake_refresh_sensor_missing,
              Toast.LENGTH_LONG).show();
          return;
        }
        int mode = AutoRefreshSettings.nextShakeActionMode(Launcher.this);
        AutoRefreshSettings.setShakeActionMode(Launcher.this, mode);
        updateAutoRefreshStatus();
        if (mode != AutoRefreshSettings.SHAKE_ACTION_OFF
            && !AutoRefreshSettings.isAccessibilityServiceEnabled(Launcher.this)) {
          Toast.makeText(Launcher.this, R.string.setting_auto_refresh_accessibility_off,
              Toast.LENGTH_LONG).show();
          startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
      }
    });
    bindClick(R.id.settingAutoRefreshShakeCount, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AutoRefreshSettings.setShakeRefreshCount(Launcher.this,
            AutoRefreshSettings.nextShakeRefreshCount(Launcher.this));
        updateAutoRefreshStatus();
      }
    });
    bindClick(R.id.settingAutoRefreshShakeAmplitude, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AutoRefreshSettings.setShakeRefreshAmplitude(Launcher.this,
            AutoRefreshSettings.nextShakeRefreshAmplitude(Launcher.this));
        updateAutoRefreshStatus();
      }
    });
  }

  private void bindUpdateSettingsRows() {
    bindClick(R.id.settingUpdateBack, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSettingsHome();
      }
    });
    bindClick(R.id.settingUpdateCheck, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        checkAndInstallUpdate();
      }
    });
  }

  private void bindPermissionSettingsRows() {
    bindClick(R.id.settingPermissionsBack, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSettingsHome();
      }
    });
    bindClick(R.id.settingPermissionRequestAll, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestFirstMissingPermission();
      }
    });
    bindClick(R.id.settingPermissionCamera, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestCameraPermission();
      }
    });
    bindClick(R.id.settingPermissionStorage, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestStoragePermission();
      }
    });
    bindClick(R.id.settingPermissionLocation, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestLocationPermission();
      }
    });
    bindClick(R.id.settingPermissionInstall, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openUnknownSourcesSettings();
      }
    });
    bindClick(R.id.settingPermissionUsage, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openUsageAccessSettings();
      }
    });
    bindClick(R.id.settingPermissionWriteSettings, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openWriteSettingsPermission();
      }
    });
    bindClick(R.id.settingPermissionOverlay, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openOverlayPermission();
      }
    });
    bindClick(R.id.settingPermissionAccessibility, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openAccessibilitySettings();
      }
    });
    bindClick(R.id.settingPermissionDeviceAdmin, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestDeviceAdminPermission();
      }
    });
    bindClick(R.id.settingPermissionScreenCapture, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestScreenCapturePermission();
      }
    });
  }

  private void bindReadingSettingsRows() {
    bindClick(R.id.settingReadingBack, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSettingsHome();
      }
    });
    bindClick(R.id.settingWereadSyncToggle, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean enabled = !config.isWereadSyncEnabled();
        config.setWereadSyncEnabled(enabled);
        if (enabled && TextUtils.isEmpty(config.getWereadApiKey())) {
          showWereadApiKeyDialog();
        }
        cn.modificator.launcher.reading.ReadingProgressRepository.clearRemoteCache(Launcher.this);
        updateReadingSyncValues();
        refreshReadingProgress();
      }
    });
    bindClick(R.id.settingWereadApiKey, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showWereadApiKeyDialog();
      }
    });
    bindClick(R.id.settingWereadSyncInterval, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        int current = config.getWereadSyncIntervalMinutes();
        int next = WEREAD_SYNC_INTERVALS[0];
        for (int i = 0; i < WEREAD_SYNC_INTERVALS.length; i++) {
          if (WEREAD_SYNC_INTERVALS[i] == current) {
            next = WEREAD_SYNC_INTERVALS[(i + 1) % WEREAD_SYNC_INTERVALS.length];
            break;
          }
        }
        config.setWereadSyncIntervalMinutes(next);
        cn.modificator.launcher.reading.ReadingProgressRepository.clearRemoteCache(Launcher.this);
        updateReadingSyncValues();
      }
    });
    bindClick(R.id.settingWereadSyncNow, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cn.modificator.launcher.reading.ReadingProgressRepository.clearRemoteCache(Launcher.this);
        Toast.makeText(Launcher.this, "开始同步阅读进度", Toast.LENGTH_SHORT).show();
        refreshReadingProgress();
      }
    });
  }

  private void bindClick(int id, View.OnClickListener listener) {
    View view = findViewById(id);
    if (view != null) {
      view.setOnClickListener(listener);
    }
  }

  private void bindStandbyClockStyleRow(int id, final int style) {
    bindClick(id, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        applyStandbyClockStyle(style);
      }
    });
  }

  private void bindStandbyRandomWallpaperIntervalRow(int id, final int minutes) {
    bindClick(id, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        applyStandbyRandomWallpaperInterval(minutes);
      }
    });
  }

  private void applyStandbyClockStyle(int style) {
    config.setStandbyClockStyle(style);
    config.setLastWallpaperUpdateMinute(0);
    updateStandbyClockStyleValue();
    StandbyWallpaperUpdater.applyLockText(this);
    StandbyMinuteRefreshReceiver.scheduleRetry(this);
    showStandbySettings();
  }

  private void applyStandbyRandomWallpaperInterval(int minutes) {
    config.setStandbyRandomWallpaperIntervalMinutes(minutes);
    config.setLastWallpaperUpdateMinute(0);
    updateStandbyRandomWallpaperIntervalValue();
    StandbyMinuteRefreshReceiver.scheduleRetry(this);
    showStandbySettings();
  }

  private void applyWereadWallpaperInterval(int minutes) {
    config.setWereadWallpaperIntervalMinutes(minutes);
    config.setLastWallpaperUpdateMinute(0);
    updateWereadWallpaperIntervalValue();
    StandbyMinuteRefreshReceiver.scheduleRetry(this);
  }

  private int nextStandbyInterval(int current) {
    for (int i = 0; i < STANDBY_RANDOM_WALLPAPER_INTERVALS.length; i++) {
      if (STANDBY_RANDOM_WALLPAPER_INTERVALS[i] == current) {
        return STANDBY_RANDOM_WALLPAPER_INTERVALS[
            (i + 1) % STANDBY_RANDOM_WALLPAPER_INTERVALS.length];
      }
    }
    return STANDBY_RANDOM_WALLPAPER_INTERVALS[0];
  }

  private void applyWereadWallpaperMode(boolean enabled) {
    config.setWereadWallpaperEnabled(enabled);
    if (enabled) {
      config.setStandbyWallpaperEnabled(false);
      config.setStandbyRandomWallpaperEnabled(false);
      config.setBookCoverEnabled(false);
      StandbyWallpaperUpdater.bookCoverActive = false;
      if (!TextUtils.isEmpty(config.getWereadApiKey())) {
        config.setWereadSyncEnabled(true);
      }
      if (!config.isWereadSyncEnabled() || TextUtils.isEmpty(config.getWereadApiKey())) {
        Toast.makeText(this, "请先在阅读同步中开启微信读书并填写 Skill Key", Toast.LENGTH_LONG).show();
      } else {
        preloadWereadWallpaperQuote();
      }
    }
    config.setLastWallpaperUpdateMinute(0);
    updateStandbyWallpaperValue();
    updateStandbyRandomWallpaperValue();
    updateBookCoverValue();
    updateWereadWallpaperValue();
    updateWereadWallpaperIntervalValue();
    StandbyWallpaperUpdater.applyLockText(this);
    StandbyMinuteRefreshReceiver.scheduleRetry(this);
  }

  private void preloadWereadWallpaperQuote() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        WeReadWallpaperQuoteSource.getDailyQuote(Launcher.this, new Config(Launcher.this), true);
      }
    }, "WereadWallpaperPreload").start();
  }

  private void startQrScan() {
    try {
      startActivityForResult(new Intent(this, QrScanActivity.class), REQUEST_QR_SCAN);
    } catch (Exception e) {
      Toast.makeText(this, "无法启动扫码：" + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void handleScannedQr(String raw) {
    if (TextUtils.isEmpty(raw)) {
      Toast.makeText(this, "二维码为空", Toast.LENGTH_SHORT).show();
      return;
    }
    raw = raw.trim();
    String wereadApiKey = extractWereadApiKey(raw);
    if (!TextUtils.isEmpty(wereadApiKey)) {
      applyScannedWereadApiKey(wereadApiKey);
      return;
    }
    if (isHttpUrl(raw)) {
      handleScannedUrl(raw);
      return;
    }

    Map<String, String> params = parseQrSettings(raw);
    if (params.isEmpty()) {
      Log.w("EInkLauncher", "QR unrecognized raw=" + raw);
      Toast.makeText(this, "不是可识别的设置二维码", Toast.LENGTH_LONG).show();
      return;
    }

    int changed = applyScannedSettings(params);
    handleScannedActions(params);
    if (changed > 0) {
      Toast.makeText(this, "已应用 " + changed + " 项扫码设置", Toast.LENGTH_SHORT).show();
    } else if (!hasScannedAction(params)) {
      Toast.makeText(this, "二维码没有可应用的设置项", Toast.LENGTH_LONG).show();
    }
  }

  private String extractWereadApiKey(String raw) {
    if (TextUtils.isEmpty(raw)) return "";
    Matcher matcher = Pattern.compile("\\bwrk-[A-Za-z0-9_-]{16,}\\b").matcher(raw);
    return matcher.find() ? matcher.group() : "";
  }

  private void applyScannedWereadApiKey(String apiKey) {
    if (TextUtils.isEmpty(apiKey)) return;
    config.setWereadApiKey(apiKey);
    config.setWereadSyncEnabled(true);
    cn.modificator.launcher.reading.ReadingProgressRepository.clearRemoteCache(this);
    updateReadingSyncValues();
    refreshReadingProgress();
    Toast.makeText(this, "已设置微信读书 Skill Key", Toast.LENGTH_SHORT).show();
  }

  private Map<String, String> parseQrSettings(String raw) {
    Map<String, String> params = new LinkedHashMap<>();
    String text = raw == null ? "" : raw.trim();
    if (text.length() == 0) return params;
    if (text.startsWith("eink-settings:")) {
      text = text.substring("eink-settings:".length()).trim();
    }
    if (text.startsWith("{")) {
      try {
        parseJsonObject(new JSONObject(text), params, "");
      } catch (Exception ignored) {
      }
    } else if (text.startsWith("eink://")) {
      Uri uri = Uri.parse(text);
      for (String name : uri.getQueryParameterNames()) {
        params.put(name, uri.getQueryParameter(name));
      }
    } else if (text.contains("=")) {
      parseKeyValueText(text, params);
    }
    addNaturalGridSetting(text, params);
    return params;
  }

  private void parseJsonObject(JSONObject json, Map<String, String> params, String prefix)
      throws Exception {
    Iterator<String> keys = json.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = json.opt(key);
      if (value == null || value == JSONObject.NULL) continue;
      if (value instanceof JSONObject) {
        String childPrefix = "settings".equalsIgnoreCase(key) ? prefix : prefix + key;
        parseJsonObject((JSONObject) value, params, childPrefix);
      } else {
        params.put(prefix + key, String.valueOf(value));
      }
    }
  }

  private void parseKeyValueText(String text, Map<String, String> params) {
    String normalized = text.replace('\n', '&').replace('\r', '&').replace(';', '&');
    String[] pairs = normalized.split("&");
    for (String pair : pairs) {
      if (pair == null) continue;
      pair = pair.trim();
      if (pair.length() == 0) continue;
      int index = pair.indexOf('=');
      if (index <= 0) index = pair.indexOf(':');
      if (index <= 0) continue;
      String key = decodeQrComponent(pair.substring(0, index).trim());
      String value = decodeQrComponent(pair.substring(index + 1).trim());
      if (key.length() > 0) params.put(key, value);
    }
  }

  private String decodeQrComponent(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (Exception e) {
      return value;
    }
  }

  private void addNaturalGridSetting(String text, Map<String, String> params) {
    Matcher matcher = Pattern.compile(
        "(横屏|竖屏|landscape|portrait)?[^0-9]{0,12}(\\d{1,2})\\s*[xX*×]\\s*(\\d{1,2})")
        .matcher(text);
    if (!matcher.find()) return;
    String target = matcher.group(1);
    String grid = matcher.group(2) + "x" + matcher.group(3);
    if (target != null && (target.contains("横") || "landscape".equalsIgnoreCase(target))) {
      params.put("landscapeGrid", grid);
    } else if (target != null && (target.contains("竖") || "portrait".equalsIgnoreCase(target))) {
      params.put("portraitGrid", grid);
    } else if (!params.containsKey("grid")) {
      params.put("grid", grid);
    }
  }

  private int applyScannedSettings(Map<String, String> params) {
    int changed = 0;
    boolean gridChanged = false;
    boolean standbyChanged = false;

    int[] allGrid = parseGridValue(firstParam(params, "allGrid", "bothGrid"));
    if (allGrid != null) {
      config.setColNum(false, allGrid[0]);
      config.setRowNum(false, allGrid[1]);
      config.setColNum(true, allGrid[0]);
      config.setRowNum(true, allGrid[1]);
      gridChanged = true;
      changed += 2;
    }
    int[] portraitGrid = parseGridValue(firstParam(params, "portraitGrid", "verticalGrid"));
    if (portraitGrid != null) {
      config.setColNum(false, portraitGrid[0]);
      config.setRowNum(false, portraitGrid[1]);
      gridChanged = true;
      changed++;
    }
    int[] landscapeGrid = parseGridValue(firstParam(params, "landscapeGrid", "horizontalGrid"));
    if (landscapeGrid != null) {
      config.setColNum(true, landscapeGrid[0]);
      config.setRowNum(true, landscapeGrid[1]);
      gridChanged = true;
      changed++;
    }
    int[] currentGrid = parseGridValue(firstParam(params, "grid", "layout"));
    if (currentGrid != null) {
      boolean landscape = isLandscapeOrientation();
      config.setColNum(landscape, currentGrid[0]);
      config.setRowNum(landscape, currentGrid[1]);
      gridChanged = true;
      changed++;
    }

    Integer portraitCols = parseInteger(firstParam(params, "portraitCols", "portraitCol", "portraitColNum"));
    Integer portraitRows = parseInteger(firstParam(params, "portraitRows", "portraitRow", "portraitRowNum"));
    Integer landscapeCols = parseInteger(firstParam(params, "landscapeCols", "landscapeCol", "landscapeColNum"));
    Integer landscapeRows = parseInteger(firstParam(params, "landscapeRows", "landscapeRow", "landscapeRowNum"));
    Integer cols = parseInteger(firstParam(params, "cols", "col", "columns", "colNum"));
    Integer rows = parseInteger(firstParam(params, "rows", "row", "rowNum"));
    if (portraitCols != null) {
      config.setColNum(false, portraitCols);
      gridChanged = true;
      changed++;
    }
    if (portraitRows != null) {
      config.setRowNum(false, portraitRows);
      gridChanged = true;
      changed++;
    }
    if (landscapeCols != null) {
      config.setColNum(true, landscapeCols);
      gridChanged = true;
      changed++;
    }
    if (landscapeRows != null) {
      config.setRowNum(true, landscapeRows);
      gridChanged = true;
      changed++;
    }
    if (cols != null) {
      config.setColNum(isLandscapeOrientation(), cols);
      gridChanged = true;
      changed++;
    }
    if (rows != null) {
      config.setRowNum(isLandscapeOrientation(), rows);
      gridChanged = true;
      changed++;
    }

    Float fontSize = parseFloat(firstParam(params, "fontSize", "font"));
    if (fontSize != null) {
      float size = Math.max(8f, Math.min(40f, fontSize));
      config.setFontSize(size);
      onFontSizeChanged(size);
      changed++;
    }
    Integer appNameLines = parseAppNameLines(firstParam(params, "appNameLines", "nameLines"));
    if (appNameLines != null) {
      config.setAppNameLines(appNameLines);
      onAppNameLinesChanged(appNameLines);
      changed++;
    }
    Integer sortMode = parseSortMode(firstParam(params, "sortMode", "sort"));
    if (sortMode != null) {
      if (AppSortComparator.modeNeedsUsageStats(sortMode)
          && !AppSortComparator.hasUsageStatsPermission(this)) {
        Toast.makeText(this, R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
      } else {
        config.setSortMode(sortMode);
        onSortModeChanged(sortMode);
        changed++;
      }
    }
    Integer orientation = parseOrientationMode(firstParam(params, "orientation", "screenOrientation"));
    if (orientation != null) {
      config.setOrientationMode(orientation);
      applyLauncherOrientation(true);
      changed++;
    }

    Boolean hideDivider = parseBoolean(firstParam(params, "hideDivider"));
    Boolean showDivider = parseBoolean(firstParam(params, "showDivider", "divider"));
    if (hideDivider != null || showDivider != null) {
      boolean hide = hideDivider != null ? hideDivider : !showDivider;
      config.setHideDivider(hide);
      onHideDividerChanged(hide);
      changed++;
    }
    Boolean showStatusBar = parseBoolean(firstParam(params, "showStatusBar", "statusBar"));
    if (showStatusBar != null) {
      config.setShowStatusBar(showStatusBar);
      onShowStatusBarChanged(showStatusBar);
      changed++;
    }
    String desktopWallpaperDir = firstParam(params,
        "desktopWallpaperDir", "launcherWallpaperDir", "homeWallpaperDir");
    if (!TextUtils.isEmpty(desktopWallpaperDir) && !isHttpUrl(desktopWallpaperDir)) {
      config.setDesktopWallpaperDir(desktopWallpaperDir);
      applyDesktopWallpaper(true);
      changed++;
    }
    String iconTheme = firstParam(params, "iconTheme", "theme");
    if (!TextUtils.isEmpty(iconTheme)) {
      applyIconTheme(iconTheme.trim());
      changed++;
    }
    Boolean customIcon = parseBoolean(firstParam(params, "showCustomIcon", "customIcon"));
    if (customIcon != null) {
      applyIconTheme(customIcon ? Config.ICON_THEME_LOCAL : Config.ICON_THEME_BUILTIN);
      changed++;
    }
    Boolean iconAutoFill = parseBoolean(firstParam(params,
        "iconAutoReportMissing", "iconAutoFill", "autoReportMissingIcon"));
    if (iconAutoFill != null) {
      config.setIconAutoReportMissing(iconAutoFill);
      checkIconThemeUpdates(true);
      changed++;
    }
    List<String> favoriteApps = parsePackageList(firstParam(params,
        "favoriteApps", "favorites", "homeApps", "homeFavoriteApps"));
    if (!favoriteApps.isEmpty()) {
      config.setFavoriteApps(favoriteApps);
      dataCenter.setFavoriteApps(favoriteApps);
      dataCenter.refreshAppList(binder.isDelete());
      changed++;
    }
    String homeMode = firstParam(params, "homeMode", "appMode", "launcherMode");
    if (!TextUtils.isEmpty(homeMode)) {
      String normalizedMode = normalizeSettingKey(homeMode);
      if (normalizedMode.contains("drawer") || normalizedMode.contains("all")
          || normalizedMode.contains("全部") || normalizedMode.contains("抽屉")) {
        showSection(SECTION_DRAWER);
      } else if (normalizedMode.contains("favorite") || normalizedMode.contains("home")
          || normalizedMode.contains("常用") || normalizedMode.contains("主页")) {
        showSection(SECTION_APPS);
      }
    }

    String standbyText = firstParam(params, "standbyText", "lockText", "standbyLockText");
    if (standbyText != null) {
      config.setStandbyLockText(standbyText);
      standbyChanged = true;
      changed++;
    }
    Boolean standbyWallpaper = parseBoolean(firstParam(params,
        "standbyWallpaperEnabled", "wallpaperEnabled", "standbyWallpaper"));
    if (standbyWallpaper != null) {
      config.setStandbyWallpaperEnabled(standbyWallpaper);
      if (standbyWallpaper) config.setWereadWallpaperEnabled(false);
      standbyChanged = true;
      changed++;
    }
    Boolean standbyRandomWallpaper = parseBoolean(firstParam(params,
        "standbyRandomWallpaperEnabled", "randomWallpaperEnabled", "standbyRandomWallpaper",
        "randomWallpaper"));
    if (standbyRandomWallpaper != null) {
      config.setStandbyRandomWallpaperEnabled(standbyRandomWallpaper);
      if (standbyRandomWallpaper) config.setWereadWallpaperEnabled(false);
      standbyChanged = true;
      changed++;
    }
    Boolean wereadWallpaper = parseBoolean(firstParam(params,
        "wereadWallpaperEnabled", "wereadWallpaper", "wereadLockWallpaper",
        "wereadStandbyWallpaper"));
    if (wereadWallpaper != null) {
      config.setWereadWallpaperEnabled(wereadWallpaper);
      if (wereadWallpaper) {
        config.setStandbyWallpaperEnabled(false);
        config.setStandbyRandomWallpaperEnabled(false);
        config.setBookCoverEnabled(false);
        if (!TextUtils.isEmpty(config.getWereadApiKey())) {
          config.setWereadSyncEnabled(true);
        }
        WeReadWallpaperQuoteSource.clearCache(this);
        preloadWereadWallpaperQuote();
      }
      standbyChanged = true;
      changed++;
    }
    String wereadWallpaperDir = firstParam(params, "wereadWallpaperDir", "wereadLockWallpaperDir");
    if (!TextUtils.isEmpty(wereadWallpaperDir) && !isHttpUrl(wereadWallpaperDir)) {
      config.setWereadWallpaperDir(wereadWallpaperDir);
      WeReadWallpaperQuoteSource.clearCache(this);
      standbyChanged = true;
      changed++;
    }
    Integer wereadWallpaperInterval = parseDurationMinutes(firstParam(params,
        "wereadWallpaperInterval", "wereadWallpaperRefreshInterval",
        "wereadWallpaperIntervalMinutes", "wereadLockWallpaperInterval"));
    if (wereadWallpaperInterval != null) {
      config.setWereadWallpaperIntervalMinutes(wereadWallpaperInterval);
      standbyChanged = true;
      changed++;
    }
    String randomWallpaperDir = firstParam(params,
        "standbyRandomWallpaperDir", "randomWallpaperDir", "wallpaperDir");
    if (!TextUtils.isEmpty(randomWallpaperDir) && !isHttpUrl(randomWallpaperDir)) {
      config.setStandbyRandomWallpaperDir(randomWallpaperDir);
      standbyChanged = true;
      changed++;
    }
    Integer randomWallpaperInterval = parseDurationMinutes(firstParam(params,
        "standbyRandomWallpaperInterval", "standbyRandomWallpaperRefreshInterval",
        "standbyRandomWallpaperIntervalMinutes", "randomWallpaperInterval",
        "randomWallpaperRefreshInterval"));
    if (randomWallpaperInterval != null) {
      config.setStandbyRandomWallpaperIntervalMinutes(randomWallpaperInterval);
      standbyChanged = true;
      changed++;
    }
    Boolean standbyFullRefresh = parseBoolean(firstParam(params,
        "standbyFullRefresh", "fullRefresh"));
    if (standbyFullRefresh != null) {
      config.setStandbyFullRefresh(standbyFullRefresh);
      standbyChanged = true;
      changed++;
    }
    Integer standbyClockStyle = parseStandbyClockStyle(firstParam(params,
        "standbyClockStyle", "clockStyle", "lockClockStyle"));
    if (standbyClockStyle != null) {
      config.setStandbyClockStyle(standbyClockStyle);
      standbyChanged = true;
      changed++;
    }
    Boolean bookCover = parseBoolean(firstParam(params, "bookCoverEnabled", "bookCover"));
    boolean hasBookCoverSetting = false;
    if (bookCover != null) {
      config.setBookCoverEnabled(bookCover);
      if (bookCover) config.setWereadWallpaperEnabled(false);
      standbyChanged = true;
      changed++;
    }
    Boolean bookCoverRandom = parseBoolean(firstParam(params,
        "bookCoverRandom", "randomBookCover", "bookCoverShuffle", "overlayRandom"));
    if (bookCoverRandom != null) {
      hasBookCoverSetting = true;
      config.setBookCoverRandom(bookCoverRandom);
      standbyChanged = true;
      changed++;
    }
    String overlayDir = firstParam(params, "bookCoverOverlayDir", "overlayDir");
    if (!TextUtils.isEmpty(overlayDir) && !isHttpUrl(overlayDir)) {
      hasBookCoverSetting = true;
      config.setBookCoverOverlayDir(overlayDir);
      standbyChanged = true;
      changed++;
    }
    if (bookCover == null && hasBookCoverSetting) {
      config.setBookCoverEnabled(true);
      config.setWereadWallpaperEnabled(false);
      standbyChanged = true;
      changed++;
    }

    changed += applyScannedAutoRefreshSettings(params);

    Boolean wifiTransfer = parseBoolean(firstParam(params, "wifiTransfer", "wifiTransferEnabled"));
    if (wifiTransfer != null) {
      setWifiTransferEnabled(wifiTransfer);
      changed++;
    }

    if (gridChanged) applyCurrentGridSize();
    if (standbyChanged) {
      config.setLastWallpaperUpdateMinute(0);
      StandbyWallpaperUpdater.applyLockText(this);
      StandbyMinuteRefreshReceiver.scheduleRetry(this);
    }
    updateDesktopSettingValues();
    updateAutoRefreshStatus();
    return changed;
  }

  private int applyScannedAutoRefreshSettings(Map<String, String> params) {
    int changed = 0;
    Boolean enabled = parseBoolean(firstParam(params, "autoRefreshEnabled", "autoRefresh"));
    if (enabled != null) {
      AutoRefreshSettings.setEnabled(this, enabled);
      changed++;
    }
    Integer interval = parseInteger(firstParam(params, "autoRefreshInterval", "refreshInterval"));
    if (interval != null) {
      AutoRefreshSettings.setInterval(this, interval);
      changed++;
    }
    Boolean tapEnabled = parseBoolean(firstParam(params, "autoRefreshTapEnabled", "tapRefreshEnabled"));
    if (tapEnabled != null) {
      AutoRefreshSettings.setTapEnabled(this, tapEnabled);
      changed++;
    }
    Integer tapInterval = parseInteger(firstParam(params, "autoRefreshTapInterval", "tapInterval"));
    if (tapInterval != null) {
      AutoRefreshSettings.setTapInterval(this, tapInterval);
      changed++;
    }
    Integer delay = parseInteger(firstParam(params, "autoRefreshDelay", "refreshDelay"));
    if (delay != null) {
      AutoRefreshSettings.setDelay(this, Math.max(0, delay));
      changed++;
    }
    Integer keycode = parseInteger(firstParam(params, "autoRefreshKeycode", "refreshKeycode", "keycode"));
    if (keycode != null && keycode > 0) {
      AutoRefreshSettings.setRefreshKeyCode(this, keycode);
      changed++;
    }
    Boolean shakeRefresh = parseBoolean(firstParam(params,
        "shakeRefreshEnabled", "shakeRefresh", "shakeFullRefresh"));
    if (shakeRefresh != null) {
      AutoRefreshSettings.setShakeRefreshEnabled(this, shakeRefresh);
      changed++;
    }
    Boolean shakePageTurn = parseBoolean(firstParam(params,
        "shakePageTurnEnabled", "shakePageTurn", "tiltPageTurn"));
    if (shakePageTurn != null) {
      AutoRefreshSettings.setShakeActionMode(this,
          shakePageTurn
              ? AutoRefreshSettings.SHAKE_ACTION_PAGE_TURN
              : AutoRefreshSettings.SHAKE_ACTION_OFF);
      changed++;
    }
    Integer shakeCount = parseInteger(firstParam(params,
        "shakeRefreshCount", "shakeCount"));
    if (shakeCount != null) {
      AutoRefreshSettings.setShakeRefreshCount(this, shakeCount);
      changed++;
    }
    Integer shakeAmplitude = parseShakeAmplitude(firstParam(params,
        "shakeRefreshAmplitude", "shakeAmplitude"));
    if (shakeAmplitude != null) {
      AutoRefreshSettings.setShakeRefreshAmplitude(this, shakeAmplitude);
      changed++;
    }
    return changed;
  }

  private Integer parseShakeAmplitude(String value) {
    if (TextUtils.isEmpty(value)) return null;
    String normalized = normalizeSettingKey(value);
    if ("small".equals(normalized) || "low".equals(normalized)
        || "小".equals(value.trim())) {
      return AutoRefreshSettings.SHAKE_AMPLITUDE_SMALL;
    }
    if ("medium".equals(normalized) || "middle".equals(normalized)
        || "中".equals(value.trim())) {
      return AutoRefreshSettings.SHAKE_AMPLITUDE_MEDIUM;
    }
    if ("large".equals(normalized) || "high".equals(normalized)
        || "大".equals(value.trim())) {
      return AutoRefreshSettings.SHAKE_AMPLITUDE_LARGE;
    }
    Integer numeric = parseInteger(value);
    return numeric == null ? null : Math.max(0, Math.min(2, numeric));
  }

  private void handleScannedActions(Map<String, String> params) {
    String wallpaperUrl = firstUrlParam(params, "wallpaperUrl", "standbyWallpaperUrl");
    String bookCoverUrl = firstUrlParam(params, "bookCoverUrl", "overlayUrl", "transparentBookCoverUrl");
    String apkUrl = firstUrlParam(params, "apkUrl", "installApk", "apk");
    String genericUrl = firstUrlParam(params, "url");
    String type = firstParam(params, "type", "target");

    if (!TextUtils.isEmpty(genericUrl)) {
      String normalizedType = type == null ? "" : normalizeSettingKey(type);
      if ("wallpaper".equals(normalizedType) || "standbywallpaper".equals(normalizedType)) {
        wallpaperUrl = genericUrl;
      } else if ("bookcover".equals(normalizedType)
          || "overlay".equals(normalizedType)
          || "transparentbookcover".equals(normalizedType)) {
        bookCoverUrl = genericUrl;
      } else if ("apk".equals(normalizedType) || "install".equals(normalizedType)) {
        apkUrl = genericUrl;
      } else if (isApkUrl(genericUrl)) {
        apkUrl = genericUrl;
      } else if (isImageUrl(genericUrl) && TextUtils.isEmpty(wallpaperUrl)
          && TextUtils.isEmpty(bookCoverUrl)) {
        showImageQrTargetDialog(genericUrl);
      } else if (TextUtils.isEmpty(wallpaperUrl) && TextUtils.isEmpty(bookCoverUrl)
          && TextUtils.isEmpty(apkUrl)) {
        handleScannedUrl(genericUrl);
      }
    }

    if (!TextUtils.isEmpty(wallpaperUrl)) downloadAndSetWallpaper(wallpaperUrl);
    if (!TextUtils.isEmpty(bookCoverUrl)) downloadAndSetBookCover(bookCoverUrl);
    if (!TextUtils.isEmpty(apkUrl)) downloadAndInstallScannedApk(apkUrl);
  }

  private boolean hasScannedAction(Map<String, String> params) {
    return !TextUtils.isEmpty(firstUrlParam(params, "url", "wallpaperUrl", "standbyWallpaperUrl",
        "bookCoverUrl", "overlayUrl", "transparentBookCoverUrl", "apkUrl", "installApk", "apk"));
  }

  private void handleScannedUrl(final String url) {
    Log.i("EInkLauncher", "QR url scanned: " + url);
    if (isApkUrl(url)) {
      downloadAndInstallScannedApk(url);
      return;
    }
    if (isImageUrl(url)) {
      showImageQrTargetDialog(url);
      return;
    }
    Toast.makeText(this, "正在识别链接类型...", Toast.LENGTH_SHORT).show();
    new Thread(new Runnable() {
      @Override
      public void run() {
        final String contentType = fetchContentType(url);
        Log.i("EInkLauncher", "QR url contentType=" + contentType + " url=" + url);
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (isImageContentType(contentType)) {
              showImageQrTargetDialog(url);
            } else if (isApkContentType(contentType)) {
              downloadAndInstallScannedApk(url);
            } else {
              showUnknownUrlTargetDialog(url, contentType);
            }
          }
        });
      }
    }, "QrUrlProbe").start();
  }

  private void showUnknownUrlTargetDialog(final String url, String contentType) {
    String message = TextUtils.isEmpty(contentType)
        ? "未识别到文件类型，请选择用途"
        : "类型：" + contentType + "\n请选择用途";
    new AlertDialog.Builder(this)
        .setTitle("链接二维码")
        .setMessage(message)
        .setPositiveButton("设为壁纸", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            downloadAndSetWallpaper(url);
          }
        })
        .setNeutralButton("设为透明书封", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            downloadAndSetBookCover(url);
          }
        })
        .setNegativeButton("安装 APK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            downloadAndInstallScannedApk(url);
          }
        })
        .show();
  }

  private void showImageQrTargetDialog(final String url) {
    final String[] targets = new String[]{"设为桌面壁纸", "设为待机壁纸", "设为透明书封"};
    new AlertDialog.Builder(this)
        .setTitle("图片二维码")
        .setItems(targets, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              downloadAndSetDesktopWallpaper(url);
            } else if (which == 1) {
              downloadAndSetWallpaper(url);
            } else if (which == 2) {
              downloadAndSetBookCover(url);
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void downloadAndSetDesktopWallpaper(final String url) {
    Log.i("EInkLauncher", "QR desktop wallpaper begin url=" + url);
    Toast.makeText(this, "正在下载桌面壁纸...", Toast.LENGTH_SHORT).show();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          File dir = new File(config.getDesktopWallpaperDir());
          final File file = downloadUrlToFile(url, dir, "desktop-wallpaper", ".jpg");
          final Bitmap bitmap = decodeDesktopWallpaper(file);
          if (bitmap == null) throw new IOException("无法读取图片");
          bitmap.recycle();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              applyDesktopWallpaper(true);
              updateDesktopSettingValues();
              Toast.makeText(Launcher.this, "桌面壁纸已设置：" + file.getName(),
                  Toast.LENGTH_LONG).show();
            }
          });
        } catch (final Exception e) {
          Log.w("EInkLauncher", "QR desktop wallpaper failed url=" + url, e);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Toast.makeText(Launcher.this, "桌面壁纸设置失败：" + e.getMessage(),
                  Toast.LENGTH_LONG).show();
            }
          });
        }
      }
    }, "QrDesktopWallpaperDownload").start();
  }

  private void downloadAndSetWallpaper(final String url) {
    Log.i("EInkLauncher", "QR wallpaper begin url=" + url);
    Toast.makeText(this, "正在下载壁纸...", Toast.LENGTH_SHORT).show();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          File dir = new File(config.getStandbyRandomWallpaperDir());
          final File file = downloadUrlToFile(url, dir, "wallpaper", ".jpg");
          Log.i("EInkLauncher", "QR wallpaper downloaded file=" + file.getAbsolutePath()
              + " size=" + file.length());
          final Bitmap bitmap = decodeScaledBitmap(file, 1600);
          if (bitmap == null) throw new IOException("无法读取图片");
          Log.i("EInkLauncher", "QR wallpaper decoded " + bitmap.getWidth()
              + "x" + bitmap.getHeight());
          boolean published = StandbyWallpaperUpdater.update(Launcher.this, bitmap,
              config.getStandbyLockText(), true, false);
          Log.i("EInkLauncher", "QR standby wallpaper update published=" + published);
          bitmap.recycle();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              config.setWereadWallpaperEnabled(false);
              config.setLastWallpaperUpdateMinute(0);
              updateDesktopSettingValues();
              Toast.makeText(Launcher.this, "待机壁纸已保存并设置：" + file.getName(),
                  Toast.LENGTH_LONG).show();
            }
          });
        } catch (final Exception e) {
          Log.w("EInkLauncher", "QR wallpaper failed url=" + url, e);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Toast.makeText(Launcher.this, "壁纸设置失败：" + e.getMessage(),
                  Toast.LENGTH_LONG).show();
            }
          });
        }
      }
    }, "QrWallpaperDownload").start();
  }

  private void syncSystemWallpaperAsync(final File file) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        Bitmap bitmap = null;
        try {
          Log.i("EInkLauncher", "System wallpaper sync begin file=" + file.getAbsolutePath());
          bitmap = decodeScaledBitmap(file, 1600);
          if (bitmap == null) return;
          WallpaperManager.getInstance(Launcher.this).setBitmap(bitmap);
          Log.i("EInkLauncher", "System wallpaper synced from scanned image");
        } catch (Exception e) {
          Log.w("EInkLauncher", "System wallpaper sync failed, standby wallpaper already set", e);
        } finally {
          if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
          }
        }
      }
    }, "QrSystemWallpaperSync").start();
  }

  private void downloadAndSetBookCover(final String url) {
    config.setBookCoverEnabled(true);
    config.setBookCoverRandom(false);
    config.setWereadWallpaperEnabled(false);
    updateDesktopSettingValues();
    Toast.makeText(this, "正在下载透明书封...", Toast.LENGTH_SHORT).show();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          File dir = new File(config.getBookCoverOverlayDir());
          File file = downloadUrlToFile(url, dir, "book-cover", ".png");
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              config.setBookCoverEnabled(true);
              config.setBookCoverRandom(false);
              config.setWereadWallpaperEnabled(false);
              updateDesktopSettingValues();
              Toast.makeText(Launcher.this, "透明书封已保存：" + file.getName(),
                  Toast.LENGTH_LONG).show();
            }
          });
        } catch (final Exception e) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Toast.makeText(Launcher.this, "透明书封下载失败：" + e.getMessage(),
                  Toast.LENGTH_LONG).show();
            }
          });
        }
      }
    }, "QrBookCoverDownload").start();
  }

  private void downloadAndInstallScannedApk(final String url) {
    Toast.makeText(this, "正在下载 APK...", Toast.LENGTH_SHORT).show();
    updateUpdateStatus("正在下载扫码 APK...");
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          File dir = getPublicDownloadsDirectory("updates");
          final File apk = downloadUrlToFile(url, dir, "scanned", ".apk");
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Toast.makeText(Launcher.this, "APK 已下载，打开安装器", Toast.LENGTH_SHORT).show();
              installDownloadedUpdate(apk);
            }
          });
        } catch (final Exception e) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateUpdateStatus("扫码 APK 下载失败：" + e.getMessage());
              Toast.makeText(Launcher.this, "APK 下载失败：" + e.getMessage(),
                  Toast.LENGTH_LONG).show();
            }
          });
        }
      }
    }, "QrApkDownload").start();
  }

  private File downloadUrlToFile(String rawUrl, File dir, String baseName, String defaultExt)
      throws IOException {
    if (dir == null) throw new IOException("缓存目录不可用");
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("无法创建目录：" + dir.getAbsolutePath());
    }
    IOException systemDownloadError = null;
    if (isHttpUrl(rawUrl)) {
      try {
        return downloadUrlWithSystemDownloader(rawUrl, dir, baseName, defaultExt);
      } catch (IOException e) {
        systemDownloadError = e;
        Log.w("EInkLauncher", "QR system download unavailable, fallback direct url=" + rawUrl, e);
      }
    }
    IOException lastError = null;
    for (int attempt = 1; attempt <= QR_DOWNLOAD_MAX_RETRIES; attempt++) {
      try {
        return downloadUrlToFileOnce(rawUrl, dir, baseName, defaultExt, attempt);
      } catch (IOException e) {
        lastError = e;
        Log.w("EInkLauncher", "QR download failed attempt=" + attempt + " url=" + rawUrl, e);
        if (attempt < QR_DOWNLOAD_MAX_RETRIES) {
          try {
            Thread.sleep(800L * attempt);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw e;
          }
        }
      }
    }
    if (lastError != null && isTimeoutError(lastError)) {
      throw new IOException("下载超时，请检查网络后重试", lastError);
    }
    if (lastError == null && systemDownloadError != null) {
      throw systemDownloadError;
    }
    throw lastError != null ? lastError : new IOException("下载失败");
  }

  private File getPublicDownloadsDirectory(String fallbackChild) {
    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    if (downloads != null) {
      if ((downloads.exists() || downloads.mkdirs()) && downloads.isDirectory()) {
        return downloads;
      }
      Log.w("EInkLauncher", "Public Downloads unavailable: " + downloads.getAbsolutePath());
    }
    File fallback = getExternalFilesDir(fallbackChild);
    if (fallback == null) fallback = new File(getCacheDir(), fallbackChild);
    return fallback;
  }

  private File downloadUrlWithSystemDownloader(String rawUrl, File dir, String baseName,
                                               String defaultExt) throws IOException {
    DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
    if (manager == null) throw new IOException("系统下载器不可用");

    String ext = getUrlExtension(rawUrl, defaultExt);
    if (TextUtils.isEmpty(ext)) ext = defaultExt;
    File target = new File(dir, baseName + "-" + System.currentTimeMillis() + "-system" + ext);
    if (target.exists() && !target.delete()) {
      throw new IOException("无法覆盖下载文件");
    }

    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(rawUrl));
    request.setTitle("E-Ink Launcher " + baseName);
    request.setDescription(rawUrl);
    request.setMimeType(guessDownloadMimeType(ext, defaultExt));
    request.setAllowedOverMetered(true);
    request.setAllowedOverRoaming(true);
    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
    if (isPublicDownloadsDirectory(dir)) {
      request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, target.getName());
    } else {
      request.setDestinationUri(Uri.fromFile(target));
    }

    long id = manager.enqueue(request);
    Log.i("EInkLauncher", "QR system download enqueued id=" + id + " url=" + rawUrl
        + " target=" + target.getAbsolutePath());
    boolean keepDownload = false;
    try {
      File result = waitForSystemDownload(manager, id, target,
          getSystemDownloadTimeoutMs(ext, defaultExt),
          getSystemDownloadStallTimeoutMs(ext, defaultExt));
      keepDownload = true;
      return result;
    } finally {
      if (!keepDownload) {
        try {
          manager.remove(id);
        } catch (Exception ignored) {
        }
        if (target.exists()) {
          //noinspection ResultOfMethodCallIgnored
          target.delete();
        }
      }
    }
  }

  private File waitForSystemDownload(DownloadManager manager, long id, File target,
                                     long timeoutMs, long stallTimeoutMs) throws IOException {
    long start = System.currentTimeMillis();
    long lastProgressAt = start;
    long lastLogAt = 0L;
    long lastDownloaded = -1L;
    long bestDownloaded = -1L;
    while (true) {
      long now = System.currentTimeMillis();
      Cursor cursor = null;
      try {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        cursor = manager.query(query);
        if (cursor != null && cursor.moveToFirst()) {
          int status = getCursorInt(cursor, DownloadManager.COLUMN_STATUS,
              DownloadManager.STATUS_PENDING);
          long downloaded = getCursorLong(cursor,
              DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR, -1L);
          long total = getCursorLong(cursor, DownloadManager.COLUMN_TOTAL_SIZE_BYTES, -1L);
          if (status == DownloadManager.STATUS_SUCCESSFUL) {
            File result = resolveSystemDownloadedFile(cursor, target);
            if (result.exists() && result.length() > 0) {
              Log.i("EInkLauncher", "QR system download saved " + result.getAbsolutePath()
                  + " size=" + result.length());
              return result;
            }
            throw new IOException("系统下载完成但文件不可用");
          }
          if (status == DownloadManager.STATUS_FAILED) {
            int reason = getCursorInt(cursor, DownloadManager.COLUMN_REASON, 0);
            throw new IOException("系统下载失败：" + formatDownloadReason(reason));
          }
          if (downloaded > bestDownloaded) {
            bestDownloaded = downloaded;
            lastProgressAt = now;
          }
          if (downloaded != lastDownloaded || now - lastLogAt > 3000L) {
            Log.i("EInkLauncher", "QR system download status=" + status
                + " downloaded=" + downloaded + " total=" + total);
            lastDownloaded = downloaded;
            lastLogAt = now;
          }
        }
      } finally {
        if (cursor != null) cursor.close();
      }

      now = System.currentTimeMillis();
      if (now - lastProgressAt > stallTimeoutMs) {
        throw new IOException("系统下载器无进度，改用内置下载器");
      }
      if (now - start > timeoutMs) {
        throw new IOException("系统下载超时，改用内置下载器");
      }
      try {
        Thread.sleep(QR_SYSTEM_DOWNLOAD_POLL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("下载被中断", e);
      }
    }
  }

  private File resolveSystemDownloadedFile(Cursor cursor, File target) {
    String localUri = getCursorString(cursor, DownloadManager.COLUMN_LOCAL_URI);
    if (!TextUtils.isEmpty(localUri)) {
      try {
        Uri uri = Uri.parse(localUri);
        if ("file".equals(uri.getScheme()) && !TextUtils.isEmpty(uri.getPath())) {
          return new File(uri.getPath());
        }
      } catch (Exception ignored) {
      }
    }
    return target;
  }

  private boolean isPublicDownloadsDirectory(File dir) {
    if (dir == null) return false;
    try {
      File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
      return downloads != null && downloads.getCanonicalFile().equals(dir.getCanonicalFile());
    } catch (IOException e) {
      return false;
    }
  }

  private int getCursorInt(Cursor cursor, String column, int fallback) {
    int index = cursor == null ? -1 : cursor.getColumnIndex(column);
    if (index < 0) return fallback;
    return cursor.getInt(index);
  }

  private long getCursorLong(Cursor cursor, String column, long fallback) {
    int index = cursor == null ? -1 : cursor.getColumnIndex(column);
    if (index < 0) return fallback;
    return cursor.getLong(index);
  }

  private String getCursorString(Cursor cursor, String column) {
    int index = cursor == null ? -1 : cursor.getColumnIndex(column);
    if (index < 0) return "";
    return cursor.getString(index);
  }

  private long getSystemDownloadTimeoutMs(String ext, String defaultExt) {
    if (isApkExtension(ext) || isApkExtension(defaultExt)) {
      return QR_SYSTEM_APK_DOWNLOAD_TIMEOUT_MS;
    }
    return QR_SYSTEM_DOWNLOAD_TIMEOUT_MS;
  }

  private long getSystemDownloadStallTimeoutMs(String ext, String defaultExt) {
    if (isApkExtension(ext) || isApkExtension(defaultExt)) {
      return QR_SYSTEM_APK_STALL_TIMEOUT_MS;
    }
    return QR_SYSTEM_IMAGE_STALL_TIMEOUT_MS;
  }

  private String guessDownloadMimeType(String ext, String defaultExt) {
    String lower = TextUtils.isEmpty(ext) ? defaultExt : ext;
    lower = lower == null ? "" : lower.toLowerCase(Locale.US);
    if (".png".equals(lower)) return "image/png";
    if (".webp".equals(lower)) return "image/webp";
    if (".apk".equals(lower)) return "application/vnd.android.package-archive";
    return "image/jpeg";
  }

  private String formatDownloadReason(int reason) {
    switch (reason) {
      case DownloadManager.ERROR_CANNOT_RESUME:
        return "无法继续";
      case DownloadManager.ERROR_DEVICE_NOT_FOUND:
        return "存储不可用";
      case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
        return "文件已存在";
      case DownloadManager.ERROR_FILE_ERROR:
        return "文件写入失败";
      case DownloadManager.ERROR_HTTP_DATA_ERROR:
        return "服务器数据异常";
      case DownloadManager.ERROR_INSUFFICIENT_SPACE:
        return "空间不足";
      case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
        return "重定向过多";
      case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
        return "HTTP 状态异常";
      case DownloadManager.ERROR_UNKNOWN:
        return "未知错误";
      default:
        if (reason >= 400 && reason < 600) return "HTTP " + reason;
        return String.valueOf(reason);
    }
  }

  private File downloadUrlToFileOnce(String rawUrl, File dir, String baseName, String defaultExt,
                                     int attempt)
      throws IOException {
    Log.i("EInkLauncher", "QR download begin attempt=" + attempt + " url=" + rawUrl);
    HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
    connection.setConnectTimeout(QR_DOWNLOAD_CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(isApkExtension(defaultExt) ? QR_DOWNLOAD_READ_TIMEOUT_MS : 30000);
    connection.setInstanceFollowRedirects(true);
    connection.setRequestProperty("Accept", "*/*");
    connection.setRequestProperty("Connection", "close");
    connection.setRequestProperty("User-Agent", "E-Ink-Launcher");
    InputStream in = null;
    FileOutputStream out = null;
    File tmp = null;
    try {
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("HTTP " + code);
      }
      String contentType = connection.getContentType();
      String ext = getDownloadExtension(rawUrl, contentType, defaultExt);
      long contentLength = getContentLength(connection);
      Log.i("EInkLauncher", "QR download response code=" + code + " contentType="
          + contentType + " ext=" + ext + " length=" + contentLength);
      File target = new File(dir, baseName + "-" + System.currentTimeMillis()
          + "-" + attempt + ext);
      tmp = new File(dir, target.getName() + ".tmp");
      in = connection.getInputStream();
      out = new FileOutputStream(tmp);
      byte[] buffer = new byte[16 * 1024];
      int read;
      long readTotal = 0L;
      IOException readError = null;
      try {
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
          readTotal += read;
        }
      } catch (IOException e) {
        readError = e;
        Log.w("EInkLauncher", "QR download stream interrupted attempt=" + attempt
            + " bytes=" + readTotal + " expected=" + contentLength, e);
      }
      out.flush();
      if (readError != null) {
        boolean lastAttempt = attempt >= QR_DOWNLOAD_MAX_RETRIES;
        boolean recoverableImage = lastAttempt
            && (isImageContentType(contentType) || isImageExtension(ext))
            && isDecodableImageFile(tmp);
        if (!recoverableImage) {
          throw readError;
        }
        Log.w("EInkLauncher", "QR download using decodable partial image bytes="
            + readTotal + " expected=" + contentLength);
      }
      if (!tmp.renameTo(target)) {
        throw new IOException("无法保存下载文件");
      }
      Log.i("EInkLauncher", "QR download saved " + target.getAbsolutePath()
          + " size=" + target.length());
      return target;
    } finally {
      if (out != null) {
        try {
          out.close();
        } catch (IOException ignored) {
        }
      }
      if (in != null) {
        try {
          in.close();
        } catch (IOException ignored) {
        }
      }
      if (tmp != null && tmp.exists()) {
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
      }
      connection.disconnect();
    }
  }

  private long getContentLength(HttpURLConnection connection) {
    if (connection == null) return -1L;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return connection.getContentLengthLong();
    }
    return connection.getContentLength();
  }

  private Bitmap decodeScaledBitmap(File file, int maxSize) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    int sample = 1;
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
      sample *= 2;
    }
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = sample;
    opts.inPreferredConfig = Bitmap.Config.RGB_565;
    return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
  }

  private String getUrlExtension(String rawUrl, String defaultExt) {
    try {
      String path = new URL(rawUrl).getPath();
      int dot = path == null ? -1 : path.lastIndexOf('.');
      if (dot >= 0) {
        String ext = path.substring(dot).toLowerCase(Locale.US);
        if (".png".equals(ext) || ".jpg".equals(ext) || ".jpeg".equals(ext)
            || ".webp".equals(ext) || ".apk".equals(ext)) {
          return ext;
        }
      }
    } catch (Exception ignored) {
    }
    return defaultExt;
  }

  private String getDownloadExtension(String rawUrl, String contentType, String defaultExt) {
    String ext = getUrlExtension(rawUrl, "");
    if (!TextUtils.isEmpty(ext)) return ext;
    if (contentType != null) {
      String lower = contentType.toLowerCase(Locale.US);
      if (lower.contains("image/png")) return ".png";
      if (lower.contains("image/jpeg") || lower.contains("image/jpg")) return ".jpg";
      if (lower.contains("image/webp")) return ".webp";
      if (lower.contains("android.package-archive") || lower.contains("application/vnd.android")) {
        return ".apk";
      }
    }
    return defaultExt;
  }

  private boolean isTimeoutError(Throwable error) {
    while (error != null) {
      String name = error.getClass().getName().toLowerCase(Locale.US);
      String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
      if (name.contains("timeout") || message.contains("timeout") || message.contains("timed out")) {
        return true;
      }
      error = error.getCause();
    }
    return false;
  }

  private String fetchContentType(String rawUrl) {
    String contentType = fetchContentType(rawUrl, "HEAD");
    if (!TextUtils.isEmpty(contentType)) return contentType;
    return fetchContentType(rawUrl, "GET");
  }

  private String fetchContentType(String rawUrl, String method) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(rawUrl).openConnection();
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(8000);
      connection.setInstanceFollowRedirects(true);
      connection.setRequestMethod(method);
      if ("GET".equals(method)) {
        connection.setRequestProperty("Range", "bytes=0-0");
      }
      int code = connection.getResponseCode();
      if (code >= 200 && code < 400) {
        return connection.getContentType();
      }
    } catch (Exception ignored) {
    } finally {
      if (connection != null) connection.disconnect();
    }
    return "";
  }

  private String firstParam(Map<String, String> params, String... names) {
    if (params == null || names == null) return null;
    for (String name : names) {
      String normalizedName = normalizeSettingKey(name);
      for (Map.Entry<String, String> entry : params.entrySet()) {
        if (normalizedName.equals(normalizeSettingKey(entry.getKey()))) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  private String firstUrlParam(Map<String, String> params, String... names) {
    String value = firstParam(params, names);
    return isHttpUrl(value) ? value.trim() : null;
  }

  private String normalizeSettingKey(String key) {
    if (key == null) return "";
    return key.toLowerCase(Locale.US)
        .replace("_", "")
        .replace("-", "")
        .replace(".", "")
        .replace(" ", "");
  }

  private boolean isHttpUrl(String value) {
    if (value == null) return false;
    String lower = value.trim().toLowerCase(Locale.US);
    return lower.startsWith("http://") || lower.startsWith("https://");
  }

  private boolean isImageUrl(String value) {
    String ext = getUrlExtension(value, "");
    return ".png".equals(ext) || ".jpg".equals(ext) || ".jpeg".equals(ext) || ".webp".equals(ext);
  }

  private boolean isApkUrl(String value) {
    return ".apk".equals(getUrlExtension(value, ""));
  }

  private boolean isImageContentType(String contentType) {
    if (contentType == null) return false;
    String lower = contentType.toLowerCase(Locale.US);
    return lower.startsWith("image/")
        || lower.contains("image/png")
        || lower.contains("image/jpeg")
        || lower.contains("image/webp");
  }

  private boolean isImageExtension(String ext) {
    if (ext == null) return false;
    String lower = ext.toLowerCase(Locale.US);
    return ".png".equals(lower) || ".jpg".equals(lower)
        || ".jpeg".equals(lower) || ".webp".equals(lower);
  }

  private boolean isApkExtension(String ext) {
    return ext != null && ".apk".equals(ext.toLowerCase(Locale.US));
  }

  private boolean isDecodableImageFile(File file) {
    if (file == null || !file.exists() || file.length() <= 0) return false;
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    return bounds.outWidth > 0 && bounds.outHeight > 0;
  }

  private boolean isApkContentType(String contentType) {
    if (contentType == null) return false;
    String lower = contentType.toLowerCase(Locale.US);
    return lower.contains("android.package-archive")
        || lower.contains("application/vnd.android");
  }

  private int[] parseGridValue(String value) {
    if (TextUtils.isEmpty(value)) return null;
    Matcher matcher = Pattern.compile("(\\d{1,2})\\s*[xX*×,，]\\s*(\\d{1,2})").matcher(value);
    if (!matcher.find()) return null;
    return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
  }

  private List<String> parsePackageList(String value) {
    List<String> packages = new ArrayList<>();
    if (TextUtils.isEmpty(value) || isHttpUrl(value)) return packages;
    String[] parts = value.split("[,;\\n\\s]+");
    for (String part : parts) {
      if (part == null) continue;
      String trimmed = part.trim();
      if (trimmed.length() == 0 || !AppDataCenter.canPinToHome(trimmed)) continue;
      if (!packages.contains(trimmed)) packages.add(trimmed);
    }
    return packages;
  }

  private Integer parseInteger(String value) {
    if (TextUtils.isEmpty(value)) return null;
    try {
      return Integer.parseInt(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private Integer parseDurationMinutes(String value) {
    if (TextUtils.isEmpty(value)) return null;
    String raw = value.trim().toLowerCase(Locale.US);
    Integer numeric = parseInteger(raw);
    if (numeric != null) return numeric;
    Matcher matcher = Pattern.compile("^(\\d+)\\s*(m|min|minute|minutes|分钟|分)$")
        .matcher(raw);
    if (matcher.matches()) {
      return parseInteger(matcher.group(1));
    }
    matcher = Pattern.compile("^(\\d+)\\s*(h|hr|hour|hours|小时|时)$").matcher(raw);
    if (matcher.matches()) {
      Integer hours = parseInteger(matcher.group(1));
      return hours == null ? null : hours * 60;
    }
    return null;
  }

  private Float parseFloat(String value) {
    if (TextUtils.isEmpty(value)) return null;
    try {
      return Float.parseFloat(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private Boolean parseBoolean(String value) {
    if (TextUtils.isEmpty(value) || isHttpUrl(value)) return null;
    String lower = value.trim().toLowerCase(Locale.US);
    if ("1".equals(lower) || "true".equals(lower) || "yes".equals(lower)
        || "on".equals(lower) || "open".equals(lower) || "enabled".equals(lower)
        || "开".equals(lower) || "开启".equals(lower) || "是".equals(lower)) {
      return true;
    }
    if ("0".equals(lower) || "false".equals(lower) || "no".equals(lower)
        || "off".equals(lower) || "close".equals(lower) || "disabled".equals(lower)
        || "关".equals(lower) || "关闭".equals(lower) || "否".equals(lower)) {
      return false;
    }
    return null;
  }

  private Integer parseAppNameLines(String value) {
    if (TextUtils.isEmpty(value)) return null;
    String lower = value.trim().toLowerCase(Locale.US);
    if ("hide".equals(lower) || "hidden".equals(lower) || "none".equals(lower)
        || "隐藏".equals(lower)) {
      return 0;
    }
    if ("all".equals(lower) || "unlimited".equals(lower) || "不限".equals(lower)) {
      return Integer.MAX_VALUE;
    }
    return parseInteger(value);
  }

  private Integer parseSortMode(String value) {
    if (TextUtils.isEmpty(value)) return null;
    Integer numeric = parseInteger(value);
    if (numeric != null) return Math.max(0, Math.min(7, numeric));
    String lower = normalizeSettingKey(value);
    if (lower.contains("namedesc") || lower.contains("名称逆")) return AppSortComparator.SORT_NAME_DESC;
    if (lower.contains("installasc") || lower.contains("安装正")) return AppSortComparator.SORT_INSTALL_ASC;
    if (lower.contains("installdesc") || lower.contains("安装逆") || lower.contains("最新")) {
      return AppSortComparator.SORT_INSTALL_DESC;
    }
    if (lower.contains("usageasc") || lower.contains("使用少")) return AppSortComparator.SORT_USAGE_ASC;
    if (lower.contains("usagedesc") || lower.contains("使用多")) return AppSortComparator.SORT_USAGE_DESC;
    if (lower.contains("recentasc") || lower.contains("最久")) return AppSortComparator.SORT_RECENT_ASC;
    if (lower.contains("recentdesc") || lower.contains("最近")) return AppSortComparator.SORT_RECENT_DESC;
    if (lower.contains("name") || lower.contains("名称")) return AppSortComparator.SORT_NAME_ASC;
    return null;
  }

  private Integer parseStandbyClockStyle(String value) {
    if (TextUtils.isEmpty(value)) return null;
    Integer numeric = parseInteger(value);
    if (numeric != null) {
      if (numeric >= Config.STANDBY_CLOCK_STYLE_CLASSIC
          && numeric <= Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE) {
        return numeric;
      }
      return Config.STANDBY_CLOCK_STYLE_CLASSIC;
    }
    String lower = normalizeSettingKey(value);
    if (lower.contains("hollow") || lower.contains("outline")
        || lower.contains("镂空") || lower.contains("描边")) {
      return Config.STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW;
    }
    if ((lower.contains("white") || lower.contains("light") || lower.contains("白底"))
        && (lower.contains("flip") || lower.contains("landscape")
        || lower.contains("横屏") || lower.contains("翻页"))) {
      return Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT;
    }
    if (lower.contains("minimal") || lower.contains("simple")
        || lower.contains("borderless") || lower.contains("noborder")
        || lower.contains("极简") || lower.contains("大时钟")
        || lower.contains("大钟") || lower.contains("无边框")) {
      return Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE;
    }
    if (lower.contains("flip") || lower.contains("landscape")
        || lower.contains("card") || lower.contains("横屏")
        || lower.contains("翻页") || lower.contains("数字卡")) {
      return Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE;
    }
    if (lower.contains("classic") || lower.contains("default")
        || lower.contains("经典") || lower.contains("默认")) {
      return Config.STANDBY_CLOCK_STYLE_CLASSIC;
    }
    return null;
  }

  private Integer parseOrientationMode(String value) {
    if (TextUtils.isEmpty(value)) return null;
    Integer numeric = parseInteger(value);
    if (numeric != null) return Math.max(0, Math.min(4, numeric));
    String lower = normalizeSettingKey(value);
    if (lower.contains("reverselandscape") || lower.contains("反向横") || lower.contains("反横")) {
      return Config.ORIENTATION_REVERSE_LANDSCAPE;
    }
    if (lower.contains("reverseportrait") || lower.contains("反向竖") || lower.contains("反竖")) {
      return Config.ORIENTATION_REVERSE_PORTRAIT;
    }
    if (lower.contains("landscape") || lower.contains("横")) return Config.ORIENTATION_LANDSCAPE;
    if (lower.contains("portrait") || lower.contains("竖")) return Config.ORIENTATION_PORTRAIT;
    if (lower.contains("auto") || lower.contains("自动")) return Config.ORIENTATION_AUTO;
    return null;
  }

  private void setWifiTransferEnabled(boolean enabled) {
    if (enabled) {
      if (!WifiFileTransferService.isRunning()) {
        if (WifiFileTransferService.isConnectedToWifi(this)) {
          startService(new Intent(this, WifiFileTransferService.class));
        } else {
          Toast.makeText(this, R.string.toast_need_wifi_connnect, Toast.LENGTH_SHORT).show();
        }
      }
    } else if (WifiFileTransferService.isRunning()) {
      stopService(new Intent(this, WifiFileTransferService.class));
    }
    updateWifiTransferStatus();
  }

  private void updatePermissionValues() {
    setPermissionValue(settingPermissionCameraValue, hasCameraPermission());
    setPermissionValue(settingPermissionStorageValue, hasStoragePermission());
    setPermissionValue(settingPermissionLocationValue, hasLocationPermission());
    setPermissionValue(settingPermissionInstallValue, hasInstallPermission());
    setPermissionValue(settingPermissionUsageValue, hasUsageAccessPermission());
    setPermissionValue(settingPermissionWriteSettingsValue, hasWriteSettingsPermission());
    setPermissionValue(settingPermissionOverlayValue, hasOverlayPermission());
    setPermissionValue(settingPermissionAccessibilityValue, hasAccessibilityPermission());
    setPermissionValue(settingPermissionDeviceAdminValue, hasDeviceAdminPermission());
    setPermissionValue(settingPermissionScreenCaptureValue, hasScreenCapturePermission());
  }

  private void setPermissionValue(TextView view, boolean allowed) {
    if (view != null) view.setText(allowed ? "已允许" : "缺失");
  }

  private void requestFirstMissingPermission() {
    permissionRequestFlowActive = true;
    permissionRequestFlowWaitingSettings = false;
    permissionRequestFlowWaitingResult = false;
    permissionRequestFlowStep = null;
    requestNextMissingPermissionInFlow();
  }

  private void requestNextMissingPermissionInFlow() {
    updatePermissionValues();
    String next = getFirstMissingPermissionStep();
    if (next == null) {
      stopPermissionRequestFlow();
      Toast.makeText(this, "权限已全部允许", Toast.LENGTH_SHORT).show();
      return;
    }
    permissionRequestFlowStep = next;
    requestPermissionStep(next);
  }

  private String getFirstMissingPermissionStep() {
    if (!hasCameraPermission()) return PERMISSION_STEP_CAMERA;
    if (!hasStoragePermission()) return PERMISSION_STEP_STORAGE;
    if (!hasLocationPermission()) return PERMISSION_STEP_LOCATION;
    if (!hasInstallPermission()) return PERMISSION_STEP_INSTALL;
    if (!hasUsageAccessPermission()) return PERMISSION_STEP_USAGE;
    if (!hasWriteSettingsPermission()) return PERMISSION_STEP_WRITE_SETTINGS;
    if (!hasOverlayPermission()) return PERMISSION_STEP_OVERLAY;
    if (!hasAccessibilityPermission()) return PERMISSION_STEP_ACCESSIBILITY;
    if (!hasDeviceAdminPermission()) return PERMISSION_STEP_DEVICE_ADMIN;
    if (!hasScreenCapturePermission()) return PERMISSION_STEP_SCREEN_CAPTURE;
    return null;
  }

  private void requestPermissionStep(String step) {
    if (PERMISSION_STEP_CAMERA.equals(step)) {
      requestCameraPermission();
    } else if (PERMISSION_STEP_STORAGE.equals(step)) {
      requestStoragePermission();
    } else if (PERMISSION_STEP_LOCATION.equals(step)) {
      requestLocationPermission();
    } else if (PERMISSION_STEP_INSTALL.equals(step)) {
      openUnknownSourcesSettings();
    } else if (PERMISSION_STEP_USAGE.equals(step)) {
      openUsageAccessSettings();
    } else if (PERMISSION_STEP_WRITE_SETTINGS.equals(step)) {
      openWriteSettingsPermission();
    } else if (PERMISSION_STEP_OVERLAY.equals(step)) {
      openOverlayPermission();
    } else if (PERMISSION_STEP_ACCESSIBILITY.equals(step)) {
      openAccessibilitySettings();
    } else if (PERMISSION_STEP_DEVICE_ADMIN.equals(step)) {
      requestDeviceAdminPermission();
    } else if (PERMISSION_STEP_SCREEN_CAPTURE.equals(step)) {
      requestScreenCapturePermission();
    }
  }

  private boolean isPermissionStepAllowed(String step) {
    if (PERMISSION_STEP_CAMERA.equals(step)) return hasCameraPermission();
    if (PERMISSION_STEP_STORAGE.equals(step)) return hasStoragePermission();
    if (PERMISSION_STEP_LOCATION.equals(step)) return hasLocationPermission();
    if (PERMISSION_STEP_INSTALL.equals(step)) return hasInstallPermission();
    if (PERMISSION_STEP_USAGE.equals(step)) return hasUsageAccessPermission();
    if (PERMISSION_STEP_WRITE_SETTINGS.equals(step)) return hasWriteSettingsPermission();
    if (PERMISSION_STEP_OVERLAY.equals(step)) return hasOverlayPermission();
    if (PERMISSION_STEP_ACCESSIBILITY.equals(step)) return hasAccessibilityPermission();
    if (PERMISSION_STEP_DEVICE_ADMIN.equals(step)) return hasDeviceAdminPermission();
    if (PERMISSION_STEP_SCREEN_CAPTURE.equals(step)) return hasScreenCapturePermission();
    return true;
  }

  private void handlePermissionFlowResume() {
    if (!permissionRequestFlowActive || !permissionRequestFlowWaitingSettings) return;
    permissionRequestFlowWaitingSettings = false;
    if (isPermissionStepAllowed(permissionRequestFlowStep)) {
      View decor = getWindow().getDecorView();
      if (decor != null) {
        decor.postDelayed(new Runnable() {
          @Override
          public void run() {
            if (permissionRequestFlowActive) requestNextMissingPermissionInFlow();
          }
        }, 250L);
      } else {
        requestNextMissingPermissionInFlow();
      }
    } else {
      stopPermissionRequestFlow();
      Toast.makeText(this, "当前权限未开启，可再次点击继续请求", Toast.LENGTH_LONG).show();
    }
  }

  private void handlePermissionFlowResult() {
    if (!permissionRequestFlowActive || !permissionRequestFlowWaitingResult) return;
    permissionRequestFlowWaitingResult = false;
    if (isPermissionStepAllowed(permissionRequestFlowStep)) {
      requestNextMissingPermissionInFlow();
    } else {
      stopPermissionRequestFlow();
      Toast.makeText(this, "当前权限未开启，可再次点击继续请求", Toast.LENGTH_LONG).show();
    }
  }

  private void stopPermissionRequestFlow() {
    permissionRequestFlowActive = false;
    permissionRequestFlowWaitingSettings = false;
    permissionRequestFlowWaitingResult = false;
    permissionRequestFlowStep = null;
  }

  private boolean hasStoragePermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return Environment.isExternalStorageManager();
    }
    return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
  }

  private boolean hasCameraPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
  }

  private boolean hasLocationPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  private boolean hasInstallPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        || getPackageManager().canRequestPackageInstalls();
  }

  private boolean hasUsageAccessPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return true;
    try {
      AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
      if (appOps == null) return false;
      int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(), getPackageName());
      if (mode == AppOpsManager.MODE_DEFAULT) {
        return getPackageManager().checkPermission(Manifest.permission.PACKAGE_USAGE_STATS,
            getPackageName()) == PackageManager.PERMISSION_GRANTED;
      }
      return mode == AppOpsManager.MODE_ALLOWED;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean hasWriteSettingsPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this);
  }

  private boolean hasOverlayPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
  }

  private boolean hasAccessibilityPermission() {
    return AutoRefreshSettings.isAccessibilityServiceEnabled(this);
  }

  private boolean hasDeviceAdminPermission() {
    return policyManager != null
        && policyManager.isAdminActive(new ComponentName(this, AdminReceiver.class));
  }

  private boolean hasScreenCapturePermission() {
    return ScreenCaptureManager.getInstance().isReady();
  }

  private void requestCameraPermission() {
    if (hasCameraPermission()) {
      Toast.makeText(this, "扫码相机权限已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    List<String> permissions = new ArrayList<>();
    permissions.add(Manifest.permission.CAMERA);
    requestRuntimePermissions(permissions);
  }

  private void requestStoragePermission() {
    if (hasStoragePermission()) {
      Toast.makeText(this, "存储权限已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
          Uri.parse("package:" + getPackageName()));
      startPermissionIntent(intent, "请开启“允许管理所有文件”，用于本地图标、壁纸和文件传输");
      return;
    }
    List<String> permissions = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
    requestRuntimePermissions(permissions);
  }

  private void requestLocationPermission() {
    if (hasLocationPermission()) {
      Toast.makeText(this, "Wi-Fi 定位权限已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    List<String> permissions = new ArrayList<>();
    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    requestRuntimePermissions(permissions);
  }

  private void requestRuntimePermissions(List<String> permissions) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || permissions == null || permissions.isEmpty()) {
      updatePermissionValues();
      return;
    }
    if (permissionRequestFlowActive) {
      permissionRequestFlowWaitingResult = true;
    }
    requestPermissions(permissions.toArray(new String[0]), REQUEST_RUNTIME_PERMISSIONS);
  }

  private void openUnknownSourcesSettings() {
    if (hasInstallPermission()) {
      Toast.makeText(this, "安装更新权限已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + getPackageName()))
        : appDetailsIntent();
    startPermissionIntent(intent, "请允许此应用安装未知来源应用，用于应用内更新");
  }

  private void openUsageAccessSettings() {
    if (hasUsageAccessPermission()) {
      Toast.makeText(this, "使用情况访问已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    startPermissionIntent(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        "请在“使用情况访问”里允许 E-Ink Launcher");
  }

  private void openWriteSettingsPermission() {
    if (hasWriteSettingsPermission()) {
      Toast.makeText(this, "修改系统设置已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ? new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))
        : appDetailsIntent();
    startPermissionIntent(intent, "请允许修改系统设置，用于屏幕方向和刷新相关功能");
  }

  private void openOverlayPermission() {
    if (hasOverlayPermission()) {
      Toast.makeText(this, "悬浮窗权限已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ? new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))
        : appDetailsIntent();
    startPermissionIntent(intent, "请开启悬浮窗权限，用于全局旋转/刷新服务");
  }

  private void openAccessibilitySettings() {
    if (hasAccessibilityPermission()) {
      Toast.makeText(this, "自动刷新辅助服务已开启", Toast.LENGTH_SHORT).show();
      return;
    }
    startPermissionIntent(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        "请在辅助功能中开启 E-Ink 自动刷新服务");
  }

  private void requestDeviceAdminPermission() {
    if (hasDeviceAdminPermission()) {
      Toast.makeText(this, "锁屏管理权限已允许", Toast.LENGTH_SHORT).show();
      return;
    }
    if (permissionRequestFlowActive) {
      permissionRequestFlowWaitingSettings = true;
    }
    Toast.makeText(this, "请激活设备管理器权限，用于一键锁屏", Toast.LENGTH_LONG).show();
    requestDeviceAdmin();
  }

  private void requestScreenCapturePermission() {
    if (hasScreenCapturePermission()) {
      Toast.makeText(this, "透明书封录屏权限已准备", Toast.LENGTH_SHORT).show();
      return;
    }
    if (permissionRequestFlowActive) {
      permissionRequestFlowWaitingResult = true;
    }
    Toast.makeText(this, "请允许屏幕录制，用于待机透明书封", Toast.LENGTH_LONG).show();
    ScreenCaptureManager.getInstance().requestPermission(this, REQUEST_MEDIA_PROJECTION);
  }

  private void startPermissionIntent(Intent intent, String guide) {
    if (permissionRequestFlowActive) {
      permissionRequestFlowWaitingSettings = true;
    }
    Toast.makeText(this, guide, Toast.LENGTH_LONG).show();
    try {
      startActivity(intent);
    } catch (Exception e) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
          && Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION.equals(intent.getAction())) {
        try {
          startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
          return;
        } catch (Exception ignored) {
        }
      }
      try {
        startActivity(appDetailsIntent());
      } catch (Exception ignored) {
      }
    }
  }

  private Intent appDetailsIntent() {
    return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + getPackageName()));
  }

  private void bindOrientationRow(int id, final int mode) {
    bindClick(id, new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        config.setOrientationMode(mode);
        applyLauncherOrientation(true);
        updateDesktopSettingValues();
      }
    });
  }

  private int nextBoundedValue(int current, int min, int max) {
    if (current < min || current >= max) return min;
    return current + 1;
  }

  private void cycleSortMode() {
    String[] sortModes = getResources().getStringArray(R.array.sort_modes);
    if (sortModes.length == 0) return;
    int next = (config.getSortMode() + 1) % sortModes.length;
    if (AppSortComparator.modeNeedsUsageStats(next)
        && !AppSortComparator.hasUsageStatsPermission(this)) {
      Toast.makeText(this, R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
      }
      return;
    }
    config.setSortMode(next);
    onSortModeChanged(next);
    updateDesktopSettingValues();
  }

  private void saveStandbyLockText() {
    if (settingStandbyTextInput == null) return;
    config.setStandbyLockText(settingStandbyTextInput.getText().toString());
    updateStandbyTextValue();
    StandbyWallpaperUpdater.applyLockText(this);
    if (config.isStandbyRefreshEnabled()) {
      StandbyMinuteRefreshReceiver.scheduleRetry(this);
    }
  }

  private void handleWifiTransferToggle() {
    Runnable toggleTransfer = new Runnable() {
      @Override
      public void run() {
        if (!WifiFileTransferService.isRunning()) {
          if (WifiFileTransferService.isConnectedToWifi(Launcher.this)) {
            startService(new Intent(Launcher.this, WifiFileTransferService.class));
          } else {
            Toast.makeText(Launcher.this, R.string.toast_need_wifi_connnect, Toast.LENGTH_SHORT)
                .show();
          }
        } else {
          stopService(new Intent(Launcher.this, WifiFileTransferService.class));
        }
        updateWifiTransferStatus();
      }
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      toggleTransfer.run();
    } else {
      Utils.checkStoragePermission(this, toggleTransfer);
    }
  }

  private void updateWifiTransferStatus() {
    if (settingWifiTransferStatus == null) return;
    if (WifiFileTransferService.isConnectedToWifi(this)) {
      if (WifiFileTransferService.isRunning()) {
        settingWifiTransferStatus.setText("开");
        String address = WifiFileTransferService.getAddress(this);
        if (address != null && settingWifiTransferAddress != null) {
          settingWifiTransferAddress.setText(address);
          settingWifiTransferAddress.setVisibility(View.VISIBLE);
          Bitmap qr = createQrCode(address);
          if (qr != null && settingWifiTransferQr != null) {
            settingWifiTransferQr.setImageBitmap(qr);
            settingWifiTransferQr.setVisibility(View.VISIBLE);
          }
        }
      } else {
        settingWifiTransferStatus.setText("关");
        hideWifiTransferDetails();
      }
    } else {
      settingWifiTransferStatus.setText("请连 Wi-Fi");
      hideWifiTransferDetails();
    }
  }

  private void hideWifiTransferDetails() {
    if (settingWifiTransferAddress != null) {
      settingWifiTransferAddress.setVisibility(View.GONE);
    }
    if (settingWifiTransferQr != null) {
      settingWifiTransferQr.setVisibility(View.GONE);
    }
  }

  private Bitmap createQrCode(String content) {
    int size = Utils.dp2Px(this, 160);
    Map<EncodeHintType, Object> hints = new HashMap<>();
    hints.put(EncodeHintType.MARGIN, 1);
    try {
      BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
          hints);
      Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
      for (int x = 0; x < size; x++) {
        for (int y = 0; y < size; y++) {
          bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
        }
      }
      return bitmap;
    } catch (WriterException e) {
      return null;
    }
  }

  private void updateAutoRefreshStatus() {
    if (settingAutoRefreshStatus != null) {
      if (!AutoRefreshSettings.isAccessibilityServiceEnabled(this)) {
        settingAutoRefreshStatus.setText("未授权");
      } else {
        settingAutoRefreshStatus.setText(AutoRefreshSettings.isEnabled(this) ? "开" : "关");
      }
    }
    if (settingAutoRefreshTapStatus != null) {
      if (!AutoRefreshSettings.isAccessibilityServiceEnabled(this)) {
        settingAutoRefreshTapStatus.setText("未授权");
      } else {
        settingAutoRefreshTapStatus.setText(AutoRefreshSettings.isTapEnabled(this) ? "开" : "关");
      }
    }
    if (settingAutoRefreshIntervalText != null) {
      settingAutoRefreshIntervalText.setText(getString(R.string.setting_auto_refresh_interval,
          AutoRefreshSettings.getInterval(this)));
    }
    if (settingAutoRefreshTapIntervalText != null) {
      settingAutoRefreshTapIntervalText.setText(getString(R.string.setting_auto_refresh_tap_interval,
          AutoRefreshSettings.getTapInterval(this)));
    }
    if (settingAutoRefreshKeycodeText != null) {
      settingAutoRefreshKeycodeText.setText(getString(R.string.setting_auto_refresh_keycode,
          AutoRefreshSettings.getRefreshKeyCode(this)));
    }
    if (settingAutoRefreshDelayText != null) {
      settingAutoRefreshDelayText.setText(getString(R.string.setting_auto_refresh_delay,
          AutoRefreshSettings.getDelay(this)));
    }
    if (settingAutoRefreshSosFrameRefreshValue != null) {
      settingAutoRefreshSosFrameRefreshValue.setText(
          AutoRefreshSettings.isSosFrameRefreshEnabled(this) ? "开" : "关");
    }
    if (settingAutoRefreshShakeRefreshValue != null) {
      int mode = AutoRefreshSettings.getShakeActionMode(this);
      if (!AutoRefreshSettings.hasMotionSensor(this)) {
        settingAutoRefreshShakeRefreshValue.setText("不支持");
      } else if (mode != AutoRefreshSettings.SHAKE_ACTION_OFF
          && !AutoRefreshSettings.isAccessibilityServiceEnabled(this)) {
        settingAutoRefreshShakeRefreshValue.setText(
            getShakeActionLabel(mode) + " · 未授权");
      } else {
        settingAutoRefreshShakeRefreshValue.setText(getShakeActionLabel(mode));
      }
    }
    if (settingAutoRefreshShakeCountValue != null) {
      settingAutoRefreshShakeCountValue.setText(
          AutoRefreshSettings.getShakeRefreshCount(this) + " 次");
    }
    if (settingAutoRefreshShakeAmplitudeValue != null) {
      settingAutoRefreshShakeAmplitudeValue.setText(getShakeAmplitudeLabel());
    }
  }

  private String getShakeAmplitudeLabel() {
    switch (AutoRefreshSettings.getShakeRefreshAmplitude(this)) {
      case AutoRefreshSettings.SHAKE_AMPLITUDE_SMALL:
        return getString(R.string.setting_shake_amplitude_small);
      case AutoRefreshSettings.SHAKE_AMPLITUDE_LARGE:
        return getString(R.string.setting_shake_amplitude_large);
      default:
        return getString(R.string.setting_shake_amplitude_medium);
    }
  }

  private String getShakeActionLabel(int mode) {
    if (mode == AutoRefreshSettings.SHAKE_ACTION_REFRESH) {
      return getString(R.string.setting_shake_mode_refresh);
    }
    if (mode == AutoRefreshSettings.SHAKE_ACTION_PAGE_TURN) {
      return getString(R.string.setting_shake_mode_page_turn);
    }
    return getString(R.string.setting_shake_mode_off);
  }

  private void updateReadingSyncValues() {
    if (config == null) return;
    boolean hasKey = !TextUtils.isEmpty(config.getWereadApiKey());
    if (settingWereadSyncValue != null) {
      if (!config.isWereadSyncEnabled()) {
        settingWereadSyncValue.setText("关");
      } else {
        settingWereadSyncValue.setText(hasKey ? "开" : "缺 Key");
      }
    }
    if (settingWereadApiKeyValue != null) {
      settingWereadApiKeyValue.setText(hasKey ? "已配置" : "未配置");
    }
    if (settingWereadSyncIntervalValue != null) {
      settingWereadSyncIntervalValue.setText(
          getWereadSyncIntervalLabel(config.getWereadSyncIntervalMinutes()));
    }
  }

  private void showWereadApiKeyDialog() {
    final EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    input.setText(config.getWereadApiKey());
    input.setSelectAllOnFocus(true);
    int padding = Utils.dp2Px(this, 20);
    input.setPadding(padding, 0, padding, 0);

    new AlertDialog.Builder(this)
        .setTitle("微信读书 Skill Key")
        .setView(input)
        .setPositiveButton("保存", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            config.setWereadApiKey(input.getText().toString());
            if (!TextUtils.isEmpty(config.getWereadApiKey())) {
              config.setWereadSyncEnabled(true);
            }
            cn.modificator.launcher.reading.ReadingProgressRepository.clearRemoteCache(Launcher.this);
            updateReadingSyncValues();
            refreshReadingProgress();
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private String getWereadSyncIntervalLabel(int minutes) {
    if (minutes >= 60 && minutes % 60 == 0) {
      return (minutes / 60) + "小时";
    }
    return minutes + "分钟";
  }

  private void refreshReadingProgress() {
    if (legadoHomeController != null) {
      legadoHomeController.refresh(this);
    }
  }

  private void updateUpdateStatus(String status) {
    if (settingUpdateCurrentVersion != null) {
      String versionName = AppUpdateClient.getCurrentVersionName(this);
      int versionCode = AppUpdateClient.getCurrentVersionCode(this);
      settingUpdateCurrentVersion.setText(versionName + "(" + versionCode + ")");
    }
    if (settingUpdateStatus != null) {
      settingUpdateStatus.setText(status);
    }
  }

  private void checkAndInstallUpdate() {
    if (updateBusy) return;
    updateBusy = true;
    updateUpdateStatus("正在检查更新...");
    if (settingUpdateNotes != null) {
      settingUpdateNotes.setText("");
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          final AppUpdateClient.UpdateInfo info = AppUpdateClient.fetchUpdateInfo();
          int currentVersion = AppUpdateClient.getCurrentVersionCode(Launcher.this);
          if (info.versionCode <= currentVersion) {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                updateBusy = false;
                updateUpdateStatus("已是最新版");
                if (settingUpdateNotes != null && !TextUtils.isEmpty(info.notes)) {
                  settingUpdateNotes.setText(info.notes);
                }
              }
            });
            return;
          }
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateUpdateStatus("发现新版本 " + info.versionName + "，开始下载...");
              if (settingUpdateNotes != null) {
                settingUpdateNotes.setText(info.notes);
              }
            }
          });
          final File apk = AppUpdateClient.downloadApk(Launcher.this,
              info, new AppUpdateClient.ProgressListener() {
                @Override
                public void onProgress(final int percent) {
                  runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                      updateUpdateStatus("正在下载 " + percent + "%");
                    }
                  });
                }
              });
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateBusy = false;
              installDownloadedUpdate(apk);
            }
          });
        } catch (final Exception e) {
          Log.e("EInkLauncher", "更新检查失败", e);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateBusy = false;
              updateUpdateStatus("更新失败：" + e.getMessage());
            }
          });
        }
      }
    }, "AppUpdateCheck").start();
  }

  private void installDownloadedUpdate(File apk) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        && !getPackageManager().canRequestPackageInstalls()) {
      updateUpdateStatus("请允许安装未知应用后重试");
      try {
        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + getPackageName())));
      } catch (Exception e) {
        Toast.makeText(this, "无法打开安装授权设置", Toast.LENGTH_SHORT).show();
      }
      return;
    }
    try {
      Uri uri;
      Intent intent = new Intent(Intent.ACTION_VIEW);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        uri = FileProvider.getUriForFile(this, getPackageName() + ".fileProvider", apk);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } else {
        uri = Uri.fromFile(apk);
      }
      intent.setDataAndType(uri, "application/vnd.android.package-archive");
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      updateUpdateStatus("已下载，等待安装确认");
      startActivity(intent);
    } catch (Exception e) {
      updateUpdateStatus("无法启动安装器：" + e.getMessage());
    }
  }

  // =========================================================================
  // SettingFragment.OnSettingChangeListener 实现
  // =========================================================================

  @Override
  public void onRowNumChanged(int rowNum) {
    config.setRowNum(isLandscapeOrientation(), rowNum);
    applyCurrentGridSize();
  }

  @Override
  public void onColNumChanged(int colNum) {
    config.setColNum(isLandscapeOrientation(), colNum);
    applyCurrentGridSize();
  }

  @Override
  public void onFontSizeChanged(float size) {
    adapter.setFontSize(size);
  }

  @Override
  public void onAppNameLinesChanged(int lines) {
    adapter.setAppNameLines(lines);
  }

  @Override
  public void onHideDividerChanged(boolean hide) {
    launcherView.setHideDivider(hide);
  }

  @Override
  public void onShowStatusBarChanged(boolean show) {
    applyStatusBarVisibility();
  }

  @Override
  public void onShowCustomIconChanged(boolean show) {
    iconCache.markDirty();
    refreshIcons();
    updateDesktopSettingValues();
    checkIconThemeUpdates(true);
  }

  @Override
  public void onEnterManageMode() {
    binder.setDelete(true);
    dataCenter.setDrawerMode(true);
    dataCenter.refreshAppList(true);
    findViewById(R.id.deleteFinish).setVisibility(View.VISIBLE);
  }

  private void exitManageMode(boolean refresh) {
    View deleteFinish = findViewById(R.id.deleteFinish);
    if (binder == null || !binder.isDelete()) {
      if (deleteFinish != null) deleteFinish.setVisibility(View.GONE);
      return;
    }
    binder.setDelete(false);
    if (refresh && dataCenter != null) {
      dataCenter.refreshAppList(false);
    } else if (adapter != null) {
      adapter.refreshDisplay();
    }
    if (deleteFinish != null) deleteFinish.setVisibility(View.GONE);
  }

  @Override
  public void onSortModeChanged(int mode) {
    dataCenter.setSortMode(mode);
    dataCenter.refreshAppList(binder.isDelete());
  }

  // =========================================================================
  // 布局更新
  // =========================================================================

  private void refreshIcons() {
    if (adapter == null || iconCache == null) return;
    iconCache.refreshIcons(config.getIconThemeId());
    adapter.refreshDisplay();
  }

  private void ensureFavoriteAppsSeeded() {
    if (config == null || dataCenter == null || !config.getFavoriteApps().isEmpty()) return;
    List<String> defaults = dataCenter.getDefaultFavoritePackages(5);
    if (defaults.isEmpty()) return;
    config.setFavoriteApps(defaults);
    dataCenter.setFavoriteApps(defaults);
  }

  private void showNextSectionOrPage() {
    if (currentSection == SECTION_APPS) {
      showSection(SECTION_DRAWER);
      return;
    }
    if (currentSection == SECTION_DRAWER
        && dataCenter.canShowNextPage()) {
      dataCenter.showNextPage();
      updateSectionChrome();
      return;
    }
    if (currentSection == SECTION_READING && legadoHomeController != null
        && legadoHomeController.canShowNextPage(this)) {
      legadoHomeController.showNextPage(this);
      updateSectionChrome();
      return;
    }
    if (currentSection == SECTION_SETTINGS && settingsPageIndex < settingsPageCount - 1) {
      settingsPageIndex++;
      showSettingsPage();
      return;
    }
    if (currentSection == SECTION_DRAWER) {
      showSection(SECTION_READING);
    } else if (currentSection == SECTION_READING) {
      showSection(SECTION_SETTINGS);
    } else {
      dataCenter.showFirstPage();
      showSection(SECTION_APPS);
    }
  }

  private void showPreviousSectionOrPage() {
    if (currentSection == SECTION_DRAWER
        && dataCenter.canShowLastPage()) {
      dataCenter.showLastPage();
      updateSectionChrome();
      return;
    }
    if (currentSection == SECTION_READING && legadoHomeController != null
        && legadoHomeController.canShowPreviousPage()) {
      legadoHomeController.showPreviousPage(this);
      updateSectionChrome();
      return;
    }
    if (currentSection == SECTION_SETTINGS && settingsPageIndex > 0) {
      settingsPageIndex--;
      showSettingsPage();
      return;
    }
    if (currentSection == SECTION_SETTINGS && settingsSubPage != SETTINGS_SUB_HOME) {
      showPreviousSettingsLevel();
      return;
    }
    if (currentSection == SECTION_APPS) {
      showSection(SECTION_SETTINGS);
    } else if (currentSection == SECTION_DRAWER) {
      showSection(SECTION_APPS);
    } else if (currentSection == SECTION_READING) {
      dataCenter.showFinalPage();
      showSection(SECTION_DRAWER);
    } else {
      showSection(SECTION_READING);
    }
  }

  private void showSection(int section) {
    currentSection = section;
    if (section == SECTION_APPS || section == SECTION_DRAWER) {
      dataCenter.setDrawerMode(section == SECTION_DRAWER);
      applyCurrentGridSize();
    }
    boolean appSection = section == SECTION_APPS || section == SECTION_DRAWER;
    if (appsPage != null) appsPage.setVisibility(appSection ? View.VISIBLE : View.GONE);
    if (readingPage != null) readingPage.setVisibility(section == SECTION_READING ? View.VISIBLE : View.GONE);
    if (settingsPage != null) settingsPage.setVisibility(section == SECTION_SETTINGS ? View.VISIBLE : View.GONE);
    updateAppsHeader(section);
    if (section == SECTION_SETTINGS) {
      showSettingsHome();
    }
    updateSectionChrome();
    if (section == SECTION_READING && legadoHomeController != null) {
      legadoHomeController.refresh(this);
    }
  }

  private void updateAppsHeader(int section) {
    boolean drawer = section == SECTION_DRAWER;
    if (textClock != null) textClock.setVisibility(drawer ? View.GONE : View.VISIBLE);
    if (dateClock != null) dateClock.setVisibility(drawer ? View.GONE : View.VISIBLE);
    if (lunarClock != null) lunarClock.setVisibility(drawer ? View.GONE : View.VISIBLE);
    if (appsSectionTitle != null) {
      appsSectionTitle.setVisibility(drawer ? View.VISIBLE : View.GONE);
      appsSectionTitle.setText(drawer ? "全部应用" : "");
    }
    View homeReadingCard = findViewById(R.id.homeReadingCard);
    if (drawer) {
      if (homeReadingCard != null) homeReadingCard.setVisibility(View.GONE);
    } else if (legadoHomeController != null) {
      legadoHomeController.renderHomeCard(this);
    }
  }

  private void showSettingsPage() {
    if (settingsHomePage != null) {
      settingsHomePage.setVisibility(settingsSubPage == SETTINGS_SUB_HOME ? View.VISIBLE : View.GONE);
    }
    if (settingsDesktopPage != null) {
      settingsDesktopPage.setVisibility(isDesktopSettingsSubPage() ? View.VISIBLE : View.GONE);
    }
    if (settingsTransferPage != null) {
      settingsTransferPage.setVisibility(settingsSubPage == SETTINGS_SUB_TRANSFER ? View.VISIBLE : View.GONE);
    }
    if (settingsAutoRefreshPage != null) {
      settingsAutoRefreshPage.setVisibility(settingsSubPage == SETTINGS_SUB_AUTO_REFRESH ? View.VISIBLE : View.GONE);
    }
    if (settingsUpdatePage != null) {
      settingsUpdatePage.setVisibility(settingsSubPage == SETTINGS_SUB_UPDATE ? View.VISIBLE : View.GONE);
    }
    if (settingsIconThemePage != null) {
      settingsIconThemePage.setVisibility(
          settingsSubPage == SETTINGS_SUB_ICON_THEME ? View.VISIBLE : View.GONE);
    }
    if (settingsPermissionPage != null) {
      settingsPermissionPage.setVisibility(
          settingsSubPage == SETTINGS_SUB_PERMISSIONS ? View.VISIBLE : View.GONE);
    }
    if (settingsReadingPage != null) {
      settingsReadingPage.setVisibility(
          settingsSubPage == SETTINGS_SUB_READING ? View.VISIBLE : View.GONE);
    }
    hideAllSettingsRows();
    List<View> rows = collectVisibleSettingsRows(settingsRows);
    if (rows.isEmpty()) {
      settingsPageCount = 1;
      settingsPageIndex = 0;
      updateSettingsTitle();
      updateSectionChrome();
      return;
    }
    int capacity = calculateSettingsRowsCapacity(rows);
    settingsPageCount = Math.max(1, (rows.size() + capacity - 1) / capacity);
    int perPage = Math.max(1, (rows.size() + settingsPageCount - 1) / settingsPageCount);
    if (settingsPageIndex >= settingsPageCount) settingsPageIndex = settingsPageCount - 1;
    if (settingsPageIndex < 0) settingsPageIndex = 0;

    int start = settingsPageIndex * perPage;
    int end = Math.min(start + perPage, rows.size());
    for (int i = start; i < end; i++) {
      rows.get(i).setVisibility(View.VISIBLE);
    }
    updateSettingsTitle();
    updateSectionChrome();
    if (settingsPage != null && settingsPage.getHeight() <= 0) {
      settingsPage.post(new Runnable() {
        @Override
        public void run() {
          if (currentSection == SECTION_SETTINGS) {
            showSettingsPage();
          }
        }
      });
    }
  }

  private int calculateSettingsRowsCapacity(List<View> rows) {
    int availableHeight = settingsPage != null ? settingsPage.getHeight() : 0;
    if (availableHeight <= 0 && getWindow() != null && getWindow().getDecorView() != null) {
      availableHeight = getWindow().getDecorView().getHeight();
    }
    if (availableHeight <= 0) {
      availableHeight = getResources().getDisplayMetrics().heightPixels;
    }

    int usedHeight = viewBlockHeight(getSettingsTitleView(), 36);
    int bottomPadding = dp(10);
    int count = 0;
    for (View row : rows) {
      int rowHeight = viewBlockHeight(row, 54);
      if (count > 0 && usedHeight + rowHeight > availableHeight - bottomPadding) break;
      usedHeight += rowHeight;
      count++;
    }
    return Math.max(1, count);
  }

  private int viewBlockHeight(View view, int fallbackDp) {
    ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
    int height = view == null ? 0 : view.getHeight();
    if (height <= 0 && params != null && params.height > 0) {
      height = params.height;
    }
    if (height <= 0) {
      height = dp(fallbackDp);
    }
    int margin = 0;
    if (params instanceof ViewGroup.MarginLayoutParams) {
      ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
      margin = marginParams.topMargin + marginParams.bottomMargin;
    }
    return height + margin;
  }

  private boolean isDesktopSettingsSubPage() {
    return settingsSubPage == SETTINGS_SUB_DESKTOP
        || settingsSubPage == SETTINGS_SUB_LAYOUT
        || settingsSubPage == SETTINGS_SUB_DISPLAY
        || settingsSubPage == SETTINGS_SUB_ICON
        || settingsSubPage == SETTINGS_SUB_ORIENTATION
        || settingsSubPage == SETTINGS_SUB_STANDBY
        || settingsSubPage == SETTINGS_SUB_STANDBY_CLOCK_STYLE
        || settingsSubPage == SETTINGS_SUB_STANDBY_RANDOM_WALLPAPER_INTERVAL;
  }

  private List<View> collectVisibleSettingsRows(View[] rows) {
    List<View> visibleRows = new ArrayList<>();
    if (rows == null) return visibleRows;
    for (View row : rows) {
      if (row != null && !visibleRows.contains(row)) visibleRows.add(row);
    }
    return visibleRows;
  }

  private void hideAllSettingsRows() {
    hideSettingsRows(settingsHomeRows);
    hideSettingsRows(settingsDesktopRows);
    hideSettingsRows(settingsLayoutRows);
    hideSettingsRows(settingsDisplayRows);
    hideSettingsRows(settingsIconRows);
    hideSettingsRows(settingsOrientationRows);
    hideSettingsRows(settingsStandbyRows);
    hideSettingsRows(settingsStandbyClockStyleRows);
    hideSettingsRows(settingsStandbyRandomWallpaperIntervalRows);
    hideSettingsRows(settingsTransferRows);
    hideSettingsRows(settingsAutoRefreshRows);
    hideSettingsRows(settingsUpdateRows);
    hideSettingsRows(settingsIconThemeRows);
    hideSettingsRows(settingsPermissionRows);
    hideSettingsRows(settingsReadingRows);
  }

  private void hideSettingsRows(View[] rows) {
    if (rows == null) return;
    for (View row : rows) {
      if (row != null) row.setVisibility(View.GONE);
    }
  }

  private void updateSettingsTitle() {
    TextView title = getSettingsTitleView();
    if (title != null) {
      String text = getSettingsTitleText();
      if (settingsPageCount > 1) {
        text += " " + (settingsPageIndex + 1) + "/" + settingsPageCount;
      }
      title.setText(text);
    }
    updateSettingsBackLabels();
  }

  private void updateSettingsBackLabels() {
    if (settingDesktopBackLabel != null) {
      String label;
      if (settingsSubPage == SETTINGS_SUB_DESKTOP) {
        label = "返回设置";
      } else if (settingsSubPage == SETTINGS_SUB_STANDBY_CLOCK_STYLE
          || settingsSubPage == SETTINGS_SUB_STANDBY_RANDOM_WALLPAPER_INTERVAL) {
        label = "返回待机锁屏";
      } else {
        label = "返回桌面与外观";
      }
      settingDesktopBackLabel.setText(label);
    }
    if (settingIconThemeBackLabel != null) {
      settingIconThemeBackLabel.setText("返回图标设置");
    }
  }

  private TextView getSettingsTitleView() {
    if (settingsSubPage == SETTINGS_SUB_HOME) return settingsHomeTitle;
    if (settingsSubPage == SETTINGS_SUB_TRANSFER) return settingsTransferTitle;
    if (settingsSubPage == SETTINGS_SUB_AUTO_REFRESH) return settingsAutoRefreshTitle;
    if (settingsSubPage == SETTINGS_SUB_UPDATE) return settingsUpdateTitle;
    if (settingsSubPage == SETTINGS_SUB_ICON_THEME) return settingsIconThemeTitle;
    if (settingsSubPage == SETTINGS_SUB_PERMISSIONS) return settingsPermissionTitle;
    if (settingsSubPage == SETTINGS_SUB_READING) return settingsReadingTitle;
    if (isDesktopSettingsSubPage()) return settingsDesktopTitle;
    return null;
  }

  private String getSettingsTitleText() {
    switch (settingsSubPage) {
      case SETTINGS_SUB_DESKTOP:
        return "设置 / 桌面与外观";
      case SETTINGS_SUB_LAYOUT:
        return "设置 / 布局";
      case SETTINGS_SUB_DISPLAY:
        return "设置 / 显示";
      case SETTINGS_SUB_ICON:
        return "设置 / 图标";
      case SETTINGS_SUB_ORIENTATION:
        return "设置 / 屏幕方向";
      case SETTINGS_SUB_STANDBY:
        return "设置 / 待机锁屏";
      case SETTINGS_SUB_STANDBY_CLOCK_STYLE:
        return "设置 / 熄屏时钟样式";
      case SETTINGS_SUB_STANDBY_RANDOM_WALLPAPER_INTERVAL:
        return "设置 / 随机壁纸刷新";
      case SETTINGS_SUB_TRANSFER:
        return "设置 / 文件传输";
      case SETTINGS_SUB_AUTO_REFRESH:
        return "设置 / 自动刷新";
      case SETTINGS_SUB_UPDATE:
        return "设置 / 联网更新";
      case SETTINGS_SUB_ICON_THEME:
        return "设置 / 图标风格";
      case SETTINGS_SUB_PERMISSIONS:
        return "设置 / 权限检查";
      case SETTINGS_SUB_READING:
        return "设置 / 阅读同步";
      default:
        return "设置";
    }
  }

  private void showSettingsHome() {
    settingsSubPage = SETTINGS_SUB_HOME;
    settingsRows = settingsHomeRows;
    settingsPageIndex = 0;
    showSettingsPage();
  }

  private void showDesktopSettings() {
    settingsSubPage = SETTINGS_SUB_DESKTOP;
    settingsRows = settingsDesktopRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showLayoutSettings() {
    settingsSubPage = SETTINGS_SUB_LAYOUT;
    settingsRows = settingsLayoutRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showDisplaySettings() {
    settingsSubPage = SETTINGS_SUB_DISPLAY;
    settingsRows = settingsDisplayRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showIconSettings() {
    settingsSubPage = SETTINGS_SUB_ICON;
    settingsRows = settingsIconRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showOrientationSettings() {
    settingsSubPage = SETTINGS_SUB_ORIENTATION;
    settingsRows = settingsOrientationRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showStandbySettings() {
    settingsSubPage = SETTINGS_SUB_STANDBY;
    settingsRows = settingsStandbyRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showStandbyClockStyleSettings() {
    settingsSubPage = SETTINGS_SUB_STANDBY_CLOCK_STYLE;
    settingsRows = settingsStandbyClockStyleRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showStandbyRandomWallpaperIntervalSettings() {
    settingsSubPage = SETTINGS_SUB_STANDBY_RANDOM_WALLPAPER_INTERVAL;
    settingsRows = settingsStandbyRandomWallpaperIntervalRows;
    settingsPageIndex = 0;
    updateDesktopSettingValues();
    showSettingsPage();
  }

  private void showPreviousSettingsLevel() {
    if (settingsSubPage == SETTINGS_SUB_STANDBY_CLOCK_STYLE
        || settingsSubPage == SETTINGS_SUB_STANDBY_RANDOM_WALLPAPER_INTERVAL) {
      showStandbySettings();
    } else if (settingsSubPage == SETTINGS_SUB_LAYOUT
        || settingsSubPage == SETTINGS_SUB_DISPLAY
        || settingsSubPage == SETTINGS_SUB_ICON
        || settingsSubPage == SETTINGS_SUB_ORIENTATION
        || settingsSubPage == SETTINGS_SUB_STANDBY) {
      showDesktopSettings();
    } else if (settingsSubPage == SETTINGS_SUB_ICON_THEME) {
      showIconSettings();
    } else {
      showSettingsHome();
    }
  }

  private void showTransferSettings() {
    settingsSubPage = SETTINGS_SUB_TRANSFER;
    settingsRows = settingsTransferRows;
    settingsPageIndex = 0;
    updateWifiTransferStatus();
    showSettingsPage();
  }

  private void showAutoRefreshSettings() {
    settingsSubPage = SETTINGS_SUB_AUTO_REFRESH;
    settingsRows = settingsAutoRefreshRows;
    settingsPageIndex = 0;
    updateAutoRefreshStatus();
    showSettingsPage();
  }

  private void showUpdateSettings() {
    settingsSubPage = SETTINGS_SUB_UPDATE;
    settingsRows = settingsUpdateRows;
    settingsPageIndex = 0;
    updateUpdateStatus("未检查");
    showSettingsPage();
  }

  private void showIconThemeSettings() {
    settingsSubPage = SETTINGS_SUB_ICON_THEME;
    buildIconThemeRows();
    settingsRows = settingsIconThemeRows;
    settingsPageIndex = 0;
    showSettingsPage();
  }

  private void showPermissionSettings() {
    settingsSubPage = SETTINGS_SUB_PERMISSIONS;
    settingsRows = settingsPermissionRows;
    settingsPageIndex = 0;
    updatePermissionValues();
    showSettingsPage();
  }

  private void showReadingSettings() {
    settingsSubPage = SETTINGS_SUB_READING;
    settingsRows = settingsReadingRows;
    settingsPageIndex = 0;
    updateReadingSyncValues();
    showSettingsPage();
  }

  private void updateSectionChrome() {
    if (navAppsText != null) navAppsText.setText(currentSection == SECTION_APPS ? "━" : "");
    if (navDrawerText != null) navDrawerText.setText(currentSection == SECTION_DRAWER ? "━" : "");
    if (navReadingText != null) navReadingText.setText(currentSection == SECTION_READING ? "━" : "");
    if (navSettingsText != null) navSettingsText.setText(currentSection == SECTION_SETTINGS ? "━" : "");
    if (pageIndicator == null) return;
    if (currentSection == SECTION_APPS || currentSection == SECTION_DRAWER) {
      pageIndicator.setPages(dataCenter.getPageTotal(), dataCenter.getPageIndex());
    } else if (currentSection == SECTION_READING) {
      if (legadoHomeController != null) {
        pageIndicator.setPages(legadoHomeController.getPageCount(this),
            legadoHomeController.getPageIndex(this));
      } else {
        pageIndicator.setPages(1, 0);
      }
    } else {
      pageIndicator.setPages(settingsPageCount, settingsPageIndex);
    }
  }

  private File applyDesktopWallpaper() {
    return applyDesktopWallpaper(false);
  }

  private File applyDesktopWallpaper(boolean forceRefresh) {
    File file = ensureDesktopWallpaperFile();
    if (desktopWallpaper == null) {
      updateDesktopWallpaperValue(file);
      return file;
    }
    if (file == null) {
      desktopWallpaper.setImageDrawable(null);
      clearDesktopWallpaperCache();
      updateDesktopWallpaperValue(null);
      return null;
    }
    Bitmap bitmap = getCachedDesktopWallpaper(file, forceRefresh);
    if (bitmap == null) {
      desktopWallpaper.setImageDrawable(null);
      updateDesktopWallpaperValue(null);
      return null;
    }
    desktopWallpaper.setImageBitmap(bitmap);
    updateDesktopWallpaperValue(file);
    return file;
  }

  private void showDesktopWallpaperDialog() {
    File file = applyDesktopWallpaper();
    String current = file == null ? "未找到图片" : file.getName();
    new AlertDialog.Builder(this)
        .setTitle("整页壁纸")
        .setMessage("目录:\n" + config.getDesktopWallpaperDir() + "\n\n当前:\n" + current)
        .setPositiveButton("刷新", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            File refreshed = applyDesktopWallpaper(true);
            Toast.makeText(Launcher.this,
                refreshed == null ? "未找到桌面壁纸" : "已刷新桌面壁纸",
                Toast.LENGTH_SHORT).show();
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private File ensureDesktopWallpaperFile() {
    File dir = new File(config.getDesktopWallpaperDir());
    if (!dir.exists() && !dir.mkdirs()) {
      Log.w("EInkLauncher", "Unable to create desktop wallpaper dir: " + dir);
      return null;
    }
    if (!dir.isDirectory()) return null;

    File sample = new File(dir, "desktop-wallpaper.png");
    File file = findDesktopWallpaperFile(dir, sample);
    if (file != null) return file;

    if (createDefaultDesktopWallpaper(sample)) {
      return sample;
    }
    return null;
  }

  private boolean createDefaultDesktopWallpaper(File file) {
    if (file == null) return false;
    Bitmap bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.RGB_565);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);

    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file);
      return bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    } catch (Exception e) {
      Log.w("EInkLauncher", "Unable to create default desktop wallpaper", e);
      return false;
    } finally {
      if (out != null) {
        try {
          out.close();
        } catch (IOException ignored) {
        }
      }
      bitmap.recycle();
    }
  }

  private Bitmap decodeDesktopWallpaper(File file) {
    if (file == null || !file.isFile()) return null;
    int targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
    int targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inPreferredConfig = Bitmap.Config.RGB_565;
    opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight,
        targetWidth, targetHeight);
    try {
      return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    } catch (Exception e) {
      Log.w("EInkLauncher", "Unable to decode desktop wallpaper: " + file, e);
      return null;
    }
  }

  private Bitmap getCachedDesktopWallpaper(File file, boolean forceRefresh) {
    if (file == null || !file.isFile()) return null;
    if (forceRefresh || !isDesktopWallpaperCacheValid(file)) {
      if (desktopWallpaper != null) desktopWallpaper.setImageDrawable(null);
      clearDesktopWallpaperCache();
      Bitmap portrait = decodeDesktopWallpaper(file);
      if (portrait == null) return null;
      desktopWallpaperCachePath = file.getAbsolutePath();
      desktopWallpaperCacheModified = file.lastModified();
      desktopWallpaperCacheLength = file.length();
      desktopWallpaperCachePortrait = portrait;
      desktopWallpaperCacheLandscape = createRotatedDesktopWallpaper(portrait, 90);
      desktopWallpaperCacheReverseLandscape = createRotatedDesktopWallpaper(portrait, 270);
    }

    int degrees = getDesktopWallpaperRotationDegrees();
    if (degrees == 90 && isUsableBitmap(desktopWallpaperCacheLandscape)) {
      return desktopWallpaperCacheLandscape;
    }
    if (degrees == 270 && isUsableBitmap(desktopWallpaperCacheReverseLandscape)) {
      return desktopWallpaperCacheReverseLandscape;
    }
    return isUsableBitmap(desktopWallpaperCachePortrait) ? desktopWallpaperCachePortrait : null;
  }

  private boolean isDesktopWallpaperCacheValid(File file) {
    return file != null
        && desktopWallpaperCachePath != null
        && desktopWallpaperCachePath.equals(file.getAbsolutePath())
        && desktopWallpaperCacheModified == file.lastModified()
        && desktopWallpaperCacheLength == file.length()
        && isUsableBitmap(desktopWallpaperCachePortrait);
  }

  private Bitmap createRotatedDesktopWallpaper(Bitmap bitmap, int degrees) {
    if (bitmap == null || degrees == 0) return bitmap;
    Matrix matrix = new Matrix();
    matrix.setRotate(degrees);
    try {
      return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
          true);
    } catch (Exception e) {
      Log.w("EInkLauncher", "Unable to rotate desktop wallpaper", e);
      return null;
    }
  }

  private boolean isUsableBitmap(Bitmap bitmap) {
    return bitmap != null && !bitmap.isRecycled();
  }

  private void clearDesktopWallpaperCache() {
    Bitmap portrait = desktopWallpaperCachePortrait;
    Bitmap landscape = desktopWallpaperCacheLandscape;
    Bitmap reverseLandscape = desktopWallpaperCacheReverseLandscape;
    desktopWallpaperCachePath = null;
    desktopWallpaperCacheModified = 0L;
    desktopWallpaperCacheLength = 0L;
    desktopWallpaperCachePortrait = null;
    desktopWallpaperCacheLandscape = null;
    desktopWallpaperCacheReverseLandscape = null;
    recycleDesktopWallpaperCacheBitmap(portrait, null, null);
    recycleDesktopWallpaperCacheBitmap(landscape, portrait, null);
    recycleDesktopWallpaperCacheBitmap(reverseLandscape, portrait, landscape);
  }

  private void recycleDesktopWallpaperCacheBitmap(Bitmap bitmap, Bitmap recycled1,
      Bitmap recycled2) {
    if (bitmap == null || bitmap.isRecycled()) return;
    if (bitmap == recycled1 || bitmap == recycled2) return;
    bitmap.recycle();
  }

  private int getDesktopWallpaperRotationDegrees() {
    if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
      return 0;
    }
    int mode = config.getOrientationMode();
    if (mode == Config.ORIENTATION_LANDSCAPE) return 90;
    if (mode == Config.ORIENTATION_REVERSE_LANDSCAPE) return 270;
    try {
      int rotation = getWindowManager().getDefaultDisplay().getRotation();
      if (rotation == Surface.ROTATION_270) return 270;
    } catch (Exception ignored) {
    }
    return 90;
  }

  private int calculateInSampleSize(int width, int height, int targetWidth, int targetHeight) {
    int sample = 1;
    while ((height / sample) > targetHeight * 2 || (width / sample) > targetWidth * 2) {
      sample *= 2;
    }
    return Math.max(1, sample);
  }

  private File findDesktopWallpaperFile(File dir) {
    return findDesktopWallpaperFile(dir, null);
  }

  private File findDesktopWallpaperFile(File dir, File ignoredFile) {
    if (dir == null || !dir.isDirectory()) return null;
    File[] files = dir.listFiles();
    if (files == null || files.length == 0) return null;
    File newest = null;
    for (File file : files) {
      if (file == null || !file.isFile() || !isImageFile(file)) continue;
      if (ignoredFile != null && file.getAbsolutePath().equals(ignoredFile.getAbsolutePath())) {
        continue;
      }
      if (newest == null || file.lastModified() > newest.lastModified()) {
        newest = file;
      }
    }
    return newest;
  }

  private boolean isImageFile(File file) {
    String name = file == null ? "" : file.getName().toLowerCase(Locale.US);
    return name.endsWith(".png")
        || name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".webp");
  }

  private void updateDesktopWallpaperValue(File file) {
    if (settingDesktopWallpaperValue != null) {
      settingDesktopWallpaperValue.setText(file == null ? "未找到" : file.getName());
    }
  }

  private void applyRailStyle() {
    if (primaryRail != null) {
      primaryRail.setBackgroundColor(Color.TRANSPARENT);
      applyRailStyleToView(primaryRail, Color.BLACK);
    }
    if (secondaryRail != null) {
      secondaryRail.setBackgroundColor(Color.TRANSPARENT);
      applyRailStyleToView(secondaryRail, Color.BLACK);
    }
  }

  private void applyRailStyleToView(View view, int foregroundColor) {
    if (view == null) return;
    if (view instanceof BatteryView) {
      ((BatteryView) view).setForegroundColor(foregroundColor);
    } else if (view instanceof PageIndicatorView) {
      ((PageIndicatorView) view).setColor(foregroundColor);
    } else if (view instanceof ImageView) {
      ImageView image = (ImageView) view;
      image.clearColorFilter();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        image.setImageTintList(null);
      }
    } else if (view instanceof TextView) {
      ((TextView) view).setTextColor(foregroundColor);
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        applyRailStyleToView(group.getChildAt(i), foregroundColor);
      }
    }
  }

  private void updateNetworkIcon() {
    if (sideNetworkIcon == null) return;
    int res = R.drawable.ic_rail_wifi;
    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    if (cm != null) {
      NetworkInfo info = cm.getActiveNetworkInfo();
      if (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_MOBILE) {
        res = R.drawable.ic_rail_mobile;
      }
    }
    sideNetworkIcon.setImageResource(res);
    applyRailStyle();
  }

  private boolean isLandscapeOrientation() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  private void applyCurrentGridSize() {
    int[] gridSize = getGridSizeForCurrentSection();
    boolean hideDivider = currentSection == SECTION_DRAWER || config.isHideDivider();
    if (adapter != null) {
      adapter.setCompactMode(currentSection == SECTION_DRAWER);
    }
    launcherView.configure(gridSize[0], gridSize[1], hideDivider);
    dataCenter.setGridSize(gridSize[0], gridSize[1]);
    updateSectionChrome();
  }

  private int[] getGridSizeForCurrentSection() {
    if (currentSection == SECTION_DRAWER) {
      if (isLandscapeOrientation()) {
        return new int[] { DRAWER_LANDSCAPE_COLS, DRAWER_LANDSCAPE_ROWS };
      }
      return new int[] { DRAWER_PORTRAIT_COLS, DRAWER_PORTRAIT_ROWS };
    }
    return config.getGridSize(isLandscapeOrientation());
  }

  private void openSettings() {
    getFragmentManager().beginTransaction()
        .replace(android.R.id.content, new SettingFragment())
        .addToBackStack(null)
        .commit();
  }

  private void showOrientationDialog() {
    final String[] labels = {
        "自动旋转", "竖屏", "横屏", "反向竖屏", "反向横屏"
    };
    new AlertDialog.Builder(this)
        .setTitle("屏幕方向")
        .setSingleChoiceItems(labels, config.getOrientationMode(), new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        config.setOrientationMode(which);
        applyLauncherOrientation(true);
        updateOrientationValue();
            dialog.dismiss();
          }
        })
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showStandbyTextDialog() {
    final EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setText(config.getStandbyLockText());
    input.setSelectAllOnFocus(true);
    int padding = Utils.dp2Px(this, 20);
    input.setPadding(padding, 0, padding, 0);

    new AlertDialog.Builder(this)
        .setTitle("锁屏文字")
        .setView(input)
        .setPositiveButton("保存", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            config.setStandbyLockText(input.getText().toString());
            updateStandbyTextValue();
            StandbyWallpaperUpdater.applyLockText(Launcher.this);
            if (config.isStandbyRefreshEnabled()) {
              StandbyMinuteRefreshReceiver.scheduleRetry(Launcher.this);
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void updateOrientationValue() {
    if (settingRotationValues == null) return;
    int selected = config.getOrientationMode();
    for (int i = 0; i < settingRotationValues.length; i++) {
      if (settingRotationValues[i] != null) {
        settingRotationValues[i].setText(i == selected ? "当前" : "");
      }
    }
  }

  private void updateStandbyTextValue() {
    if (settingStandbyTextInput != null && !settingStandbyTextInput.hasFocus()) {
      String text = config.getStandbyLockText();
      if (!TextUtils.equals(settingStandbyTextInput.getText(), text)) {
        settingStandbyTextInput.setText(text);
        settingStandbyTextInput.setSelection(settingStandbyTextInput.getText().length());
      }
    }
  }

  private void updateStandbyWallpaperValue() {
    if (settingStandbyWallpaperValue != null) {
      settingStandbyWallpaperValue.setText(config.isStandbyWallpaperEnabled() ? "开" : "关");
    }
    updateStandbyGroupValue();
  }

  private void updateStandbyRandomWallpaperValue() {
    if (settingStandbyRandomWallpaperValue != null) {
      settingStandbyRandomWallpaperValue.setText(
          config.isStandbyRandomWallpaperEnabled() ? "开" : "关");
    }
    updateStandbyGroupValue();
  }

  private void updateStandbyRandomWallpaperIntervalValue() {
    int minutes = config.getStandbyRandomWallpaperIntervalMinutes();
    if (settingStandbyRandomWallpaperIntervalValue != null) {
      settingStandbyRandomWallpaperIntervalValue.setText(
          getStandbyRandomWallpaperIntervalLabel(minutes));
    }
    if (settingStandbyRandomWallpaperIntervalValues != null) {
      for (int i = 0; i < settingStandbyRandomWallpaperIntervalValues.length; i++) {
        TextView value = settingStandbyRandomWallpaperIntervalValues[i];
        if (value != null) {
          value.setText(i < STANDBY_RANDOM_WALLPAPER_INTERVALS.length
              && STANDBY_RANDOM_WALLPAPER_INTERVALS[i] == minutes ? "当前" : "");
        }
      }
    }
    updateStandbyGroupValue();
  }

  private void updateStandbyFullRefreshValue() {
    if (settingStandbyFullRefreshValue != null) {
      settingStandbyFullRefreshValue.setText(config.isStandbyFullRefresh() ? "开" : "关");
    }
    updateStandbyGroupValue();
  }

  private void updateStandbyClockStyleValue() {
    int style = config.getStandbyClockStyle();
    if (settingStandbyClockStyleValue != null) {
      settingStandbyClockStyleValue.setText(getStandbyClockStyleLabel(style));
    }
    if (settingStandbyClockStyleValues != null) {
      for (int i = 0; i < settingStandbyClockStyleValues.length; i++) {
        if (settingStandbyClockStyleValues[i] != null) {
          settingStandbyClockStyleValues[i].setText(i == style ? "当前" : "");
        }
      }
    }
    updateStandbyGroupValue();
  }

  private void updateBookCoverValue() {
    if (settingBookCoverValue != null) {
      settingBookCoverValue.setText(config.isBookCoverEnabled() ? "开" : "关");
    }
    updateStandbyGroupValue();
  }

  private void updateBookCoverRandomValue() {
    if (settingBookCoverRandomValue != null) {
      settingBookCoverRandomValue.setText(config.isBookCoverRandom() ? "开" : "关");
    }
    updateStandbyGroupValue();
  }

  private void updateWereadWallpaperValue() {
    if (settingWereadWallpaperValue != null) {
      settingWereadWallpaperValue.setText(config.isWereadWallpaperEnabled() ? "开" : "关");
    }
    updateStandbyGroupValue();
  }

  private void updateWereadWallpaperIntervalValue() {
    if (settingWereadWallpaperIntervalValue != null) {
      settingWereadWallpaperIntervalValue.setText(
          getStandbyRandomWallpaperIntervalLabel(config.getWereadWallpaperIntervalMinutes()));
    }
    updateStandbyGroupValue();
  }

  private Bitmap compositeBookCoverOverlay(Bitmap screenshot) {
    if (screenshot == null) return null;
    try {
      Bitmap result = Bitmap.createBitmap(screenshot.getWidth(), screenshot.getHeight(),
          Bitmap.Config.RGB_565);
      Canvas canvas = new Canvas(result);
      canvas.drawColor(Color.WHITE);
      canvas.drawBitmap(screenshot, 0, 0, null);

      String overlayDir = config.getBookCoverOverlayDir();
      Log.i("EInkLauncher", "compositeBookCoverOverlay loading from " + overlayDir);
      Bitmap overlay = OverlayImageLoader.loadOverlay(overlayDir, config.isBookCoverRandom());
      if (overlay != null) {
        Log.i("EInkLauncher", "compositeBookCoverOverlay overlay " + overlay.getWidth() + "x" + overlay.getHeight());
        android.graphics.RectF dst = new android.graphics.RectF(0, 0,
            result.getWidth(), result.getHeight());
        canvas.drawBitmap(overlay, null, dst, null);
        overlay.recycle();
      } else {
        Log.w("EInkLauncher", "compositeBookCoverOverlay no overlay found in " + overlayDir);
      }

      return result;
    } catch (Exception e) {
      Log.w("EInkLauncher", "compositeBookCoverOverlay failed", e);
      return null;
    }
  }

  private void updateDesktopSettingValues() {
    boolean landscape = isLandscapeOrientation();
    if (settingLayoutGroupValue != null) {
      settingLayoutGroupValue.setText(formatLayoutSummary(landscape));
    }
    if (settingFavoriteAppsValue != null) {
      settingFavoriteAppsValue.setText(config.getFavoriteApps().size() + "个");
    }
    if (settingDisplayGroupValue != null) {
      settingDisplayGroupValue.setText("字号" + formatFontSize(config.getFontSize()));
    }
    if (settingIconGroupValue != null) {
      settingIconGroupValue.setText(IconThemeClient.getThemeName(this, config.getIconThemeId()));
    }
    if (settingOrientationGroupValue != null) {
      settingOrientationGroupValue.setText(getOrientationLabel(config.getOrientationMode()));
    }
    updateStandbyGroupValue();
    if (settingColNumValue != null) {
      settingColNumValue.setText(formatDirectionalGridValue(landscape, config.getColNum(landscape)));
    }
    if (settingRowNumValue != null) {
      settingRowNumValue.setText(formatDirectionalGridValue(landscape, config.getRowNum(landscape)));
    }
    if (settingSortModeValue != null) {
      String[] sortModes = getResources().getStringArray(R.array.sort_modes);
      int mode = config.getSortMode();
      settingSortModeValue.setText(mode >= 0 && mode < sortModes.length ? sortModes[mode] : "");
    }
    if (settingAppNameLinesValue != null) {
      settingAppNameLinesValue.setText(getAppNameLinesLabel(config.getAppNameLines()));
    }
    if (settingFontSizeValue != null) {
      settingFontSizeValue.setText(formatFontSize(config.getFontSize()));
    }
    if (settingDesktopWallpaperValue != null) {
      File file = findDesktopWallpaperFile(new File(config.getDesktopWallpaperDir()));
      settingDesktopWallpaperValue.setText(file == null ? "未找到" : file.getName());
    }
    if (settingDividerValue != null) {
      settingDividerValue.setText(config.isHideDivider() ? "关" : "开");
    }
    if (settingStatusBarValue != null) {
      settingStatusBarValue.setText(config.isShowStatusBar() ? "开" : "关");
    }
    if (settingCustomIconValue != null) {
      settingCustomIconValue.setText(IconThemeClient.getThemeName(this, config.getIconThemeId()));
    }
    if (settingIconAutoFillValue != null) {
      settingIconAutoFillValue.setText(config.isIconAutoReportMissing() ? "开" : "关");
    }
    updateOrientationValue();
    updateStandbyTextValue();
    updateStandbyWallpaperValue();
    updateStandbyRandomWallpaperValue();
    updateStandbyRandomWallpaperIntervalValue();
    updateStandbyFullRefreshValue();
    updateStandbyClockStyleValue();
    updateBookCoverValue();
    updateBookCoverRandomValue();
    updateWereadWallpaperValue();
    updateWereadWallpaperIntervalValue();
  }

  private void updateStandbyGroupValue() {
    if (settingStandbyGroupValue == null || config == null) return;
    if (config.isWereadWallpaperEnabled()) {
      settingStandbyGroupValue.setText("微信读书/"
          + getStandbyRandomWallpaperIntervalLabel(config.getWereadWallpaperIntervalMinutes()));
    } else if (config.isBookCoverEnabled()) {
      settingStandbyGroupValue.setText(config.isBookCoverRandom() ? "随机书封" : "固定书封");
    } else if (config.isStandbyWallpaperEnabled()) {
      String label = getStandbyClockStyleLabel(config.getStandbyClockStyle());
      if (config.isStandbyRandomWallpaperEnabled()) {
        label = label + "/随机壁纸/"
            + getStandbyRandomWallpaperIntervalLabel(
                config.getStandbyRandomWallpaperIntervalMinutes());
      }
      settingStandbyGroupValue.setText(label);
    } else if (config.isStandbyRandomWallpaperEnabled()) {
      settingStandbyGroupValue.setText("随机壁纸/"
          + getStandbyRandomWallpaperIntervalLabel(
              config.getStandbyRandomWallpaperIntervalMinutes()));
    } else {
      settingStandbyGroupValue.setText("文字");
    }
  }

  private String getAppNameLinesLabel(int lines) {
    if (lines == 0) return "隐藏";
    if (lines == 1) return "一行";
    if (lines == 2) return "两行";
    return "不限";
  }

  private String getStandbyClockStyleLabel(int style) {
    if (style == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE) return "横屏翻页钟";
    if (style == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT) return "白底黑字翻页钟";
    if (style == Config.STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW) return "经典镂空";
    if (style == Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE) return "横屏极简大钟";
    return "经典";
  }

  private String getStandbyRandomWallpaperIntervalLabel(int minutes) {
    if (minutes >= 60 && minutes % 60 == 0) {
      return (minutes / 60) + "小时";
    }
    return minutes + "分钟";
  }

  private String formatFontSize(float size) {
    if (Math.abs(size - Math.round(size)) < 0.01f) {
      return String.valueOf(Math.round(size));
    }
    return String.format(Locale.getDefault(), "%.1f", size);
  }

  private String formatDirectionalGridValue(boolean landscape, int value) {
    return (landscape ? "横" : "竖") + value;
  }

  private String formatLayoutSummary(boolean landscape) {
    return (landscape ? "横" : "竖")
        + config.getColNum(landscape)
        + "x"
        + config.getRowNum(landscape);
  }

  private String getOrientationLabel(int mode) {
    switch (mode) {
      case Config.ORIENTATION_PORTRAIT:
        return "竖屏";
      case Config.ORIENTATION_LANDSCAPE:
        return "横屏";
      case Config.ORIENTATION_REVERSE_PORTRAIT:
        return "反竖";
      case Config.ORIENTATION_REVERSE_LANDSCAPE:
        return "反横";
      case Config.ORIENTATION_AUTO:
      default:
        return "自动";
    }
  }

  private void applyLauncherOrientation(boolean explicit) {
    if (config == null) return;
    int mode = config.getOrientationMode();
    int requested = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
    switch (mode) {
      case Config.ORIENTATION_PORTRAIT:
        requested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        break;
      case Config.ORIENTATION_LANDSCAPE:
        requested = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        break;
      case Config.ORIENTATION_REVERSE_PORTRAIT:
        requested = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        break;
      case Config.ORIENTATION_REVERSE_LANDSCAPE:
        requested = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        break;
      case Config.ORIENTATION_AUTO:
      default:
        requested = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        break;
    }
    if (getRequestedOrientation() != requested) {
      setRequestedOrientation(requested);
    }

    boolean wroteSystem = applySystemOrientation(mode);
    boolean forceServiceStarted = syncForceRotationService(mode, explicit);
    if (explicit) {
      if (mode == Config.ORIENTATION_AUTO) {
        Toast.makeText(this, "已恢复自动旋转", Toast.LENGTH_SHORT).show();
      } else if (forceServiceStarted) {
        Toast.makeText(this, "已开启全局强制方向", Toast.LENGTH_SHORT).show();
      } else if (!wroteSystem) {
        Toast.makeText(this, "已应用桌面方向；全局强制需悬浮窗权限", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private boolean syncForceRotationService(int mode, boolean explicit) {
    if (mode == Config.ORIENTATION_AUTO) {
      ForceRotationService.stop(this);
      return true;
    }
    if (!ForceRotationService.canDrawOverlays(this)) {
      ForceRotationService.stop(this);
      if (explicit) {
        showOverlayPermissionDialog();
      }
      return false;
    }
    ForceRotationService.start(this, mode);
    return true;
  }

  private void showOverlayPermissionDialog() {
    new AlertDialog.Builder(this)
        .setTitle("开启全局强制方向")
        .setMessage("要让微信读书这类写死竖屏的应用也横屏，需要允许桌面显示悬浮窗。悬浮窗只有 1px 且不可点击。")
        .setPositiveButton("去开启", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            try {
              startActivity(ForceRotationService.overlayPermissionIntent(Launcher.this));
            } catch (Exception e) {
              Toast.makeText(Launcher.this, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show();
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private boolean applySystemOrientation(int mode) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
        return false;
      }
      if (mode == Config.ORIENTATION_AUTO) {
        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
        return true;
      }
      Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
      Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION,
          getSystemRotationForMode(mode));
      return true;
    } catch (SecurityException e) {
      return false;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private int getSystemRotationForMode(int mode) {
    switch (mode) {
      case Config.ORIENTATION_LANDSCAPE:
        return Surface.ROTATION_90;
      case Config.ORIENTATION_REVERSE_PORTRAIT:
        return Surface.ROTATION_180;
      case Config.ORIENTATION_REVERSE_LANDSCAPE:
        return Surface.ROTATION_270;
      case Config.ORIENTATION_PORTRAIT:
      default:
        return Surface.ROTATION_0;
    }
  }

  private void toggleForcedOrientation() {
    int current = config.getOrientationMode();
    int next = isLandscapeOrientationMode(current)
        ? Config.ORIENTATION_PORTRAIT
        : Config.ORIENTATION_LANDSCAPE;
    config.setOrientationMode(next);
    applyLauncherOrientation(true);
    updateDesktopSettingValues();
  }

  private boolean isLandscapeOrientationMode(int mode) {
    return mode == Config.ORIENTATION_LANDSCAPE
        || mode == Config.ORIENTATION_REVERSE_LANDSCAPE;
  }

  // =========================================================================
  // AppItemBinder.Callback 实现
  // =========================================================================

  @Override
  public void onItemClick(ResolveInfo info) {
    String pkgName = info.activityInfo.packageName;

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkgName)) {
      openStandbyAndLock();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkgName)) {
      WifiControl.onClickWifiItem();
    } else if (AppDataCenter.DRAWER_PACKAGE_NAME.equals(pkgName)) {
      showSection(SECTION_DRAWER);
    } else if (AppDataCenter.ROTATE_PACKAGE_NAME.equals(pkgName)) {
      toggleForcedOrientation();
    } else if (AppDataCenter.QR_SCAN_PACKAGE_NAME.equals(pkgName)) {
      startQrScan();
    } else {
      if (ReadingProgressRepository.isKnownReaderPackage(pkgName)) {
        ReadingProgressRepository.markRefreshNeeded(this, pkgName);
      }
      ComponentName comp = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      intent.addCategory(Intent.CATEGORY_LAUNCHER);
      intent.setComponent(comp);
      startActivity(intent);
    }
  }

  @Override
  public void onItemLongClick(View anchor, ResolveInfo info) {
    String packageName = info.activityInfo.packageName;

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(packageName)) {
      showPowerMenu();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(packageName)) {
      WifiControl.onLongClickWifiItem();
    } else if (AppDataCenter.DRAWER_PACKAGE_NAME.equals(packageName)) {
      showSection(SECTION_DRAWER);
    } else if (AppDataCenter.ROTATE_PACKAGE_NAME.equals(packageName)) {
      showVirtualAppDialog(packageName, getString(R.string.item_rotate_screen),
          R.drawable.ic_line_rotate);
    } else if (AppDataCenter.QR_SCAN_PACKAGE_NAME.equals(packageName)) {
      showVirtualAppDialog(packageName, getString(R.string.item_qr_scan),
          R.drawable.ic_line_qr_scan);
    } else {
      showAppInfoDialog(info, packageName);
    }
  }

  @Override
  public void onItemDeleteClick(ResolveInfo info) {
    Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
        Uri.parse("package:" + info.activityInfo.packageName));
    startActivity(deleteIntent);
  }

  @Override
  public void onItemHideToggle(String packageName, boolean hidden) {
    // 管理模式下的隐藏切换仅更新 UI 状态，"完成" 按钮处理持久化
  }

  // =========================================================================
  // EInkLauncherView.OnPageChangeListener 实现
  // =========================================================================

  @Override
  public void onPageNext() {
    showNextSectionOrPage();
  }

  @Override
  public void onPagePrev() {
    showPreviousSectionOrPage();
  }

  private void showPowerMenu() {
    if (!isSystemApp) return;
    new AlertDialog.Builder(this)
        .setTitle(R.string.power_title)
        .setItems(R.array.power_menu, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
              intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } else {
              PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
              pm.reboot("重启");
            }
          }
        })
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showAppInfoDialog(ResolveInfo info, final String packageName) {
    final boolean favorite = config.isFavoriteApp(packageName);
    final boolean onHomePage = currentSection == SECTION_APPS;
    final String favoriteAction = onHomePage || favorite ? "从主页移除" : "加入主页";
    final String hideAction = binder.getHideAppPkg().contains(packageName) ? "取消隐藏" : "隐藏";
    final AlertDialog[] dialogRef = new AlertDialog[1];
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(14), 0, dp(14), dp(6));

    TextView pkg = new TextView(this);
    pkg.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
    pkg.setGravity(Gravity.CENTER_VERTICAL);
    pkg.setSingleLine(true);
    pkg.setEllipsize(TextUtils.TruncateAt.END);
    pkg.setTextColor(Color.BLACK);
    pkg.setTextSize(13);
    pkg.setText(getString(R.string.dialog_pkg_name, packageName));
    body.addView(pkg);

    addAppActionRow(body, favoriteAction, new Runnable() {
      @Override
      public void run() {
        if (onHomePage && !config.isFavoriteApp(packageName)) {
          return;
        }
        toggleFavoriteApp(packageName);
        if (dialogRef[0] != null) dialogRef[0].dismiss();
      }
    });
    addAppActionRow(body, hideAction, new Runnable() {
      @Override
      public void run() {
        Set<String> hideApps = binder.getHideAppPkg();
        if (!hideApps.add(packageName)) {
          hideApps.remove(packageName);
        }
        dataCenter.refreshAppList();
        config.setHideApps(dataCenter.getHideApps());
        if (dialogRef[0] != null) dialogRef[0].dismiss();
      }
    });
    addAppActionRow(body, "卸载", new Runnable() {
      @Override
      public void run() {
        Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
            Uri.parse("package:" + packageName));
        startActivity(deleteIntent);
        if (dialogRef[0] != null) dialogRef[0].dismiss();
      }
    });

    dialogRef[0] = new AlertDialog.Builder(this)
        .setIcon(iconCache.getIcon(packageName, info, getPackageManager()))
        .setTitle(iconCache.getLabel(packageName, info, getPackageManager()))
        .setView(body)
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showVirtualAppDialog(final String packageName, String title, int iconRes) {
    final boolean favorite = config.isFavoriteApp(packageName);
    final boolean onHomePage = currentSection == SECTION_APPS;
    final String favoriteAction = onHomePage || favorite ? "从主页移除" : "加入主页";
    final AlertDialog[] dialogRef = new AlertDialog[1];
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(14), 0, dp(14), dp(6));

    TextView pkg = new TextView(this);
    pkg.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
    pkg.setGravity(Gravity.CENTER_VERTICAL);
    pkg.setSingleLine(true);
    pkg.setEllipsize(TextUtils.TruncateAt.END);
    pkg.setTextColor(Color.BLACK);
    pkg.setTextSize(13);
    pkg.setText(getString(R.string.dialog_pkg_name, packageName));
    body.addView(pkg);

    addAppActionRow(body, favoriteAction, new Runnable() {
      @Override
      public void run() {
        if (onHomePage && !config.isFavoriteApp(packageName)) {
          return;
        }
        toggleFavoriteApp(packageName);
        if (dialogRef[0] != null) dialogRef[0].dismiss();
      }
    });
    String primaryAction = AppDataCenter.QR_SCAN_PACKAGE_NAME.equals(packageName)
        ? "开始扫码" : "切换方向";
    addAppActionRow(body, primaryAction, new Runnable() {
      @Override
      public void run() {
        if (AppDataCenter.QR_SCAN_PACKAGE_NAME.equals(packageName)) {
          startQrScan();
        } else {
          toggleForcedOrientation();
        }
        if (dialogRef[0] != null) dialogRef[0].dismiss();
      }
    });

    dialogRef[0] = new AlertDialog.Builder(this)
        .setIcon(iconRes)
        .setTitle(title)
        .setView(body)
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void addAppActionRow(LinearLayout parent, String text, final Runnable action) {
    TextView row = new TextView(this);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
    params.topMargin = dp(4);
    row.setLayoutParams(params);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), 0, dp(12), 0);
    row.setSingleLine(true);
    row.setEllipsize(TextUtils.TruncateAt.END);
    row.setTextColor(Color.BLACK);
    row.setTextSize(19);
    row.setText(text);
    row.setBackgroundResource(R.drawable.bg_launcher_row);
    row.setClickable(true);
    row.setFocusable(true);
    row.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        action.run();
      }
    });
    parent.addView(row);
  }

  private void toggleFavoriteApp(String packageName) {
    if (config.isFavoriteApp(packageName)) {
      config.removeFavoriteApp(packageName);
      Toast.makeText(this, "已从主页移除", Toast.LENGTH_SHORT).show();
    } else {
      config.addFavoriteApp(packageName);
      Toast.makeText(this, "已加入主页", Toast.LENGTH_SHORT).show();
    }
    dataCenter.setFavoriteApps(config.getFavoriteApps());
    dataCenter.refreshAppList(binder.isDelete());
    updateSectionChrome();
    updateDesktopSettingValues();
  }

  // =========================================================================
  // 时间显示
  // =========================================================================

  private void updateTimeShow() {
    if (textClock == null || dateClock == null || calendar == null) return;

    boolean is24Hour = DateFormat.is24HourFormat(this);
    calendar.setTimeInMillis(System.currentTimeMillis());

    StringBuilder timePattern = new StringBuilder(is24Hour ? "HH:mm" : "hh:mm");
    if (!is24Hour && !isChina) {
      timePattern.append(" a");
    }

    String timeText = new SimpleDateFormat(timePattern.toString(), Locale.getDefault()).format(calendar.getTime());
    String dateText = new SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(calendar.getTime());
    String solarDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    textClock.setText(timeText);
    dateClock.setText(dateText);
    if (readingTimeClock != null) readingTimeClock.setText(timeText);
    if (readingDateClock != null) readingDateClock.setText(dateText);
    updateLunarClock(solarDate);
  }

  private void updateLunarClock(final String solarDate) {
    if (lunarClock == null || TextUtils.isEmpty(solarDate)) return;
    String cached = LunarDateClient.getCached(this, solarDate);
    if (!TextUtils.isEmpty(cached)) {
      lunarClock.setText(cached);
      return;
    }
    lunarClock.setText("农历获取中");
    if (lunarFetchInFlight && solarDate.equals(lunarFetchDate)) return;
    lunarFetchInFlight = true;
    lunarFetchDate = solarDate;
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          final String lunar = LunarDateClient.fetchAndCache(Launcher.this, solarDate);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              lunarFetchInFlight = false;
              if (lunarClock != null && solarDate.equals(currentSolarDate())) {
                lunarClock.setText(lunar);
              }
            }
          });
        } catch (final Exception e) {
          Log.w("EInkLauncher", "Fetch lunar date failed: " + solarDate, e);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              lunarFetchInFlight = false;
              if (lunarClock != null && solarDate.equals(currentSolarDate())) {
                lunarClock.setText("农历--");
              }
            }
          });
        }
      }
    }, "LunarDateFetch").start();
  }

  private String currentSolarDate() {
    return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
        java.util.Calendar.getInstance().getTime());
  }

  // =========================================================================
  // 电池信息
  // =========================================================================

  private void handleBatteryChanged(Intent intent) {
    int rawLevel = intent.getIntExtra("level", -1);
    int scale = intent.getIntExtra("scale", -1);
    int status = intent.getIntExtra("status", -1);
    int health = intent.getIntExtra("health", -1);

    int level = (rawLevel >= 0 && scale > 0) ? (rawLevel * 100) / scale : -1;
    batteryProgress.setProgress(level);
    batteryProgress.setCharging(status == BatteryManager.BATTERY_STATUS_CHARGING);
    if (batteryStatus != null) {
      batteryStatus.setVisibility(View.VISIBLE);
      batteryStatus.setText(level >= 0 ? String.valueOf(level) : "");
    }

    if (BatteryManager.BATTERY_HEALTH_OVERHEAT == health) {
      return;
    }

    switch (status) {
      case BatteryManager.BATTERY_STATUS_UNKNOWN:
        break;
      case BatteryManager.BATTERY_STATUS_CHARGING:
        break;
      case BatteryManager.BATTERY_STATUS_DISCHARGING:
      case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
        if (level < 15) {
          batteryProgress.setProgress(Math.max(level, 4));
        }
        break;
      case BatteryManager.BATTERY_STATUS_FULL:
        break;
      default:
        break;
    }
  }

  // =========================================================================
  // 广播注册/注销
  // =========================================================================

  /** 注册生命周期不变的静态广播 */
  private void registerStaticReceivers() {
    // 应用安装/卸载广播
    IntentFilter appChangeFilter = new IntentFilter();
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    appChangeFilter.addDataScheme("package");
    registerCompatReceiver(appChangeReceiver, appChangeFilter);
  }

  /** 注册跟随 onResume/onPause 的动态广播 */
  private void registerDynamicReceivers() {
    if (!batteryRegistered) {
      registerCompatReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryRegistered = true;
    }
    if (!timeRegistered) {
      registerCompatReceiver(timeReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
      timeRegistered = true;
    }
    updateTimeShow();
    if (!usbRegistered) {
      registerUsbReceiver();
    }
    if (!wifiRegistered) {
      registerCompatReceiver(wifiReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
      wifiRegistered = true;
    }
    if (!wifiTransferRegistered) {
      IntentFilter wifiTransferFilter = new IntentFilter();
      wifiTransferFilter.addAction(WifiFileTransferService.ACTION_STARTED);
      wifiTransferFilter.addAction(WifiFileTransferService.ACTION_STOPPED);
      wifiTransferFilter.addAction(WifiFileTransferService.ACTION_FAILEDTOSTART);
      wifiTransferFilter.addAction(WifiFileTransferService.ACTION_FILE_RECEIVED);
      registerCompatReceiver(wifiTransferReceiver, wifiTransferFilter);
      wifiTransferRegistered = true;
    }
    if (!ftpRegistered) {
      IntentFilter ftpFilter = new IntentFilter(FTPService.ACTION_START_FTPSERVER);
      ftpFilter.addAction(FTPService.ACTION_STOP_FTPSERVER);
      registerCompatReceiver(ftpReceiver, ftpFilter);
      ftpRegistered = true;
    }
  }

  private void unregisterDynamicReceivers() {
    if (batteryRegistered) {
      safeUnregisterReceiver(batteryReceiver);
      batteryRegistered = false;
    }
    if (timeRegistered) {
      safeUnregisterReceiver(timeReceiver);
      timeRegistered = false;
    }
    if (usbRegistered) {
      safeUnregisterReceiver(usbReceiver);
      usbRegistered = false;
    }
    if (ftpRegistered) {
      safeUnregisterReceiver(ftpReceiver);
      ftpRegistered = false;
    }
    if (wifiRegistered) {
      safeUnregisterReceiver(wifiReceiver);
      wifiRegistered = false;
    }
    if (wifiTransferRegistered) {
      safeUnregisterReceiver(wifiTransferReceiver);
      wifiTransferRegistered = false;
    }
  }

  private void safeUnregisterReceiver(BroadcastReceiver receiver) {
    try {
      unregisterReceiver(receiver);
    } catch (IllegalArgumentException ignored) {
    }
  }

  private void registerUsbReceiver() {
    IntentFilter usbFilter = new IntentFilter();
    usbFilter.addAction(Intent.ACTION_UMS_DISCONNECTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
    usbFilter.addDataScheme("file");
    registerCompatReceiver(usbReceiver, usbFilter);
    usbRegistered = true;
  }

  private void registerCompatReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    Utils.registerReceiverCompat(this, receiver, filter);
  }

  // =========================================================================
  // 按键处理
  // =========================================================================

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      showPreviousSectionOrPage();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      showNextSectionOrPage();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (currentSection == SECTION_SETTINGS && settingsSubPage != SETTINGS_SUB_HOME) {
        showPreviousSettingsLevel();
      }
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && getFragmentManager().getBackStackEntryCount() == 0) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public void onBackPressed() {
    if (currentSection == SECTION_SETTINGS && settingsSubPage != SETTINGS_SUB_HOME) {
      showPreviousSettingsLevel();
      return;
    }
    if (getFragmentManager().getBackStackEntryCount() > 0) {
      super.onBackPressed();
      config.setFontSize(config.getFontSize());
    }
  }

  // =========================================================================
  // 锁屏
  // =========================================================================

  public void lockScreen() {
    try {
      if (policyManager.isAdminActive(new ComponentName(this, AdminReceiver.class))) {
        policyManager.lockNow();
      } else {
        requestDeviceAdmin();
      }
    } catch (Exception e) {
      showDeviceAdminDialog();
    }
  }

  private void openStandbyPreview() {
    startActivity(StandbyActivity.previewIntent(this));
  }

  private void openStandbyAndLock() {
    Log.i("EInkLauncher", "openStandbyAndLock bookCover=" + config.isBookCoverEnabled()
        + " captureReady=" + ScreenCaptureManager.getInstance().isReady());
    if (config.isWereadWallpaperEnabled()) {
      updateWereadWallpaperAndLock();
    } else if (config.isBookCoverEnabled()
        && ScreenCaptureManager.getInstance().isReady()) {
      // 书封模式：长按音量减触发，禁止后台时钟刷新
      StandbyWallpaperUpdater.bookCoverActive = true;
      ScreenCaptureManager.getInstance().captureScreen(
          new ScreenCaptureManager.CaptureCallback() {
            @Override
            public void onCaptured(Bitmap screenshot) {
              Bitmap composited = compositeBookCoverOverlay(screenshot);
              if (composited != null) {
                StandbyWallpaperUpdater.update(Launcher.this, composited,
                    config.getStandbyLockText(), true, true);
                composited.recycle();
              }
              screenshot.recycle();
              lockScreen();
              schedulePostLockRefresh();
            }

            @Override
            public void onError(String error) {
              Log.w("EInkLauncher", "Screen capture failed: " + error);
              if (config.isStandbyRefreshEnabled()) {
                boolean showTime = config.isStandbyWallpaperEnabled();
                StandbyWallpaperUpdater.update(Launcher.this, null,
                    config.getStandbyLockText(), true, showTime);
                config.setLastWallpaperUpdateMinute(System.currentTimeMillis() / 60000L);
              }
              lockScreen();
              schedulePostLockRefresh();
            }
          });
    } else {
      // 正常时钟模式：电源键或锁屏按钮触发
      StandbyWallpaperUpdater.bookCoverActive = false;
      if (config.isStandbyRefreshEnabled()) {
        boolean showTime = config.isStandbyWallpaperEnabled();
        StandbyWallpaperUpdater.update(this, null, config.getStandbyLockText(), true, showTime);
        config.setLastWallpaperUpdateMinute(System.currentTimeMillis() / 60000L);
      }
      lockScreen();
      schedulePostLockRefresh();
    }
  }

  private void updateWereadWallpaperAndLock() {
    StandbyWallpaperUpdater.bookCoverActive = false;
    Toast.makeText(this, "正在更新微信读书壁纸...", Toast.LENGTH_SHORT).show();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          StandbyWallpaperUpdater.update(Launcher.this, null,
              config.getStandbyLockText(), true, false);
          config.setLastWallpaperUpdateMinute(System.currentTimeMillis() / 60000L);
        } catch (Exception e) {
          Log.w("EInkLauncher", "Weread wallpaper update before lock failed", e);
        }
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            lockScreen();
            schedulePostLockRefresh();
          }
        });
      }
    }, "WereadWallpaperLock").start();
  }

  private void schedulePostLockRefresh() {
    if (!config.isStandbyRefreshEnabled() && !StandbyWallpaperUpdater.bookCoverActive) return;
    View decor = getWindow() != null ? getWindow().getDecorView() : null;
    if (decor == null) return;
    decor.postDelayed(new Runnable() {
      private int attempts = 0;

      @Override
      public void run() {
        if (StandbyWallpaperUpdater.isScreenOff(Launcher.this)) {
          StandbyWallpaperUpdater.requestVendorRefresh(Launcher.this);
        } else if (attempts < 3) {
          attempts++;
          View d = getWindow() != null ? getWindow().getDecorView() : null;
          if (d != null) d.postDelayed(this, 1000);
        }
      }
    }, 1000);
  }

  private void requestDeviceAdmin() {
    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "E-Ink Launcher 获取锁屏权限");
    startActivity(intent);
  }

  private void showDeviceAdminDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.launch_failed)
        .setMessage(R.string.launch_devicemanager_failed)
        .setPositiveButton(R.string.launch_devicemanager, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            try {
              Intent intent = Intent.parseUri(
                  "intent:#Intent;component=com.android.settings/.DeviceAdminSettings;end",
                  Intent.URI_INTENT_SCHEME);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_MEDIA_PROJECTION) {
      ScreenCaptureManager.getInstance().onPermissionResult(this, resultCode, data);
      updateBookCoverValue();
      updatePermissionValues();
      handlePermissionFlowResult();
      return;
    }
    if (requestCode == REQUEST_QR_SCAN) {
      if (resultCode == RESULT_OK && data != null) {
        handleScannedQr(data.getStringExtra(QrScanActivity.EXTRA_RESULT));
      }
      return;
    }
    if (resultCode == RESULT_OK && requestCode == REQUEST_DEVICE_ADMIN) {
      policyManager.lockNow();
    }
    updatePermissionValues();
    handlePermissionFlowResult();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_RUNTIME_PERMISSIONS) {
      updatePermissionValues();
      if (permissionRequestFlowActive && permissionRequestFlowWaitingResult) {
        handlePermissionFlowResult();
        return;
      }
      boolean allGranted = grantResults != null && grantResults.length > 0;
      if (grantResults != null) {
        for (int result : grantResults) {
          if (result != PackageManager.PERMISSION_GRANTED) {
            allGranted = false;
            break;
          }
        }
      }
      Toast.makeText(this, allGranted ? "权限已允许" : "仍有权限未允许",
          Toast.LENGTH_SHORT).show();
    }
  }

  // =========================================================================
  // 状态栏/系统应用判断/通知栏
  // =========================================================================

  public void applyStatusBarVisibility() {
    int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
    if (config.isShowStatusBar()) {
      getWindow().setFlags(flags, flags);
    } else {
      getWindow().clearFlags(flags);
    }
    applySystemUiVisibility();
  }

  private void applySystemUiVisibility() {
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  public boolean isUserApp(PackageInfo pInfo) {
    return (pInfo.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
  }

  private void checkLaunchHomeNotification() {
    if (!TextUtils.equals(Build.DEVICE, "virgo-perf1")) return;
    Intent service = new Intent(this, HomeEntranceService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(service);
    } else {
      startService(service);
    }
  }
}
