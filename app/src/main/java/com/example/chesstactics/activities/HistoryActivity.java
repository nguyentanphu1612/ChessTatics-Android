package com.example.chesstactics.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chesstactics.R;
import com.example.chesstactics.adapters.MatchHistoryAdapter;
import com.example.chesstactics.database.ChessDatabaseHelper;
import com.example.chesstactics.models.MatchRecord;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private ChessDatabaseHelper dbHelper;
    private RecyclerView rvMatchHistory;
    private MatchHistoryAdapter adapter;
    private final List<MatchRecord> displayedMatches = new ArrayList<>();

    private TextView tvStatTotal, tvStatWins, tvStatRate;
    private Button btnFilterAll, btnFilterWin, btnFilterLoss;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        dbHelper = new ChessDatabaseHelper(this);

        tvStatTotal = findViewById(R.id.tvStatTotal);
        tvStatWins = findViewById(R.id.tvStatWins);
        tvStatRate = findViewById(R.id.tvStatRate);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterWin = findViewById(R.id.btnFilterWin);
        btnFilterLoss = findViewById(R.id.btnFilterLoss);
        rvMatchHistory = findViewById(R.id.rvMatchHistory);

        findViewById(R.id.btnBackHistory).setOnClickListener(v -> finish());

        findViewById(R.id.btnClearHistory).setOnClickListener(v -> {
            if (displayedMatches.isEmpty() && dbHelper.getMatches("ALL").isEmpty()) return;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.clear_all)
                    .setMessage(R.string.confirm_clear_history)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        dbHelper.clearAll();
                        refreshData();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        });

        rvMatchHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MatchHistoryAdapter(displayedMatches, new MatchHistoryAdapter.OnMatchItemListener() {
            @Override
            public void onReplayClicked(MatchRecord record) {
                Intent intent = new Intent(HistoryActivity.this, ReplayActivity.class);
                intent.putExtra(ReplayActivity.EXTRA_UCI_MOVES, record.movesUci);
                intent.putExtra(ReplayActivity.EXTRA_PLAYER_COLOR, record.playerColor);
                startActivity(intent);
            }

            @Override
            public void onDeleteClicked(MatchRecord record) {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle(R.string.delete_match)
                        .setMessage(R.string.confirm_delete_match)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            dbHelper.deleteMatch(record.id);
                            refreshData();
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
            }
        });
        rvMatchHistory.setAdapter(adapter);

        btnFilterAll.setOnClickListener(v -> applyFilter("ALL"));
        btnFilterWin.setOnClickListener(v -> applyFilter("WIN"));
        btnFilterLoss.setOnClickListener(v -> applyFilter("LOSS"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        updateFilterButtonStyles();
        loadFilteredMatches();
    }

    private void refreshData() {
        updateStatsSummary();
        updateFilterButtonStyles();
        loadFilteredMatches();
    }

    private void updateStatsSummary() {
        List<MatchRecord> allMatches = dbHelper.getMatches("ALL");
        int total = allMatches.size();
        int wins = 0;
        for (MatchRecord record : allMatches) {
            if ("WIN".equals(record.result)) {
                wins++;
            }
        }
        int rate = (total > 0) ? (wins * 100 / total) : 0;

        tvStatTotal.setText(String.valueOf(total));
        tvStatWins.setText(String.valueOf(wins));
        tvStatRate.setText(rate + "%");
    }

    private void loadFilteredMatches() {
        displayedMatches.clear();
        displayedMatches.addAll(dbHelper.getMatches(currentFilter));
        adapter.notifyDataSetChanged();
    }

    private void updateFilterButtonStyles() {
        btnFilterAll.setBackgroundResource("ALL".equals(currentFilter)
                ? R.drawable.bg_btn_blue : R.drawable.bg_chip_pill);
        btnFilterWin.setBackgroundResource("WIN".equals(currentFilter)
                ? R.drawable.bg_btn_blue : R.drawable.bg_chip_pill);
        btnFilterLoss.setBackgroundResource("LOSS".equals(currentFilter)
                ? R.drawable.bg_btn_blue : R.drawable.bg_chip_pill);
    }
}