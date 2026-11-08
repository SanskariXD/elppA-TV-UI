package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

final class HomeRail extends RecyclerView {
  interface Events {
    void selected(Object item);

    void clicked(Object item);

    void held(Object item);

    void vertical(boolean up);

    boolean motion();

    default boolean reducedGlass() {
      return false;
    }

    String sourceName(String pkg);
  }

  private final List<Object> items = new ArrayList<>();
  private final Events events;
  private final ArtworkLoader artwork;
  private final boolean media;
  private final Adapter<Tile> cards =
      new Adapter<Tile>() {
        @Override
        public Tile onCreateViewHolder(ViewGroup parent, int type) {
          return new Tile(parent.getContext());
        }

        @Override
        public void onBindViewHolder(Tile tile, int position) {
          tile.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
          return items.size();
        }

        @Override
        public void onViewRecycled(Tile holder) {
          holder.card.animate().cancel();
          holder.image.setTag(null);
          holder.image.setImageDrawable(null);
        }
      };
  private float scale = 1;
  private int lastPosition;

  HomeRail(Context c, boolean media, ArtworkLoader artwork, Events events) {
    super(c);
    this.media = media;
    this.artwork = artwork;
    this.events = events;
    setLayoutManager(new LinearLayoutManager(c, HORIZONTAL, false));
    setAdapter(cards);
    setItemAnimator(null);
    setClipToPadding(false);
    setClipChildren(false);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    setFocusable(false);
    setHasFixedSize(true);
    setItemViewCacheSize(2);
  }

  void scale(float value) {
    if (Math.abs(value - scale) < .01f) return;
    scale = value;
    cards.notifyDataSetChanged();
  }

  /** Update material only: keep focus and avoid re-requesting art on a settings toggle. */
  void refreshAppearance() {
    if (isComputingLayout()) {
      post(this::refreshAppearance);
      return;
    }
    for (int i = 0; i < getChildCount(); i++) {
      ViewHolder holder = getChildViewHolder(getChildAt(i));
      if (holder instanceof Tile) {
        Tile tile = (Tile) holder;
        tile.focusCard(tile.frame.hasFocus());
      }
    }
  }

  void submit(List<?> values) {
    Object focused = lastPosition < items.size() ? items.get(lastPosition) : null;
    String id = identity(focused);
    boolean restore = hasFocus();
    items.clear();
    items.addAll(values);
    lastPosition = Math.min(lastPosition, Math.max(0, items.size() - 1));
    for (int i = 0; i < items.size(); i++)
      if (identity(items.get(i)).equals(id)) {
        lastPosition = i;
        break;
      }
    cards.notifyDataSetChanged();
    if (restore) post(this::focusLast);
  }

  private static String identity(Object item) {
    return item instanceof MediaEntry
        ? ((MediaEntry) item).key
        : item instanceof AppCatalog.App ? ((AppCatalog.App) item).pkg : "apps";
  }

  boolean empty() {
    return items.isEmpty();
  }

  void focusLast() {
    if (items.isEmpty()) return;
    scrollToPosition(lastPosition);
    post(
        () -> {
          ViewHolder h = findViewHolderForAdapterPosition(lastPosition);
          if (h != null) h.itemView.requestFocus();
        });
  }

  private int px(float units) {
    return Math.round(units * scale);
  }

  private final class Tile extends ViewHolder {
    final FrameLayout frame;
    final FrameLayout card;
    final ImageView image;
    final TextView label;
    final TextView fallback;
    final View progress;
    Object item;

