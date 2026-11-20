package com.example.chesstactics.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chesstactics.R;
import com.example.chesstactics.database.ChessDatabaseHelper;
import com.example.chesstactics.engine.Piece;
import com.example.chesstactics.models.MatchRecord;
import com.example.chesstactics.utils.LocaleHelper;
import com.example.chesstactics.utils.PreferenceManager;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PreferenceManager prefManager;
    private ChessDatabaseHelper dbHelper;

    private TextView tvHomeTotalMatches, tvHomeWins, tvHomeWinRate;
    private RadioGroup rgGameMode;
    private Spinner spinnerSide, spinnerTimeControl, spinnerAiLevel;
    private TextView tvLabelAiLevel;

    private final int[] timeOptionsMinutes = {0, 10, 15, 30, 45,};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefManager = new PreferenceManager(this);
        dbHelper = new ChessDatabaseHelper(this);

        tvHomeTotalMatches = findViewById(R.id.tvHomeTotalMatches);
        tvHomeWins = findViewById(R.id.tvHomeWins);
        tvHomeWinRate = findViewById(R.id.tvHomeWinRate);

        rgGameMode = findViewById(R.id.rgGameMode);
        spinnerSide = findViewById(R.id.spinnerSide);
        spinnerTimeControl = findViewById(R.id.spinnerTimeControl);
        spinnerAiLevel = findViewById(R.id.spinnerAiLevel);
        tvLabelAiLevel = findViewById(R.id.tvLabelAiLevel);

        ImageButton btnSound = findViewById(R.id.btnSoundToggle);
        TextView btnLang = findViewById(R.id.btnLanguage);
        ImageButton btnMoreMenu = findViewById(R.id.btnMoreMenu);

        btnLang.setText(prefManager.getLanguage().toUpperCase());
        updateSoundIcon(btnSound);

        setupSpinners();
        setupModeRadioListener();

        // Nút BẮT ĐẦU TRẬN ĐẤU
        findViewById(R.id.btnStartMatch).setOnClickListener(v -> startConfiguredMatch());

        // Nút ĐẤU ONLINE
        findViewById(R.id.btnPlayOnline).setOnClickListener(v -> {
            Intent intent = new Intent(this, OnlineLobbyActivity.class);
            intent.putExtra(GameActivity.EXTRA_TIME_MINUTES, getSelectedTimeMinutes());
            startActivity(intent);
        });

        // 2 Thẻ truy cập nhanh
        findViewById(R.id.cardHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.cardSkinShop).setOnClickListener(v ->
                startActivity(new Intent(this, SkinShopActivity.class)));

        // Bottom Navigation Bar
        findViewById(R.id.navPlay).setOnClickListener(v -> startConfiguredMatch());
        findViewById(R.id.navHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.navSkin).setOnClickListener(v ->
                startActivity(new Intent(this, SkinShopActivity.class)));

        // Nút Âm thanh, Ngôn ngữ & Menu 3 chấm
        btnSound.setOnClickListener(v -> toggleSound(btnSound));
        btnLang.setOnClickListener(v -> toggleLanguage());
        btnMoreMenu.setOnClickListener(v -> showTopPopupMenu(v, btnSound));
    }

    private void setupSpinners() {
        // 1. Spinner Phe quân cờ
        String[] sideLabels = {
                getString(R.string.play_as_white),
                getString(R.string.play_as_black)
        };
        ArrayAdapter<String> sideAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, sideLabels);
        sideAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerSide.setAdapter(sideAdapter);

        // 2. Spinner Thời gian suy nghĩ (mặc định "Không giới hạn thời gian" giống ảnh)
        String[] timeLabels = {
                getString(R.string.time_unlimited),
                getString(R.string.time_1_min),
                getString(R.string.time_5_min),
                getString(R.string.time_10_min),
                getString(R.string.time_15_min)
        };
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, timeLabels);
        timeAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerTimeControl.setAdapter(timeAdapter);

        // 3. Spinner Cấp độ AI (mặc định "Cực hạn" giống ảnh)
        String[] aiLabels = {
                getString(R.string.mode_ai_easy),
                getString(R.string.mode_ai_hard),
                getString(R.string.mode_ai_extreme)
        };
        ArrayAdapter<String> aiAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, aiLabels);
        aiAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerAiLevel.setAdapter(aiAdapter);
        spinnerAiLevel.setSelection(2); // Cực hạn
    }

    private void setupModeRadioListener() {
        rgGameMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isAi = (checkedId == R.id.rbVsAi);
            spinnerAiLevel.setEnabled(isAi);
            spinnerAiLevel.setAlpha(isAi ? 1.0f : 0.45f);
            tvLabelAiLevel.setAlpha(isAi ? 1.0f : 0.45f);
        });
    }

    private void startConfiguredMatch() {
        boolean isAi = (rgGameMode.getCheckedRadioButtonId() == R.id.rbVsAi);
        int selectedColor = (spinnerSide.getSelectedItemPosition() == 0) ? Piece.WHITE : Piece.BLACK;
        int timeMinutes = getSelectedTimeMinutes();

        String mode;
        if (!isAi) {
            mode = "PVP_LOCAL";
        } else {
            int aiPos = spinnerAiLevel.getSelectedItemPosition();
            mode = (aiPos == 0) ? "AI_EASY" : (aiPos == 1) ? "AI_HARD" : "AI_EXTREME";
        }

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_MODE, mode);
        intent.putExtra(GameActivity.EXTRA_PLAYER_COLOR, selectedColor);
        intent.putExtra(GameActivity.EXTRA_TIME_MINUTES, timeMinutes);
        startActivity(intent);
    }

    private int getSelectedTimeMinutes() {
        int pos = spinnerTimeControl.getSelectedItemPosition();
        if (pos >= 0 && pos < timeOptionsMinutes.length) {
            return timeOptionsMinutes[pos];
        }
        return 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<MatchRecord> all = dbHelper.getMatches("ALL");
        int total = all.size();
        int wins = 0;
        for (MatchRecord r : all) {
            if ("WIN".equals(r.result)) wins++;
        }
        int rate = (total > 0) ? (wins * 100 / total) : 0;

        tvHomeTotalMatches.setText(String.valueOf(total));
        tvHomeWins.setText(String.valueOf(wins));
        tvHomeWinRate.setText(rate + "%");
    }

    private void showTopPopupMenu(android.view.View anchor, ImageButton btnSound) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.sound_toggle_label) + ": "
                + (prefManager.isSoundEnabled() ? "ON" : "OFF"));
        popup.getMenu().add(0, 2, 1, getString(R.string.language_toggle_label));
        popup.getMenu().add(0, 3, 2, getString(R.string.card_custom_skin));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                toggleSound(btnSound);
            } else if (item.getItemId() == 2) {
                toggleLanguage();
            } else if (item.getItemId() == 3) {
                startActivity(new Intent(this, SkinShopActivity.class));
            }
            return true;
        });
        popup.show();
    }

    private void toggleSound(ImageButton btnSound) {
        boolean next = !prefManager.isSoundEnabled();
        prefManager.setSoundEnabled(next);
        updateSoundIcon(btnSound);
    }

    private void toggleLanguage() {
        String nextLang = prefManager.getLanguage().equals("vi") ? "en" : "vi";
        prefManager.setLanguage(nextLang);
        LocaleHelper.applyLocale(nextLang);
    }

    private void updateSoundIcon(ImageButton btn) {
        btn.setImageResource(prefManager.isSoundEnabled()
                ? android.R.drawable.ic_lock_silent_mode_off
                : android.R.drawable.ic_lock_silent_mode);
    }
}