package com.example.chesstactics.ai;

import com.example.chesstactics.engine.BoardState;
import com.example.chesstactics.engine.Piece;

/**
 * Hàm lượng giá thế cờ chuẩn Grandmaster (Tapered Evaluation):
 * - Tự động nội suy trọng số giữa Trung cuộc (Middlegame) và Tàn cuộc (Endgame)
 * - Giá trị vật chất + Bảng vị trí Piece-Square Tables (PST)
 * - Thưởng cặp Tượng (Bishop Pair), Xe kiểm soát cột mở / nửa mở
 * - Phạt Tốt chồng (Doubled Pawns), Tốt cô lập (Isolated Pawns), thưởng Tốt thông (Passed Pawns)
 * - Đánh giá lá chắn Tốt bảo vệ Vua
 */
public final class BoardEvaluator {

    // Giá trị vật chất cơ bản (đơn vị: centipawns, 1 Tốt = 100)
    public static final int[] PIECE_VALUES = {
            0,    // NONE
            100,  // PAWN
            320,  // KNIGHT
            335,  // BISHOP
            500,  // ROOK
            920,  // QUEEN
            20000 // KING
    };

    // Bảng vị trí Tốt (Góc nhìn quân Trắng: hàng 0 = hàng 8, hàng 7 = hàng 1)
    private static final int[] PAWN_MG = {
            0,   0,   0,   0,   0,   0,   0,   0,
            50,  50,  50,  50,  50,  50,  50,  50,
            10,  10,  20,  32,  32,  20,  10,  10,
            5,   5,  10,  27,  27,  10,   5,   5,
            0,   0,   0,  24,  24,   0,   0,   0,
            5,  -5, -10,   0,   0, -10,  -5,   5,
            5,  10,  10, -22, -22,  10,  10,   5,
            0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] PAWN_EG = {
            0,   0,   0,   0,   0,   0,   0,   0,
            80,  80,  80,  80,  80,  80,  80,  80,
            50,  50,  50,  50,  50,  50,  50,  50,
            30,  30,  30,  30,  30,  30,  30,  30,
            15,  15,  15,  15,  15,  15,  15,  15,
            5,   5,   5,   5,   5,   5,   5,   5,
            0,   0,   0,   0,   0,   0,   0,   0,
            0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] KNIGHT_PST = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20,   0,   0,   0,   0, -20, -40,
            -30,   0,  12,  16,  16,  12,   0, -30,
            -30,   5,  16,  22,  22,  16,   5, -30,
            -30,   0,  16,  22,  22,  16,   0, -30,
            -30,   5,  12,  16,  16,  12,   5, -30,
            -40, -20,   0,   5,   5,   0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
    };

    private static final int[] BISHOP_PST = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10,   0,   0,   0,   0,   0,   0, -10,
            -10,   0,   8,  12,  12,   8,   0, -10,
            -10,   6,   6,  12,  12,   6,   6, -10,
            -10,   0,  12,  12,  12,  12,   0, -10,
            -10,  12,  12,  12,  12,  12,  12, -10,
            -10,   6,   0,   0,   0,   0,   6, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
    };

    private static final int[] ROOK_PST = {
            0,   0,   0,   4,   4,   0,   0,   0,
            6,  12,  12,  12,  12,  12,  12,   6,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            0,   0,   0,   8,   8,   0,   0,   0
    };

    private static final int[] QUEEN_PST = {
            -20, -10, -10,  -5,  -5, -10, -10, -20,
            -10,   0,   0,   0,   0,   0,   0, -10,
            -10,   0,   6,   6,   6,   6,   0, -10,
            -5,   0,   6,   8,   8,   6,   0,  -5,
            0,   0,   6,   8,   8,   6,   0,  -5,
            -10,   6,   6,   6,   6,   6,   0, -10,
            -10,   0,   6,   0,   0,   0,   0, -10,
            -20, -10, -10,  -5,  -5, -10, -10, -20
    };

    // Vua Trung cuộc: Nép an toàn ở 2 góc nhập thành
    private static final int[] KING_MG = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20,  22,   0,   0,   0,   0,  22,  20,
            20,  32,  12,   0,   0,  12,  32,  20
    };

    // Vua Tàn cuộc: Tiến mạnh ra trung tâm hỗ trợ Tốt phong cấp
    private static final int[] KING_EG = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10,   0,   0, -10, -20, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -10,  30,  42,  42,  30, -10, -30,
            -30, -10,  30,  42,  42,  30, -10, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -30,   0,   0,   0,   0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50
    };

    private BoardEvaluator() {}

    /**
     * Trả về điểm thế cờ theo góc nhìn của bên đang có lượt (`state.sideToMove`).
     */
    public static int evaluate(BoardState state) {
        int[] board = state.board;

        int whiteMg = 0, whiteEg = 0;
        int blackMg = 0, blackEg = 0;
        int gamePhase = 0; // Tổng điểm pha trận đấu (Max = 24)

        int whiteBishops = 0;
        int blackBishops = 0;

        int[] whitePawnsOnFile = new int[8];
        int[] blackPawnsOnFile = new int[8];
        int[] whiteMostAdvancedRow = {8, 8, 8, 8, 8, 8, 8, 8};
        int[] blackMostAdvancedRow = {-1, -1, -1, -1, -1, -1, -1, -1};

        // 1. Quét toàn bàn cờ tính vật chất + Piece-Square Tables
        for (int sq = 0; sq < 64; sq++) {
            int p = board[sq];
            if (p == Piece.NONE) continue;

            int type = Piece.getType(p);
            boolean isWhite = Piece.isWhite(p);
            int pstSq = isWhite ? sq : (sq ^ 56);
            int file = sq & 7;
            int row = sq >> 3;

            int mat = PIECE_VALUES[type];
            switch (type) {
                case Piece.PAWN:
                    if (isWhite) {
                        whiteMg += mat + PAWN_MG[pstSq];
                        whiteEg += mat + PAWN_EG[pstSq];
                        whitePawnsOnFile[file]++;
                        if (row < whiteMostAdvancedRow[file]) whiteMostAdvancedRow[file] = row;
                    } else {
                        blackMg += mat + PAWN_MG[pstSq];
                        blackEg += mat + PAWN_EG[pstSq];
                        blackPawnsOnFile[file]++;
                        if (row > blackMostAdvancedRow[file]) blackMostAdvancedRow[file] = row;
                    }
                    break;

                case Piece.KNIGHT:
                    gamePhase += 1;
                    if (isWhite) {
                        whiteMg += mat + KNIGHT_PST[pstSq];
                        whiteEg += mat + KNIGHT_PST[pstSq];
                    } else {
                        blackMg += mat + KNIGHT_PST[pstSq];
                        blackEg += mat + KNIGHT_PST[pstSq];
                    }
                    break;

                case Piece.BISHOP:
                    gamePhase += 1;
                    if (isWhite) {
                        whiteBishops++;
                        whiteMg += mat + BISHOP_PST[pstSq];
                        whiteEg += mat + BISHOP_PST[pstSq];
                    } else {
                        blackBishops++;
                        blackMg += mat + BISHOP_PST[pstSq];
                        blackEg += mat + BISHOP_PST[pstSq];
                    }
                    break;

                case Piece.ROOK:
                    gamePhase += 2;
                    if (isWhite) {
                        whiteMg += mat + ROOK_PST[pstSq];
                        whiteEg += mat + ROOK_PST[pstSq];
                    } else {
                        blackMg += mat + ROOK_PST[pstSq];
                        blackEg += mat + ROOK_PST[pstSq];
                    }
                    break;

                case Piece.QUEEN:
                    gamePhase += 4;
                    if (isWhite) {
                        whiteMg += mat + QUEEN_PST[pstSq];
                        whiteEg += mat + QUEEN_PST[pstSq];
                    } else {
                        blackMg += mat + QUEEN_PST[pstSq];
                        blackEg += mat + QUEEN_PST[pstSq];
                    }
                    break;

                case Piece.KING:
                    if (isWhite) {
                        whiteMg += KING_MG[pstSq];
                        whiteEg += KING_EG[pstSq];
                    } else {
                        blackMg += KING_MG[pstSq];
                        blackEg += KING_EG[pstSq];
                    }
                    break;
            }
        }

        // 2. Thưởng cặp Tượng (Bishop Pair)
        if (whiteBishops >= 2) {
            whiteMg += 32;
            whiteEg += 48;
        }
        if (blackBishops >= 2) {
            blackMg += 32;
            blackEg += 48;
        }

        // 3. Đánh giá cấu trúc Tốt (Tốt chồng, Tốt cô lập, Tốt thông)
        for (int f = 0; f < 8; f++) {
            if (whitePawnsOnFile[f] > 1) {
                int penalty = (whitePawnsOnFile[f] - 1) * 16;
                whiteMg -= penalty;
                whiteEg -= penalty + 6;
            }
            if (blackPawnsOnFile[f] > 1) {
                int penalty = (blackPawnsOnFile[f] - 1) * 16;
                blackMg -= penalty;
                blackEg -= penalty + 6;
            }

            if (whitePawnsOnFile[f] > 0) {
                boolean leftSupport = (f > 0 && whitePawnsOnFile[f - 1] > 0);
                boolean rightSupport = (f < 7 && whitePawnsOnFile[f + 1] > 0);
                if (!leftSupport && !rightSupport) {
                    whiteMg -= 14;
                    whiteEg -= 18;
                }
                // Tốt thông Trắng
                int r = whiteMostAdvancedRow[f];
                boolean blocked = (blackMostAdvancedRow[f] != -1 && blackMostAdvancedRow[f] < r) ||
                        (f > 0 && blackMostAdvancedRow[f - 1] != -1 && blackMostAdvancedRow[f - 1] < r) ||
                        (f < 7 && blackMostAdvancedRow[f + 1] != -1 && blackMostAdvancedRow[f + 1] < r);
                if (!blocked) {
                    int bonus = (7 - r) * 12;
                    whiteMg += bonus / 2;
                    whiteEg += bonus + 15;
                }
            }

            if (blackPawnsOnFile[f] > 0) {
                boolean leftSupport = (f > 0 && blackPawnsOnFile[f - 1] > 0);
                boolean rightSupport = (f < 7 && blackPawnsOnFile[f + 1] > 0);
                if (!leftSupport && !rightSupport) {
                    blackMg -= 14;
                    blackEg -= 18;
                }
                // Tốt thông Đen
                int r = blackMostAdvancedRow[f];
                boolean blocked = (whiteMostAdvancedRow[f] != 8 && whiteMostAdvancedRow[f] > r) ||
                        (f > 0 && whiteMostAdvancedRow[f - 1] != 8 && whiteMostAdvancedRow[f - 1] > r) ||
                        (f < 7 && whiteMostAdvancedRow[f + 1] != 8 && whiteMostAdvancedRow[f + 1] > r);
                if (!blocked) {
                    int bonus = r * 12;
                    blackMg += bonus / 2;
                    blackEg += bonus + 15;
                }
            }
        }

        // 4. Đánh giá Xe trên cột mở / nửa mở & Lá chắn Vua Trung cuộc
        for (int sq = 0; sq < 64; sq++) {
            int p = board[sq];
            if (p == Piece.WHITE_ROOK) {
                int f = sq & 7;
                if (whitePawnsOnFile[f] == 0) {
                    whiteMg += (blackPawnsOnFile[f] == 0) ? 22 : 12;
                    whiteEg += 14;
                }
            } else if (p == Piece.BLACK_ROOK) {
                int f = sq & 7;
                if (blackPawnsOnFile[f] == 0) {
                    blackMg += (whitePawnsOnFile[f] == 0) ? 22 : 12;
                    blackEg += 14;
                }
            }
        }

        whiteMg += evaluateKingShield(board, state.whiteKingSq, Piece.WHITE);
        blackMg += evaluateKingShield(board, state.blackKingSq, Piece.BLACK);

        // 5. Nội suy Trung cuộc <-> Tàn cuộc (Tapered Eval)
        int mgPhase = Math.min(24, gamePhase);
        int egPhase = 24 - mgPhase;

        int whiteScore = (whiteMg * mgPhase + whiteEg * egPhase) / 24;
        int blackScore = (blackMg * mgPhase + blackEg * egPhase) / 24;

        // Thưởng quyền đi trước (Tempo)
        int eval = whiteScore - blackScore;
        return (state.sideToMove == Piece.WHITE) ? (eval + 12) : (-eval + 12);
    }

    private static int evaluateKingShield(int[] board, int kingSq, int color) {
        int r = kingSq >> 3;
        int c = kingSq & 7;
        int shieldRow = (color == Piece.WHITE) ? (r - 1) : (r + 1);
        if ((shieldRow & ~7) != 0) return 0;

        int friendlyPawn = color | Piece.PAWN;
        int shieldBonus = 0;
        for (int dc = -1; dc <= 1; dc++) {
            int nc = c + dc;
            if ((nc & ~7) == 0 && board[(shieldRow << 3) | nc] == friendlyPawn) {
                shieldBonus += 10;
            }
        }
        return shieldBonus;
    }

    /**
     * Kiểm tra bên đang có lượt có còn quân nặng/nhẹ (ngoài Vua và Tốt) để tránh Zugzwang khi Null-Move.
     */
    public static boolean hasNonPawnMaterial(BoardState state, int color) {
        for (int sq = 0; sq < 64; sq++) {
            int p = state.board[sq];
            if (p != Piece.NONE && Piece.isColor(p, color)) {
                int t = Piece.getType(p);
                if (t == Piece.KNIGHT || t == Piece.BISHOP || t == Piece.ROOK || t == Piece.QUEEN) {
                    return true;
                }
            }
        }
        return false;
    }
}