    Tile(Context c) {
      super(new FrameLayout(c));
      frame = (FrameLayout) itemView;
      frame.setFocusable(true);
      frame.setClickable(true);
      frame.setId(View.generateViewId());
      frame.setClipChildren(false);
      frame.setClipToPadding(false);
      card = new FrameLayout(c);
      card.setClipChildren(false);
      card.setClipToPadding(false);
      card.setClipToOutline(true);
      card.setOutlineProvider(
          new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
              outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(14));
              outline.setAlpha(.55f);
            }
          });
      if (android.os.Build.VERSION.SDK_INT >= 28) {
        card.setOutlineAmbientShadowColor(Color.BLACK);
        card.setOutlineSpotShadowColor(Color.BLACK);
      }
      frame.addView(card);
      fallback = ElppaUi.text(c, 13);
      fallback.setMaxLines(3);
      fallback.setGravity(Gravity.CENTER);
      fallback.setEllipsize(android.text.TextUtils.TruncateAt.END);
      card.addView(fallback);
      image = new ImageView(c);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      card.addView(image);
      label = ElppaUi.text(c, 11);
      label.setMaxLines(2);
      label.setEllipsize(android.text.TextUtils.TruncateAt.END);
      label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      frame.addView(label);
      progress = new View(c);
      progress.setBackgroundColor(ElppaUi.TEXT);
      card.addView(progress);
      frame.setOnHoverListener(
          (v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) v.requestFocus();
            return false;
          });
      frame.setOnClickListener(v -> events.clicked(item));
      frame.setOnLongClickListener(
          v -> {
            events.held(item);
            return true;
          });
      frame.setOnFocusChangeListener(
          (v, on) -> {
            focusCard(on);
            label.setVisibility(media && on ? VISIBLE : INVISIBLE);
            if (on) {
              int p = getBindingAdapterPosition();
              if (p != NO_POSITION) lastPosition = p;
              events.selected(item);
            }
          });
      frame.setOnKeyListener(
          (v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
              if (event.getAction() == KeyEvent.ACTION_DOWN)
                events.vertical(key == KeyEvent.KEYCODE_DPAD_UP);
              return true;
            }
            int p = getBindingAdapterPosition();
            if ((key == KeyEvent.KEYCODE_DPAD_LEFT && p == 0)
                || (key == KeyEvent.KEYCODE_DPAD_RIGHT && p == items.size() - 1)) return true;
            return false;
          });
    }

    private void focusCard(boolean on) {
      boolean reduced = events.reducedGlass();
      // Neutral surfaces prevent a brand-coloured frame around the fitted banner.
      card.setBackground(ElppaUi.shape(ElppaUi.SURFACE, px(14), false));
      card.setForeground(!media && !reduced ? new CardReflection(px(14)) : null);
      card.setElevation(px(3));
      card.animate().cancel();
      float zoom = on ? 1.045f : 1f;
      float lift = on ? -px(3) : 0;
      float depth = on ? px(9) : 0;
      if (events.motion() && android.animation.ValueAnimator.areAnimatorsEnabled())
        card.animate()
            .scaleX(zoom)
            .scaleY(zoom)
            .translationY(lift)
            .translationZ(depth)
            .setDuration(200)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
      else {
        card.setScaleX(zoom);
        card.setScaleY(zoom);
        card.setTranslationY(lift);
        card.setTranslationZ(depth);
      }
    }

    void bind(Object next) {
      item = next;
      int w = px(media ? 176 : 160), h = px(media ? 99 : 90);
      RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, h + px(media ? 38 : 0));
      lp.setMargins(px(8), px(12), px(8), px(8));
      frame.setLayoutParams(lp);
      frame.setBackgroundColor(Color.TRANSPARENT);
      card.setLayoutParams(new FrameLayout.LayoutParams(w, h));
      fallback.setLayoutParams(
          new FrameLayout.LayoutParams(w - px(24), h - px(24), Gravity.CENTER));
      ElppaUi.type(fallback, 13, scale);
      fallback.setVisibility(media ? VISIBLE : GONE);
      image.setLayoutParams(
          new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
      image.setTranslationY(0);
      image.setBackground(ElppaUi.shape(Color.TRANSPARENT, px(14), false));
      image.setClipToOutline(true);
      FrameLayout.LayoutParams textLp =
          new FrameLayout.LayoutParams(w, px(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      textLp.topMargin = h + px(10);
      label.setLayoutParams(textLp);
      ElppaUi.type(label, 11, scale);
      label.setAutoSizeTextTypeUniformWithConfiguration(
          Math.max(1, px(9)),
          Math.max(2, px(11 * Math.min(1.5f, getResources().getConfiguration().fontScale))),
          1,
          android.util.TypedValue.COMPLEX_UNIT_PX);
      label.setVisibility(media && frame.hasFocus() ? VISIBLE : INVISIBLE);
      label.setBackgroundColor(Color.TRANSPARENT);
      label.setPadding(px(4), 0, px(4), 0);
      if (next instanceof MediaEntry) {
        MediaEntry e = (MediaEntry) next;
        image.setPadding(0, 0, 0, 0);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.load(image, e.heroArt(), px(352), px(198));
        fallback.setText(e.title);
        label.setText(e.title);
        frame.setContentDescription(
            e.title + ", " + events.sourceName(e.source) + ", " + e.subtitle);
        FrameLayout.LayoutParams bar =
            new FrameLayout.LayoutParams(
                Math.max(1, (w - px(12)) * e.progress() / 100),
                px(2),
                Gravity.BOTTOM | Gravity.LEFT);
        bar.leftMargin = px(6);
        bar.bottomMargin = px(3);
        progress.setLayoutParams(bar);
        progress.setVisibility(e.hasProgress() ? VISIBLE : GONE);
      } else {
        image.setTag(null);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(px(22), px(22), px(22), px(22));
        progress.setVisibility(GONE);
        if (next instanceof AppCatalog.App) {
          AppCatalog.App app = (AppCatalog.App) next;
          android.graphics.drawable.Drawable art = app.art(getContext().getPackageManager());
          boolean banner = art != null && art.getIntrinsicWidth() > art.getIntrinsicHeight() * 1.3f;
          image.setScaleType(ImageView.ScaleType.FIT_CENTER);
          // TV banners normally match16:9. Preserve every logo pixel without a synthetic inset.
          int inset = banner ? 0 : px(22);
          image.setPadding(inset, inset, inset, inset);
          image.setImageDrawable(art);
          label.setText(app.label);
          frame.setContentDescription(app.label);
        } else {
          image.setImageResource(R.drawable.ic_apps);
          label.setText(R.string.elppa_all_apps);
          frame.setContentDescription(getContext().getString(R.string.elppa_all_apps));
        }
      }
      focusCard(frame.hasFocus());
    }
  }
}
