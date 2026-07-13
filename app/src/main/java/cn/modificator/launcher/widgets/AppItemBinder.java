package cn.modificator.launcher.widgets;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.modificator.launcher.R;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.IconCategoryResolver;
import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.model.WifiControl;

/**
 * 负责将应用数据绑定到 {@link LauncherAdapter.ItemViewHolder}，
 * 以及处理点击/长按/卸载/隐藏等交互事件。
 * <p>
 * 与 {@link LauncherAdapter} 协作：Adapter 管理 ViewHolder 池和数据列表，
 * Binder 处理"怎样把数据画到 View 上"以及"用户点了之后做什么"。
 */
public class AppItemBinder {

  // =========================================================================
  // 回调接口
  // =========================================================================

  /** 宿主实现此接口以响应用户交互。 */
  public interface Callback {
    /** 普通模式下点击应用 */
    void onItemClick(ResolveInfo info);

    /** 长按应用 */
    void onItemLongClick(View anchor, ResolveInfo info);

    /** 管理模式下点击卸载 */
    void onItemDeleteClick(ResolveInfo info);

    /** 管理模式下切换隐藏状态 */
    void onItemHideToggle(String packageName, boolean hidden);
  }

  // =========================================================================
  // 字段
  // =========================================================================

  private final PackageManager packageManager;
  private final Set<String> hideAppPkg = new HashSet<>();

  private Callback callback;
  private IconCache iconCache;
  private boolean isDelete = false;

  // 当前绑定的数据（由 Adapter 在 bindAll 时传入）
  private List<ResolveInfo> dataRef;

  // =========================================================================
  // 构造器
  // =========================================================================

  public AppItemBinder(PackageManager pm) {
    this.packageManager = pm;
  }

  // =========================================================================
  // 外部依赖
  // =========================================================================

  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  public void setIconCache(IconCache iconCache) {
    this.iconCache = iconCache;
  }

  // =========================================================================
  // 管理模式
  // =========================================================================

  public void setDelete(boolean delete) {
    this.isDelete = delete;
  }

  public boolean isDelete() {
    return isDelete;
  }

  // =========================================================================
  // 隐藏应用
  // =========================================================================

  public void setHideAppPkg(Set<String> pkgs) {
    hideAppPkg.clear();
    hideAppPkg.addAll(pkgs);
  }

  public Set<String> getHideAppPkg() {
    return hideAppPkg;
  }

  // =========================================================================
  // 绑定入口（包级可见，由 LauncherAdapter 调用）
  // =========================================================================

  /**
   * 绑定所有 ViewHolder。
   *
   * @param holders ViewHolder 列表
   * @param data    当前页要显示的应用列表
   */
  void bindAll(List<LauncherAdapter.ItemViewHolder> holders, List<ResolveInfo> data) {
    this.dataRef = data;
    Map<String, File> customIcons = iconCache != null ? iconCache.getCustomIconMap() : null;
    WifiControl.bind(null, customIcons);

    for (int i = 0; i < holders.size(); i++) {
      LauncherAdapter.ItemViewHolder holder = holders.get(i);
      if (i < data.size()) {
        bindItem(holder, i, customIcons);
      } else {
        clearItem(holder);
      }
    }
  }

  /**
   * 更新所有 ViewHolder 的管理模式状态（删除/隐藏按钮可见性）。
   */
  void updateDeleteState(List<LauncherAdapter.ItemViewHolder> holders, List<ResolveInfo> data) {
    for (int i = 0; i < holders.size() && i < data.size(); i++) {
      LauncherAdapter.ItemViewHolder holder = holders.get(i);

      if (!isDelete) {
        holder.menuContainer.setVisibility(View.GONE);
        continue;
      }

      String pkg = data.get(i).activityInfo.packageName;
      if (AppDataCenter.isVirtualPackage(pkg)) {
        holder.menuContainer.setVisibility(View.GONE);
        continue;
      }

      holder.menuContainer.setVisibility(View.VISIBLE);
      boolean canDelete = false;
      try {
        canDelete = (packageManager.getPackageInfo(pkg, 0).applicationInfo.flags
            & ApplicationInfo.FLAG_SYSTEM) == 0;
      } catch (PackageManager.NameNotFoundException ignored) {
      }

      holder.menuDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
      holder.menuHide.setSelected(hideAppPkg.contains(pkg));
    }
  }

  // =========================================================================
  // 单项绑定
  // =========================================================================

