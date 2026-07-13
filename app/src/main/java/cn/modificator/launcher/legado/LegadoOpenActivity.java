package cn.modificator.launcher.legado;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

public class LegadoOpenActivity extends Activity {
  public static final String EXTRA_BOOK_URL = "bookUrl";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String bookUrl = getIntent().getStringExtra(EXTRA_BOOK_URL);
    if (TextUtils.isEmpty(bookUrl)) {
      Toast.makeText(this, "无法打开书籍：缺少 bookUrl", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    String packageName = LegadoBooksRepository.installedPackage(this);
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(
        packageName,
        LegadoBooksRepository.readActivityForPackage(packageName)));
    if (LegadoBooksRepository.isLegacyPackage(packageName)) {
      intent.putExtra("noteUrl", bookUrl);
    } else {
      intent.putExtra("bookUrl", bookUrl);
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    try {
      startActivity(intent);
    } catch (Exception e) {
      Toast.makeText(this, "无法打开 Legado，请确认已安装并启用阅读", Toast.LENGTH_LONG).show();
    }
    finish();
  }
}
