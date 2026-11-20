package com.example.chesstactics.engine;

/**
 * Bộ trọng tài phân xử kết quả ván đấu chuẩn FIDE:
 * - Đang thi đấu (ONGOING)
 * - Chiếu bí (WHITE_WIN_CHECKMATE / BLACK_WIN_CHECKMATE)
 * - Hòa Pat (DRAW_STALEMATE)
 * - Hòa lặp 3 lần (DRAW_THREEFOLD)
 * - Hòa luật 50 nước (DRAW_FIFTY_MOVE)
 * - Hòa không đủ lực chiếu bí (DRAW_INSUFFICIENT_MATERIAL)
 */
public final class GameRuleEvaluator {

    public enum GameResult {
        ONGOING,
        WHITE_WIN_CHECKMATE,
        BLACK_WIN_CHECKMATE,
        DRAW_STALEMATE,
        DRAW_THREEFOLD,
        DRAW_FIFTY_MOVE,
        DRAW_INSUFFICIENT_MATERIAL
    }

    private GameRuleEvaluator() {}

    public static GameResult evaluateGameResult(BoardState state) {
        if (state.halfMoveClock >= 100) {
            return GameResult.DRAW_FIFTY_MOVE;
        }
        if (state.getRepetitionCount() >= 3) {
            return GameResult.DRAW_THREEFOLD;
        }
        if (isInsufficientMaterial(state)) {
            return GameResult.DRAW_INSUFFICIENT_MATERIAL;
        }

        int[] legalMoves = new int[256];
        int legalCount = MoveGenerator.generateLegalMoves(state, legalMoves, false);

        if (legalCount == 0) {
            if (MoveGenerator.isKingInCheck(state, state.sideToMove)) {
                return (state.sideToMove == Piece.WHITE)
                        ? GameResult.BLACK_WIN_CHECKMATE
                        : GameResult.WHITE_WIN_CHECKMATE;
            } else {
                return GameResult.DRAW_STALEMATE;
            }
        }

        return GameResult.ONGOING;
    }

    /**
     * Kiểm tra các thế cờ không đủ quân để chiếu bí (Vua vs Vua, Vua+Mã vs Vua, Vua+Tượng vs Vua,
     * hoặc 2 Tượng cùng màu ô).
     */
    public static boolean isInsufficientMaterial(BoardState state) {
        int whiteKnights = 0, whiteBishops = 0, whiteBishopColor = -1;
        int blackKnights = 0, blackBishops = 0, blackBishopColor = -1;

        for (int sq = 0; sq < 64; sq++) {
            int p = state.board[sq];
            if (p == Piece.NONE) continue;

            int type = Piece.getType(p);
            if (type == Piece.PAWN || type == Piece.ROOK || type == Piece.QUEEN) {
                return false;
            }
            if (type == Piece.KNIGHT) {
                if (Piece.isWhite(p)) whiteKnights++;
                else blackKnights++;
            } else if (type == Piece.BISHOP) {
                int sqColor = ((sq >> 3) + (sq & 7)) & 1;
                if (Piece.isWhite(p)) {
                    whiteBishops++;
                    whiteBishopColor = sqColor;
                } else {
                    blackBishops++;
                    blackBishopColor = sqColor;
                }
            }
        }

        int whiteMinors = whiteKnights + whiteBishops;
        int blackMinors = blackKnights + blackBishops;

        if (whiteMinors == 0 && blackMinors == 0) return true;
        if (whiteMinors == 1 && blackMinors == 0) return true;
        if (whiteMinors == 0 && blackMinors == 1) return true;
        if (whiteMinors == 1 && blackMinors == 1 && whiteBishops == 1 && blackBishops == 1) {
            return whiteBishopColor == blackBishopColor;
        }
        return false;
    }
}