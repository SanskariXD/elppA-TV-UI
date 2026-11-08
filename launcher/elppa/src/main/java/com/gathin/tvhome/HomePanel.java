package com.gathin.tvhome;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Remote-first side sheet. Solid surfaces and native scrolling; no backdrop blur or extra library.
 */
final class HomePanel extends Dialog {
  private final float scale;
  private final LinearLayout content;
  private View first;

  private HomePanel(Builder builder) {
    super(builder.context);
    scale =
        Math.min(
            getContext().getResources().getDisplayMetrics().widthPixels / 960f,
            getContext().getResources().getDisplayMetrics().heightPixels / 540f);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout panel = new LinearLayout(getContext());
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(px(22), px(24), px(22), px(18));
    panel.setBackground(ElppaUi.shape(0xFF171A20, px(22), false));
    TextView title = text(builder.title, 27, ElppaUi.TEXT);
    title.setPadding(px(8), 0, px(8), px(20));
    panel.addView(title);
    ScrollView scroll = new ScrollView(getContext());
    scroll.setFillViewport(false);
    scroll.setVerticalScrollBarEnabled(false);
    content = new LinearLayout(getContext());
    content.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(content);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    for (Consumer<HomePanel> item : builder.items) item.accept(this);
    if (!builder.buttons.isEmpty()) {
      LinearLayout footer = new LinearLayout(getContext());
      footer.setOrientation(LinearLayout.VERTICAL);
      footer.setPadding(0, px(10), 0, 0);
      for (Button button : builder.buttons) {
        TextView v = text(button.label, 15, ElppaUi.TEXT);
        v.setGravity(Gravity.CENTER);
        v.setPadding(px(12), px(12), px(12), px(12));
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(ElppaUi.shape(0xFF292E36, px(12), false));
        v.setOnFocusChangeListener(
            (view, on) -> {
              view.setBackground(ElppaUi.shape(on ? ElppaUi.TEXT : 0xFF292E36, px(12), false));
              v.setTextColor(on ? ElppaUi.BACKGROUND : ElppaUi.TEXT);
            });
        v.setOnClickListener(
            view -> {
              dismiss();
              if (button.click != null) button.click.onClick(this, button.which);
            });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(6);
        footer.addView(v, lp);
        if (first == null) first = v;
      }
      panel.addView(footer);
    }
    setContentView(panel);
    Window window = getWindow();
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    window.setDimAmount(.52f);
    window.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    WindowManager.LayoutParams lp = window.getAttributes();
    lp.width = px(390);
    lp.height = px(500);
    lp.x = px(20);
    window.setAttributes(lp);
    setOnShowListener(
        d -> {
          if (first != null) first.requestFocus();
        });
  }

  private int px(float value) {
    return Math.max(1, Math.round(value * scale));
  }

  private TextView text(CharSequence value, float size, int colour) {
    TextView t = ElppaUi.text(getContext(), size);
    ElppaUi.type(t, size, scale);
    t.setText(value);
    t.setTextColor(colour);
    return t;
  }

  private void password(java.util.function.Consumer<String> change) {
    android.widget.EditText input = new android.widget.EditText(getContext());
    input.setHint("API Read Access Token");
    input.setSingleLine(true);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    input.setTextColor(ElppaUi.TEXT);
    input.setHintTextColor(ElppaUi.MUTED);
    ElppaUi.type(input, 14, scale);
    input.setPadding(px(12), px(12), px(12), px(12));
    input.setBackground(ElppaUi.shape(0xFF30363E, px(12), false));
    input.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            change.accept(s.toString());
          }

