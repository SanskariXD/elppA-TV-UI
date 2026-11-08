package com.gathin.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Cinematic Home. Google Assistant and phone remote remain system-owned. */
public final class ElppaHomeActivity extends Activity {
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService dataWorker = Executors.newSingleThreadExecutor();
  private final Map<String, String> sourceNames = new HashMap<>();
  private String artworkRequestKey = "";
  private android.graphics.Bitmap playFrost;
  private HomePreferences preferences;
  private ArtworkLoader artwork;
  private TvContentRepository repository;
  private TvContentRepository.Snapshot snapshot;
  private Future<?> task;
  private CancellationSignal cancellation;
  private int generation, heroIndex;
  private boolean active, observing, initialFocus;
  private boolean loading = true;
  private FrameLayout root;
  private ImageView backdrop, network, settings, action;
  private TextClock clock;
  private TextView title, subtitle, empty, status;
  private HomeRail appsRail, mediaRail;
  private final List<AppCatalog.App> apps = new ArrayList<>();
  private List<MediaEntry> heroes = new ArrayList<>();
  private MediaEntry selected;
  private float scale = 1;
  private final Runnable refreshSoon = this::refresh;
  private final Runnable rotate =
      new Runnable() {
        @Override
        public void run() {
          if (!active) return;
          if (preferences.rotate() && heroes.size() > 1 && appsRail.hasFocus() && hasWindowFocus())
            showHero(heroes.get(++heroIndex % heroes.size()));
          main.postDelayed(this, 15000);
        }
      };
  private final Runnable networkTick =
      new Runnable() {
        @Override
        public void run() {
          if (active) {
            updateNetwork();
            main.postDelayed(this, 30000);
          }
        }
      };
  private final ContentObserver observer =
      new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
          main.removeCallbacks(refreshSoon);
          main.postDelayed(refreshSoon, 500);
        }
      };
  private final BroadcastReceiver packages =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          sourceNames.clear();
          main.removeCallbacks(refreshSoon);
          main.postDelayed(refreshSoon, 300);
        }
      };

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    preferences = new HomePreferences(this);
    artwork = new ArtworkLoader(this);
    repository = new TvContentRepository(this);
    getWindow()
        .setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    buildScreen();
    artwork.setHeroListener(
        frost -> {
          playFrost = frost;
          stylePlay();
        });
    render();
  }

  private void buildScreen() {
    root = new FrameLayout(this);
    root.setBackgroundColor(ElppaUi.BACKGROUND);
    root.setClipChildren(false);
    setContentView(root);
    backdrop = new ImageView(this);
    backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
    backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
    View side = new View(this);
    side.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {0xE6090B10, 0x66090B10, 0x00090B10}));
    root.addView(side, new FrameLayout.LayoutParams(-1, -1));
    View bottom = new View(this);
    bottom.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0x00090B10, 0x00090B10, 0xD9090B10, 0xFF090B10}));
    root.addView(bottom, new FrameLayout.LayoutParams(-1, -1));
    clock = new TextClock(this);
    clock.setFormat12Hour("h:mm a");
    clock.setFormat24Hour("HH:mm");
    clock.setTextColor(ElppaUi.TEXT);
    clock.setTypeface(UiFont.get(this, R.string.font_regular));
    root.addView(clock);
    network =
        icon(
            R.drawable.ic_wifi,
            R.string.elppa_network,
            () -> open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS)));
    settings = icon(R.drawable.ic_settings, R.string.elppa_settings, this::showSettings);
    title = ElppaUi.text(this, 46);
    title.setMaxLines(2);
    title.setGravity(Gravity.BOTTOM);
    title.setEllipsize(TextUtils.TruncateAt.END);
    root.addView(title);
    subtitle = ElppaUi.text(this, 14);
    subtitle.setTextColor(ElppaUi.MUTED);
    subtitle.setMaxLines(2);
    subtitle.setEllipsize(TextUtils.TruncateAt.END);
    root.addView(subtitle);
    action = icon(R.drawable.elppa_play, R.string.elppa_continue, this::activateHero);
    action.setOnFocusChangeListener(
        (view, on) -> {
          stylePlay();
          view.animate().cancel();
          float zoom = on ? 1.045f : 1f;
          if (preferences.motion() && android.animation.ValueAnimator.areAnimatorsEnabled())
            view.animate().scaleX(zoom).scaleY(zoom).setDuration(180).start();
          else {
            view.setScaleX(zoom);
            view.setScaleY(zoom);
          }
        });
    action.setOnKeyListener(
        (v, key, event) -> {
          if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_UP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
              if (key == KeyEvent.KEYCODE_DPAD_DOWN) appsRail.focusLast();
              else settings.requestFocus();
            }
            return true;
          }
          return false;
        });
    appsRail =
        new HomeRail(
            this,
            false,
            artwork,
            new HomeRail.Events() {
              @Override
              public void selected(Object item) {}

              @Override
              public void clicked(Object item) {
                if (item instanceof AppCatalog.App) launchApp((AppCatalog.App) item);
                else showAllApps();
              }

              @Override
              public void held(Object item) {
                if (item instanceof AppCatalog.App) appMenu((AppCatalog.App) item);
                else showAllApps();
              }

              @Override
              public void vertical(boolean up) {
                if (up) {
                  if (action.getVisibility() == View.VISIBLE) action.requestFocus();
                  else settings.requestFocus();
                } else if (!mediaRail.empty()) mediaRail.focusLast();
                else empty.requestFocus();
              }

              @Override
              public boolean reducedGlass() {
                return preferences.reducedGlass();
              }

              @Override
              public boolean motion() {
                return preferences.motion();
              }

              @Override
              public String sourceName(String pkg) {
                return ElppaHomeActivity.this.sourceName(pkg);
              }
            });
    root.addView(appsRail);
    mediaRail =
        new HomeRail(
            this,
            true,
            artwork,
            new HomeRail.Events() {
              @Override
              public void selected(Object item) {
                if (item instanceof MediaEntry) showHero((MediaEntry) item);
              }

              @Override
              public void clicked(Object item) {
                if (item instanceof MediaEntry) play((MediaEntry) item);
              }

              @Override
              public void held(Object item) {
                if (item instanceof MediaEntry) mediaMenu((MediaEntry) item);
              }

              @Override
              public void vertical(boolean up) {
                if (up) appsRail.focusLast();
              }

              @Override
              public boolean reducedGlass() {
                return preferences.reducedGlass();
              }

              @Override
              public boolean motion() {
                return preferences.motion();
              }

              @Override
              public String sourceName(String pkg) {
                return ElppaHomeActivity.this.sourceName(pkg);
              }
            });
    root.addView(mediaRail);
    empty = ElppaUi.button(this, getString(R.string.elppa_empty_detail));
    empty.setGravity(Gravity.CENTER_VERTICAL);
    empty.setPadding(20, 0, 20, 0);
    empty.setMaxLines(2);
    root.addView(empty);
    empty.setOnClickListener(
        v -> {
          if (!repository.permitted()) requestContent();
          else showDiagnostics();
        });
    empty.setOnFocusChangeListener(
        (v, on) -> ElppaUi.focus(v, on, preferences.motion(), 12 * scale));
    empty.setOnKeyListener(
        (v, key, event) -> {
          if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) appsRail.focusLast();
            return true;
          }
          return key == KeyEvent.KEYCODE_DPAD_DOWN;
        });
    status = ElppaUi.text(this, 10);
    status.setTextColor(ElppaUi.MUTED);
    status.setMaxLines(1);
    status.setEllipsize(TextUtils.TruncateAt.END);
    root.addView(status);
    root.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          if (r - l != or - ol || b - t != ob - ot) arrange(r - l, b - t);
        });
    action.setNextFocusLeftId(action.getId());
    action.setNextFocusRightId(action.getId());
    network.setNextFocusLeftId(network.getId());
    network.setNextFocusRightId(settings.getId());
    settings.setNextFocusLeftId(network.getId());
    settings.setNextFocusRightId(settings.getId());
  }

  private ImageView icon(int resource, int label, Runnable click) {
    ImageView v = new ImageView(this);
    v.setImageResource(resource);
    v.setContentDescription(getString(label));
    v.setId(View.generateViewId());
    v.setFocusable(true);
    v.setClickable(true);
    v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    v.setBackground(ElppaUi.shape(0x44292D35, 24, false));
    v.setOnClickListener(view -> click.run());
    v.setOnFocusChangeListener(
        (view, on) -> ElppaUi.focus(view, on, preferences.motion(), 24 * scale));
    v.setOnKeyListener(
        (view, key, event) -> {
          if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
              if (action.getVisibility() == View.VISIBLE) action.requestFocus();
              else appsRail.focusLast();
            }
            return true;
          }
          return key == KeyEvent.KEYCODE_DPAD_UP;
        });
    root.addView(v);
    return v;
  }

  private void arrange(int width, int height) {
    if (width == 0 || height == 0) return;
    scale = Math.min(width / 960f, height / 540f);
    boolean largeText = getResources().getConfiguration().fontScale > 1.05f;
    place(clock, 36, 16, 250, largeText ? 42 : 32);
    ElppaUi.type(clock, 23, scale);
    place(network, 848, 14, 38, 38);
    place(settings, 900, 14, 38, 38);
    for (ImageView v : new ImageView[] {network, settings})
      v.setPadding(px(9), px(9), px(9), px(9));
    place(title, 48, largeText ? 76 : 94, 450, largeText ? 96 : 78);
    ElppaUi.type(title, 36, scale);
    fitText(title, 26, 36);
    place(subtitle, 50, 190, 440, 24);
    ElppaUi.type(subtitle, 12, scale);
    fitText(subtitle, 11, 12);
    place(action, 50, 220, 88, 36);
    action.setPadding(px(33), px(9), px(33), px(9));
    stylePlay();
    place(appsRail, 24, 264, 912, 112);
    appsRail.scale(scale);
    place(mediaRail, 24, 378, 912, 158);
    mediaRail.scale(scale);
    place(empty, 36, 420, 888, 70);
    ElppaUi.type(empty, 14, scale);
    place(status, 330, 22, 450, 24);
    ElppaUi.type(status, 10, scale);
    if (selected != null) loadHeroArt(selected);
  }

  private void fitText(TextView view, float minimum, float preferred) {
    int min = Math.max(1, px(minimum));
    int max = Math.max(min + 1, px(preferred * getResources().getConfiguration().fontScale));
    view.setAutoSizeTextTypeUniformWithConfiguration(
        min, max, 1, android.util.TypedValue.COMPLEX_UNIT_PX);
  }

  private int px(float value) {
    return Math.round(value * scale);
  }

  private void place(View v, float x, float y, float w, float h) {
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(h));
    lp.leftMargin = px(x) + (int) ((root.getWidth() - 960 * scale) / 2);
    lp.topMargin = px(y) + (int) ((root.getHeight() - 540 * scale) / 2);
    v.setLayoutParams(lp);
  }

  @Override
  protected void onStart() {
    super.onStart();
    active = true;
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addDataScheme("package");
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(packages, filter);
    attachObservers();
    main.post(rotate);
    main.post(networkTick);
  }

  @Override
  protected void onResume() {
    super.onResume();
    refresh();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    appsRail.focusLast();
    refresh();
  }

  private void attachObservers() {
    if (observing || !repository.permitted()) return;
    try {
      getContentResolver().registerContentObserver(TvContentRepository.WATCH, true, observer);
      getContentResolver().registerContentObserver(TvContentRepository.PREVIEW, true, observer);
      getContentResolver().registerContentObserver(TvContentRepository.CHANNEL, true, observer);
      observing = true;
    } catch (RuntimeException ignored) {
      getContentResolver().unregisterContentObserver(observer);
    }
  }

  @Override
  protected void onStop() {
    active = false;
    generation++;
    main.removeCallbacksAndMessages(null);
    if (cancellation != null) cancellation.cancel();
    if (task != null) task.cancel(true);
    if (observing) {
      getContentResolver().unregisterContentObserver(observer);
      observing = false;
    }
    unregisterReceiver(packages);
    artworkRequestKey = "";
    artwork.pause();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    dataWorker.shutdownNow();
    artwork.close();
    super.onDestroy();
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (!active && level >= TRIM_MEMORY_BACKGROUND) artwork.trim();
  }

  private void refresh() {
    if (!active) return;
    loading = true;
    attachObservers();
    final int ticket = ++generation;
    if (cancellation != null) cancellation.cancel();
    if (task != null) task.cancel(true);
    cancellation = new CancellationSignal();
    final CancellationSignal signal = cancellation;
    if (!repository.permitted() && !preferences.demo()) snapshot = null;
    render();
    task =
        dataWorker.submit(
            () -> {
              List<AppCatalog.App> found = AppCatalog.load(this, preferences, false);
              TvContentRepository.Snapshot fresh;
              try {
                fresh = repository.read(signal);
              } catch (android.os.OperationCanceledException ignored) {
                return;
              }
              main.post(
                  () -> {
                    if (!active || ticket != generation) return;
                    apps.clear();
                    apps.addAll(found);
                    snapshot = fresh;
                    loading = false;
                    List<Object> row = new ArrayList<>(apps);
                    row.add("all_apps");
                    appsRail.submit(row);
                    render();
                    if (!initialFocus) {
                      initialFocus = true;
                      appsRail.focusLast();
                    }
                  });
            });
  }

  private void render() {
    List<MediaEntry> watching, previews;
    if (preferences.demo()) {
      watching = PreviewContent.entries(this);
      previews = new ArrayList<>();
      heroes = new ArrayList<>(watching);
      status.setText(R.string.elppa_preview);
    } else {
      Set<String> excluded = preferences.excludedSources(), dismissed = preferences.dismissed();
      watching =
          snapshot == null
              ? new ArrayList<>()
              : MediaSelection.watchNext(
                  snapshot.watch, excluded, dismissed, System.currentTimeMillis());
      previews =
          snapshot == null
              ? new ArrayList<>()
              : MediaSelection.recommendations(snapshot.previews, excluded, dismissed);
      heroes = new ArrayList<>(previews.isEmpty() ? watching : previews);
      status.setText(
          snapshot != null && !snapshot.notes.isEmpty()
              ? "Some content is unavailable · See Settings → Content diagnostics"
              : "");
    }
    List<MediaEntry> shelf = MediaSelection.shelf(watching, previews);
    mediaRail.submit(shelf);
    mediaRail.setVisibility(shelf.isEmpty() ? View.GONE : View.VISIBLE);
    empty.setVisibility(shelf.isEmpty() ? View.VISIBLE : View.GONE);
    if (!repository.permitted() && !preferences.demo())
      empty.setText(R.string.elppa_access_explanation);
    else if (loading && snapshot == null && !preferences.demo())
      empty.setText(R.string.elppa_loading);
    else if (snapshot != null && !snapshot.watchRead && !preferences.demo())
      empty.setText("Couldn't read Continue watching. Open content diagnostics to check access.");
    else empty.setText(R.string.elppa_empty_detail);
    action.setVisibility(heroes.isEmpty() ? View.GONE : View.VISIBLE);
    MediaEntry retained = null;
    if (selected != null) for (MediaEntry e : heroes) if (e.key.equals(selected.key)) retained = e;
    if (mediaRail.hasFocus() && selected != null)
      for (MediaEntry e : shelf) if (e.key.equals(selected.key)) retained = e;
    if (!heroes.isEmpty())
      showHero(retained == null ? heroes.get(Math.floorMod(heroIndex, heroes.size())) : retained);
    else {
      selected = null;
      artworkRequestKey = "";
      playFrost = null;
      stylePlay();
      artwork.clearHero(backdrop);
      title.setText(
          loading && repository.permitted() ? R.string.elppa_loading : R.string.elppa_empty);
      subtitle.setText(
          repository.permitted()
              ? "Your apps. Your shows. One place to begin."
              : "Bring the shows from your apps onto Home.");
      action.setContentDescription(getString(R.string.elppa_content_access));
    }
    if (getCurrentFocus() == null && initialFocus) appsRail.focusLast();
  }

  private void showHero(MediaEntry entry) {
    selected = entry;
    title.setText(entry.title);
    subtitle.setText(heroDetail(entry));
    action.setContentDescription(
        entry.source.equals("demo")
            ? "About this preview"
            : "Play " + entry.title + " in " + sourceName(entry.source));
    loadHeroArt(entry);
  }

  private String heroDetail(MediaEntry entry) {
    String detail =
        entry.source.equals("demo")
            ? "Sample artwork · Not available to play"
            : sourceName(entry.source) + (entry.subtitle.isEmpty() ? "" : " · " + entry.subtitle);
    if (entry.watchType == 1) detail += " · Next episode";
    else if (entry.watchType == 2) detail += " · New";
    else if (entry.watchType == 3) detail += " · Watchlist";
    return detail;
  }

  private void stylePlay() {
    if (action != null)
      action.setBackground(
          new FrostedPlayDrawable(playFrost, action.hasFocus(), preferences.reducedGlass()));
  }

  private void loadHeroArt(MediaEntry entry) {
    if (!active) return;
    String key = entry.key + "|" + entry.backdrop + "|" + entry.poster;
    if (key.equals(artworkRequestKey)) return;
    artworkRequestKey = key;
    artwork.loadHero(backdrop, entry.backdrop, entry.poster, preferences.motion());
  }

  private void activateHero() {
    if (selected != null) play(selected);
    else if (!repository.permitted()) requestContent();
    else showAllApps();
  }

  private void play(MediaEntry entry) {
    if (entry.source.equals("demo")) {
      message(
          "Preview content",
          "These are sample titles for checking the layout. Turn off Preview in Settings to see"
              + " content shared by your apps.");
      return;
    }
    if (!MediaLauncher.open(this, entry))
      message(
          "Can't open this title",
          "The source app may have been removed or its link may have changed. Refresh content or"
              + " open the app from the dock.");
  }

  private void launchApp(AppCatalog.App app) {
    if (!MediaLauncher.openApp(this, app.pkg))
      message("App unavailable", "This app could not be opened. Check it in TV settings.");
  }

  private String sourceName(String pkg) {
    if (pkg.equals("demo")) return "Preview";
    String known = sourceNames.get(pkg);
    if (known != null) return known;
    try {
      known =
          String.valueOf(
              getPackageManager()
                  .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
    } catch (PackageManager.NameNotFoundException ignored) {
      known = pkg;
    }
    sourceNames.put(pkg, known);
    return known;
  }

  private void requestContent() {
    if (repository.permitted()) {
      refresh();
      return;
    }
    new HomePanel.Builder(this)
        .setTitle(R.string.elppa_content_access)
        .setMessage(R.string.elppa_access_explanation)
        .setPositiveButton(
            "Allow access",
            (d, w) -> requestPermissions(new String[] {TvContentRepository.PERMISSION}, 41))
        .setNeutralButton(
            "App permissions",
            (d, w) ->
                open(
                    new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
        .setNegativeButton("Later", null)
        .show();
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == 41) refresh();
  }

  private void mediaMenu(MediaEntry e) {
    if (e.source.equals("demo")) {
      play(e);
      return;
    }
    new HomePanel.Builder(this)
        .setTitle(e.title)
        .setItems(
            new String[] {"Open in " + sourceName(e.source), "Dismiss from this Home"},
            (d, w) -> {
              if (w == 0) play(e);
              else {
                preferences.dismiss(e.key);
                render();
              }
            })
        .show();
  }

  private void showAllApps() {
    // Package reads are cached by the platform; this dialog runs only on explicit invocation.
    List<AppCatalog.App> all = AppCatalog.load(this, preferences, true);
    String[] labels = new String[all.size()];
    for (int i = 0; i < all.size(); i++) labels[i] = all.get(i).label;
    if (all.isEmpty()) {
      message("All apps", "No launchable apps were found.");
      return;
    }
    new HomePanel.Builder(this)
        .setTitle(R.string.elppa_all_apps)
        .setItems(labels, (d, w) -> launchApp(all.get(w)))
        .setLongClick(pos -> appMenu(all.get(pos)))
        .setNegativeButton("Close", null)
        .show();
  }

  private void appMenu(AppCatalog.App app) {
    boolean hidden = preferences.hiddenApps().contains(app.pkg);
    new HomePanel.Builder(this)
        .setTitle(app.label)
        .setItems(
            new String[] {
              "Open",
              "Move left",
              "Move right",
              "Pin to front",
              hidden ? "Show in dock" : "Hide from dock",
              "App info"
            },
            (d, w) -> {
              if (w == 0) launchApp(app);
              else if (w == 4) {
                preferences.showApp(app.pkg, hidden);
                refresh();
              } else if (w == 5)
                open(
                    new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.pkg)));
              else {
                List<String> order = new ArrayList<>();
                for (AppCatalog.App a : apps) order.add(a.pkg);
                if (!order.contains(app.pkg)) order.add(app.pkg);
                int i = order.indexOf(app.pkg);
                if (w == 3) {
                  order.remove(app.pkg);
                  order.add(0, app.pkg);
                  preferences.showApp(app.pkg, true);
                } else {
                  int other = w == 1 ? i - 1 : i + 1;
                  if (other >= 0 && other < order.size()) Collections.swap(order, i, other);
                }
                preferences.order(order);
                refresh();
              }
            })
        .show();
  }

  private void showSettings() {
    new HomePanel.Builder(this)
        .setTitle("Settings")
        .section("Home")
        .toggle(
            "Reduce transparency",
            "Use solid controls and simpler focus lighting",
            preferences.reducedGlass(),
            on -> {
              preferences.toggle("reduced_glass", on);
              stylePlay();
              appsRail.refreshAppearance();
              mediaRail.refreshAppearance();
            })
        .toggle(
            "Animations",
            "Gentle movement when you choose a card",
            preferences.motion(),
            on -> preferences.toggle("motion", on))
        .toggle(
            "Rotate featured shows",
            "Change the backdrop while browsing apps",
            preferences.rotate(),
            on -> preferences.toggle("rotate", on))
        .row("Recommendation sources", "Choose which apps appear on Home", this::showSources)
        .row("All apps", "Open or organise your installed apps", this::showAllApps)
        .section("Device")
        .row(
            "TV settings",
            "Picture, sound, remotes and system settings",
            () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)))
        .row("Inputs", "Open your device’s input menu", this::showInputs)
        .row("Content access", "Allow apps to share titles with this Home", this::requestContent)
        .section("Manage content")
        .row(
            "Refresh content",
            "Check for newly shared titles",
            () -> {
              artworkRequestKey = "";
              refresh();
            })
        .row(
            "Restore dismissed titles",
            "Bring back titles hidden from Home",
            () -> {
              preferences.clearDismissed();
              render();
            })
        .row(
            "Clear artwork cache",
            "Download fresh artwork for your titles",
            () -> {
              artworkRequestKey = "";
              artwork.clear();
              selected = null;
              render();
            })
        .row("Content diagnostics", "Provider coverage and artwork quality", this::showDiagnostics)
        .toggle(
            "Preview mode",
            "Use labelled sample content for layout testing",
            preferences.demo(),
            on -> {
              preferences.toggle("demo", on);
              selected = null;
              render();
            })
        .row(
            "Default Home",
            "Choose your home screen",
            () -> open(new Intent(Settings.ACTION_HOME_SETTINGS)))
        .setNegativeButton("Done", null)
        .show();
  }

  private void showSources() {
    if (snapshot == null || snapshot.counts.isEmpty()) {
      message(
          "Recommendation sources",
          "No apps have shared readable content yet. Allow content access, play something, then"
              + " refresh.");
      return;
    }
    List<String> keys = new ArrayList<>(snapshot.counts.keySet());
    Set<String> excluded = preferences.excludedSources();
    String[] labels = new String[keys.size()];
    boolean[] checked = new boolean[keys.size()];
    for (int i = 0; i < keys.size(); i++) {
      labels[i] = sourceName(keys.get(i));
      checked[i] = !excluded.contains(keys.get(i));
    }
    new HomePanel.Builder(this)
        .setTitle("Show content from")
        .setMultiChoiceItems(
            labels,
            checked,
            (d, i, on) -> {
              if (on) excluded.remove(keys.get(i));
              else excluded.add(keys.get(i));
            })
        .setPositiveButton(
            "Save",
            (d, w) -> {
              preferences.setExcluded(excluded);
              selected = null;
              render();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showDiagnostics() {
    StringBuilder text =
        new StringBuilder("Android ")
            .append(Build.VERSION.RELEASE)
            .append(" · API ")
            .append(Build.VERSION.SDK_INT)
            .append("\nTV listings: ")
            .append(repository.permitted() ? "allowed" : "not allowed")
            .append("\nGoogle Assistant: ")
            .append(packageState("com.google.android.katniss"))
            .append("\nTV Remote Service: ")
            .append(packageState("com.google.android.tv.remote.service"));
    text.append("\nArtwork: ").append(artwork.heroQuality());
    if (snapshot != null) {
      text.append("\nChecked: ")
          .append(
              DateFormat.getTimeInstance(DateFormat.SHORT)
                  .format(new java.util.Date(snapshot.checkedAt)))
          .append("\nChannels: ")
          .append(snapshot.channels)
          .append("\n\nVisible provider rows (before filtering):\n");
      for (Map.Entry<String, int[]> entry : snapshot.counts.entrySet())
        text.append(sourceName(entry.getKey()))
            .append(": ")
            .append(entry.getValue()[0])
            .append(" Watch Next, ")
            .append(entry.getValue()[1])
            .append(" previews\n");
      for (String note : snapshot.notes) text.append("\n").append(note);
    }
    text.append(
        "\n\n"
            + "An empty result does not prove an app has no history; it may not publish readable"
            + " rows. Physical mic and phone-remote operation must be tested on the TV.");
    new HomePanel.Builder(this)
        .setTitle("Content diagnostics")
        .setMessage(text)
        .setPositiveButton("Refresh", (d, w) -> refresh())
        .setNegativeButton("Close", null)
        .show();
  }

  private String packageState(String pkg) {
    try {
      ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
      return info.enabled ? "installed and enabled" : "disabled";
    } catch (PackageManager.NameNotFoundException ignored) {
      return "not visible / not installed";
    }
  }

  private void showInputs() {
    Intent nativePicker = new Intent("com.android.tv.action.VIEW_INPUTS");
    if (getPackageManager().resolveActivity(nativePicker, 0) != null) {
      open(nativePicker);
      return;
    }
    TvInputManager manager = getSystemService(TvInputManager.class);
    List<TvInputInfo> entries = manager == null ? new ArrayList<>() : manager.getTvInputList();
    if (entries.isEmpty()) {
      open(SystemSettings.intent(this, Settings.ACTION_SETTINGS));
      return;
    }
    String[] names = new String[entries.size()];
    for (int i = 0; i < names.length; i++)
      names[i] = String.valueOf(entries.get(i).loadLabel(this));
    new HomePanel.Builder(this)
        .setTitle("TV inputs")
        .setItems(
            names,
            (d, w) -> {
              TvInputInfo info = entries.get(w);
              Uri uri =
                  info.isPassthroughInput()
                      ? TvContract.buildChannelUriForPassthroughInput(info.getId())
                      : TvContract.buildChannelsUriForInput(info.getId());
              open(new Intent(Intent.ACTION_VIEW, uri));
            })
        .show();
  }

  private void updateNetwork() {
    ConnectivityManager manager = getSystemService(ConnectivityManager.class);
    NetworkCapabilities caps =
        manager == null ? null : manager.getNetworkCapabilities(manager.getActiveNetwork());
    boolean online =
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    network.setImageResource(R.drawable.ic_wifi);
    network.setImageAlpha(online ? 255 : 128);
    network.setContentDescription(
        online
            ? "Network connected. Open network settings"
            : "Network offline. Open network settings");
  }

  private boolean open(Intent intent) {
    try {
      startActivity(intent);
      return true;
    } catch (android.content.ActivityNotFoundException | SecurityException ignored) {
      message("Unavailable", getString(R.string.elppa_no_handler));
      return false;
    }
  }

  private void message(String heading, String body) {
    new HomePanel.Builder(this)
        .setTitle(heading)
        .setMessage(body)
        .setPositiveButton("OK", null)
        .show();
  }

  @Override
  public boolean onKeyUp(int key, KeyEvent event) {
    // Only used if firmware delivers SEARCH to this activity. System-owned mic events remain
    // untouched.
    if (key == KeyEvent.KEYCODE_SEARCH) {
      return open(new Intent(Intent.ACTION_ASSIST));
    }
    return super.onKeyUp(key, event);
  }

  @Override
  public void onBackPressed() {
    android.content.pm.ResolveInfo home =
        getPackageManager()
            .resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
    if (home != null
        && home.activityInfo != null
        && getPackageName().equals(home.activityInfo.packageName)) appsRail.focusLast();
    else super.onBackPressed();
  }
}
