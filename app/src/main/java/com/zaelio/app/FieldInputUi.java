package com.zaelio.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.Map;

final class FieldInputUi {
    private final Activity activity;
    private final ThemeStore theme;
    private final AppUi ui;
    private final Handler handler;
    private final Map<String, Long> timers;

    FieldInputUi(Activity activity, ThemeStore theme, AppUi ui, Handler handler, Map<String, Long> timers) {
        this.activity = activity;
        this.theme = theme;
        this.ui = ui;
        this.handler = handler;
        this.timers = timers;
    }

    void fieldControl(
            LinearLayout box,
            FieldDefinition field,
            Map<String, Object> values,
            Map<String, View> inputs,
            boolean readOnly,
            boolean collapsed,
            Runnable onChange) {
        LinearLayout fieldBox = ui.compactCard();
        LinearLayout.LayoutParams fieldBoxLp = new LinearLayout.LayoutParams(-1, -2);
        fieldBoxLp.bottomMargin = ui.spaceM();
        box.addView(fieldBox, fieldBoxLp);

        TextView title = ui.titleText(fieldLabel(field));
        ImageView expand = ui.expandIcon();
        LinearLayout header = ui.listRow(null, title, expand);
        fieldBox.addView(header);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, ui.spaceS(), ui.spaceS(), 0);
        fieldBox.addView(content);
        View.OnClickListener toggle = v -> setCollapsed(content, expand, content.getVisibility() == View.VISIBLE);
        header.setOnClickListener(toggle);
        expand.setOnClickListener(toggle);
        setCollapsed(content, expand, collapsed);

