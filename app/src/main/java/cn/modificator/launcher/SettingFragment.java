package cn.modificator.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import cn.modificator.launcher.autorefresh.AutoRefreshSettings;
import cn.modificator.launcher.model.AppSortComparator;
import cn.modificator.launcher.model.WifiControl;
import cn.modificator.launcher.wifitransfer.WifiFileTransferService;

import java.util.HashMap;
import java.util.Map;

/**
 * 设置页面 Fragment。
 */
public class SettingFragment extends Fragment implements View.OnClickListener {

  /** 设置变更回调接口：宿主 Activity 应实现此接口以响应设置变更。 */
  public interface OnSettingChangeListener {
    void onRowNumChanged(int rowNum);
    void onColNumChanged(int colNum);
    void onFontSizeChanged(float size);
    void onAppNameLinesChanged(int lines);
    void onHideDividerChanged(boolean hide);
    void onShowStatusBarChanged(boolean show);
    void onShowCustomIconChanged(boolean show);
    void onSortModeChanged(int mode);
    void onEnterManageMode();
  }

  private OnSettingChangeListener listener;

  private Spinner colNumSpinner;
  private Spinner rowNumSpinner;
  private Spinner appNameLinesSpinner;
  private Spinner sortModeSpinner;
  private SeekBar fontControl;
  private View rootView;
  private TextView hideDivider;
  private TextView wifiTransferAddr;
  private TextView wifiTransferStatus;
  private ImageView wifiTransferQr;
  private TextView autoRefreshStatus;
  private TextView autoRefreshInterval;
  private TextView autoRefreshKeyCode;
  private TextView autoRefreshTapStatus;
  private TextView autoRefreshTapInterval;
  private TextView autoRefreshDelay;
  private TextView autoRefreshSosFrameRefreshValue;
  private TextView autoRefreshShakeRefreshValue;
  private TextView autoRefreshShakeCountValue;
  private TextView autoRefreshShakeAmplitudeValue;
  private TextView showStatusBar;
  private TextView showCustomIcon;
  private Config config;

  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    if (activity instanceof OnSettingChangeListener) {
      listener = (OnSettingChangeListener) activity;
    } else {
      throw new ClassCastException(activity.toString() + " must implement OnSettingChangeListener");
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.activity_setting, null);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    rootView = getView();
    config = new Config(getActivity());
    initViews();
    initSpinners();
    initFontControl();
    updateWifiTransferStatus();
    updateAutoRefreshStatus();
  }

  // =========================================================================
  // 初始化
  // =========================================================================

  private void initViews() {
    rootView.findViewById(R.id.toBack).setOnClickListener(this);
    rootView.findViewById(R.id.rootView).setOnClickListener(this);
    rootView.findViewById(R.id.deleteApp).setOnClickListener(this);
    rootView.findViewById(R.id.showWifiName).setOnClickListener(this);
    rootView.findViewById(R.id.btnHideFontControl).setOnClickListener(this);
    rootView.findViewById(R.id.changeFontSize).setOnClickListener(this);
    rootView.findViewById(R.id.helpAbout).setOnClickListener(this);
    rootView.findViewById(R.id.menu_wifi_transfer).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_toggle).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_interval).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_keycode).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_tap_toggle).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_tap_interval).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_delay).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_sos_frame_refresh).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_shake_refresh).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_shake_count).setOnClickListener(this);
    rootView.findViewById(R.id.auto_refresh_shake_amplitude).setOnClickListener(this);
    rootView.findViewById(R.id.openDeviceManager).setOnClickListener(this);

    showStatusBar = rootView.findViewById(R.id.showStatusBar);
    showCustomIcon = rootView.findViewById(R.id.showCustomIcon);
    wifiTransferStatus = rootView.findViewById(R.id.wifi_transfer_status);
    wifiTransferAddr = rootView.findViewById(R.id.wifi_transfer_addr);
    wifiTransferQr = rootView.findViewById(R.id.wifi_transfer_qr);
    autoRefreshStatus = rootView.findViewById(R.id.auto_refresh_status);
    autoRefreshInterval = rootView.findViewById(R.id.auto_refresh_interval_text);
    autoRefreshKeyCode = rootView.findViewById(R.id.auto_refresh_keycode_text);
    autoRefreshTapStatus = rootView.findViewById(R.id.auto_refresh_tap_status);
    autoRefreshTapInterval = rootView.findViewById(R.id.auto_refresh_tap_interval_text);
    autoRefreshDelay = rootView.findViewById(R.id.auto_refresh_delay_text);
    autoRefreshSosFrameRefreshValue = rootView.findViewById(R.id.auto_refresh_sos_frame_refresh_value);
    autoRefreshShakeRefreshValue = rootView.findViewById(R.id.auto_refresh_shake_refresh_value);
    autoRefreshShakeCountValue = rootView.findViewById(R.id.auto_refresh_shake_count_value);
    autoRefreshShakeAmplitudeValue =
        rootView.findViewById(R.id.auto_refresh_shake_amplitude_value);
    hideDivider = rootView.findViewById(R.id.hideDivider);
    fontControl = rootView.findViewById(R.id.font_control);
    colNumSpinner = rootView.findViewById(R.id.col_num_spinner);
    rowNumSpinner = rootView.findViewById(R.id.row_num_spinner);
    appNameLinesSpinner = rootView.findViewById(R.id.appNameLine);
    sortModeSpinner = rootView.findViewById(R.id.sortModeSpinner);

    showStatusBar.setOnClickListener(this);
    hideDivider.setOnClickListener(this);
    showCustomIcon.setOnClickListener(this);

    // 初始化 UI 状态
    showStatusBar.getPaint().setStrikeThruText(config.isShowStatusBar());
    hideDivider.getPaint().setStrikeThruText(config.isHideDivider());
    hideDivider.setText(config.isHideDivider() ? "显示分隔线" : "隐藏分隔线");
    showCustomIcon.getPaint().setStrikeThruText(config.isShowCustomIcon());
    fontControl.setProgress((int) ((config.getFontSize() - 10) * 10));
  }

  private void initSpinners() {
    rowNumSpinner.setSelection(config.getRowNum(isLandscapeOrientation()) - 2, false);
    rowNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int rowNum = position + 2;
        config.setRowNum(isLandscapeOrientation(), rowNum);
        listener.onRowNumChanged(rowNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    colNumSpinner.setSelection(config.getColNum(isLandscapeOrientation()) - 2, false);
    colNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int colNum = position + 2;
        config.setColNum(isLandscapeOrientation(), colNum);
        listener.onColNumChanged(colNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    appNameLinesSpinner.setSelection(getAppLineSpinnerSelectPosition(), false);
    appNameLinesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int lines = (position == 3) ? Integer.MAX_VALUE : position;
        config.setAppNameLines(lines);
        listener.onAppNameLinesChanged(lines);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    sortModeSpinner.setSelection(config.getSortMode(), false);
    sortModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (AppSortComparator.modeNeedsUsageStats(position)
            && !AppSortComparator.hasUsageStatsPermission(getActivity())) {
          Toast.makeText(getActivity(), R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
          }
          sortModeSpinner.setSelection(config.getSortMode(), false);
          return;
        }
        config.setSortMode(position);
        listener.onSortModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
  }

  private void initFontControl() {
    fontControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          float newSize = 10 + progress / 10f;
          config.setFontSize(newSize);
          listener.onFontSizeChanged(newSize);
        }
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
  }

  private int getAppLineSpinnerSelectPosition() {
    int lines = config.getAppNameLines();
    return (lines <= 2) ? lines : 3;
  }

  private boolean isLandscapeOrientation() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  // =========================================================================
  // 点击处理
  // =========================================================================

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (id == R.id.toBack || id == R.id.rootView) {
      getActivity().onBackPressed();
    } else if (id == R.id.deleteApp) {
      handleDeleteApp();
    } else if (id == R.id.showStatusBar) {
      handleToggleStatusBar();
    } else if (id == R.id.helpAbout) {
      AboutDialog.getInstance(getActivity()).show();
    } else if (id == R.id.btnHideFontControl) {
      rootView.findViewById(R.id.menuList).setVisibility(View.VISIBLE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.GONE);
    } else if (id == R.id.changeFontSize) {
      rootView.findViewById(R.id.menuList).setVisibility(View.GONE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.VISIBLE);
    } else if (id == R.id.hideDivider) {
      handleToggleDivider();
    } else if (id == R.id.menu_wifi_transfer) {
      handleWifiTransfer();
    } else if (id == R.id.auto_refresh_toggle) {
      handleToggleAutoRefresh();
    } else if (id == R.id.auto_refresh_interval) {
      handleAutoRefreshInterval();
    } else if (id == R.id.auto_refresh_keycode) {
      handleAutoRefreshKeyCode();
    } else if (id == R.id.auto_refresh_tap_toggle) {
      handleToggleAutoRefreshTap();
    } else if (id == R.id.auto_refresh_tap_interval) {
      handleAutoRefreshTapInterval();
    } else if (id == R.id.auto_refresh_delay) {
      handleAutoRefreshDelay();
    } else if (id == R.id.auto_refresh_sos_frame_refresh) {
      handleSosFrameRefresh();
    } else if (id == R.id.auto_refresh_shake_refresh) {
      handleShakeRefresh();
    } else if (id == R.id.auto_refresh_shake_count) {
      AutoRefreshSettings.setShakeRefreshCount(getActivity(),
          AutoRefreshSettings.nextShakeRefreshCount(getActivity()));
      updateAutoRefreshStatus();
    } else if (id == R.id.auto_refresh_shake_amplitude) {
      AutoRefreshSettings.setShakeRefreshAmplitude(getActivity(),
          AutoRefreshSettings.nextShakeRefreshAmplitude(getActivity()));
      updateAutoRefreshStatus();
    } else if (id == R.id.showWifiName) {
      handleShowWifiName();
    } else if (id == R.id.showCustomIcon) {
      handleToggleCustomIcon();
    } else if (id == R.id.openDeviceManager) {
      startActivity(new Intent().setComponent(
          new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")));
    }
  }

  private void handleDeleteApp() {
    listener.onEnterManageMode();
    getActivity().onBackPressed();
  }

  private void handleToggleStatusBar() {
    boolean newValue = !config.isShowStatusBar();
    config.setShowStatusBar(newValue);
    listener.onShowStatusBarChanged(newValue);
    getActivity().onBackPressed();
  }

  private void handleToggleDivider() {
    boolean newValue = !config.isHideDivider();
    config.setHideDivider(newValue);
    hideDivider.setText(newValue ? "显示分隔线" : "隐藏分隔线");
    listener.onHideDividerChanged(newValue);
    getActivity().onBackPressed();
  }

  private void handleWifiTransfer() {
    Runnable toggleTransfer = new Runnable() {
      @Override
      public void run() {
        if (!WifiFileTransferService.isRunning()) {
          if (WifiFileTransferService.isConnectedToWifi(getActivity())) {
            startWifiTransferServer();
          } else {
            Toast.makeText(getActivity(), R.string.toast_need_wifi_connnect, Toast.LENGTH_SHORT).show();
          }
        } else {
          stopWifiTransferServer();
        }
        updateWifiTransferStatus();
      }
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      toggleTransfer.run();
    } else {
      Utils.checkStoragePermission(getActivity(), toggleTransfer);
    }
  }

  private void handleToggleAutoRefresh() {
    boolean enabled = !AutoRefreshSettings.isEnabled(getActivity());
    AutoRefreshSettings.setEnabled(getActivity(), enabled);
    updateAutoRefreshStatus();
    if (enabled && !AutoRefreshSettings.isAccessibilityServiceEnabled(getActivity())) {
      Toast.makeText(getActivity(), R.string.setting_auto_refresh_accessibility_off, Toast.LENGTH_LONG).show();
      startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
  }

  private void handleAutoRefreshInterval() {
    AutoRefreshSettings.setInterval(getActivity(), AutoRefreshSettings.nextInterval(getActivity()));
    updateAutoRefreshStatus();
  }

  private void handleAutoRefreshKeyCode() {
    if (!AutoRefreshSettings.isAccessibilityServiceEnabled(getActivity())) {
      Toast.makeText(getActivity(), R.string.setting_auto_refresh_accessibility_off, Toast.LENGTH_LONG).show();
      startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
      return;
    }
    AutoRefreshSettings.setCaptureNextKey(getActivity(), true);
    Toast.makeText(getActivity(), R.string.setting_auto_refresh_capture_key, Toast.LENGTH_LONG).show();
  }

  private void handleToggleAutoRefreshTap() {
    boolean enabled = !AutoRefreshSettings.isTapEnabled(getActivity());
    AutoRefreshSettings.setTapEnabled(getActivity(), enabled);
    updateAutoRefreshStatus();
    if (enabled && !AutoRefreshSettings.isAccessibilityServiceEnabled(getActivity())) {
      Toast.makeText(getActivity(), R.string.setting_auto_refresh_accessibility_off, Toast.LENGTH_LONG).show();
      startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
  }

  private void handleAutoRefreshTapInterval() {
    AutoRefreshSettings.setTapInterval(getActivity(), AutoRefreshSettings.nextTapInterval(getActivity()));
    updateAutoRefreshStatus();
  }

  private void handleAutoRefreshDelay() {
    AutoRefreshSettings.setDelay(getActivity(), AutoRefreshSettings.nextDelay(getActivity()));
    updateAutoRefreshStatus();
  }

  private void handleSosFrameRefresh() {
    boolean enabled = !AutoRefreshSettings.isSosFrameRefreshEnabled(getActivity());
    AutoRefreshSettings.setSosFrameRefreshEnabled(getActivity(), enabled);
    updateAutoRefreshStatus();
  }

  private void handleShakeRefresh() {
    if (!AutoRefreshSettings.hasMotionSensor(getActivity())) {
      Toast.makeText(getActivity(), R.string.setting_shake_refresh_sensor_missing,
          Toast.LENGTH_LONG).show();
      return;
    }
    int mode = AutoRefreshSettings.nextShakeActionMode(getActivity());
    AutoRefreshSettings.setShakeActionMode(getActivity(), mode);
    updateAutoRefreshStatus();
    if (mode != AutoRefreshSettings.SHAKE_ACTION_OFF
        && !AutoRefreshSettings.isAccessibilityServiceEnabled(getActivity())) {
      Toast.makeText(getActivity(), R.string.setting_auto_refresh_accessibility_off,
          Toast.LENGTH_LONG).show();
      startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
  }

  private void handleShowWifiName() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10002);
    }
  }

  private void handleToggleCustomIcon() {
    Utils.checkStoragePermission(getActivity(), new Runnable() {
      @Override
      public void run() {
        boolean newValue = !config.isShowCustomIcon();
        config.setShowCustomIcon(newValue);
        listener.onShowCustomIconChanged(newValue);
        getActivity().onBackPressed();
      }
    });
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 10002) {
      WifiControl.reloadWifiName();
      getActivity().onBackPressed();
    }
  }

  // =========================================================================
  // 生命周期
  // =========================================================================

  @Override
  public void onResume() {
    super.onResume();
    updateWifiTransferStatus();
    updateAutoRefreshStatus();

    IntentFilter wifiFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    Utils.registerReceiverCompat(getActivity(), wifiReceiver, wifiFilter);

    IntentFilter wifiTransferFilter = new IntentFilter();
    wifiTransferFilter.addAction(WifiFileTransferService.ACTION_STARTED);
    wifiTransferFilter.addAction(WifiFileTransferService.ACTION_STOPPED);
    wifiTransferFilter.addAction(WifiFileTransferService.ACTION_FAILEDTOSTART);
    wifiTransferFilter.addAction(WifiFileTransferService.ACTION_FILE_RECEIVED);
    Utils.registerReceiverCompat(getActivity(), wifiTransferReceiver, wifiTransferFilter);
  }

  @Override
  public void onPause() {
    super.onPause();
    getActivity().unregisterReceiver(wifiReceiver);
    getActivity().unregisterReceiver(wifiTransferReceiver);
  }

  // =========================================================================
  // Wi-Fi 文件传输控制
  // =========================================================================

  private void startWifiTransferServer() {
    getActivity().startService(new Intent(getActivity(), WifiFileTransferService.class));
  }

  private void stopWifiTransferServer() {
    getActivity().stopService(new Intent(getActivity(), WifiFileTransferService.class));
  }

  private void updateWifiTransferStatus() {
    if (WifiFileTransferService.isConnectedToWifi(getActivity())) {
      if (WifiFileTransferService.isRunning()) {
        wifiTransferStatus.setText(R.string.setting_wifi_transfer_on);
        wifiTransferAddr.setVisibility(View.VISIBLE);
        String address = WifiFileTransferService.getAddress(getActivity());
        if (address != null) {
          wifiTransferAddr.setText(address);
          Bitmap qrCode = createQrCode(address);
          if (qrCode != null) {
            wifiTransferQr.setImageBitmap(qrCode);
            wifiTransferQr.setVisibility(View.VISIBLE);
          } else {
            wifiTransferQr.setVisibility(View.GONE);
          }
        } else {
          wifiTransferAddr.setVisibility(View.GONE);
          wifiTransferQr.setVisibility(View.GONE);
        }
      } else {
        wifiTransferStatus.setText(R.string.setting_wifi_transfer_off);
        wifiTransferAddr.setVisibility(View.GONE);
        wifiTransferQr.setVisibility(View.GONE);
      }
    } else {
      wifiTransferStatus.setText(R.string.setting_wifi_transfer_wifi_off);
      wifiTransferAddr.setVisibility(View.GONE);
      wifiTransferQr.setVisibility(View.GONE);
    }
  }

  private Bitmap createQrCode(String content) {
    int size = Utils.dp2Px(getActivity(), 160);
    Map<EncodeHintType, Object> hints = new HashMap<>();
    hints.put(EncodeHintType.MARGIN, 1);
    try {
      BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
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
    if (!AutoRefreshSettings.isAccessibilityServiceEnabled(getActivity())) {
      autoRefreshStatus.setText(R.string.setting_auto_refresh_accessibility_off);
      autoRefreshTapStatus.setText(R.string.setting_auto_refresh_accessibility_off);
    } else {
      if (AutoRefreshSettings.isEnabled(getActivity())) {
        autoRefreshStatus.setText(R.string.setting_auto_refresh_on);
      } else {
        autoRefreshStatus.setText(R.string.setting_auto_refresh_off);
      }
      if (AutoRefreshSettings.isTapEnabled(getActivity())) {
        autoRefreshTapStatus.setText(R.string.setting_auto_refresh_tap_on);
      } else {
        autoRefreshTapStatus.setText(R.string.setting_auto_refresh_tap_off);
      }
    }
    autoRefreshInterval.setText(getString(
        R.string.setting_auto_refresh_interval, AutoRefreshSettings.getInterval(getActivity())));
    autoRefreshKeyCode.setText(getString(
        R.string.setting_auto_refresh_keycode, AutoRefreshSettings.getRefreshKeyCode(getActivity())));
    autoRefreshTapInterval.setText(getString(
        R.string.setting_auto_refresh_tap_interval, AutoRefreshSettings.getTapInterval(getActivity())));
    autoRefreshDelay.setText(getString(
        R.string.setting_auto_refresh_delay, AutoRefreshSettings.getDelay(getActivity())));
    if (autoRefreshSosFrameRefreshValue != null) {
      autoRefreshSosFrameRefreshValue.setText(
          AutoRefreshSettings.isSosFrameRefreshEnabled(getActivity()) ? "开" : "关");
    }
    if (autoRefreshShakeRefreshValue != null) {
      int mode = AutoRefreshSettings.getShakeActionMode(getActivity());
      if (!AutoRefreshSettings.hasMotionSensor(getActivity())) {
        autoRefreshShakeRefreshValue.setText("不支持");
      } else if (mode != AutoRefreshSettings.SHAKE_ACTION_OFF
          && !AutoRefreshSettings.isAccessibilityServiceEnabled(getActivity())) {
        autoRefreshShakeRefreshValue.setText(
            getShakeActionLabel(mode) + " · 未授权");
      } else {
        autoRefreshShakeRefreshValue.setText(getShakeActionLabel(mode));
      }
    }
    if (autoRefreshShakeCountValue != null) {
      autoRefreshShakeCountValue.setText(
          AutoRefreshSettings.getShakeRefreshCount(getActivity()) + " 次");
    }
    if (autoRefreshShakeAmplitudeValue != null) {
      autoRefreshShakeAmplitudeValue.setText(getShakeAmplitudeLabel());
    }
  }

  private String getShakeAmplitudeLabel() {
    switch (AutoRefreshSettings.getShakeRefreshAmplitude(getActivity())) {
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

  // =========================================================================
  // 广播接收器
  // =========================================================================

  private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo netInfo = conMan.getActiveNetworkInfo();
      if (netInfo == null || netInfo.getType() != ConnectivityManager.TYPE_WIFI) {
        stopWifiTransferServer();
      }
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
}
