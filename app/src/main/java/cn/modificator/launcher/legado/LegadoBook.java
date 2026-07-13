package cn.modificator.launcher.legado;

class LegadoBook {
  String name;
  String author;
  String bookUrl;
  String coverUrl;
  String coverPath;
  String apiBaseUrl;
  String durChapterTitle;
  String latestChapterTitle;
  int durChapterIndex;
  int durChapterPos;
  long durChapterTime;
  int totalChapterNum;

  int progressPercent() {
    if (totalChapterNum <= 0) return 0;
    int chapter = Math.max(0, durChapterIndex);
    return Math.max(0, Math.min(100, (chapter * 100) / totalChapterNum));
  }

  String displayName() {
    String title = name == null || name.length() == 0 ? "未命名书籍" : name;
    if (author == null || author.length() == 0) return title;
    return title + " - " + author;
  }

  String title() {
    return name == null || name.length() == 0 ? "未命名书籍" : name;
  }

  String chapterText() {
    String title = durChapterTitle == null ? "" : durChapterTitle.trim();
    int chapter = Math.max(0, durChapterIndex) + 1;
    if (title.length() == 0) {
      return "第" + chapter + "章";
    }
    if (title.startsWith("第") || title.startsWith("Chapter") || title.startsWith("chapter")) return title;
    return "第 " + chapter + " 章  " + title;
  }
}
