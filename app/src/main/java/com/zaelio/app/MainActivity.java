package com.zaelio.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.zaelio.app.theme.ThemeStore;
import com.zaelio.app.ui.AppUi;
import com.zaelio.app.ui.SettingsUi;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_JSON = 10;
    private static final int REQUEST_EXPORT_JSON = 11;

    private TrackingDatabase db;
    private ThemeStore theme;
    private AppUi ui;
    private HomeUi homeUi;
    private SettingsUi settingsUi;
    private TrackerFlowUi trackerFlowUi;
    private LinearLayout root;
    private int currentTab = 0;
    private int transferMode = 0;
    private long lastBackPressMs = 0;
    private Runnable currentBackAction;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = new ThemeStore(this);
        ui = new AppUi(this, theme);
        db = new TrackingDatabase(this);
        settingsUi = new SettingsUi(this, theme, ui, this::refreshSettings, this::refreshHome);
        trackerFlowUi = new TrackerFlowUi(this, db, theme, ui, handler, () -> showHome(0), () -> showHome(1), this::setBackAction);
        homeUi = new HomeUi(this, db, theme, ui, trackerFlowUi::openSession, trackerFlowUi::editTracker, this::refreshHome);
        showHome(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_IMPORT_JSON) {
            importJson(data.getData());
        } else if (requestCode == REQUEST_EXPORT_JSON) {
            exportJson(data.getData());
        }
    }

    @Override
    public void onBackPressed() {
        if (currentBackAction != null) {
            currentBackAction.run();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressMs < 2000) {
            super.onBackPressed();
            return;
        }
        lastBackPressMs = now;
        Toast.makeText(this, ui.t("Zum Beenden erneut Zurück drücken"), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (trackerFlowUi != null) {
            trackerFlowUi.clearTimers();
        }
        super.onDestroy();
    }

    private void refreshHome() {
        showHome(currentTab);
    }

    private void refreshSettings() {
        int scrollY = currentScrollY(root);
        ViewGroup parent = (ViewGroup) root.getParent();
        if (parent == null) {
            return;
        }

        LinearLayout oldRoot = root;
        LinearLayout newRoot = new LinearLayout(this);
        newRoot.setOrientation(LinearLayout.VERTICAL);
        newRoot.setBackgroundColor(theme.backgroundColor());
        newRoot.setAlpha(0f);
        settingsUi.render(newRoot);
        parent.addView(newRoot, oldRoot.getLayoutParams());
        restoreScrollY(newRoot, scrollY);

        newRoot.animate().alpha(1f).setDuration(120).withEndAction(() -> {
            parent.removeView(oldRoot);
            root = newRoot;
        }).start();
    }

    private void setBackAction(Runnable backAction) {
        currentBackAction = backAction;
        lastBackPressMs = 0;
    }

    private View bottomNav(int selectedTab) {
        return ui.bottomNav(selectedTab == 0, v -> showHome(0), v -> showHome(1));
    }

    private Button floatingActionButton(int tab) {
        return ui.floatingActionButton(v -> {
            if (tab == 0) {
                trackerFlowUi.chooseTracker();
            } else {
                trackerFlowUi.createTracker();
            }
        });
    }

    private void base() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.backgroundColor());
        setContentView(root);
    }

    private void showHome(int tab) {
        currentTab = tab;
        setBackAction(null);
        base();

        root.addView(ui.appBar("Zaelio", false, null, true, this::showOverflowMenu));

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, 0, 1);
        root.addView(content, contentLp);

        FrameLayout body = new FrameLayout(this);
        content.addView(body, new FrameLayout.LayoutParams(-1, -1));

        if (tab == 0) {
            sessions(body);
        } else {
            trackers(body);
        }

        content.addView(floatingActionButton(tab));

        root.addView(bottomNav(tab));
    }

    private void showOverflowMenu(View anchor) {
        ui.showActionMenu("Menü",
                ui.action("Einstellungen", this::showSettingsScreen),
                ui.action("Daten übertragen", this::showDataTransferScreen),
                ui.action("Über die App", this::showAboutScreen));
    }

    private void showDataTransferScreen() {
        setBackAction(this::refreshHome);
        base();
        LinearLayout box = ui.screenBody(root, "Daten übertragen", this::refreshHome);
        box.addView(transferCard("Vollständiges Backup", "Tracker und Sessions", 0));
        box.addView(transferCard("Nur Tracker", "Vorlagen ohne Session-Einträge", 1));
        box.addView(transferCard("Nur Sessions", "Gespeicherte Einträge", 2));
    }

    private View transferCard(String title, String subtitle, int mode) {
        LinearLayout card = ui.contentCard();
        ui.addSectionHeader(card, title, subtitle);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button importButton = ui.secondaryButton("Importieren");
        Button exportButton = ui.primaryButton("Exportieren");
        importButton.setOnClickListener(v -> chooseImportJson(mode));
        exportButton.setOnClickListener(v -> chooseExportJson(mode));
        row.addView(importButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(0, -2, 1);
        exportLp.leftMargin = ui.spaceS();
        row.addView(exportButton, exportLp);
        card.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = ui.spaceM();
        card.setLayoutParams(lp);
        return card;
    }

    private void chooseImportJson(int mode) {
        transferMode = mode;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_JSON);
    }

    private void chooseExportJson(int mode) {
        transferMode = mode;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "zaelio-" + transferName(mode) + "-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json");
        startActivityForResult(intent, REQUEST_EXPORT_JSON);
    }

    private void importJson(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            String json = readAll(in);
            int count = transferMode == 1
                    ? BackupJsonRepository.importTrackers(db, json, ui.t("Importierter Tracker"))
                    : transferMode == 2 ? BackupJsonRepository.importSessions(db, json) : BackupJsonRepository.importAll(db, json, ui.t("Importierter Tracker"));
            Toast.makeText(this, ui.t("Import abgeschlossen (") + count + " " + ui.t("Tracker") + ")", Toast.LENGTH_LONG).show();
            refreshHome();
        } catch (Exception e) {
            Toast.makeText(this, ui.t("Import fehlgeschlagen: ") + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportJson(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            String json = transferMode == 1
                    ? BackupJsonRepository.exportTrackers(db)
                    : transferMode == 2 ? BackupJsonRepository.exportSessions(db) : BackupJsonRepository.exportAll(db);
            out.write(json.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, ui.t("Export gespeichert"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, ui.t("Export fehlgeschlagen: ") + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String transferName(int mode) {
        return mode == 1 ? "trackers" : mode == 2 ? "sessions" : "backup";
    }

    private String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }

    private void showSettingsScreen() {
        setBackAction(this::refreshHome);
        base();
        settingsUi.render(root);
    }

    private void restoreScrollY(View view, int scrollY) {
        ScrollView scrollView = firstScrollView(view);
        if (scrollView != null && scrollY > 0) {
            scrollView.post(() -> scrollView.scrollTo(0, scrollY));
        }
    }

    private int currentScrollY(View view) {
        ScrollView scrollView = firstScrollView(view);
        return scrollView == null ? 0 : scrollView.getScrollY();
    }

    private ScrollView firstScrollView(View view) {
        if (view instanceof ScrollView) {
            return (ScrollView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView scrollView = firstScrollView(group.getChildAt(i));
                if (scrollView != null) {
                    return scrollView;
                }
            }
        }
        return null;
    }

    private void showAboutScreen() {
        setBackAction(this::refreshHome);
        base();
        settingsUi.renderAbout(root);
    }

    private void sessions(FrameLayout body) {
        homeUi.renderSessions(body);
    }

    private void trackers(FrameLayout body) {
        homeUi.renderTrackers(body);
    }
}
