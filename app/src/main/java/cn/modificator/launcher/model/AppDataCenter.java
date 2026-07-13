package cn.modificator.launcher.model;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.modificator.launcher.R;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 应用数据管理中心，负责加载应用列表和分页逻辑。
 */
public class AppDataCenter {

  /** 虚拟包名：Wifi 控制入口 */
  public static final String WIFI_PACKAGE_NAME = "E-ink_Launcher.WiFi";
  /** 虚拟包名：一键锁屏入口 */
  public static final String LOCK_PACKAGE_NAME = "E-ink_Launcher.Lock";
  /** 虚拟包名：进入应用抽屉 */
  public static final String DRAWER_PACKAGE_NAME = "E-ink_Launcher.Drawer";
  /** 虚拟包名：强制横竖屏切换入口 */
  public static final String ROTATE_PACKAGE_NAME = "E-ink_Launcher.Rotate";
  /** 虚拟包名：扫码设置入口 */
  public static final String QR_SCAN_PACKAGE_NAME = "E-ink_Launcher.QrScan";

  private static final int DEFAULT_HOME_FAVORITE_COUNT = 5;

  private final Context mContext;
  private final List<ResolveInfo> mApps = new ArrayList<>();
  private final List<ResolveInfo> allApps = new ArrayList<>();
  private final List<String> favoritePackages = new ArrayList<>();
  private int pageIndex = 0;
  private int pageCount = 0;
  private int colNum = 5;
  private int rowNum = 5;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private final Set<String> hideApps = new HashSet<>();
  private int sortMode = AppSortComparator.SORT_NAME_ASC;
  private boolean drawerMode;
  private boolean showAllMode;

  public AppDataCenter(Context context) {
    this.mContext = context;
  }

  // =========================================================================
  // Adapter / Binder 绑定
  // =========================================================================

  public void setAdapter(LauncherAdapter adapter) {
    this.adapter = adapter;
    this.binder = adapter.getBinder();
    if (binder != null) {
      binder.setHideAppPkg(hideApps);
    }
    setPageShow();
  }

  // =========================================================================
  // 隐藏应用管理
  // =========================================================================

  public void setHideApps(Set<String> hideApps) {
    this.hideApps.clear();
    this.hideApps.addAll(hideApps);
    loadApps();
  }

  public Set<String> getHideApps() {
    return hideApps;
  }

  public void setFavoriteApps(List<String> packages) {
    favoritePackages.clear();
    if (packages != null) {
      LinkedHashSet<String> deduped = new LinkedHashSet<>();
      for (String pkg : packages) {
        if (pkg == null) continue;
        String trimmed = pkg.trim();
        if (trimmed.length() > 0 && canPinToHome(trimmed)) {
          deduped.add(trimmed);
        }
      }
      favoritePackages.addAll(deduped);
    }
    rebuildDisplayApps();
  }

  public boolean isDrawerMode() {
    return drawerMode;
  }

  public void setDrawerMode(boolean drawerMode) {
    this.drawerMode = drawerMode;
    this.showAllMode = false;
    this.pageIndex = 0;
    rebuildDisplayApps();
  }

  public List<String> getDefaultFavoritePackages(int limit) {
    List<String> result = new ArrayList<>();
    if (limit <= 0) return result;
    for (ResolveInfo info : allApps) {
      if (info == null || info.activityInfo == null) continue;
      String pkg = info.activityInfo.packageName;
      if (isVirtualPackage(pkg) || result.contains(pkg)) continue;
      result.add(pkg);
      if (result.size() >= limit) break;
    }
    return result;
  }

  // =========================================================================
  // 列数/行数
  // =========================================================================

  public void setColNum(int colNum) {
    this.colNum = colNum;
    if (isHomeMode()) {
      rebuildDisplayApps();
    } else {
      updatePageCount();
      setPageShow();
    }
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
    if (isHomeMode()) {
      rebuildDisplayApps();
    } else {
      updatePageCount();
      setPageShow();
    }
  }

