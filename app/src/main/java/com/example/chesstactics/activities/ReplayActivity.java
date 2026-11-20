package com.example.chesstactics.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chesstactics.R;
import com.example.chesstactics.engine.BoardState;
import com.example.chesstactics.engine.MoveGenerator;
import com.example.chesstactics.engine.Piece;
import com.example.chesstactics.models.BoardTheme;
import com.example.chesstactics.models.ChessMove;
import com.example.chesstactics.utils.PreferenceManager;
import com.example.chesstactics.utils.SoundManager;
import com.example.chesstactics.views.ChessBoardView;

import java.util.ArrayList;
import java.util.List;

public class ReplayActivity extends AppCompatActivity {

    public static final String EXTRA_UCI_MOVES = "extra_uci_moves";
    public static final String EXTRA_PLAYER_COLOR = "extra_player_color";

    private ChessBoardView replayBoardView;
    private TextView tvReplayStep;
    private Button btnReplayPrev, btnReplayAuto, btnReplayNext;

    private BoardState boardState;
    private SoundManager soundManager;
    private final List<Integer> packedMoves = new ArrayList<>();
    private int currentStep = 0;

    private final Handler autoHandler = new Handler(Looper.getMainLooper());
    private boolean isAutoPlaying = false;

    private final Runnable autoStepRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoPlaying) return;
            if (currentStep < packedMoves.size()) {
                stepForward();
                autoHandler.postDelayed(this, 850);
            } else {
                stopAutoPlay();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replay);

        String uciHistoryStr = getIntent().getStringExtra(EXTRA_UCI_MOVES);
        int playerColor = getIntent().getIntExtra(EXTRA_PLAYER_COLOR, Piece.WHITE);

        soundManager = new SoundManager(this);

        replayBoardView = findViewById(R.id.replayBoardView);
        tvReplayStep = findViewById(R.id.tvReplayStep);
        btnReplayPrev = findViewById(R.id.btnReplayPrev);
        btnReplayAuto = findViewById(R.id.btnReplayAuto);
        btnReplayNext = findViewById(R.id.btnReplayNext);

        findViewById(R.id.btnBackReplay).setOnClickListener(v -> finish());

        PreferenceManager prefs = new PreferenceManager(this);
        replayBoardView.setTheme(BoardTheme.getThemeById(prefs.getThemeId()));
        replayBoardView.setInteractionEnabled(false);
        if (playerColor == Piece.BLACK) {
            replayBoardView.setFlipped(true);
        }

        parseUciMoves(uciHistoryStr);

        boardState = new BoardState();
        replayBoardView.setBoardState(boardState);
        updateStepDisplay();

        btnReplayPrev.setOnClickListener(v -> {
            stopAutoPlay();
            stepBackward();
        });

        btnReplayNext.setOnClickListener(v -> {
            stopAutoPlay();
            stepForward();
        });

        btnReplayAuto.setOnClickListener(v -> {
            if (isAutoPlaying) {
                stopAutoPlay();
            } else {
                startAutoPlay();
            }
        });
    }

    private void parseUciMoves(String uciHistoryStr) {
        packedMoves.clear();
        if (TextUtils.isEmpty(uciHistoryStr)) return;

        BoardState simulator = new BoardState();
        String[] tokens = uciHistoryStr.trim().split("\\s+");
        for (String uci : tokens) {
            if (uci.isEmpty()) continue;
            List<ChessMove> legalMoves = MoveGenerator.generateLegalMoveObjects(simulator);
            ChessMove found = null;
            for (ChessMove move : legalMoves) {
                if (move.toUciString().equals(uci)) {
                    found = move;
                    break;
                }
            }
            if (found != null) {
                int packed = found.toPacked();
                packedMoves.add(packed);
                simulator.makeMove(packed);
            } else {
                break;
            }
        }
    }

    private void stepForward() {
        if (currentStep >= packedMoves.size()) return;

        int packed = packedMoves.get(currentStep);
        boardState.makeMove(packed);
        currentStep++;

        replayBoardView.setLastMove(ChessMove.getFrom(packed), ChessMove.getTo(packed));
        replayBoardView.refreshBoardState();
        soundManager.playMove();
        updateStepDisplay();
    }

    private void stepBackward() {
        if (currentStep <= 0) return;

        currentStep--;
        int packed = packedMoves.get(currentStep);
        boardState.unmakeMove(packed);

        if (currentStep > 0) {
            int prevPacked = packedMoves.get(currentStep - 1);
            replayBoardView.setLastMove(ChessMove.getFrom(prevPacked), ChessMove.getTo(prevPacked));
        } else {
            replayBoardView.setLastMove(-1, -1);
        }

        replayBoardView.refreshBoardState();
        soundManager.playMove();
        updateStepDisplay();
    }

    private void startAutoPlay() {
        if (packedMoves.isEmpty()) return;
        if (currentStep >= packedMoves.size()) {
            boardState = new BoardState();
            currentStep = 0;
            replayBoardView.setBoardState(boardState);
            replayBoardView.setLastMove(-1, -1);
            updateStepDisplay();
        }
        isAutoPlaying = true;
        btnReplayAuto.setText("⏸");
        autoHandler.postDelayed(autoStepRunnable, 500);
    }

    private void stopAutoPlay() {
        isAutoPlaying = false;
        autoHandler.removeCallbacks(autoStepRunnable);
        btnReplayAuto.setText(R.string.replay_auto);
    }

    private void updateStepDisplay() {
        tvReplayStep.setText(currentStep + " / " + packedMoves.size());
        btnReplayPrev.setEnabled(currentStep > 0);
        btnReplayPrev.setAlpha(currentStep > 0 ? 1.0f : 0.45f);

        btnReplayNext.setEnabled(currentStep < packedMoves.size());
        btnReplayNext.setAlpha(currentStep < packedMoves.size() ? 1.0f : 0.45f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoPlay();
        soundManager.release();
    }
}