  private void bindItem(LauncherAdapter.ItemViewHolder holder, int position,
                         Map<String, File> customIcons) {
    ResolveInfo info = dataRef.get(position);
    String pkg = info.activityInfo.packageName;

    // —— 图标 & 标签 ——
    if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkg)) {
      loadIcon(holder.appImage, IconCategoryResolver.KEY_SYSTEM_WIFI_ON,
          R.drawable.ic_line_wifi, customIcons);
      holder.appName.setText("WiFi");
    } else if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkg)) {
      loadIcon(holder.appImage, IconCategoryResolver.KEY_SYSTEM_LOCK,
          R.drawable.ic_line_lock, customIcons);
      holder.appName.setText(R.string.item_lockscreen);
    } else if (AppDataCenter.DRAWER_PACKAGE_NAME.equals(pkg)) {
      loadIcon(holder.appImage, IconCategoryResolver.KEY_CATEGORY_DEFAULT,
          R.drawable.ic_line_app, customIcons);
      holder.appName.setText("全部应用");
    } else if (AppDataCenter.ROTATE_PACKAGE_NAME.equals(pkg)) {
      loadIcon(holder.appImage, IconCategoryResolver.KEY_SYSTEM_ROTATE_SCREEN,
          R.drawable.ic_line_rotate, customIcons);
      holder.appName.setText(R.string.item_rotate_screen);
    } else if (AppDataCenter.QR_SCAN_PACKAGE_NAME.equals(pkg)) {
      loadIcon(holder.appImage, IconCategoryResolver.KEY_SYSTEM_QR_SCAN,
          R.drawable.ic_line_qr_scan, customIcons);
      holder.appName.setText(R.string.item_qr_scan);
    } else {
      CharSequence label = iconCache != null
          ? iconCache.getLabel(pkg, info, packageManager)
          : info.loadLabel(packageManager);
      String categoryKey = IconCategoryResolver.getCategoryKey(pkg, label);
      loadAppIcon(holder.appImage, pkg, categoryKey, customIcons);
      holder.appName.setText(label);
    }

    // —— 监听器（通过 tag 传递 position，复用单例监听器） ——
    holder.itemView.setTag(position);
    holder.itemView.setOnClickListener(clickListener);
    holder.itemView.setOnLongClickListener(longClickListener);
    holder.menuDelete.setTag(position);
    holder.menuDelete.setOnClickListener(deleteClickListener);
    holder.menuHide.setTag(position);
    holder.menuHide.setOnClickListener(hideClickListener);

    holder.itemView.setVisibility(View.VISIBLE);
    holder.itemView.setAlpha(1);
  }

  private void clearItem(LauncherAdapter.ItemViewHolder holder) {
    holder.appName.setText("");
    holder.appImage.setImageDrawable(null);
    holder.itemView.setTag(null);
    holder.menuDelete.setTag(null);
    holder.menuHide.setTag(null);
    holder.itemView.setOnClickListener(null);
    holder.itemView.setOnLongClickListener(null);
    holder.menuDelete.setOnClickListener(null);
    holder.menuHide.setOnClickListener(null);
    holder.menuContainer.setVisibility(View.GONE);
    holder.itemView.setVisibility(View.INVISIBLE);
    holder.itemView.setAlpha(1);
  }

  // =========================================================================
  // 图标加载
  // =========================================================================

  private static void loadFileToView(ImageView iv, File file) {
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    // Calculate inSampleSize: power of 2, target ~128px for quality
    int target = 128;
    int scale = 1;
    while (opts.outWidth / (scale * 2) >= target && opts.outHeight / (scale * 2) >= target) {
      scale *= 2;
    }
    opts.inSampleSize = scale;
    opts.inJustDecodeBounds = false;
    iv.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath(), opts));
  }

  private void loadIcon(ImageView iv, String key, int defaultRes,
                        Map<String, File> customIcons) {
    File custom = customIcons != null ? customIcons.get(key) : null;
    if (custom != null) {
      loadFileToView(iv, custom);
    } else {
      iv.setImageResource(defaultRes);
    }
  }

  private void loadAppIcon(ImageView iv, String pkg, String categoryKey,
                           Map<String, File> customIcons) {
    File custom = customIcons != null ? customIcons.get(pkg) : null;
    if (custom == null && customIcons != null) {
      custom = customIcons.get(categoryKey);
    }
    if (custom == null && customIcons != null) {
      custom = customIcons.get(IconCategoryResolver.KEY_CATEGORY_DEFAULT);
    }
    if (custom != null) {
      loadFileToView(iv, custom);
    } else {
      if (customIcons != null && !customIcons.isEmpty()) {
        Log.d("IconBinder", "fallback builtin for " + pkg + " (cat=" + categoryKey + ") mapSize=" + customIcons.size());
      }
      iv.setImageResource(IconCategoryResolver.getBuiltinIconResForApp(pkg, categoryKey));
    }
  }

  // =========================================================================
  // 点击监听器（单例 + tag 传递位置）
  // =========================================================================

  private final View.OnClickListener clickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      if (callback == null || dataRef == null) return;
      int pos = (int) v.getTag();
      if (pos >= dataRef.size()) return;
      if (isDelete) {
        String pkg = dataRef.get(pos).activityInfo.packageName;
        if (AppDataCenter.isVirtualPackage(pkg)) return;
        callback.onItemDeleteClick(dataRef.get(pos));
      } else {
        callback.onItemClick(dataRef.get(pos));
      }
    }
  };

  private final View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
    @Override
    public boolean onLongClick(View v) {
      if (callback == null || dataRef == null) return false;
      int pos = (int) v.getTag();
      if (pos >= dataRef.size()) return false;
      callback.onItemLongClick(v, dataRef.get(pos));
      return true;
    }
  };

  private final View.OnClickListener deleteClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      if (callback == null || dataRef == null) return;
      int pos = (int) v.getTag();
      if (pos >= dataRef.size()) return;
      callback.onItemDeleteClick(dataRef.get(pos));
    }
  };

  private final View.OnClickListener hideClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      if (dataRef == null) return;
      int pos = (int) v.getTag();
      if (pos >= dataRef.size()) return;
      String pkg = dataRef.get(pos).activityInfo.packageName;
      boolean hidden;
      if (hideAppPkg.contains(pkg)) {
        hideAppPkg.remove(pkg);
        hidden = false;
      } else {
        hideAppPkg.add(pkg);
        hidden = true;
      }
      v.setSelected(hidden);
      if (callback != null) {
        callback.onItemHideToggle(pkg, hidden);
      }
    }
  };
}