  /** 批量设置行列数，只触发一次分页更新 */
  public void setGridSize(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    if (isHomeMode()) {
      rebuildDisplayApps();
    } else {
      updatePageCount();
      setPageShow();
    }
  }

  // =========================================================================
  // 排序
  // =========================================================================

  public void setSortMode(int sortMode) {
    this.sortMode = sortMode;
  }

  public int getSortMode() {
    return sortMode;
  }

  // =========================================================================
  // 翻页
  // =========================================================================

  public void showNextPage() {
    if (pageIndex >= pageCount) return;
    pageIndex++;
    setPageShow();
  }

  public void showLastPage() {
    if (pageIndex <= 0) return;
    pageIndex--;
    setPageShow();
  }

  public boolean canShowNextPage() {
    return pageIndex < pageCount;
  }

  public boolean canShowLastPage() {
    return pageIndex > 0;
  }

  public void showFirstPage() {
    pageIndex = 0;
    setPageShow();
  }

  public void showFinalPage() {
    pageIndex = pageCount;
    setPageShow();
  }

  public String getPageText() {
    return (pageIndex + 1) + "/" + (pageCount + 1);
  }

  public int getPageIndex() {
    return pageIndex;
  }

  public int getPageTotal() {
    return pageCount + 1;
  }

  public List<ResolveInfo> getAppsSnapshot() {
    return new ArrayList<>(allApps.isEmpty() ? mApps : allApps);
  }

  // =========================================================================
  // 刷新
  // =========================================================================

  public void refreshAppList() {
    refreshAppList(false);
  }

  public void refreshAppList(boolean showAll) {
    showAllMode = showAll;
    if (showAll) loadAllApps(); else loadApps();
  }

  // =========================================================================
  // 内部加载
  // =========================================================================

  private void loadApps() {
    long t0 = System.currentTimeMillis();
    loadLauncherApps(false);
    long t1 = System.currentTimeMillis();
    Log.d("Perf", "loadApps: query=" + (t1 - t0) + "ms sort=" + (System.currentTimeMillis() - t1) + "ms total=" + (System.currentTimeMillis() - t0) + "ms");
  }

  private void loadAllApps() {
    loadLauncherApps(true);
  }

  private void loadLauncherApps(boolean includeHidden) {
    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

    if (binder != null) {
      hideApps.clear();
      hideApps.addAll(binder.getHideAppPkg());
      binder.setHideAppPkg(hideApps);
    }

    allApps.clear();
    for (ResolveInfo resolveInfo : mContext.getPackageManager().queryIntentActivities(mainIntent, 0)) {
      if (resolveInfo == null || resolveInfo.activityInfo == null) continue;
      if ("cn.modificator.launcher.Launcher".equals(resolveInfo.activityInfo.name)) continue;
      String pkg = resolveInfo.activityInfo.packageName;
      if (!includeHidden && hideApps.contains(pkg)) continue;
      allApps.add(resolveInfo);
    }
    allApps.add(createRotateIcon());
    allApps.add(createQrScanIcon());

    sortApps(allApps);
    rebuildDisplayApps();
  }

  private void rebuildDisplayApps() {
    mApps.clear();
    if (showAllMode || drawerMode) {
      mApps.addAll(allApps);
    } else {
      buildHomeApps();
    }
    updatePageCount();
    setPageShow();
  }

  private void buildHomeApps() {
    int capacity = Math.max(1, colNum * rowNum);
    LinkedHashSet<String> added = new LinkedHashSet<>();
    if (!favoritePackages.isEmpty()) {
      for (String pkg : favoritePackages) {
        ResolveInfo info = findAppByPackage(pkg);
        if (info != null && added.add(pkg)) {
          mApps.add(info);
          if (mApps.size() >= capacity) break;
        }
      }
    } else {
      for (ResolveInfo info : allApps) {
        if (info == null || info.activityInfo == null) continue;
        String pkg = info.activityInfo.packageName;
        if (isVirtualPackage(pkg) || !added.add(pkg)) continue;
        mApps.add(info);
        if (mApps.size() >= Math.min(DEFAULT_HOME_FAVORITE_COUNT, capacity)) break;
      }
    }
  }