        Object value = values.get(field.key);
        if ("string".equals(field.type)) {
            stringControl(content, field, value, inputs, readOnly, onChange);
            return;
        }
        if ("duration".equals(field.type)) {
            timerControl(content, field, value, inputs, readOnly, onChange);
            return;
        }
        numericControl(content, field, value, inputs, readOnly, onChange);
    }

    private void stringControl(LinearLayout fieldBox, FieldDefinition field, Object value, Map<String, View> inputs, boolean readOnly, Runnable onChange) {
        EditText editText = styledEditText(value);
        editText.setSingleLine(false);
        editText.setMinLines(stringMinLines());
        editText.setMaxLines(stringMaxLines());
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setTextSize(ui.sp(18));
        editText.setPadding(ui.spaceM(), ui.spaceM(), ui.spaceM(), ui.spaceM());
        editText.setEnabled(!readOnly);
        if (!readOnly) {
            ui.onTextChanged(editText, onChange);
        }
        fieldBox.addView(ui.outlinedInput(fieldLabel(field), editText), new LinearLayout.LayoutParams(-1, -2));
        inputs.put(field.key, editText);
    }

    private void timerControl(LinearLayout fieldBox, FieldDefinition field, Object value, Map<String, View> inputs, boolean readOnly, Runnable onChange) {
        EditText display = styledEditText(FormatUtil.formatMs(FormatUtil.toLong(value)));
        display.setTextSize(ui.sp(18));
        display.setPadding(ui.spaceM(), ui.spaceM(), ui.spaceM(), ui.spaceM());
        display.setFocusable(false);
        display.setCursorVisible(false);
        display.setInputType(InputType.TYPE_NULL);
        display.setTag(FormatUtil.toLong(value));
        int timerHeight = numericHeight();
        LinearLayout.LayoutParams displayLp = new LinearLayout.LayoutParams(-1, -2);
        displayLp.bottomMargin = ui.spaceS();
        fieldBox.addView(ui.outlinedInput(fieldLabel(field), display), displayLp);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button toggle = timers.containsKey(field.key) ? ui.dangerButton("Stop") : ui.primaryButton("Start");
        Button reset = ui.ghostButton("Reset");
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(0, timerHeight, 2f);
        toggleLp.rightMargin = ui.spaceS();
        row.addView(toggle, toggleLp);
        row.addView(reset, new LinearLayout.LayoutParams(0, timerHeight, 1f));
        fieldBox.addView(row);

        toggle.setEnabled(!readOnly);
        reset.setEnabled(!readOnly);

        toggle.setOnClickListener(v -> {
            if (timers.containsKey(field.key)) {
                timers.remove(field.key);
                styleTimerToggle(toggle, false);
            } else {
                long current = (Long) display.getTag();
                timers.put(field.key, System.currentTimeMillis() - current);
                styleTimerToggle(toggle, true);
                tick(display, field.key);
            }
            onChange.run();
        });
        reset.setOnClickListener(v -> {
            timers.remove(field.key);
            styleTimerToggle(toggle, false);
            display.setTag(0L);
            display.setText(FormatUtil.formatMs(0));
            onChange.run();
        });

        inputs.put(field.key, display);
    }

    private void numericControl(LinearLayout fieldBox, FieldDefinition field, Object value, Map<String, View> inputs, boolean readOnly, Runnable onChange) {
        Button minus = ui.secondaryButton("−");
        Button plus = ui.primaryButton("+");
        EditText editText = styledEditText(value);
        editText.setInputType("int".equals(field.type)
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editText.setTextSize(ui.sp(18));
        editText.setPadding(ui.spaceM(), ui.spaceM(), ui.spaceM(), ui.spaceM());
        editText.setEnabled(!readOnly);
        minus.setEnabled(!readOnly);
        plus.setEnabled(!readOnly);

        View.OnClickListener adjust = v -> {
            hideKeyboard(editText);
            double current = FormatUtil.parseDouble(editText.getText().toString(), 0);
            current += v == plus ? field.increment : -field.increment;
            editText.setText("int".equals(field.type)
                    ? String.valueOf(Math.round(current))
                    : String.format(Locale.US, "%." + field.decimals + "f", current));
        };
        minus.setOnClickListener(adjust);
        plus.setOnClickListener(adjust);
        if (!readOnly) {
            ui.onTextChanged(editText, onChange);
        }

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.bottomMargin = ui.spaceS();
        fieldBox.addView(ui.outlinedInput(fieldLabel(field), editText), inputLp);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int buttonHeight = numericHeight();
        minus.setTextSize(ui.sp(24));
        plus.setTextSize(ui.sp(24));
        LinearLayout.LayoutParams minusLp = new LinearLayout.LayoutParams(0, buttonHeight, 1f);
        minusLp.rightMargin = ui.spaceS();
        row.addView(minus, minusLp);
        row.addView(plus, new LinearLayout.LayoutParams(0, buttonHeight, 1f));
        fieldBox.addView(row);
        inputs.put(field.key, editText);
    }

    private EditText styledEditText(Object value) {
        EditText editText = new TextInputEditText(activity);
        editText.setText(value == null ? "" : String.valueOf(value));
        editText.setTextColor(theme.primaryTextColor());
        editText.setHintTextColor(theme.mutedTextColor());
        editText.setMinHeight(ui.buttonHeight());
        editText.setBackground(null);
        return editText;
    }


    private String fieldLabel(FieldDefinition field) {
        return field.label;
    }

    private void setCollapsed(View content, ImageView expand, boolean collapsed) {
        expand.animate().rotation(collapsed ? 0f : 180f).setDuration(150).start();
        ui.animateCollapse(content, collapsed);
    }

    private void styleTimerToggle(Button button, boolean running) {
        button.setText(running ? ui.t("Stop") : ui.t("Start"));
        button.setTextColor(running ? 0xffb42318 : Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(running ? theme.cautionFillColor() : theme.accentColor()));
    }

    private void tick(TextView display, String key) {
        Long startedAt = timers.get(key);
        if (startedAt == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        display.setTag(elapsed);
        display.setText(FormatUtil.formatMs(elapsed));
        handler.postDelayed(() -> tick(display, key), 500);
    }

    private void hideKeyboard(View view) {
        if (!view.hasFocus()) {
            return;
        }
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        view.clearFocus();
    }


    private int stringMinLines() {
        String size = theme.fieldSize();
        return "large".equals(size) ? 3 : "compact".equals(size) ? 1 : 2;
    }

    private int stringMaxLines() {
        String size = theme.fieldSize();
        return "large".equals(size) ? 10 : "compact".equals(size) ? 3 : 6;
    }

    private int numericHeight() {
        String size = theme.fieldSize();
        return ui.px("large".equals(size) ? 64 : "compact".equals(size) ? 48 : 56);
    }

}
