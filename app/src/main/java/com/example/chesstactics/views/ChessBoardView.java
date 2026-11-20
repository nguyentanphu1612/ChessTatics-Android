package com.example.chesstactics.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.chesstactics.R;
import com.example.chesstactics.engine.BoardState;
import com.example.chesstactics.engine.MoveGenerator;
import com.example.chesstactics.engine.Piece;
import com.example.chesstactics.models.BoardTheme;
import com.example.chesstactics.models.ChessMove;

import java.util.ArrayList;
import java.util.List;

public class ChessBoardView extends View {

    public interface OnBoardActionListener {
        void onMoveSelected(ChessMove move);
        void onPromotionRequired(int fromSq, int toSq);
    }

    private BoardState boardState;
    private BoardTheme currentTheme;
    private OnBoardActionListener listener;

    private boolean isFlipped = false;
    private boolean interactionEnabled = true;
    private int restrictedColor = 0; // 0 = Cho phép đi cả 2 bên; Piece.WHITE/BLACK = Chỉ đi 1 bên

    private int selectedSquare = -1;
    private int lastMoveFrom = -1;
    private int lastMoveTo = -1;
    private int hintFrom = -1;
    private int hintTo = -1;
    private int checkedKingSquare = -1;

    private final List<ChessMove> legalMovesForSelected = new ArrayList<>();
    private List<ChessMove> allCurrentLegalMoves = new ArrayList<>();

    private final Drawable[] pieceDrawables = new Drawable[24];

    private final Paint squarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captureRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float squareSize = 0f;

    public ChessBoardView(Context context) {
        super(context);
        init(context);
    }

    public ChessBoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        currentTheme = BoardTheme.getAllThemes().get(0);
        initPieceDrawables(context);

        hintDotPaint.setColor(Color.parseColor("#55000000"));
        hintDotPaint.setStyle(Paint.Style.FILL);

        captureRingPaint.setColor(Color.parseColor("#66D32F2F"));
        captureRingPaint.setStyle(Paint.Style.STROKE);

        coordPaint.setTypeface(Typeface.DEFAULT_BOLD);

        hintArrowPaint.setColor(Color.parseColor("#CC00BCD4"));
        hintArrowPaint.setStyle(Paint.Style.STROKE);
        hintArrowPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    private void initPieceDrawables(Context context) {
        pieceDrawables[Piece.WHITE_KING]   = ContextCompat.getDrawable(context, R.drawable.ic_white_king);
        pieceDrawables[Piece.WHITE_QUEEN]  = ContextCompat.getDrawable(context, R.drawable.ic_white_queen);
        pieceDrawables[Piece.WHITE_ROOK]   = ContextCompat.getDrawable(context, R.drawable.ic_white_rook);
        pieceDrawables[Piece.WHITE_BISHOP] = ContextCompat.getDrawable(context, R.drawable.ic_white_bishop);
        pieceDrawables[Piece.WHITE_KNIGHT] = ContextCompat.getDrawable(context, R.drawable.ic_white_knight);
        pieceDrawables[Piece.WHITE_PAWN]   = ContextCompat.getDrawable(context, R.drawable.ic_white_pawn);

        pieceDrawables[Piece.BLACK_KING]   = ContextCompat.getDrawable(context, R.drawable.ic_black_king);
        pieceDrawables[Piece.BLACK_QUEEN]  = ContextCompat.getDrawable(context, R.drawable.ic_black_queen);
        pieceDrawables[Piece.BLACK_ROOK]   = ContextCompat.getDrawable(context, R.drawable.ic_black_rook);
        pieceDrawables[Piece.BLACK_BISHOP] = ContextCompat.getDrawable(context, R.drawable.ic_black_bishop);
        pieceDrawables[Piece.BLACK_KNIGHT] = ContextCompat.getDrawable(context, R.drawable.ic_black_knight);
        pieceDrawables[Piece.BLACK_PAWN]   = ContextCompat.getDrawable(context, R.drawable.ic_black_pawn);
    }

    public void setBoardState(BoardState state) {
        this.boardState = state;
        refreshBoardState();
    }

    public void setTheme(BoardTheme theme) {
        this.currentTheme = theme;
        invalidate();
    }

    public void setOnBoardActionListener(OnBoardActionListener listener) {
        this.listener = listener;
    }

    public void setFlipped(boolean flipped) {
        this.isFlipped = flipped;
        invalidate();
    }