  private boolean isHomeMode() {
    return !drawerMode && !showAllMode;
  }

  private ResolveInfo findAppByPackage(String packageName) {
    if (packageName == null) return null;
    for (ResolveInfo info : allApps) {
      if (info != null && info.activityInfo != null
          && packageName.equals(info.activityInfo.packageName)) {
        return info;
      }
    }
    return null;
  }

  private void setPageShow() {
    if (adapter == null) return;
    int itemCount = colNum * rowNum;
    int pageStart = pageIndex * itemCount;
    int pageEnd = Math.min(pageStart + itemCount, mApps.size());
    adapter.setAppList(mApps.subList(pageStart, pageEnd));
  }

  private void updatePageCount() {
    int itemCount = colNum * rowNum;
    if (itemCount <= 0) {
      pageCount = 0;
      pageIndex = 0;
      return;
    }
    pageCount = mApps.size() / itemCount - (mApps.size() % itemCount == 0 ? 1 : 0);
    pageCount = Math.max(pageCount, 0);
    pageIndex = Math.min(pageIndex, pageCount);
  }

  private void sortApps(List<ResolveInfo> apps) {
    Collections.sort(apps, new AppSortComparator(mContext, mContext.getPackageManager(), sortMode, apps));
  }

  public static boolean isVirtualPackage(String packageName) {
    return WIFI_PACKAGE_NAME.equals(packageName)
        || LOCK_PACKAGE_NAME.equals(packageName)
        || DRAWER_PACKAGE_NAME.equals(packageName)
        || ROTATE_PACKAGE_NAME.equals(packageName)
        || QR_SCAN_PACKAGE_NAME.equals(packageName);
  }

  public static boolean canPinToHome(String packageName) {
    return !isVirtualPackage(packageName)
        || ROTATE_PACKAGE_NAME.equals(packageName)
        || QR_SCAN_PACKAGE_NAME.equals(packageName);
  }

  public static void appendIconThemeVirtualApps(Context context, List<ResolveInfo> target) {
    if (context == null || target == null) return;
    target.add(createVirtualIcon(context, ROTATE_PACKAGE_NAME, R.string.item_rotate_screen,
        R.drawable.ic_line_rotate));
    target.add(createVirtualIcon(context, QR_SCAN_PACKAGE_NAME, R.string.item_qr_scan,
        R.drawable.ic_line_qr_scan));
  }

  public static int getVirtualIconRes(String packageName) {
    if (WIFI_PACKAGE_NAME.equals(packageName)) return R.drawable.ic_line_wifi;
    if (LOCK_PACKAGE_NAME.equals(packageName)) return R.drawable.ic_line_lock;
    if (DRAWER_PACKAGE_NAME.equals(packageName)) return R.drawable.ic_line_app;
    if (ROTATE_PACKAGE_NAME.equals(packageName)) return R.drawable.ic_line_rotate;
    if (QR_SCAN_PACKAGE_NAME.equals(packageName)) return R.drawable.ic_line_qr_scan;
    return 0;
  }

  // =========================================================================
  // 虚拟图标创建
  // =========================================================================

  private ResolveInfo createWifiIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.wifi_on;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = WIFI_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createPowerIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.ic_onekeylock;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = LOCK_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createDrawerIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.ic_line_app;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = DRAWER_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createRotateIcon() {
    return createVirtualIcon(mContext, ROTATE_PACKAGE_NAME, R.string.item_rotate_screen,
        R.drawable.ic_line_rotate);
  }

  private ResolveInfo createQrScanIcon() {
    return createVirtualIcon(mContext, QR_SCAN_PACKAGE_NAME, R.string.item_qr_scan,
        R.drawable.ic_line_qr_scan);
  }

  private static ResolveInfo createVirtualIcon(Context context, String packageName, int labelRes,
                                               int iconRes) {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = iconRes;
    resolveInfo.nonLocalizedLabel = context.getString(labelRes);
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = packageName;
    resolveInfo.activityInfo.name = packageName;
    resolveInfo.activityInfo.icon = iconRes;
    return resolveInfo;
  }
}
