package cn.modificator.launcher.legado;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.modificator.launcher.R;
import cn.modificator.launcher.reading.ReadingProgressItem;
import cn.modificator.launcher.reading.ReadingProgressRepository;
import cn.modificator.launcher.reading.ReadingProgressResult;
import cn.modificator.launcher.widgets.ReadingProgressBar;

public class LegadoHomeController {
  private static final int READING_LIST_ID = R.id.readingBooksList;
  private static final int HOME_CARD_ID = R.id.homeReadingCard;
  private static final int HOME_TITLE_ID = R.id.homeReadingTitle;
  private static final int HOME_CHAPTER_ID = R.id.homeReadingChapter;
  private static final int HOME_PROGRESS_ID = R.id.homeReadingProgress;
  private static final int READING_CARD_HEIGHT_DP = 74;
  private static final int READING_CARD_GAP_DP = 8;
  private static final int READING_COLUMN_GAP_DP = 8;
  private static final int LANDSCAPE_READING_COLUMNS = 2;
  private static final int MAX_READING_ROWS = 8;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final List<ReadingProgressItem> cachedItems = new ArrayList<>();
  private final List<ReadingRow> readingRows = new ArrayList<>();
  private int pageIndex = 0;
  private ReadingProgressResult lastResult;
  private boolean readingRefreshInFlight;
  private boolean homeRefreshInFlight;
  private boolean readingLayoutRefreshPosted;

  public void refresh(final Activity activity) {
    refresh(activity, false);
  }

  private void refresh(final Activity activity, final boolean forceRemote) {
    final View panel = activity.findViewById(R.id.readingPage);
    if (panel == null) return;

    setupRefreshAction(activity);
    if (readingRefreshInFlight) return;
    readingRefreshInFlight = true;
    setRefreshActionText(activity, true);

    TextView status = activity.findViewById(R.id.readingStatus);
    if (status != null) {
      status.setText("阅读进度");
    }

    new Thread(new Runnable() {
      @Override
      public void run() {
        ReadingProgressRepository repository = new ReadingProgressRepository();
        final ReadingProgressResult result =
            forceRemote
                ? repository.refreshNow(activity.getApplicationContext())
                : repository.loadRecent(activity.getApplicationContext());
        mainHandler.post(new Runnable() {
          @Override
          public void run() {
            readingRefreshInFlight = false;
            setRefreshActionText(activity, false);
            if (!activity.isFinishing()) {
              lastResult = result;
              cachedItems.clear();
              cachedItems.addAll(result.items);
              pageIndex = clampPageIndex(pageIndex, getPageCount(activity));
              render(activity, result);
            }
          }
        });
      }
    }, "legado-home-refresh").start();
  }