    public boolean isFlipped() {
        return isFlipped;
    }

    public void setInteractionEnabled(boolean enabled) {
        this.interactionEnabled = enabled;
        if (!enabled) clearSelection();
    }

    public void setRestrictedColor(int color) {
        this.restrictedColor = color;
    }

    public void setLastMove(int from, int to) {
        this.lastMoveFrom = from;
        this.lastMoveTo = to;
        this.hintFrom = -1;
        this.hintTo = -1;
        invalidate();
    }

    public void showHintMove(int from, int to) {
        this.hintFrom = from;
        this.hintTo = to;
        invalidate();
    }

    public void refreshBoardState() {
        if (boardState == null) return;
        allCurrentLegalMoves = MoveGenerator.generateLegalMoveObjects(boardState);
        if (MoveGenerator.isKingInCheck(boardState, boardState.sideToMove)) {
            checkedKingSquare = (boardState.sideToMove == Piece.WHITE)
                    ? boardState.whiteKingSq : boardState.blackKingSq;
        } else {
            checkedKingSquare = -1;
        }
        clearSelection();
        invalidate();
    }

    public void clearSelection() {
        selectedSquare = -1;
        legalMovesForSelected.clear();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = (height > 0) ? Math.min(width, height) : width;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0) return;

        squareSize = getWidth() / 8f;
        coordPaint.setTextSize(squareSize * 0.18f);
        captureRingPaint.setStrokeWidth(squareSize * 0.08f);
        hintArrowPaint.setStrokeWidth(squareSize * 0.11f);

