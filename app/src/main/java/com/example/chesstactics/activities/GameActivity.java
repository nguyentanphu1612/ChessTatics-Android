package com.example.chesstactics.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chesstactics.R;
import com.example.chesstactics.adapters.MoveNotationAdapter;
import com.example.chesstactics.ai.BoardEvaluator;
import com.example.chesstactics.ai.ChessAI;
import com.example.chesstactics.database.ChessDatabaseHelper;
import com.example.chesstactics.engine.BoardState;
import com.example.chesstactics.engine.GameRuleEvaluator;
import com.example.chesstactics.engine.MoveGenerator;
import com.example.chesstactics.engine.NotationConverter;
import com.example.chesstactics.engine.Piece;
import com.example.chesstactics.models.BoardTheme;
import com.example.chesstactics.models.ChessMove;
import com.example.chesstactics.models.MatchRecord;
import com.example.chesstactics.online.FirebaseChessManager;
import com.example.chesstactics.utils.PreferenceManager;
import com.example.chesstactics.utils.SoundManager;
import com.example.chesstactics.views.ChessBoardView;
import com.example.chesstactics.views.PromotionDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_PLAYER_COLOR = "extra_player_color";
    public static final String EXTRA_TIME_MINUTES = "extra_time_minutes";
    public static final String EXTRA_ROOM_CODE = "extra_room_code";

    private ChessBoardView chessBoardView;
    private LinearLayout layoutTopPlayerCard, layoutBottomPlayerCard;
    private ImageView ivTopAvatar, ivBottomAvatar;
    private TextView tvTopPlayer, tvBottomPlayer, tvTopCaptured, tvBottomCaptured;
    private TextView tvTopClock, tvBottomClock, tvStatusBanner, tvModeTitle, tvHintLabel;
    private ProgressBar progressAiThinking;
    private LinearLayout btnHint, btnUndo;
    private RecyclerView rvMoveNotations;

    private BoardState boardState;
    private MoveNotationAdapter notationAdapter;
    private SoundManager soundManager;
    private ChessDatabaseHelper dbHelper;
    private FirebaseChessManager firebaseManager;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();

    private String gameMode;
    private int myColor = Piece.WHITE;
    private String roomCode;
    private int hintsRemaining = 3;
    private boolean isGameOver = false;

    private final List<Integer> packedMoveHistory = new ArrayList<>();
    private final List<String> uciHistory = new ArrayList<>();
    private final List<String> sanHistory = new ArrayList<>();

    private long whiteTimeRemainingMs = -1L;
    private long blackTimeRemainingMs = -1L;
    private CountDownTimer activeTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        gameMode = getIntent().getStringExtra(EXTRA_MODE);
        if (gameMode == null) gameMode = "AI_HARD";
        myColor = getIntent().getIntExtra(EXTRA_PLAYER_COLOR, Piece.WHITE);
        int timeMinutes = getIntent().getIntExtra(EXTRA_TIME_MINUTES, 10);
        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);

        soundManager = new SoundManager(this);
        dbHelper = new ChessDatabaseHelper(this);

        initViews();
        setupBoardAndTimers(timeMinutes);

        if ("ONLINE".equals(gameMode) && roomCode != null) {
            setupOnlineSession();
        } else if (isAiMode() && myColor == Piece.BLACK) {
            triggerAiTurn();
        }
    }

    private void initViews() {
        chessBoardView = findViewById(R.id.chessBoardView);
        layoutTopPlayerCard = findViewById(R.id.layoutTopPlayerCard);
        layoutBottomPlayerCard = findViewById(R.id.layoutBottomPlayerCard);
        ivTopAvatar = findViewById(R.id.ivTopAvatar);
        ivBottomAvatar = findViewById(R.id.ivBottomAvatar);
        tvTopPlayer = findViewById(R.id.tvTopPlayer);
        tvBottomPlayer = findViewById(R.id.tvBottomPlayer);
        tvTopCaptured = findViewById(R.id.tvTopCaptured);
        tvBottomCaptured = findViewById(R.id.tvBottomCaptured);
        tvTopClock = findViewById(R.id.tvTopClock);
        tvBottomClock = findViewById(R.id.tvBottomClock);
        tvStatusBanner = findViewById(R.id.tvStatusBanner);
        tvModeTitle = findViewById(R.id.tvModeTitle);
        tvHintLabel = findViewById(R.id.tvHintLabel);
        progressAiThinking = findViewById(R.id.progressAiThinking);
        btnHint = findViewById(R.id.btnHint);
        btnUndo = findViewById(R.id.btnUndo);
        rvMoveNotations = findViewById(R.id.rvMoveNotations);

        tvModeTitle.setText(getModeDisplayLabel());

        notationAdapter = new MoveNotationAdapter();
        rvMoveNotations.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvMoveNotations.setAdapter(notationAdapter);

        PreferenceManager prefs = new PreferenceManager(this);
        chessBoardView.setTheme(BoardTheme.getThemeById(prefs.getThemeId()));

        findViewById(R.id.btnBackGame).setOnClickListener(v -> confirmResign());
        findViewById(R.id.btnFlipBoard).setOnClickListener(v -> {
            chessBoardView.setFlipped(!chessBoardView.isFlipped());
            updatePlayerHeadersAndCaptured();
            updateClockDisplays();
        });

        btnHint.setOnClickListener(v -> requestAiHint());
        btnUndo.setOnClickListener(v -> undoLastMoves());
        findViewById(R.id.btnResign).setOnClickListener(v -> confirmResign());
    }

    private void setupBoardAndTimers(int timeMinutes) {
        boardState = new BoardState();
        chessBoardView.setBoardState(boardState);

        if (myColor == Piece.BLACK && !"PVP_LOCAL".equals(gameMode)) {
            chessBoardView.setFlipped(true);
        }
        if (isAiMode() || "ONLINE".equals(gameMode)) {
            chessBoardView.setRestrictedColor(myColor);
        }
        if ("ONLINE".equals(gameMode)) {
            btnUndo.setVisibility(View.GONE);
            btnHint.setVisibility(View.GONE);
        }

        if (timeMinutes > 0) {
            whiteTimeRemainingMs = timeMinutes * 60_000L;
            blackTimeRemainingMs = timeMinutes * 60_000L;
            // Ở chế độ Online, chủ phòng (Trắng) chỉ bắt đầu đếm giờ khi người thứ 2 vào phòng
            if (!("ONLINE".equals(gameMode) && myColor == Piece.WHITE)) {
                startClockForCurrentTurn();
            }
        }
        updatePlayerHeadersAndCaptured();
        updateClockDisplays();
        updateHintButtonText();

        chessBoardView.setOnBoardActionListener(new ChessBoardView.OnBoardActionListener() {
            @Override
            public void onMoveSelected(ChessMove move) {
                executeMove(move, true);
            }

            @Override
            public void onPromotionRequired(int fromSq, int toSq) {
                PromotionDialog.show(GameActivity.this, boardState.sideToMove, promoPiece -> {
                    int movingPawn = boardState.board[fromSq];
                    int captured = boardState.board[toSq];
                    ChessMove promoMove = new ChessMove(fromSq, toSq, movingPawn, captured,
                            promoPiece, ChessMove.FLAG_NORMAL);
                    executeMove(promoMove, true);
                });
            }
        });
    }

    private void setupOnlineSession() {
        firebaseManager = new FirebaseChessManager();
        tvModeTitle.setText("ID: " + roomCode + " 📋");
        tvModeTitle.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null) {
                cb.setPrimaryClip(ClipData.newPlainText("ChessRoomCode", roomCode));
                Toast.makeText(this, getString(R.string.room_code_banner, roomCode), Toast.LENGTH_SHORT).show();
            }
        });

        if (myColor == Piece.WHITE) {
            chessBoardView.setInteractionEnabled(false);
            tvStatusBanner.setText(getString(R.string.waiting_opponent_with_code, roomCode));
        } else {
            tvStatusBanner.setText(R.string.turn_opponent);
        }

        firebaseManager.listenToRoom(roomCode, myColor, new FirebaseChessManager.RoomEventListener() {
            @Override
            public void onOpponentJoined() {
                chessBoardView.setInteractionEnabled(true);
                Toast.makeText(GameActivity.this, R.string.opponent_joined, Toast.LENGTH_SHORT).show();
                updateTurnIndicators();
                startClockForCurrentTurn();
            }

            @Override
            public void onRemoteMoveReceived(String moveUci, int moveCount, long wTime, long bTime) {
                if (isGameOver) return;
                if (wTime > 0) whiteTimeRemainingMs = wTime;
                if (bTime > 0) blackTimeRemainingMs = bTime;

                ChessMove matched = findLegalMoveByUci(moveUci);
                if (matched != null) {
                    executeMove(matched, false);
                }
            }

            @Override
            public void onRemoteGameEnded(String winnerSide, String reason) {
                if (isGameOver) return;
                String result = "DRAW".equals(winnerSide) ? "DRAW" :
                        (("WHITE".equals(winnerSide) && myColor == Piece.WHITE) ||
                                ("BLACK".equals(winnerSide) && myColor == Piece.BLACK)) ? "WIN" : "LOSS";
                finishMatch(result, reason);
            }
        });
    }

    private void executeMove(ChessMove move, boolean isLocalAction) {
        if (isGameOver) return;

        int packed = move.toPacked();
        String san = NotationConverter.moveToSan(boardState, packed);
        String uci = move.toUciString();

        boardState.makeMove(packed);
        packedMoveHistory.add(packed);
        uciHistory.add(uci);
        sanHistory.add(san);

        notationAdapter.setMoves(sanHistory);
        if (!sanHistory.isEmpty()) {
            rvMoveNotations.scrollToPosition(notationAdapter.getItemCount() - 1);
        }

        chessBoardView.setLastMove(move.from, move.to);
        chessBoardView.refreshBoardState();
        updatePlayerHeadersAndCaptured();

        if (MoveGenerator.isKingInCheck(boardState, boardState.sideToMove)) {
            soundManager.playCheck();
        } else if (move.isCapture()) {
            soundManager.playCapture();
        } else {
            soundManager.playMove();
        }

        if ("ONLINE".equals(gameMode) && isLocalAction && firebaseManager != null) {
            firebaseManager.sendMove(roomCode, uci, uciHistory.size(), whiteTimeRemainingMs, blackTimeRemainingMs);
        }

        GameRuleEvaluator.GameResult ruleResult = GameRuleEvaluator.evaluateGameResult(boardState);
        if (ruleResult != GameRuleEvaluator.GameResult.ONGOING) {
            handleRuleEnd(ruleResult, isLocalAction);
            return;
        }

        startClockForCurrentTurn();

        if (isAiMode() && boardState.sideToMove != myColor) {
            triggerAiTurn();
        }
    }

    private void triggerAiTurn() {
        chessBoardView.setInteractionEnabled(false);
        progressAiThinking.setVisibility(View.VISIBLE);
        tvStatusBanner.setText(R.string.turn_ai_thinking);

        int difficulty = "AI_EASY".equals(gameMode) ? 1 : ("AI_HARD".equals(gameMode) ? 2 : 3);

        aiExecutor.execute(() -> {
            ChessMove aiMove = ChessAI.findBestMove(boardState, difficulty);
            runOnUiThread(() -> {
                progressAiThinking.setVisibility(View.GONE);
                chessBoardView.setInteractionEnabled(true);
                if (aiMove != null && !isGameOver) {
                    executeMove(aiMove, true);
                }
            });
        });
    }

    private void requestAiHint() {
        if (hintsRemaining <= 0 || isGameOver) return;
        if (isAiMode() && boardState.sideToMove != myColor) return;

        hintsRemaining--;
        updateHintButtonText();
        progressAiThinking.setVisibility(View.VISIBLE);

        aiExecutor.execute(() -> {
            ChessMove hint = ChessAI.getHintMove(boardState);
            runOnUiThread(() -> {
                progressAiThinking.setVisibility(View.GONE);
                if (hint != null && !isGameOver) {
                    chessBoardView.showHintMove(hint.from, hint.to);
                }
            });
        });
    }

    private void undoLastMoves() {
        if (isGameOver || packedMoveHistory.isEmpty() || "ONLINE".equals(gameMode)) return;

        int stepsToUndo = isAiMode() ? 2 : 1;
        if (packedMoveHistory.size() < stepsToUndo) return;

        for (int i = 0; i < stepsToUndo; i++) {
            int lastIdx = packedMoveHistory.size() - 1;
            int packed = packedMoveHistory.remove(lastIdx);
            uciHistory.remove(lastIdx);
            sanHistory.remove(lastIdx);
            boardState.unmakeMove(packed);
        }

        notationAdapter.setMoves(sanHistory);
        if (!packedMoveHistory.isEmpty()) {
            int prev = packedMoveHistory.get(packedMoveHistory.size() - 1);
            chessBoardView.setLastMove(ChessMove.getFrom(prev), ChessMove.getTo(prev));
        } else {
            chessBoardView.setLastMove(-1, -1);
        }
        chessBoardView.refreshBoardState();
        updatePlayerHeadersAndCaptured();
        startClockForCurrentTurn();
    }

    private void startClockForCurrentTurn() {
        if (activeTimer != null) activeTimer.cancel();
        if (whiteTimeRemainingMs < 0 || blackTimeRemainingMs < 0 || isGameOver) return;

        final boolean isWhiteTurn = (boardState.sideToMove == Piece.WHITE);
        long startMillis = isWhiteTurn ? whiteTimeRemainingMs : blackTimeRemainingMs;

        activeTimer = new CountDownTimer(startMillis, 200) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isWhiteTurn) whiteTimeRemainingMs = millisUntilFinished;
                else blackTimeRemainingMs = millisUntilFinished;
                updateClockDisplays();
            }

            @Override
            public void onFinish() {
                if (isWhiteTurn) whiteTimeRemainingMs = 0;
                else blackTimeRemainingMs = 0;
                updateClockDisplays();

                String result;
                if ("PVP_LOCAL".equals(gameMode)) {
                    result = isWhiteTurn ? "BLACK_WIN" : "WHITE_WIN";
                } else {
                    result = (isWhiteTurn == (myColor == Piece.WHITE)) ? "LOSS" : "WIN";
                }

                if ("ONLINE".equals(gameMode) && firebaseManager != null) {
                    firebaseManager.endRoomGame(roomCode, isWhiteTurn ? "BLACK" : "WHITE", "TIMEOUT");
                }
                finishMatch(result, "TIMEOUT");
            }
        }.start();
    }

    private void handleRuleEnd(GameRuleEvaluator.GameResult ruleResult, boolean notifyOnline) {
        String result;
        String winnerStr = "DRAW";
        if (ruleResult == GameRuleEvaluator.GameResult.WHITE_WIN_CHECKMATE) {
            winnerStr = "WHITE";
            if ("PVP_LOCAL".equals(gameMode)) {
                result = "WHITE_WIN";
            } else {
                result = (myColor == Piece.WHITE) ? "WIN" : "LOSS";
            }
        } else if (ruleResult == GameRuleEvaluator.GameResult.BLACK_WIN_CHECKMATE) {
            winnerStr = "BLACK";
            if ("PVP_LOCAL".equals(gameMode)) {
                result = "BLACK_WIN";
            } else {
                result = (myColor == Piece.BLACK) ? "WIN" : "LOSS";
            }
        } else {
            result = "DRAW";
        }

        if ("ONLINE".equals(gameMode) && notifyOnline && firebaseManager != null) {
            firebaseManager.endRoomGame(roomCode, winnerStr, ruleResult.name());
        }
        finishMatch(result, ruleResult.name());
    }

    private void confirmResign() {
        if (isGameOver) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.resign_title)
                .setMessage(R.string.resign_confirm_msg)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    if ("ONLINE".equals(gameMode) && firebaseManager != null) {
                        firebaseManager.endRoomGame(roomCode, myColor == Piece.WHITE ? "BLACK" : "WHITE", "RESIGN");
                    }
                    if ("PVP_LOCAL".equals(gameMode)) {
                        String winner = (boardState.sideToMove == Piece.WHITE) ? "BLACK_WIN" : "WHITE_WIN";
                        finishMatch(winner, "RESIGN");
                    } else {
                        finishMatch("LOSS", "RESIGN");
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void finishMatch(String result, String reason) {
        if (isGameOver) return;
        isGameOver = true;
        if (activeTimer != null) activeTimer.cancel();
        chessBoardView.setInteractionEnabled(false);
        soundManager.playGameOver();

        String dbResult = result;
        if ("WHITE_WIN".equals(result)) {
            dbResult = (myColor == Piece.WHITE) ? "WIN" : "LOSS";
        } else if ("BLACK_WIN".equals(result)) {
            dbResult = (myColor == Piece.BLACK) ? "WIN" : "LOSS";
        }

        if (!uciHistory.isEmpty()) {
            dbHelper.insertMatch(new MatchRecord(
                    0, gameMode, dbResult, reason,
                    String.join(" ", uciHistory),
                    String.join(" ", sanHistory),
                    myColor, uciHistory.size(), System.currentTimeMillis()
            ));
        }

        String title;
        if ("WHITE_WIN".equals(result)) {
            title = getString(R.string.dialog_white_wins);
        } else if ("BLACK_WIN".equals(result)) {
            title = getString(R.string.dialog_black_wins);
        } else if ("WIN".equals(result)) {
            title = getString(R.string.dialog_victory);
        } else if ("LOSS".equals(result)) {
            title = getString(R.string.dialog_defeat);
        } else {
            title = getString(R.string.dialog_draw);
        }

        String localizedReason = getLocalizedReason(result, reason);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(getString(R.string.dialog_reason_prefix, localizedReason))
                .setCancelable(false)
                .setPositiveButton(R.string.back_to_menu, (d, w) -> finish())
                .show();
    }

    private String getLocalizedReason(String result, String rawReason) {
        if (rawReason == null) rawReason = "";
        switch (rawReason) {
            case "RESIGN":
                if ("LOSS".equals(result)) return getString(R.string.reason_you_resigned);
                if ("WIN".equals(result)) return getString(R.string.reason_opponent_resigned);
                if ("WHITE_WIN".equals(result)) return getString(R.string.reason_black_resigned);
                if ("BLACK_WIN".equals(result)) return getString(R.string.reason_white_resigned);
                return getString(R.string.reason_you_resigned);

            case "TIMEOUT":
                if ("LOSS".equals(result)) return getString(R.string.reason_you_timeout);
                if ("WIN".equals(result)) return getString(R.string.reason_opponent_timeout);
                if ("WHITE_WIN".equals(result)) return getString(R.string.reason_black_timeout);
                if ("BLACK_WIN".equals(result)) return getString(R.string.reason_white_timeout);
                return getString(R.string.reason_you_timeout);

            case "DISCONNECT":
                return getString(R.string.reason_disconnect);

            case "WHITE_WIN_CHECKMATE":
            case "BLACK_WIN_CHECKMATE":
            case "CHECKMATE":
                return getString(R.string.reason_checkmate);

            case "DRAW_STALEMATE":
            case "STALEMATE":
                return getString(R.string.reason_stalemate);

            case "DRAW_INSUFFICIENT_MATERIAL":
            case "INSUFFICIENT_MATERIAL":
                return getString(R.string.reason_insufficient_material);

            case "DRAW_FIFTY_MOVE":
            case "FIFTY_MOVE":
                return getString(R.string.reason_fifty_move);

            case "DRAW_THREEFOLD_REPETITION":
            case "THREEFOLD_REPETITION":
                return getString(R.string.reason_threefold);

            default:
                if ("DRAW".equals(result)) {
                    return getString(R.string.reason_draw_generic);
                }
                return rawReason;
        }
    }

    private void updatePlayerHeadersAndCaptured() {
        boolean whiteAtBottom = !chessBoardView.isFlipped();
        tvBottomPlayer.setText(whiteAtBottom ? R.string.side_white : R.string.side_black);
        tvTopPlayer.setText(whiteAtBottom ? R.string.side_black : R.string.side_white);
        ivBottomAvatar.setImageResource(whiteAtBottom ? R.drawable.ic_white_king : R.drawable.ic_black_king);
        ivTopAvatar.setImageResource(whiteAtBottom ? R.drawable.ic_black_king : R.drawable.ic_white_king);

        int whiteMat = 0, blackMat = 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = boardState.board[sq];
            if (p == Piece.NONE) continue;
            int type = Piece.getType(p);
            if (type == Piece.KING) continue;
            int val = BoardEvaluator.PIECE_VALUES[type] / 100;
            if (Piece.isWhite(p)) whiteMat += val;
            else blackMat += val;
        }
        int diff = whiteMat - blackMat;
        String whiteAdv = diff > 0 ? ("+" + diff) : "";
        String blackAdv = diff < 0 ? ("+" + (-diff)) : "";

        tvBottomCaptured.setText(whiteAtBottom ? whiteAdv : blackAdv);
        tvTopCaptured.setText(whiteAtBottom ? blackAdv : whiteAdv);

        updateTurnIndicators();
    }

    private void updateTurnIndicators() {
        boolean whiteAtBottom = !chessBoardView.isFlipped();
        boolean isBottomTurn = (boardState.sideToMove == Piece.WHITE) == whiteAtBottom;

        layoutBottomPlayerCard.setBackgroundResource(isBottomTurn
                ? R.drawable.bg_player_card_active : R.drawable.bg_player_card);
        layoutTopPlayerCard.setBackgroundResource(!isBottomTurn
                ? R.drawable.bg_player_card_active : R.drawable.bg_player_card);

        if ("PVP_LOCAL".equals(gameMode)) {
            tvStatusBanner.setText(boardState.sideToMove == Piece.WHITE ? R.string.side_white : R.string.side_black);
        } else {
            boolean isMyTurn = (boardState.sideToMove == myColor);
            tvStatusBanner.setText(isMyTurn ? R.string.turn_your_turn : R.string.turn_opponent);
        }
    }

    private String getModeDisplayLabel() {
        switch (gameMode) {
            case "AI_EASY": return getString(R.string.mode_ai_easy);
            case "AI_HARD": return getString(R.string.mode_ai_hard);
            case "AI_EXTREME": return getString(R.string.mode_ai_extreme);
            case "ONLINE": return getString(R.string.mode_online);
            default: return getString(R.string.mode_pvp);
        }
    }

    private ChessMove findLegalMoveByUci(String uci) {
        for (ChessMove m : MoveGenerator.generateLegalMoveObjects(boardState)) {
            if (m.toUciString().equals(uci)) return m;
        }
        return null;
    }

    private boolean isAiMode() {
        return gameMode != null && gameMode.startsWith("AI_");
    }

    private void updateClockDisplays() {
        if (whiteTimeRemainingMs < 0) {
            tvTopClock.setText("--:--");
            tvBottomClock.setText("--:--");
            return;
        }
        boolean whiteAtBottom = !chessBoardView.isFlipped();
        tvBottomClock.setText(formatMillis(whiteAtBottom ? whiteTimeRemainingMs : blackTimeRemainingMs));
        tvTopClock.setText(formatMillis(whiteAtBottom ? blackTimeRemainingMs : whiteTimeRemainingMs));
    }

    private String formatMillis(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60);
    }

    private void updateHintButtonText() {
        tvHintLabel.setText(getString(R.string.hint_count_format, hintsRemaining));
        btnHint.setEnabled(hintsRemaining > 0);
        btnHint.setAlpha(hintsRemaining > 0 ? 1.0f : 0.45f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeTimer != null) activeTimer.cancel();
        if (firebaseManager != null) firebaseManager.detachListener();
        soundManager.release();
        aiExecutor.shutdownNow();
    }
}