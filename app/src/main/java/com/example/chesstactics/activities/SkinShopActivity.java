package com.example.chesstactics.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chesstactics.R;
import com.example.chesstactics.adapters.SkinAdapter;
import com.example.chesstactics.engine.BoardState;
import com.example.chesstactics.models.BoardTheme;
import com.example.chesstactics.utils.PreferenceManager;
import com.example.chesstactics.views.ChessBoardView;

public class SkinShopActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_shop);

        findViewById(R.id.btnBackSkin).setOnClickListener(v -> finish());

        PreferenceManager prefs = new PreferenceManager(this);
        ChessBoardView previewBoard = findViewById(R.id.previewBoardView);
        previewBoard.setBoardState(new BoardState());
        previewBoard.setInteractionEnabled(false);
        previewBoard.setTheme(BoardTheme.getThemeById(prefs.getThemeId()));

        RecyclerView rvSkins = findViewById(R.id.rvSkins);
        rvSkins.setLayoutManager(new LinearLayoutManager(this));

        final SkinAdapter[] adapterHolder = new SkinAdapter[1];
        adapterHolder[0] = new SkinAdapter(BoardTheme.getAllThemes(), prefs.getThemeId(), theme -> {
            prefs.setThemeId(theme.id);
            previewBoard.setTheme(theme);
            adapterHolder[0].setActiveThemeId(theme.id);
        });
        rvSkins.setAdapter(adapterHolder[0]);
    }
}