          public void afterTextChanged(android.text.Editable s) {}
        });
    content.addView(input, new LinearLayout.LayoutParams(-1, -2));
    if (first == null) first = input;
  }

  private void brand(int resource) {
    android.widget.ImageView image = new android.widget.ImageView(getContext());
    image.setImageResource(resource);
    image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
    image.setContentDescription("The Movie Database");
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(120), px(48));
    lp.bottomMargin = px(16);
    content.addView(image, lp);
  }

  private void section(String label) {
    TextView v = text(label, 12, ElppaUi.MUTED);
    v.setPadding(px(10), px(18), px(8), px(8));
    content.addView(v);
  }

  private void body(CharSequence message) {
    TextView v = text(message, 14, ElppaUi.MUTED);
    v.setPadding(px(8), 0, px(8), px(16));
    content.addView(v);
  }

  private void row(
      String label,
      String detail,
      Runnable click,
      Runnable hold,
      Boolean checked,
      Consumer<Boolean> toggle) {
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(px(12), px(12), px(12), px(12));
    row.setMinimumHeight(px(52));
    row.setFocusable(true);
    row.setClickable(true);
    row.setBackground(ElppaUi.shape(0xFF22262D, px(12), false));
    LinearLayout copy = new LinearLayout(getContext());
    copy.setOrientation(LinearLayout.VERTICAL);
    TextView name = text(label, 15, ElppaUi.TEXT);
    copy.addView(name);
    TextView hint = text(detail, 11, ElppaUi.MUTED);
    if (!detail.isEmpty()) {
      hint.setPadding(0, px(4), 0, 0);
      copy.addView(hint);
    }
    row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    Switch control = checked == null ? null : new Switch(getContext());
    if (control != null) {
      control.setChecked(checked);
      control.setFocusable(false);
      control.setClickable(false);
      control.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      control.setThumbTintList(android.content.res.ColorStateList.valueOf(ElppaUi.TEXT));
      control.setTrackTintList(
          new android.content.res.ColorStateList(
              new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
              new int[] {0xFF577D79, 0xFF50555D}));
      row.addView(control);
      row.setContentDescription(label + (checked ? ", On" : ", Off"));
    }
    row.setOnFocusChangeListener(
        (v, on) -> {
          v.setBackground(ElppaUi.shape(on ? ElppaUi.TEXT : 0xFF22262D, px(12), false));
          name.setTextColor(on ? ElppaUi.BACKGROUND : ElppaUi.TEXT);
          hint.setTextColor(on ? 0xFF45484D : ElppaUi.MUTED);
        });
    row.setOnClickListener(
        v -> {
          if (control != null) {
            boolean on = !control.isChecked();
            control.setChecked(on);
            row.setContentDescription(label + (on ? ", On" : ", Off"));
            toggle.accept(on);
          } else {
            dismiss();
            click.run();
          }
        });
    if (hold != null)
      row.setOnLongClickListener(
          v -> {
            dismiss();
            hold.run();
            return true;
          });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = px(6);
    content.addView(row, lp);
    if (first == null) first = row;
  }

  private static final class Button {
    final String label;
    final int which;
    final DialogInterface.OnClickListener click;

    Button(String label, int which, DialogInterface.OnClickListener click) {
      this.label = label;
      this.which = which;
      this.click = click;
    }
  }

  static final class Builder {
    final Context context;
    CharSequence title = "elppA TV";
    final List<Consumer<HomePanel>> items = new ArrayList<>();
    final List<Button> buttons = new ArrayList<>();
    IntConsumer longClick;

    Builder(Context context) {
      this.context = context;
    }

    Builder setTitle(CharSequence title) {
      this.title = title;
      return this;
    }

    Builder setTitle(int title) {
      return setTitle(context.getString(title));
    }

    Builder setMessage(CharSequence message) {
      items.add(p -> p.body(message));
      return this;
    }

    Builder setMessage(int message) {
      return setMessage(context.getString(message));
    }

    Builder passwordInput(java.util.function.Consumer<String> change) {
      items.add(p -> p.password(change));
      return this;
    }

    Builder brand(int resource) {
      items.add(p -> p.brand(resource));
      return this;
    }

    Builder section(String label) {
      items.add(p -> p.section(label));
      return this;
    }

    Builder row(String label, String detail, Runnable click) {
      items.add(p -> p.row(label, detail, click, null, null, null));
      return this;
    }

    Builder toggle(String label, String detail, boolean on, Consumer<Boolean> toggle) {
      items.add(p -> p.row(label, detail, null, null, on, toggle));
      return this;
    }

    Builder setItems(String[] labels, DialogInterface.OnClickListener click) {
      for (int i = 0; i < labels.length; i++) {
        final int index = i;
        items.add(
            p ->
                p.row(
                    labels[index],
                    "",
                    () -> click.onClick(p, index),
                    longClick == null ? null : () -> longClick.accept(index),
                    null,
                    null));
      }
      return this;
    }

    Builder setLongClick(IntConsumer click) {
      longClick = click;
      return this;
    }

    Builder setMultiChoiceItems(
        String[] labels, boolean[] checked, DialogInterface.OnMultiChoiceClickListener click) {
      for (int i = 0; i < labels.length; i++) {
        final int index = i;
        items.add(
            p ->
                p.row(
                    labels[index],
                    "",
                    null,
                    null,
                    checked[index],
                    on -> click.onClick(p, index, on)));
      }
      return this;
    }

    Builder setPositiveButton(String label, DialogInterface.OnClickListener click) {
      buttons.add(new Button(label, -1, click));
      return this;
    }

    Builder setNegativeButton(String label, DialogInterface.OnClickListener click) {
      buttons.add(new Button(label, -2, click));
      return this;
    }

    Builder setNeutralButton(String label, DialogInterface.OnClickListener click) {
      buttons.add(new Button(label, -3, click));
      return this;
    }

    HomePanel show() {
      HomePanel panel = new HomePanel(this);
      panel.show();
      return panel;
    }
  }
}