  private void render(final Activity activity, ReadingProgressResult result) {
    setupRefreshAction(activity);
    TextView status = activity.findViewById(R.id.readingStatus);
    if (status != null) {
      if (result.error != null && result.items.isEmpty()) {
        status.setText(result.error);
      } else if (result.items.isEmpty()) {
        status.setText("阅读进度  书架为空");
      } else {
        status.setText("阅读进度");
      }
    }

    List<ReadingProgressItem> items = result.items;
    renderHomeCard(activity, items);
    int visibleRows = getVisibleRowCount(activity);
    int columns = getReadingColumnCount(activity);
    ensureReadingRows(activity, visibleRows);
    int pageCount = getPageCount(activity);
    pageIndex = clampPageIndex(pageIndex, pageCount);
    updatePageIndicator(activity, pageCount, pageIndex);

    int start = pageIndex * visibleRows;
    for (int i = 0; i < readingRows.size(); i++) {
      ReadingRow row = readingRows.get(i);
      if (i >= visibleRows) {
        row.root.setVisibility(View.GONE);
        continue;
      }

      int dataIndex = start + i;
      if (dataIndex >= items.size()) {
        row.root.setVisibility(columns > 1 ? View.INVISIBLE : View.GONE);
        row.root.setOnClickListener(null);
        row.title.setOnClickListener(null);
        row.chapter.setOnClickListener(null);
        row.title.setText("");
        row.chapter.setText("");
        row.progress.setProgress(0);
        continue;
      }

      final ReadingProgressItem item = items.get(dataIndex);
      int percent = item.progressPercent();
      row.root.setVisibility(View.VISIBLE);
      row.title.setText(item.title());
      row.chapter.setText(formatSubtitle(item, false));
      row.progress.setProgress(percent);
      View.OnClickListener openBook = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          openItem(activity, item);
        }
      };
      row.root.setOnClickListener(openBook);
      row.title.setOnClickListener(openBook);
      row.chapter.setOnClickListener(openBook);
    }

    postRenderAfterReadingLayout(activity);
  }

  public boolean canShowNextPage(Activity activity) {
    return pageIndex < getPageCount(activity) - 1;
  }

  public boolean canShowPreviousPage() {
    return pageIndex > 0;
  }

  public void showNextPage(Activity activity) {
    if (!canShowNextPage(activity)) return;
    pageIndex++;
    renderCurrentPage(activity);
  }

  public void showPreviousPage(Activity activity) {
    if (!canShowPreviousPage()) return;
    pageIndex--;
    renderCurrentPage(activity);
  }

  public void resetPage(Activity activity) {
    pageIndex = 0;
    renderCurrentPage(activity);
  }

  public void renderHomeCard(Activity activity) {
    if (isDrawerVisible(activity)) {
      renderHomeCard(activity, cachedItems);
      return;
    }
    if (!cachedItems.isEmpty()) {
      renderHomeCard(activity, cachedItems);
      return;
    }
    renderHomeCardLoading(activity);
    refreshHomeCard(activity);
  }

  public int getPageIndex(Activity activity) {
    return clampPageIndex(pageIndex, getPageCount(activity));
  }

  public int getPageCount(Activity activity) {
    int visibleRows = getVisibleRowCount(activity);
    if (visibleRows <= 0) return 1;
    if (cachedItems.isEmpty()) return 1;
    return Math.max(1, (cachedItems.size() + visibleRows - 1) / visibleRows);
  }

  public int getVisibleRowCount(Activity activity) {
    View list = activity.findViewById(READING_LIST_ID);
    int availableHeight = list == null ? 0 : list.getHeight();
    if (availableHeight <= 0) {
      View page = activity.findViewById(R.id.readingPage);
      int pageHeight = page == null ? 0 : page.getHeight();
      if (pageHeight <= 0) pageHeight = activity.getResources().getDisplayMetrics().heightPixels;
      View titleBar = activity.findViewById(R.id.readingTitleBar);
      availableHeight = pageHeight - viewBlockHeight(activity, titleBar, 36) - dp(activity, 8);
    }
    int columns = getReadingColumnCount(activity);
    int rowBlock = dp(activity, READING_CARD_HEIGHT_DP + READING_CARD_GAP_DP);
    int rows = rowBlock <= 0 ? 1 : availableHeight / rowBlock;
    rows = Math.max(1, Math.min(MAX_READING_ROWS, rows));
    return rows * columns;
  }

  private void renderCurrentPage(Activity activity) {
    if (lastResult != null) {
      render(activity, lastResult);
    }
  }

  private void renderHomeCard(final Activity activity, List<ReadingProgressItem> items) {
    View card = activity.findViewById(HOME_CARD_ID);
    TextView title = activity.findViewById(HOME_TITLE_ID);
    TextView chapter = activity.findViewById(HOME_CHAPTER_ID);
    ProgressBar progress = activity.findViewById(HOME_PROGRESS_ID);
    if (card == null || title == null || chapter == null || progress == null) return;
    if (isDrawerVisible(activity)) {
      card.setVisibility(View.GONE);
      return;
    }
    if (items == null || items.isEmpty()) {
      card.setVisibility(View.GONE);
      return;
    }

    final ReadingProgressItem item = items.get(0);
    card.setVisibility(View.VISIBLE);
    title.setText(item.title());
    chapter.setText(formatSubtitle(item, true));
    progress.setProgress(item.progressPercent());
    View.OnClickListener openBook = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openItem(activity, item);
      }
    };
    card.setOnClickListener(openBook);
    title.setOnClickListener(openBook);
  }

  private void refreshHomeCard(final Activity activity) {
    if (homeRefreshInFlight) return;
    homeRefreshInFlight = true;
    new Thread(new Runnable() {
      @Override
      public void run() {
        final ReadingProgressResult result =
            new ReadingProgressRepository().loadRecent(activity.getApplicationContext());
        mainHandler.post(new Runnable() {
          @Override
          public void run() {
            homeRefreshInFlight = false;
            if (activity.isFinishing()) return;
            lastResult = result;
            cachedItems.clear();
            cachedItems.addAll(result.items);
            pageIndex = clampPageIndex(pageIndex, getPageCount(activity));
            renderHomeCard(activity, cachedItems);
          }
        });
      }
    }, "legado-home-card-refresh").start();
  }

  private void renderHomeCardLoading(Activity activity) {
    View card = activity.findViewById(HOME_CARD_ID);
    TextView title = activity.findViewById(HOME_TITLE_ID);
    TextView chapter = activity.findViewById(HOME_CHAPTER_ID);
    ProgressBar progress = activity.findViewById(HOME_PROGRESS_ID);
    if (card == null || title == null || chapter == null || progress == null) return;
    if (isDrawerVisible(activity)) {
      card.setVisibility(View.GONE);
      return;
    }
    card.setVisibility(View.VISIBLE);
    title.setText("正在读取阅读进度");
    chapter.setText("从阅读来源同步中");
    progress.setProgress(0);
    card.setOnClickListener(null);
    title.setOnClickListener(null);
  }

  private void setupRefreshAction(final Activity activity) {
    TextView refreshAction = activity.findViewById(R.id.readingRefreshAction);
    if (refreshAction == null) return;
    setRefreshActionText(activity, readingRefreshInFlight);
    refreshAction.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        refresh(activity, true);
      }
    });
  }

  private void setRefreshActionText(Activity activity, boolean refreshing) {
    TextView refreshAction = activity.findViewById(R.id.readingRefreshAction);
    if (refreshAction == null) return;
    refreshAction.setText(refreshing ? "刷新中" : "刷新");
    refreshAction.setEnabled(!refreshing);
  }

  private void openItem(Activity activity, ReadingProgressItem item) {
    if (item == null) return;
    ReadingProgressRepository.markRefreshNeeded(activity, item.source);
    if (ReadingProgressItem.SOURCE_LEGADO.equals(item.source)) {
      Intent intent = new Intent(activity, LegadoOpenActivity.class);
      intent.putExtra(LegadoOpenActivity.EXTRA_BOOK_URL, item.openRef);
      activity.startActivity(intent);
      return;
    }

    if (openExternalReadingUri(activity, item)) {
      return;
    }

    if (!TextUtils.isEmpty(item.openPackageName)) {
      Intent launch = activity.getPackageManager().getLaunchIntentForPackage(item.openPackageName);
      if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(launch);
        return;
      }
    }
    Toast.makeText(activity, item.sourceLabel() + "未安装", Toast.LENGTH_SHORT).show();
  }

  private boolean openExternalReadingUri(Activity activity, ReadingProgressItem item) {
    String uri = item.openRef;
    if (ReadingProgressItem.SOURCE_WEREAD.equals(item.source) && !isUri(uri)) {
      uri = wereadReadingUri(item.id);
    }
    if (!isUri(uri)) return false;

    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    if (!TextUtils.isEmpty(item.openPackageName)) {
      intent.setPackage(item.openPackageName);
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      activity.startActivity(intent);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isUri(String value) {
    return !TextUtils.isEmpty(value)
        && (value.startsWith("weread://")
        || value.startsWith("http://")
        || value.startsWith("https://"));
  }

  private String wereadReadingUri(String bookId) {
    if (TextUtils.isEmpty(bookId)) return "";
    return "weread://reading?bId=" + bookId;
  }

  private String formatSubtitle(ReadingProgressItem item, boolean homeCard) {
    StringBuilder builder = new StringBuilder();
    if (!TextUtils.isEmpty(item.sourceLabel())) {
      builder.append(item.sourceLabel());
    }
    String subtitle = item.subtitle();
    if (!TextUtils.isEmpty(subtitle)) {
      if (builder.length() > 0) builder.append(" · ");
      builder.append(subtitle);
    }
    if (builder.length() > 0) builder.append(homeCard ? "  " : " · ");
    builder.append(item.progressPercent()).append("%");
    return builder.toString();
  }

  private boolean isDrawerVisible(Activity activity) {
    View drawerTitle = activity.findViewById(R.id.appsSectionTitle);
    return drawerTitle != null && drawerTitle.getVisibility() == View.VISIBLE;
  }

  private int clampPageIndex(int value, int pageCount) {
    return Math.max(0, Math.min(value, Math.max(0, pageCount - 1)));
  }

  private void updatePageIndicator(Activity activity, int pageCount, int pageIndex) {
    View readingPage = activity.findViewById(R.id.readingPage);
    View view = activity.findViewById(R.id.pageIndicator);
    if (view instanceof cn.modificator.launcher.widgets.PageIndicatorView) {
      if (readingPage == null || readingPage.getVisibility() == View.VISIBLE) {
        ((cn.modificator.launcher.widgets.PageIndicatorView) view).setPages(pageCount, pageIndex);
      }
    }
  }

  private void ensureReadingRows(Activity activity, int count) {
    View listView = activity.findViewById(READING_LIST_ID);
    if (!(listView instanceof LinearLayout)) return;
    LinearLayout list = (LinearLayout) listView;
    int columns = getReadingColumnCount(activity);
    while (readingRows.size() < count) {
      ReadingRow row = createReadingRow(activity);
      readingRows.add(row);
    }

    list.setOrientation(LinearLayout.VERTICAL);
    list.removeAllViews();
    for (int i = 0; i < count; i += columns) {
      if (columns <= 1) {
        ReadingRow row = readingRows.get(i);
        detachFromParent(row.root);
        applyReadingRowLayout(activity, row.root, false, 0);
        list.addView(row.root);
      } else {
        LinearLayout group = new LinearLayout(activity);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, READING_CARD_HEIGHT_DP));
        groupParams.topMargin = dp(activity, READING_CARD_GAP_DP);
        group.setLayoutParams(groupParams);
        group.setOrientation(LinearLayout.HORIZONTAL);
        list.addView(group);
        for (int column = 0; column < columns && i + column < count; column++) {
          ReadingRow row = readingRows.get(i + column);
          detachFromParent(row.root);
          applyReadingRowLayout(activity, row.root, true, column);
          group.addView(row.root);
        }
      }
    }
    for (int i = 0; i < readingRows.size(); i++) {
      readingRows.get(i).root.setVisibility(i < count ? View.VISIBLE : View.GONE);
    }
  }

  private ReadingRow createReadingRow(Activity activity) {
    LinearLayout root = new LinearLayout(activity);
    LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, READING_CARD_HEIGHT_DP));
    rootParams.topMargin = dp(activity, READING_CARD_GAP_DP);
    root.setLayoutParams(rootParams);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER_VERTICAL);
    root.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
    root.setBackgroundResource(R.drawable.bg_launcher_card);
    root.setClickable(true);
    root.setFocusable(true);

    TextView title = new TextView(activity);
    title.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    title.setGravity(Gravity.CENTER_VERTICAL);
    title.setSingleLine(true);
    title.setIncludeFontPadding(false);
    title.setTextColor(0xff000000);
    title.setTextSize(17);
    root.addView(title);

    TextView chapter = new TextView(activity);
    chapter.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    chapter.setGravity(Gravity.CENTER_VERTICAL);
    chapter.setSingleLine(true);
    chapter.setIncludeFontPadding(false);
    chapter.setTextColor(0xff000000);
    chapter.setTextSize(13);
    root.addView(chapter);

    ReadingProgressBar progress = new ReadingProgressBar(
        activity, null, android.R.attr.progressBarStyleHorizontal);
    LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 10));
    progressParams.topMargin = dp(activity, 4);
    progress.setLayoutParams(progressParams);
    progress.setMax(100);
    root.addView(progress);

    return new ReadingRow(root, title, chapter, progress);
  }

  private int getReadingColumnCount(Activity activity) {
    return activity.getResources().getConfiguration().orientation
        == Configuration.ORIENTATION_LANDSCAPE ? LANDSCAPE_READING_COLUMNS : 1;
  }

  private void applyReadingRowLayout(Activity activity, View root, boolean inGrid, int column) {
    if (inGrid) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
      if (column > 0) params.leftMargin = dp(activity, READING_COLUMN_GAP_DP);
      root.setLayoutParams(params);
    } else {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, READING_CARD_HEIGHT_DP));
      params.topMargin = dp(activity, READING_CARD_GAP_DP);
      root.setLayoutParams(params);
    }
  }

  private void detachFromParent(View view) {
    ViewParent parent = view.getParent();
    if (parent instanceof ViewGroup) {
      ((ViewGroup) parent).removeView(view);
    }
  }

  private void postRenderAfterReadingLayout(final Activity activity) {
    View list = activity.findViewById(READING_LIST_ID);
    if (list == null || list.getHeight() > 0 || readingLayoutRefreshPosted) return;
    readingLayoutRefreshPosted = true;
    list.post(new Runnable() {
      @Override
      public void run() {
        readingLayoutRefreshPosted = false;
        if (!activity.isFinishing()) {
          renderCurrentPage(activity);
        }
      }
    });
  }

  private int viewBlockHeight(Activity activity, View view, int fallbackDp) {
    int height = view == null ? 0 : view.getHeight();
    ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
    if (height <= 0 && params != null && params.height > 0) height = params.height;
    if (height <= 0) height = dp(activity, fallbackDp);
    int margin = 0;
    if (params instanceof ViewGroup.MarginLayoutParams) {
      ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
      margin = marginParams.topMargin + marginParams.bottomMargin;
    }
    return height + margin;
  }

  private int dp(Activity activity, int value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }

  private static class ReadingRow {
    final LinearLayout root;
    final TextView title;
    final TextView chapter;
    final ReadingProgressBar progress;

    ReadingRow(LinearLayout root, TextView title, TextView chapter, ReadingProgressBar progress) {
      this.root = root;
      this.title = title;
      this.chapter = chapter;
      this.progress = progress;
    }
  }
}
