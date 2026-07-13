package cn.modificator.launcher.wifitransfer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import cn.modificator.launcher.Utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WifiFileTransferService extends Service implements Runnable {

  public static final int DEFAULT_PORT = 8080;
  public static final String ACTION_STARTED =
      "cn.modificator.launcher.wifitransfer.WifiFileTransferService.STARTED";
  public static final String ACTION_STOPPED =
      "cn.modificator.launcher.wifitransfer.WifiFileTransferService.STOPPED";
  public static final String ACTION_FAILEDTOSTART =
      "cn.modificator.launcher.wifitransfer.WifiFileTransferService.FAILED_TO_START";
  public static final String ACTION_FILE_RECEIVED =
      "cn.modificator.launcher.wifitransfer.WifiFileTransferService.FILE_RECEIVED";

  private static final String TAG = WifiFileTransferService.class.getSimpleName();
  private static final Charset UTF_8 = Charset.forName("UTF-8");
  private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
  private static final int MAX_HEADER_SIZE = 64 * 1024;

  private static volatile Thread serverThread;
  private static volatile int runningPort = DEFAULT_PORT;

  private volatile boolean shouldExit;
  private ServerSocket serverSocket;
  private ExecutorService clientExecutor;

  private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (!isConnectedToWifi(context)) {
        stopSelf();
      }
    }
  };

  @Override
  public void onCreate() {
    super.onCreate();
    IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    Utils.registerReceiverCompat(this, networkReceiver, filter);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (isRunning()) {
      return START_STICKY;
    }
    shouldExit = false;
    clientExecutor = Executors.newCachedThreadPool();
    serverThread = new Thread(this, "WifiFileTransferServer");
    serverThread.start();
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void run() {
    try {
      serverSocket = openServerSocket();
      runningPort = serverSocket.getLocalPort();
      sendBroadcast(new Intent(ACTION_STARTED));
      while (!shouldExit) {
        final Socket socket = serverSocket.accept();
        clientExecutor.execute(new Runnable() {
          @Override
          public void run() {
            handleClient(socket);
          }
        });
      }
    } catch (IOException e) {
      if (!shouldExit) {
        Log.e(TAG, "Failed to run HTTP server", e);
        sendBroadcast(new Intent(ACTION_FAILEDTOSTART));
      }
    } finally {
      closeServerSocket();
      if (clientExecutor != null) {
        clientExecutor.shutdownNow();
      }
      serverThread = null;
    }
  }

  @Override
  public void onDestroy() {
    shouldExit = true;
    closeServerSocket();
    if (clientExecutor != null) {
      clientExecutor.shutdownNow();
    }
    unregisterReceiver(networkReceiver);
    sendBroadcast(new Intent(ACTION_STOPPED));
    super.onDestroy();
  }

  private ServerSocket openServerSocket() throws IOException {
    IOException lastError = null;
    for (int port = DEFAULT_PORT; port < DEFAULT_PORT + 20; port++) {
      ServerSocket socket = new ServerSocket();
      socket.setReuseAddress(true);
      try {
        socket.bind(new InetSocketAddress(port));
        return socket;
      } catch (IOException e) {
        lastError = e;
        try {
          socket.close();
        } catch (IOException ignored) {
        }
      }
    }
    throw lastError == null ? new IOException("No available port") : lastError;
  }

  private void closeServerSocket() {
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException ignored) {
      }
      serverSocket = null;
    }
  }

  private void handleClient(Socket socket) {
    try {
      socket.setSoTimeout(120000);
      BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
      OutputStream output = socket.getOutputStream();
      HttpRequest request = readRequest(input);
      if (request == null) {
        return;
      }

      if ("GET".equals(request.method) && isRootPath(request.path)) {
        sendResponse(output, 200, "OK", "text/html; charset=utf-8", buildUploadPage());
      } else if ("POST".equals(request.method) && request.path.startsWith("/upload")) {
        handleUpload(input, output, request);
      } else if ("OPTIONS".equals(request.method)) {
        sendResponse(output, 204, "No Content", "text/plain; charset=utf-8", "");
      } else {
        sendResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found");
      }
    } catch (Exception e) {
      Log.e(TAG, "HTTP request failed", e);
    } finally {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }
  }

  private HttpRequest readRequest(InputStream input) throws IOException {
    ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
    int previous3 = -1;
    int previous2 = -1;
    int previous1 = -1;
    int current;
    while ((current = input.read()) != -1) {
      headerBytes.write(current);
      if (headerBytes.size() > MAX_HEADER_SIZE) {
        throw new IOException("HTTP header is too large");
      }
      if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && current == '\n') {
        break;
      }
      previous3 = previous2;
      previous2 = previous1;
      previous1 = current;
    }
    if (headerBytes.size() == 0) {
      return null;
    }

    String headerText = new String(headerBytes.toByteArray(), ISO_8859_1);
    String[] lines = headerText.split("\r\n");
    if (lines.length == 0) {
      return null;
    }
    String[] parts = lines[0].split(" ");
    if (parts.length < 2) {
      return null;
    }

    HttpRequest request = new HttpRequest();
    request.method = parts[0].toUpperCase(Locale.US);
    request.path = parts[1];
    for (int i = 1; i < lines.length; i++) {
      int colon = lines[i].indexOf(':');
      if (colon > 0) {
        String key = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
        String value = lines[i].substring(colon + 1).trim();
        request.headers.put(key, value);
      }
    }
    return request;
  }

  private void handleUpload(InputStream input, OutputStream responseOutput, HttpRequest request)
      throws IOException {
    long contentLength = getContentLength(request);
    boolean chunked = isChunked(request);
    if (contentLength < 0 && !chunked) {
      sendResponse(responseOutput, 411, "Length Required", "text/plain; charset=utf-8",
          "Content-Length is required");
      return;
    }

    String fileName = sanitizeFileName(getQueryParam(request.path, "name"));
    String contentType = normalizeContentType(request.headers.get("content-type"), fileName);
    DownloadTarget target = null;
    try {
      target = openDownloadTarget(fileName, contentType);
      if (chunked) {
        copyChunked(input, target.output);
      } else {
        copyBytes(input, target.output, contentLength);
      }
      target.finish(this);
      sendBroadcast(new Intent(ACTION_FILE_RECEIVED));
      sendResponse(responseOutput, 200, "OK", "text/plain; charset=utf-8",
          "Saved: " + target.displayName);
    } catch (IOException e) {
      if (target != null) {
        target.abort(this);
      }
      Log.e(TAG, "Failed to save upload", e);
      sendResponse(responseOutput, 500, "Internal Server Error", "text/plain; charset=utf-8",
          "Failed to save file");
    }
  }

  private long getContentLength(HttpRequest request) {
    String value = request.headers.get("content-length");
    if (TextUtils.isEmpty(value)) {
      return -1;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private boolean isChunked(HttpRequest request) {
    String value = request.headers.get("transfer-encoding");
    return value != null && value.toLowerCase(Locale.US).contains("chunked");
  }

  private DownloadTarget openDownloadTarget(String fileName, String contentType) throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
      values.put(MediaStore.MediaColumns.MIME_TYPE, contentType);
      values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
      values.put(MediaStore.MediaColumns.IS_PENDING, 1);
      Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
      if (uri == null) {
        throw new IOException("Failed to create download entry");
      }
      OutputStream output = getContentResolver().openOutputStream(uri);
      if (output == null) {
        getContentResolver().delete(uri, null, null);
        throw new IOException("Failed to open download output stream");
      }
      return new DownloadTarget(output, uri, fileName);
    }

    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    if (!downloads.exists() && !downloads.mkdirs()) {
      throw new IOException("Failed to create downloads directory");
    }
    File targetFile = uniqueFile(downloads, fileName);
    return new DownloadTarget(new FileOutputStream(targetFile), targetFile, targetFile.getName());
  }

  private File uniqueFile(File directory, String fileName) {
    File file = new File(directory, fileName);
    if (!file.exists()) {
      return file;
    }

    int dot = fileName.lastIndexOf('.');
    String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
    String extension = dot > 0 ? fileName.substring(dot) : "";
    int index = 1;
    do {
      file = new File(directory, baseName + " (" + index + ")" + extension);
      index++;
    } while (file.exists());
    return file;
  }

  private void copyBytes(InputStream input, OutputStream output, long length) throws IOException {
    byte[] buffer = new byte[16 * 1024];
    long remaining = length;
    while (remaining > 0) {
      int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read == -1) {
        throw new EOFException("Upload ended early");
      }
      output.write(buffer, 0, read);
      remaining -= read;
    }
  }

  private void copyChunked(InputStream input, OutputStream output) throws IOException {
    while (true) {
      String sizeLine = readBodyLine(input);
      int extensionStart = sizeLine.indexOf(';');
      if (extensionStart >= 0) {
        sizeLine = sizeLine.substring(0, extensionStart);
      }
      long chunkSize;
      try {
        chunkSize = Long.parseLong(sizeLine.trim(), 16);
      } catch (NumberFormatException e) {
        throw new IOException("Invalid chunk size", e);
      }
      if (chunkSize == 0) {
        while (!TextUtils.isEmpty(readBodyLine(input))) {
          // Drain chunk trailers.
        }
        return;
      }
      copyBytes(input, output, chunkSize);
      readBodyLine(input);
    }
  }

  private String readBodyLine(InputStream input) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int current;
    while ((current = input.read()) != -1) {
      if (current == '\n') {
        break;
      }
      if (current != '\r') {
        line.write(current);
      }
      if (line.size() > MAX_HEADER_SIZE) {
        throw new IOException("HTTP body line is too large");
      }
    }
    return new String(line.toByteArray(), ISO_8859_1);
  }

  private String normalizeContentType(String contentType, String fileName) {
    if (!TextUtils.isEmpty(contentType)) {
      int semicolon = contentType.indexOf(';');
      if (semicolon > 0) {
        contentType = contentType.substring(0, semicolon);
      }
      contentType = contentType.trim();
      if (!TextUtils.isEmpty(contentType)) {
        return contentType;
      }
    }
    String guessed = URLConnection.guessContentTypeFromName(fileName);
    return TextUtils.isEmpty(guessed) ? "application/octet-stream" : guessed;
  }

  private String sanitizeFileName(String fileName) {
    if (TextUtils.isEmpty(fileName)) {
      return "upload.bin";
    }
    fileName = fileName.trim();
    int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
    if (slash >= 0 && slash + 1 < fileName.length()) {
      fileName = fileName.substring(slash + 1);
    }

    StringBuilder safeName = new StringBuilder(fileName.length());
    for (int i = 0; i < fileName.length(); i++) {
      char c = fileName.charAt(i);
      if (c < 32 || c == 127 || "\\/:*?\"<>|".indexOf(c) >= 0) {
        safeName.append('_');
      } else {
        safeName.append(c);
      }
    }
    String result = safeName.toString().trim();
    if (TextUtils.isEmpty(result) || ".".equals(result) || "..".equals(result)) {
      return "upload.bin";
    }
    if (result.length() > 120) {
      result = result.substring(0, 120);
    }
    return result;
  }

  private String getQueryParam(String path, String paramName) {
    int queryStart = path.indexOf('?');
    if (queryStart < 0 || queryStart + 1 >= path.length()) {
      return null;
    }
    String[] pairs = path.substring(queryStart + 1).split("&");
    for (String pair : pairs) {
      int equals = pair.indexOf('=');
      String key = equals >= 0 ? pair.substring(0, equals) : pair;
      if (paramName.equals(urlDecode(key))) {
        return equals >= 0 ? urlDecode(pair.substring(equals + 1)) : "";
      }
    }
    return null;
  }

  private String urlDecode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (Exception e) {
      return value;
    }
  }

  private boolean isRootPath(String path) {
    return "/".equals(path) || path.startsWith("/?");
  }

  private void sendResponse(OutputStream output, int code, String status, String contentType,
                            String body) throws IOException {
    byte[] bodyBytes = body.getBytes(UTF_8);
    String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
        + "Content-Type: " + contentType + "\r\n"
        + "Content-Length: " + bodyBytes.length + "\r\n"
        + "Access-Control-Allow-Origin: *\r\n"
        + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
        + "Connection: close\r\n"
        + "\r\n";
    output.write(headers.getBytes(ISO_8859_1));
    output.write(bodyBytes);
    output.flush();
  }

  private String buildUploadPage() {
    return "<!doctype html><html><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<title>Wi-Fi File Transfer</title>"
        + "<style>"
        + "body{font-family:sans-serif;margin:0;background:#f5f5f5;color:#111;}"
        + "main{max-width:520px;margin:0 auto;padding:24px;}"
        + "h1{font-size:24px;margin:0 0 16px;}"
        + "input,button{font-size:16px;width:100%;box-sizing:border-box;margin:8px 0;padding:12px;}"
        + "button{background:#111;color:#fff;border:0;}"
        + "li{margin:8px 0;word-break:break-all;}"
        + "</style></head><body><main>"
        + "<h1>Wi-Fi File Transfer</h1>"
        + "<input id=\"files\" type=\"file\" multiple>"
        + "<button id=\"upload\">Upload</button>"
        + "<ul id=\"log\"></ul>"
        + "<script>"
        + "const files=document.getElementById('files'),log=document.getElementById('log');"
        + "function add(t){const li=document.createElement('li');li.textContent=t;log.appendChild(li);}"
        + "document.getElementById('upload').onclick=async()=>{"
        + "if(!files.files.length){add('Please choose files first.');return;}"
        + "for(const file of files.files){"
        + "add('Uploading '+file.name+' ...');"
        + "try{const res=await fetch('/upload?name='+encodeURIComponent(file.name),"
        + "{method:'POST',headers:{'Content-Type':file.type||'application/octet-stream'},body:file});"
        + "add((res.ok?'Saved ':'Failed ')+file.name);}"
        + "catch(e){add('Failed '+file.name);}"
        + "}"
        + "};"
        + "</script></main></body></html>";
  }

  public static boolean isRunning() {
    Thread thread = serverThread;
    return thread != null && thread.isAlive();
  }

  public static int getPort() {
    return runningPort;
  }

  public static String getAddress(Context context) {
    InetAddress address = getLocalInetAddress(context);
    if (address == null) {
      return null;
    }
    return "http://" + address.getHostAddress() + ":" + getPort() + "/";
  }

  public static boolean isConnectedToWifi(Context context) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
    return ni != null && ni.isConnected() && ni.getType() == ConnectivityManager.TYPE_WIFI;
  }

  public static InetAddress getLocalInetAddress(Context context) {
    if (isConnectedToWifi(context)) {
      WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
      if (wm != null && wm.getConnectionInfo() != null) {
        int ipAddress = wm.getConnectionInfo().getIpAddress();
        if (ipAddress != 0) {
          return intToInet(ipAddress);
        }
      }
    }

    try {
      Enumeration<NetworkInterface> netinterfaces = NetworkInterface.getNetworkInterfaces();
      while (netinterfaces.hasMoreElements()) {
        NetworkInterface netinterface = netinterfaces.nextElement();
        for (InetAddress address : Collections.list(netinterface.getInetAddresses())) {
          if (!address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
            return address;
          }
        }
      }
    } catch (SocketException e) {
      Log.e(TAG, "Failed to find local address", e);
    }
    return null;
  }

  private static InetAddress intToInet(int value) {
    byte[] bytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      bytes[i] = (byte) (value >> (i * 8));
    }
    try {
      return InetAddress.getByAddress(bytes);
    } catch (UnknownHostException e) {
      return null;
    }
  }

  private static class HttpRequest {
    String method;
    String path;
    final Map<String, String> headers = new HashMap<>();
  }

  private static class DownloadTarget {
    OutputStream output;
    final Uri uri;
    final File file;
    final String displayName;

    DownloadTarget(OutputStream output, Uri uri, String displayName) {
      this.output = output;
      this.uri = uri;
      this.file = null;
      this.displayName = displayName;
    }

    DownloadTarget(OutputStream output, File file, String displayName) {
      this.output = output;
      this.uri = null;
      this.file = file;
      this.displayName = displayName;
    }

    void finish(Context context) throws IOException {
      close();
      if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);
      }
    }

    void abort(Context context) {
      try {
        close();
      } catch (IOException ignored) {
      }
      if (uri != null) {
        context.getContentResolver().delete(uri, null, null);
      } else if (file != null) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
      }
    }

    private void close() throws IOException {
      if (output != null) {
        output.close();
        output = null;
      }
    }
  }
}