        // 1. Vẽ 64 ô bàn cờ & Highlight
        for (int viewRow = 0; viewRow < 8; viewRow++) {
            for (int viewCol = 0; viewCol < 8; viewCol++) {
                int boardRow = isFlipped ? (7 - viewRow) : viewRow;
                int boardCol = isFlipped ? (7 - viewCol) : viewCol;
                int sq = (boardRow << 3) | boardCol;

                float left = viewCol * squareSize;
                float top = viewRow * squareSize;
                float right = left + squareSize;
                float bottom = top + squareSize;

                boolean isLight = ((boardRow + boardCol) & 1) == 0;
                squarePaint.setColor(isLight ? currentTheme.lightSquareColor : currentTheme.darkSquareColor);
                canvas.drawRect(left, top, right, bottom, squarePaint);

                // Highlight nước vừa đi
                if (sq == lastMoveFrom || sq == lastMoveTo) {
                    highlightPaint.setColor(currentTheme.highlightColor);
                    canvas.drawRect(left, top, right, bottom, highlightPaint);
                }

                // Highlight ô đang chọn
                if (sq == selectedSquare) {
                    highlightPaint.setColor(currentTheme.selectedColor);
                    canvas.drawRect(left, top, right, bottom, highlightPaint);
                }

                // Hiệu ứng cảnh báo đỏ khi Vua bị chiếu
                if (sq == checkedKingSquare) {
                    float cx = left + squareSize / 2f;
                    float cy = top + squareSize / 2f;
                    checkGlowPaint.setShader(new RadialGradient(
                            cx, cy, squareSize * 0.55f,
                            Color.parseColor("#E53935"), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    canvas.drawRect(left, top, right, bottom, checkGlowPaint);
                }

                // Vẽ ký hiệu cột (a-h) và hàng (1-8)
                coordPaint.setColor(isLight ? currentTheme.darkSquareColor : currentTheme.lightSquareColor);
                if (viewRow == 7) {
                    String fileLabel = String.valueOf((char) ('a' + boardCol));
                    canvas.drawText(fileLabel, right - squareSize * 0.22f, bottom - squareSize * 0.08f, coordPaint);
                }
                if (viewCol == 0) {
                    String rankLabel = String.valueOf(8 - boardRow);
                    canvas.drawText(rankLabel, left + squareSize * 0.08f, top + squareSize * 0.24f, coordPaint);
                }
            }
        }

        if (boardState == null) return;

        // 2. Vẽ toàn bộ 12 loại quân cờ Vector SVG sắc nét
        for (int sq = 0; sq < 64; sq++) {
            int piece = boardState.board[sq];
            if (piece == Piece.NONE) continue;

            int boardRow = sq >> 3;
            int boardCol = sq & 7;
            int viewRow = isFlipped ? (7 - boardRow) : boardRow;
            int viewCol = isFlipped ? (7 - boardCol) : boardCol;

            float cx = (viewCol + 0.5f) * squareSize;
            float cy = (viewRow + 0.5f) * squareSize;
            drawChessPiece(canvas, piece, cx, cy);
        }

        // 3. Vẽ chấm gợi ý nước đi hợp lệ cho quân đang chọn
        for (ChessMove move : legalMovesForSelected) {
            int boardRow = move.to >> 3;
            int boardCol = move.to & 7;
            int viewRow = isFlipped ? (7 - boardRow) : boardRow;
            int viewCol = isFlipped ? (7 - boardCol) : boardCol;

            float cx = (viewCol + 0.5f) * squareSize;
            float cy = (viewRow + 0.5f) * squareSize;

            if (move.isCapture()) {
                canvas.drawCircle(cx, cy, squareSize * 0.42f, captureRingPaint);
            } else {
                canvas.drawCircle(cx, cy, squareSize * 0.16f, hintDotPaint);
            }
        }

        // 4. Vẽ đường chỉ dẫn gợi ý AI (Hint)
        if (hintFrom != -1 && hintTo != -1) {
            drawHintLine(canvas, hintFrom, hintTo);
        }
    }

    private void drawChessPiece(Canvas canvas, int piece, float cx, float cy) {
        Drawable drawable = pieceDrawables[piece];
        if (drawable == null) return;

        int halfSize = (int) (squareSize * 0.43f);
        int left = (int) cx - halfSize;
        int top = (int) cy - halfSize;
        int right = (int) cx + halfSize;
        int bottom = (int) cy + halfSize;

        drawable.setBounds(left, top, right, bottom);
        drawable.draw(canvas);
    }

    private void drawHintLine(Canvas canvas, int from, int to) {
        int r1 = isFlipped ? (7 - (from >> 3)) : (from >> 3);
        int c1 = isFlipped ? (7 - (from & 7)) : (from & 7);
        int r2 = isFlipped ? (7 - (to >> 3)) : (to >> 3);
        int c2 = isFlipped ? (7 - (to & 7)) : (to & 7);

        float x1 = (c1 + 0.5f) * squareSize;
        float y1 = (r1 + 0.5f) * squareSize;
        float x2 = (c2 + 0.5f) * squareSize;
        float y2 = (r2 + 0.5f) * squareSize;

        canvas.drawLine(x1, y1, x2, y2, hintArrowPaint);
        canvas.drawCircle(x2, y2, squareSize * 0.22f, hintArrowPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactionEnabled || boardState == null || squareSize <= 0) return false;
        if (restrictedColor != 0 && boardState.sideToMove != restrictedColor) return false;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int viewCol = (int) (event.getX() / squareSize);
            int viewRow = (int) (event.getY() / squareSize);
            if (viewRow < 0 || viewRow > 7 || viewCol < 0 || viewCol > 7) return false;

            int boardRow = isFlipped ? (7 - viewRow) : viewRow;
            int boardCol = isFlipped ? (7 - viewCol) : viewCol;
            int tappedSq = (boardRow << 3) | boardCol;

            handleSquareTapped(tappedSq);
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleSquareTapped(int tappedSq) {
        // Kiểm tra xem ô vừa bấm có phải là đích đến hợp lệ của quân đang chọn không
        if (selectedSquare != -1) {
            List<ChessMove> matchingMoves = new ArrayList<>();
            for (ChessMove m : legalMovesForSelected) {
                if (m.to == tappedSq) {
                    matchingMoves.add(m);
                }
            }

            if (!matchingMoves.isEmpty()) {
                if (matchingMoves.size() > 1 && matchingMoves.get(0).isPromotion()) {
                    if (listener != null) {
                        listener.onPromotionRequired(selectedSquare, tappedSq);
                    }
                } else {
                    if (listener != null) {
                        listener.onMoveSelected(matchingMoves.get(0));
                    }
                }
                clearSelection();
                return;
            }
        }

        // Chọn quân cờ thuộc phe đang có lượt
        int piece = boardState.board[tappedSq];
        if (piece != Piece.NONE && Piece.isColor(piece, boardState.sideToMove)) {
            selectedSquare = tappedSq;
            legalMovesForSelected.clear();
            for (ChessMove m : allCurrentLegalMoves) {
                if (m.from == tappedSq) {
                    legalMovesForSelected.add(m);
                }
            }
            invalidate();
        } else {
            clearSelection();
        }
    }
}