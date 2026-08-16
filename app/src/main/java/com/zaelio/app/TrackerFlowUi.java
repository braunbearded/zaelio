package com.zaelio.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.text.InputType;
import android.widget.Filter;
import android.view.Gravity;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public final class TrackerFlowUi {
    private final Activity activity;
    private final TrackingDatabase db;
    private final ThemeStore theme;
    private final AppUi ui;
    private final Handler handler;
    private final Runnable backToSessions;
    private final Runnable backToTrackers;
    private final Consumer<Runnable> setBackAction;
    private final Map<String, Long> timers = new HashMap<>();
    private final FieldInputUi fieldInputUi;
    private LinearLayout root;

    public TrackerFlowUi(Activity activity, TrackingDatabase db, ThemeStore theme, AppUi ui,
                         Handler handler, Runnable backToSessions, Runnable backToTrackers,
                         Consumer<Runnable> setBackAction) {
        this.activity = activity;
        this.db = db;
        this.theme = theme;
        this.ui = ui;
        this.handler = handler;
        this.backToSessions = backToSessions;
        this.backToTrackers = backToTrackers;
        this.setBackAction = setBackAction;
        this.fieldInputUi = new FieldInputUi(activity, theme, ui, handler, timers);
    }

    public void clearTimers() {
        timers.clear();
    }

    public void chooseTracker() {
        List<Tracker> trackers = db.trackers();
        setBackAction.accept(backToSessions);
        base();
        root.addView(ui.appBar("Tracker auswählen", false, null, false, null));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.spaceL(), ui.spaceM(), ui.spaceL(), ui.spaceL());
        scrollView.addView(box);

        TextView intro = ui.bodyText("Wähle einen Tracker für die neue Session.");
        intro.setPadding(ui.spaceXs(), ui.spaceXs(), ui.spaceXs(), ui.spaceM());
        box.addView(intro);

        if (trackers.isEmpty()) {
            Button create = ui.primaryButton("Neuen Tracker anlegen");
            create.setOnClickListener(v -> createTracker());
            box.addView(create, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (Tracker tracker : trackers) {
                View item = selectionRow(tracker.name == null || tracker.name.trim().isEmpty() ? ui.t("Unbenannter Tracker") : tracker.name);
                item.setOnClickListener(v -> {
                    long sessionId = db.createSession(tracker.id);
                    if (sessionId == -1) {
                        Toast.makeText(activity, ui.t("Session konnte nicht angelegt werden"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    openSession(sessionId);
                });
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = ui.spaceSm();
                box.addView(item, rowLp);
            }
        }

        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(footerButton("Zurück", backToSessions));
    }

    private View selectionRow(String title) {
        LinearLayout row = ui.listRow(null, ui.twoLineText(ui.titleText(title), null), ui.listIcon("›"));
        row.setPadding(ui.spaceL(), ui.spaceMl(), ui.spaceL(), ui.spaceMl());
        row.setBackground(ui.makeRoundedCard(theme.surfaceAltColor(), theme.borderColor()));
        return row;
    }

    public void createTracker() {
        openTrackerEditor(-1, templateTracker(), true);
    }

    public void editTracker(long id) {
        Tracker tracker = db.readTracker(id);
        if (tracker == null) {
            Toast.makeText(activity, ui.t("Tracker nicht gefunden"), Toast.LENGTH_SHORT).show();
            backToTrackers.run();
            return;
        }
        openTrackerEditor(id, tracker, false);
    }

    public void openSession(long sessionId) {
        Session session = db.session(sessionId);
        if (session == null) {
            Toast.makeText(activity, ui.t("Session nicht gefunden"), Toast.LENGTH_SHORT).show();
            backToSessions.run();
            return;
        }

        Tracker tracker = db.readTracker(session.trackerId);
        if (tracker == null || tracker.fields.isEmpty()) {
            Toast.makeText(activity, ui.t("Tracker enthält keine Felder"), Toast.LENGTH_SHORT).show();
            backToSessions.run();
            return;
        }

        showFields(session, tracker);
    }

    private void openTrackerEditor(long id, Tracker tracker, boolean isNew) {
        if (!isNew && db.readTracker(id) == null) {
            Toast.makeText(activity, ui.t("Tracker nicht gefunden"), Toast.LENGTH_SHORT).show();
            backToTrackers.run();
            return;
        }

        base();
        root.addView(ui.appBar(isNew ? "Neuer Tracker" : "Tracker bearbeiten", false, null, !isNew, v -> showTrackerMenu(v, id, tracker.name)));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.spaceL(), ui.spaceL(), ui.spaceL(), ui.spaceL());
        scrollView.addView(body);

        TrackerEditorForm form = buildTrackerEditorForm(tracker);
        LinearLayout formCard = ui.compactCard();
        ui.addSectionHeader(formCard, "Grunddaten", null);

        formCard.addView(outlinedInput("Tracker-Name", form.nameInput));
        TextInputLayout descriptionInput = outlinedInput("Beschreibung", form.descriptionInput);
        ((LinearLayout.LayoutParams) descriptionInput.getLayoutParams()).bottomMargin = ui.spaceXs();
        formCard.addView(descriptionInput);
        body.addView(formCard);

        LinearLayout fieldsHeader = new LinearLayout(activity);
        fieldsHeader.setOrientation(LinearLayout.HORIZONTAL);
        fieldsHeader.setGravity(Gravity.CENTER_VERTICAL);
        fieldsHeader.setPadding(ui.spaceM(), ui.spaceS(), ui.spaceS(), ui.spaceS());
        fieldsHeader.setBackground(ui.makeRoundedCard(theme.surfaceAltColor(), theme.borderColor()));

        TextView fieldsTitle = ui.titleText("Felder");
        fieldsHeader.addView(fieldsTitle);

        TextView fieldsCount = new TextView(activity);
        fieldsCount.setTextSize(ui.sp(12));
        fieldsCount.setTextColor(theme.accentColor());
        fieldsCount.setGravity(Gravity.CENTER);
        fieldsCount.setPadding(ui.spaceS(), ui.focusedStrokeWidth(), ui.spaceS(), ui.focusedStrokeWidth());
        fieldsCount.setBackground(ui.makeRoundedCard(theme.accentSoftColor(), theme.accentSoftColor()));
        LinearLayout.LayoutParams fieldCountLp = new LinearLayout.LayoutParams(-2, -2);
        fieldCountLp.leftMargin = ui.spaceS();
        fieldsHeader.addView(fieldsCount, fieldCountLp);
        fieldsHeader.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));

        Button addField = ui.primaryButton("Feld hinzufügen");
        fieldsHeader.addView(addField, new LinearLayout.LayoutParams(-2, ui.buttonHeight()));
        LinearLayout.LayoutParams fieldsHeaderLp = new LinearLayout.LayoutParams(-1, -2);
        fieldsHeaderLp.topMargin = ui.spaceM();
        fieldsHeaderLp.bottomMargin = ui.spaceM();
        body.addView(fieldsHeader, fieldsHeaderLp);

        LinearLayout fieldsContainer = new LinearLayout(activity);
        fieldsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(fieldsContainer, new LinearLayout.LayoutParams(-1, -2));

        final Runnable[] updateFieldsHeaderRef = new Runnable[1];
        updateFieldsHeaderRef[0] = () -> {
            int count = 0;
            for (FieldEditorViews fieldViews : form.fields) {
                if (!fieldViews.removed) {
                    count++;
                }
            }
            fieldsCount.setText(String.valueOf(count));
        };

        final long[] trackerIdRef = new long[]{id};
        final Runnable[] persistRef = new Runnable[1];
        Runnable scheduleSave = () -> {
            if (updateFieldsHeaderRef[0] != null) {
                updateFieldsHeaderRef[0].run();
            }
            if (persistRef[0] != null) {
                persistRef[0].run();
            }
        };
        persistRef[0] = () -> {
            try {
                String json = trackerEditorToJson(form);
                if (trackerIdRef[0] == -1) {
                    trackerIdRef[0] = TrackerJsonRepository.saveTracker(db, -1, json, true);
                    if (trackerIdRef[0] == -1) {
                        throw new IllegalStateException(ui.t("Tracker konnte nicht gespeichert werden"));
                    }
                } else {
                    TrackerJsonRepository.updateTracker(db, trackerIdRef[0], json);
                }
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };

        for (FieldDefinition field : tracker.fields) {
            addFieldEditor(scrollView, fieldsContainer, form.fields, field, scheduleSave);
        }
        updateFieldsHeaderRef[0].run();

        addField.setOnClickListener(v -> {
            FieldEditorViews added = addFieldEditor(scrollView, fieldsContainer, form.fields, null, scheduleSave);
            scrollIntoView(scrollView, added.row);
            scheduleSave.run();
        });

        Runnable editorBack = () -> {
            try {
                trackerEditorToJson(form);
                backToTrackers.run();
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        setBackAction.accept(editorBack);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(footerButton("Zurück", editorBack));
        attachTrackerAutosave(form, scheduleSave);
    }

    private void showFields(Session session, Tracker tracker) {
        base();
        Map<String, View> inputs = new HashMap<>();
        root.addView(ui.appBar(tracker.name == null || tracker.name.trim().isEmpty() ? "Session" : tracker.name,
                false, null, true, v -> showSessionMenu(v, session)));
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.spaceL(), ui.spaceL(), ui.spaceL(), ui.spaceL());
        scrollView.addView(box);

        Map<String, Object> values = initialValues(session, tracker.fields);
        Map<Long, FieldDefinition> dirtyFields = new LinkedHashMap<>();
        final Runnable[] pendingSave = new Runnable[1];
        Runnable flushSave = () -> {
            if (pendingSave[0] != null) {
                handler.removeCallbacks(pendingSave[0]);
                pendingSave[0] = null;
            }
            saveSessionFields(session, tracker.fields, inputs);
        };
        Runnable saveDirtyFields = () -> {
            saveSessionFields(session, new ArrayList<>(dirtyFields.values()), inputs);
            dirtyFields.clear();
        };
        for (FieldDefinition field : tracker.fields) {
            Runnable scheduleSave = () -> {
                dirtyFields.put(field.id, field);
                if (pendingSave[0] != null) {
                    handler.removeCallbacks(pendingSave[0]);
                }
                pendingSave[0] = () -> {
                    pendingSave[0] = null;
                    saveDirtyFields.run();
                };
                handler.postDelayed(pendingSave[0], 700);
            };
            fieldInputUi.fieldControl(box, field, values, inputs, false, theme.sessionFieldsCollapsed(), scheduleSave);
        }

        Runnable back = () -> {
            flushSave.run();
            clearTimers();
            backToSessions.run();
        };
        setBackAction.accept(back);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(footerButton("Zurück", back));
    }

    private Map<String, Object> initialValues(Session session, List<FieldDefinition> fieldDefinitions) {
        Map<Long, FieldRecord> records = db.records(session.id);
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldDefinition field : fieldDefinitions) {
            if (records.containsKey(field.id)) {
                values.putAll(JsonUtil.toMap(records.get(field.id).valuesJson));
                continue;
            }

            Object value = TrackingDatabase.NO_PREVIOUS;
            if (field.prefillFromPrevious) {
                value = db.previousValue(session.trackerId, field.id, field.key);
            }
            if (!field.prefillFromPrevious || value == TrackingDatabase.NO_PREVIOUS) {
                value = parse(field.defaultValue, field.type);
            }
            values.put(field.key, value);
        }
        return values;
    }

    private Object parse(String value, String type) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            if ("int".equals(type) || "duration".equals(type)) {
                return Long.parseLong(value);
            }
            if ("float".equals(type)) {
                return Double.parseDouble(value);
            }
        } catch (Exception ignored) {
        }
        return value;
    }

    private Map<String, Object> readInputs(List<FieldDefinition> fieldDefinitions, Map<String, View> inputs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldDefinition field : fieldDefinitions) {
            View view = inputs.get(field.key);
            if (view instanceof TextView && view.getTag() instanceof Long) {
                values.put(field.key, (Long) view.getTag());
            } else if (view instanceof EditText) {
                String value = ((EditText) view).getText().toString();
                values.put(field.key, parse(value, field.type));
            }
        }
        return values;
    }

    private Tracker templateTracker() {
        Tracker tracker = new Tracker();
        tracker.name = "";
        tracker.description = "";
        return tracker;
    }

    private TrackerEditorForm buildTrackerEditorForm(Tracker tracker) {
        TrackerEditorForm form = new TrackerEditorForm();
        form.nameInput = labeledInput("Tracker-Name", tracker.name == null ? "" : tracker.name,
                InputType.TYPE_CLASS_TEXT);
        form.descriptionInput = labeledInput("Beschreibung", tracker.description == null ? "" : tracker.description,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        form.descriptionInput.setMinLines(2);
        form.descriptionInput.setGravity(Gravity.TOP);
        return form;
    }

    private void attachTrackerAutosave(TrackerEditorForm form, Runnable scheduleSave) {
        ui.onTextChanged(form.nameInput, scheduleSave);
        ui.onTextChanged(form.descriptionInput, scheduleSave);
    }

    private FieldEditorViews addFieldEditor(ScrollView scrollView, LinearLayout container, List<FieldEditorViews> fieldEditors, FieldDefinition field, Runnable scheduleSave) {
        FieldEditorViews views = new FieldEditorViews();
        views.existing = field != null && field.id > 0;

        View reorder = reorderHandle();
        TextView menu = iconAction("⋮");
        ImageView expand = ui.expandIcon();

        views.keyInput = labeledInput("Key", field == null ? "" : field.key, InputType.TYPE_CLASS_TEXT);
        views.labelInput = labeledInput("Feldname", field == null ? "" : field.label, InputType.TYPE_CLASS_TEXT);
        views.defaultValueInput = labeledInput("Standardwert", field == null ? "" : String.valueOf(field.defaultValue == null ? "" : field.defaultValue), InputType.TYPE_CLASS_TEXT);
        views.incrementInput = labeledInput("Schrittweite", field == null ? "1" : String.valueOf(field.increment), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        views.decimalsInput = labeledInput("Nachkommastellen", field == null ? "1" : String.valueOf(field.decimals), InputType.TYPE_CLASS_NUMBER);

        TextInputLayout typeLayout = new TextInputLayout(activity);
        typeLayout.setHint(ui.t("Typ"));
        typeLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        typeLayout.setBoxBackgroundColor(theme.surfaceColor());
        typeLayout.setBoxStrokeColor(theme.accentColor());
        typeLayout.setBoxStrokeColorStateList(inputBorderStateList());
        typeLayout.setBoxStrokeWidth(ui.strokeWidth());
        typeLayout.setBoxStrokeWidthFocused(ui.focusedStrokeWidth());
        typeLayout.setHintTextColor(inputHintStateList());
        typeLayout.setBoxCornerRadii(ui.cornerRadius(), ui.cornerRadius(), ui.cornerRadius(), ui.cornerRadius());
        typeLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(-1, -2);
        typeLp.bottomMargin = ui.spaceXs();
        typeLayout.setLayoutParams(typeLp);

        MaterialAutoCompleteTextView typeInput = new MaterialAutoCompleteTextView(activity);
        String[] typeLabels = {ui.t("Text"), ui.t("Ganzzahl"), ui.t("Dezimalzahl"), ui.t("Timer")};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(activity, android.R.layout.simple_dropdown_item_1line, typeLabels) {
            private final Filter filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = typeLabels;
                    results.count = typeLabels.length;
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };

            @Override
            public Filter getFilter() {
                return filter;
            }
        };
        typeInput.setAdapter(typeAdapter);
        typeInput.setThreshold(0);
        typeInput.setOnClickListener(v -> typeInput.showDropDown());
        typeInput.setText(typeLabels[typeIndex(field == null ? null : field.type)], false);
        typeInput.setInputType(0);
        typeInput.setTextColor(theme.primaryTextColor());
        typeInput.setHintTextColor(inputHintStateList());
        typeInput.setBackgroundTintList(inputBorderStateList());
        tintCursor(typeInput);
        typeInput.setPadding(ui.spaceM(), 0, ui.spaceM(), 0);
        typeLayout.addView(typeInput, new LinearLayout.LayoutParams(-1, ui.rowHeight()));
        views.typeInput = typeInput;

        MaterialCheckBox required = new MaterialCheckBox(activity);
        required.setText(ui.t("Pflichtfeld"));
        required.setChecked(field != null && field.required);
        styleCheckBox(required);
        views.requiredCheck = required;

        MaterialCheckBox prefill = new MaterialCheckBox(activity);
        prefill.setText(ui.t("Vorherigen Wert übernehmen"));
        prefill.setChecked(field != null && field.prefillFromPrevious);
        styleCheckBox(prefill);
        views.prefillCheck = prefill;

        LinearLayout numericRow = new LinearLayout(activity);
        numericRow.setOrientation(LinearLayout.HORIZONTAL);
        numericRow.setWeightSum(2);
        TextInputLayout incrementWrap = outlinedInput("Schrittweite", views.incrementInput);
        TextInputLayout decimalsWrap = outlinedInput("Nachkommastellen", views.decimalsInput);
        LinearLayout.LayoutParams incrementLp = new LinearLayout.LayoutParams(0, -2, 1);
        incrementLp.rightMargin = ui.spaceS();
        numericRow.addView(incrementWrap, incrementLp);
        numericRow.addView(decimalsWrap, new LinearLayout.LayoutParams(0, -2, 1));
        views.incrementWrap = incrementWrap;
        views.decimalsWrap = decimalsWrap;
        views.numericRow = numericRow;

        LinearLayout editor = new LinearLayout(activity);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(0, ui.spaceM(), 0, 0);
        editor.addView(outlinedInput("Standardwert", views.defaultValueInput));
        editor.addView(typeLayout);
        editor.addView(required);
        editor.addView(prefill);
        editor.addView(numericRow);
        views.editor = editor;

        LinearLayout row = ui.compactCard();
        views.summaryTitle = ui.titleText("");
        views.summaryMeta = ui.metaText("");
        LinearLayout summaryText = ui.twoLineText(views.summaryTitle, views.summaryMeta);
        views.summaryText = summaryText;
        views.summaryInput = outlinedInput("Feldname", views.labelInput);
        FrameLayout summarySlot = new FrameLayout(activity);
        summarySlot.addView(summaryText, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        summarySlot.addView(views.summaryInput, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        LinearLayout summaryRow = ui.listRow(reorder, summarySlot, menu, expand);
        row.addView(summaryRow);
        expandReorderTouchArea(row, summaryRow, reorder);
        row.addView(editor);

        Runnable fieldChanged = () -> {
            updateFieldSummary(views);
            scheduleSave.run();
        };
        ui.onTextChanged(views.keyInput, fieldChanged);
        ui.onTextChanged(views.labelInput, fieldChanged);
        ui.onTextChanged(views.defaultValueInput, fieldChanged);
        ui.onTextChanged(views.incrementInput, fieldChanged);
        ui.onTextChanged(views.decimalsInput, fieldChanged);
        required.setOnCheckedChangeListener((buttonView, isChecked) -> fieldChanged.run());
        prefill.setOnCheckedChangeListener((buttonView, isChecked) -> fieldChanged.run());
        updateFieldEditorControls(views, selectedType(typeInput));
        updateFieldSummary(views);
        typeInput.setOnItemClickListener((parent, view, position, id) -> {
            updateFieldEditorControls(views, selectedType(typeInput));
            fieldChanged.run();
        });

        final LinearLayout[] shellRef = new LinearLayout[1];
        Runnable duplicateAction = () -> {
            FieldEditorViews added = addFieldEditor(scrollView, container, fieldEditors, fieldFromViews(views), scheduleSave);
            scrollIntoView(scrollView, added.row);
            scheduleSave.run();
        };
        Runnable removeNow = () -> {
            container.removeView(shellRef[0]);
            updateChildBottomMargins(container);
            views.removed = true;
            scheduleSave.run();
        };
        Runnable removeAction = () -> confirmDeleteField(views, null, () -> DeleteGestureHelper.animateDelete(ui, shellRef[0]), removeNow);
        menu.setOnClickListener(v -> showFieldMenu(v, duplicateAction, removeAction));
        View.OnClickListener toggle = v -> toggleFieldEditor(views, expand);
        expand.setOnClickListener(toggle);
        summaryRow.setOnClickListener(toggle);
        summarySlot.setOnClickListener(toggle);
        summaryText.setOnClickListener(toggle);
        views.summaryTitle.setOnClickListener(toggle);
        views.summaryMeta.setOnClickListener(toggle);
        LinearLayout shell = row;
        shellRef[0] = shell;

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = ui.spaceM();
        container.addView(shell, rowLp);
        updateChildBottomMargins(container);
        views.row = shell;
        views.container = container;
        fieldEditors.add(views);
        shell.setTag(views);
        attachFieldReorder(reorder, container, fieldEditors, views, scheduleSave);
        BiConsumer<Runnable, Runnable> deleteGesture = (restore, animateDelete) -> confirmDeleteField(views, restore, animateDelete, removeNow);
        DeleteGestureHelper.attachToTree(activity, theme, ui, summaryRow, shell, deleteGesture, null, reorder, menu, expand, views.summaryInput);
        setFieldExpanded(views, expand, field == null);
        return views;
    }

    private void confirmDeleteField(FieldEditorViews views, Runnable restore, Runnable animateDelete, Runnable removeNow) {
        String label = views.labelInput.getText().toString().trim();
        DeleteGestureHelper.runDelete(activity, ui, "Feld löschen",
                (label.isEmpty() ? ui.t("Dieses Feld") : label) + ui.t(" wirklich löschen?"),
                restore, animateDelete, removeNow);
    }

    private void showFieldMenu(View anchor, Runnable duplicateAction, Runnable removeAction) {
        ui.showActionMenu("Feld", ui.action("Kopieren", duplicateAction), ui.action("Löschen", removeAction));
    }

    private void toggleFieldEditor(FieldEditorViews views, ImageView expand) {
        setFieldExpanded(views, expand, views.editor.getVisibility() != View.VISIBLE);
    }

    private void setFieldExpanded(FieldEditorViews views, ImageView expand, boolean expanded) {
        views.summaryText.setVisibility(expanded ? View.GONE : View.VISIBLE);
        views.summaryInput.setVisibility(expanded ? View.VISIBLE : View.GONE);
        ui.animateCollapse(views.editor, !expanded);
        expand.animate().rotation(expanded ? 180f : 0f).setDuration(150).start();
    }

    private void updateFieldSummary(FieldEditorViews views) {
        String label = views.labelInput.getText().toString().trim();
        if (label.isEmpty()) {
            label = ui.t("Neues Feld");
        }
        views.summaryTitle.setText(ui.t(label));
        String type = selectedType(views.typeInput);
        String typeLabel = "string".equals(type) ? "Text" : "int".equals(type) ? "Ganzzahl" : "float".equals(type) ? "Dezimalzahl" : "Timer";
        views.summaryMeta.setText(ui.t(typeLabel));
    }

    private FieldDefinition fieldFromViews(FieldEditorViews views) {
        FieldDefinition field = new FieldDefinition();
        field.key = views.keyInput.getText().toString();
        field.label = views.labelInput.getText().toString();
        field.defaultValue = views.defaultValueInput.getText().toString();
        field.increment = FormatUtil.parseDouble(views.incrementInput.getText().toString(), 1);
        field.decimals = parseIntSafe(views.decimalsInput.getText().toString(), 1);
        field.type = selectedType(views.typeInput);
        field.required = views.requiredCheck.isChecked();
        field.prefillFromPrevious = views.prefillCheck.isChecked();
        return field;
    }

    private View reorderHandle() {
        TextView handle = new TextView(activity);
        handle.setText("⠿");
        handle.setTextSize(ui.sp(24));
        handle.setGravity(Gravity.CENTER);
        handle.setTextColor(theme.mutedTextColor());
        handle.setContentDescription(ui.t("Verschieben"));
        handle.setClickable(true);
        handle.setFocusable(true);
        return handle;
    }

    private void scrollIntoView(ScrollView scrollView, View target) {
        if (target == null) {
            return;
        }
        target.post(() -> {
            Rect rect = new Rect();
            target.getDrawingRect(rect);
            scrollView.offsetDescendantRectToMyCoords(target, rect);
            scrollView.smoothScrollTo(0, Math.max(0, rect.top - ui.spaceM()));
        });
    }

    private void expandReorderTouchArea(View parent, View row, View handle) {
        parent.post(() -> {
            Rect rect = new Rect();
            handle.getHitRect(rect);
            rect.offset(row.getLeft(), row.getTop());
            rect.left = 0;
            rect.top = row.getTop();
            rect.bottom = parent.getHeight();
            rect.right = Math.max(rect.right, ui.rowHeight());
            parent.setTouchDelegate(new TouchDelegate(rect, handle));
        });
    }

    private void attachFieldReorder(View handle, LinearLayout container, List<FieldEditorViews> editors, FieldEditorViews views, Runnable onChange) {
        ReorderHelper.attach(ui, handle, container, views.row, onChange, direction -> {
            updateChildBottomMargins(container);
            reorderList(editors, views, direction);
        });
    }

    private TextView iconAction(String text) {
        return ui.listIcon(text);
    }

    private <T> void reorderList(List<T> list, T item, int direction) {
        int from = list.indexOf(item);
        int to = from + direction;
        if (from >= 0 && to >= 0 && to < list.size()) {
            list.remove(from);
            list.add(to, item);
        }
    }

    private void updateChildBottomMargins(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                lp.bottomMargin = i == container.getChildCount() - 1 ? ui.spaceXs() : ui.spaceM();
                child.setLayoutParams(lp);
            }
        }
    }

    private void styleCheckBox(MaterialCheckBox checkBox) {
        checkBox.setUseMaterialThemeColors(false);
        checkBox.setTextColor(theme.primaryTextColor());
        checkBox.setButtonTintList(checkBoxStateList());
        checkBox.setMinHeight(ui.checkRowHeight());
        checkBox.setMinimumHeight(ui.checkRowHeight());
        checkBox.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.checkRowHeight());
        checkBox.setLayoutParams(lp);
    }

    private ColorStateList checkBoxStateList() {
        int accent = theme.accentColor();
        int normal = theme.darkMode() ? theme.secondaryTextColor() : theme.borderColor();
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{android.R.attr.state_hovered},
                        new int[]{android.R.attr.state_focused},
                        new int[]{android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{accent, accent, accent, normal, normal});
    }

    private ColorStateList inputHintStateList() {
        int accent = theme.accentColor();
        int normal = theme.mutedTextColor();
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{android.R.attr.state_hovered},
                        new int[]{android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{accent, accent, normal, normal});
    }

    private ColorStateList inputBorderStateList() {
        int accent = theme.accentColor();
        int normal = theme.darkMode() ? theme.secondaryTextColor() : theme.borderColor();
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{android.R.attr.state_hovered},
                        new int[]{android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{accent, accent, normal, normal});
    }

    private void tintCursor(EditText input) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(theme.accentColor());
            cursor.setSize(ui.focusedStrokeWidth(), ui.rowHeight() / 2);
            input.setTextCursorDrawable(cursor);
        }
    }

    private TextInputLayout outlinedInput(String label, EditText input) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = ui.spaceM();
        input.setHintTextColor(inputHintStateList());
        tintCursor(input);
        boolean multiline = input.getMinLines() > 1;
        input.setPadding(ui.spaceM(), multiline ? ui.spaceM() : 0, ui.spaceM(), multiline ? ui.spaceM() : 0);
        TextInputLayout layout = ui.outlinedInput(label, input);
        layout.setLayoutParams(lp);
        return layout;
    }

    private EditText labeledInput(String label, String value, int inputType) {
        return ui.textInput(label, value, inputType);
    }

    private String trackerEditorToJson(TrackerEditorForm form) throws Exception {
        JSONObject root = new JSONObject();
        root.put("name", form.nameInput.getText().toString().trim());
        root.put("description", form.descriptionInput.getText().toString().trim());

        JSONArray fields = new JSONArray();
        List<String> usedFieldKeys = new ArrayList<>();
        int fieldOrder = 0;
        for (FieldEditorViews fieldViews : form.fields) {
            if (fieldViews.removed) {
                continue;
            }

            String fieldLabel = fieldViews.labelInput.getText().toString().trim();
            if (fieldLabel.isEmpty()) {
                if (fieldViews.existing) {
                    throw new IllegalStateException(ui.t("Bestehende Felder brauchen einen Namen."));
                }
                continue;
            }
            String fieldKey = uniqueFieldKey(fieldLabel, usedFieldKeys);

            JSONObject field = new JSONObject();
            field.put("key", fieldKey);
            field.put("label", fieldLabel);
            field.put("type", selectedType(fieldViews.typeInput));
            field.put("order", fieldOrder++);

            String defaultValue = fieldViews.defaultValueInput.getText().toString().trim();
            field.put("defaultValue", defaultValue.isEmpty() ? JSONObject.NULL : defaultValue);
            field.put("increment", FormatUtil.parseDouble(fieldViews.incrementInput.getText().toString(), 1));
            field.put("decimals", parseIntSafe(fieldViews.decimalsInput.getText().toString(), 1));
            field.put("required", fieldViews.requiredCheck.isChecked());
            field.put("prefillFromPrevious", fieldViews.prefillCheck.isChecked());
            fields.put(field);
        }

        root.put("fields", fields);
        return root.toString(2);
    }

    private String uniqueFieldKey(String label, List<String> usedKeys) {
        String base = label.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "field";
        }

        String candidate = base;
        int suffix = 2;
        while (usedKeys.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        usedKeys.add(candidate);
        return candidate;
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int typeIndex(String type) {
        if ("int".equals(type)) {
            return 1;
        }
        if ("float".equals(type)) {
            return 2;
        }
        if ("duration".equals(type)) {
            return 3;
        }
        return 0;
    }

    private String selectedType(MaterialAutoCompleteTextView input) {
        String value = String.valueOf(input.getText());
        if (ui.t("Ganzzahl").equals(value)) {
            return "int";
        }
        if (ui.t("Dezimalzahl").equals(value)) {
            return "float";
        }
        if (ui.t("Timer").equals(value)) {
            return "duration";
        }
        return "string";
    }

    private void updateFieldEditorControls(FieldEditorViews views, String type) {
        boolean showIncrement = "int".equals(type) || "float".equals(type);
        boolean showDecimals = "float".equals(type);

        if (views.incrementWrap != null) {
            views.incrementWrap.setVisibility(showIncrement ? View.VISIBLE : View.GONE);
        }
        if (views.decimalsWrap != null) {
            views.decimalsWrap.setVisibility(showDecimals ? View.VISIBLE : View.GONE);
        }
    }

    private void saveSessionFields(Session session, List<FieldDefinition> fieldDefinitions, Map<String, View> inputs) {
        Map<String, Object> values = readInputs(fieldDefinitions, inputs);
        Map<Long, Map<String, Object>> valuesByFieldId = new LinkedHashMap<>();
        for (FieldDefinition field : fieldDefinitions) {
            Map<String, Object> fieldValue = new LinkedHashMap<>();
            fieldValue.put(field.key, values.get(field.key));
            valuesByFieldId.put(field.id, fieldValue);
        }
        db.saveRecords(session, valuesByFieldId);
    }

    private void showTrackerMenu(View anchor, long trackerId, String trackerName) {
        if (trackerId != -1) {
            ui.showActionMenu("Tracker",
                    ui.action("Tracker duplizieren", () -> duplicateTracker(trackerId)),
                    ui.action("Tracker löschen", () -> deleteTracker(trackerId)));
        }
    }

    private void showSessionMenu(View anchor, Session session) {
        ui.showActionMenu("Session", ui.action("Session löschen", () -> deleteSession(session.id)));
    }

    private void duplicateTracker(long trackerId) {
        try {
            if (TrackerJsonRepository.duplicateTracker(db, trackerId, ui.t("Unbenannter Tracker"), ui.t("Kopie")) == -1) {
                Toast.makeText(activity, ui.t("Tracker nicht gefunden"), Toast.LENGTH_SHORT).show();
                return;
            }
            backToTrackers.run();
        } catch (Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteTracker(long trackerId) {
        db.deleteTracker(trackerId);
        clearTimers();
        backToTrackers.run();
    }

    private void deleteSession(long sessionId) {
        db.deleteSession(sessionId);
        clearTimers();
        backToSessions.run();
    }

    private LinearLayout footerButton(String text, Runnable onClick) {
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(ui.spaceL(), ui.spaceS(), ui.spaceL(), ui.spaceL());

        Button button = ui.backButton(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> {
            if (onClick != null) {
                onClick.run();
            }
        });
        footer.addView(button);
        return footer;
    }

    private void base() {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.backgroundColor());
        activity.setContentView(root);
    }

    private static final class TrackerEditorForm {
        EditText nameInput;
        EditText descriptionInput;
        final List<FieldEditorViews> fields = new ArrayList<>();
    }

    private static final class FieldEditorViews {
        LinearLayout row;
        LinearLayout container;
        LinearLayout numericRow;
        View incrementWrap;
        View decimalsWrap;
        EditText keyInput;
        EditText labelInput;
        EditText defaultValueInput;
        EditText incrementInput;
        EditText decimalsInput;
        MaterialAutoCompleteTextView typeInput;
        TextView summaryTitle;
        TextView summaryMeta;
        View summaryText;
        View summaryInput;
        LinearLayout editor;
        MaterialCheckBox requiredCheck;
        MaterialCheckBox prefillCheck;
        boolean existing;
        boolean removed;
    }
}
