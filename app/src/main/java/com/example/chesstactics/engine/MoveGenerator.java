package com.example.chesstactics.engine;

import com.example.chesstactics.models.ChessMove;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ sinh nước đi Cờ Vua chuẩn xác 100%:
 * - Kiểm tra ô bị tấn công (isSquareAttacked)
 * - Sinh nước đi hợp lệ tuyệt đối (lọc mọi nước khiến Vua mình bị chiếu)
 * - Sinh riêng nước ăn quân & phong cấp cho Quiescence Search của AI
 */
public final class MoveGenerator {

    private static final int[][] KNIGHT_OFFSETS = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
            {1, -2},  {1, 2},  {2, -1},  {2, 1}
    };

    private static final int[][] KING_OFFSETS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
    };

    private static final int[][] BISHOP_DIRS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] ROOK_DIRS   = {{-1, 0},  {1, 0},  {0, -1}, {0, 1}};
    private static final int[][] QUEEN_DIRS  = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
    };

    private MoveGenerator() {}

    /**
     * Kiểm tra ô `sq` có đang bị tấn công bởi phe `attackerColor` hay không.
     */
    public static boolean isSquareAttacked(BoardState state, int sq, int attackerColor) {
        int[] board = state.board;
        int r = sq >> 3;
        int c = sq & 7;

        // 1. Tấn công từ Tốt
        if (attackerColor == Piece.WHITE) {
            // Tốt Trắng đứng ở hàng dưới (r + 1) tấn công chéo lên (r)
            int pr = r + 1;
            if (pr < 8) {
                if (c - 1 >= 0 && board[(pr << 3) | (c - 1)] == Piece.WHITE_PAWN) return true;
                if (c + 1 < 8  && board[(pr << 3) | (c + 1)] == Piece.WHITE_PAWN) return true;
            }
        } else {
            // Tốt Đen đứng ở hàng trên (r - 1) tấn công chéo xuống (r)
            int pr = r - 1;
            if (pr >= 0) {
                if (c - 1 >= 0 && board[(pr << 3) | (c - 1)] == Piece.BLACK_PAWN) return true;
                if (c + 1 < 8  && board[(pr << 3) | (c + 1)] == Piece.BLACK_PAWN) return true;
            }
        }

        // 2. Tấn công từ Mã
        int enemyKnight = attackerColor | Piece.KNIGHT;
        for (int[] off : KNIGHT_OFFSETS) {
            int nr = r + off[0];
            int nc = c + off[1];
            if ((nr & ~7) == 0 && (nc & ~7) == 0) {
                if (board[(nr << 3) | nc] == enemyKnight) return true;
            }
        }

        // 3. Tấn công từ Vua
        int enemyKing = attackerColor | Piece.KING;
        for (int[] off : KING_OFFSETS) {
            int nr = r + off[0];
            int nc = c + off[1];
            if ((nr & ~7) == 0 && (nc & ~7) == 0) {
                if (board[(nr << 3) | nc] == enemyKing) return true;
            }
        }

        // 4. Tấn công đường chéo (Tượng & Hậu)
        int enemyBishop = attackerColor | Piece.BISHOP;
        int enemyQueen = attackerColor | Piece.QUEEN;
        for (int[] dir : BISHOP_DIRS) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            while ((nr & ~7) == 0 && (nc & ~7) == 0) {
                int p = board[(nr << 3) | nc];
                if (p != Piece.NONE) {
                    if (p == enemyBishop || p == enemyQueen) return true;
                    break;
                }
                nr += dir[0];
                nc += dir[1];
            }
        }

        // 5. Tấn công ngang/dọc (Xe & Hậu)
        int enemyRook = attackerColor | Piece.ROOK;
        for (int[] dir : ROOK_DIRS) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            while ((nr & ~7) == 0 && (nc & ~7) == 0) {
                int p = board[(nr << 3) | nc];
                if (p != Piece.NONE) {
                    if (p == enemyRook || p == enemyQueen) return true;
                    break;
                }
                nr += dir[0];
                nc += dir[1];
            }
        }

        return false;
    }

    public static boolean isKingInCheck(BoardState state, int kingColor) {
        int kingSq = (kingColor == Piece.WHITE) ? state.whiteKingSq : state.blackKingSq;
        return isSquareAttacked(state, kingSq, Piece.oppositeColor(kingColor));
    }

    /**
     * Sinh danh sách toàn bộ nước đi hợp lệ (dạng số nguyên nén 32-bit) cho bên đang có lượt.
     */
    public static int generateLegalMoves(BoardState state, int[] outMoves, boolean capturesOnly) {
        int[] pseudoMoves = new int[256];
        int pseudoCount = generatePseudoLegalMoves(state, pseudoMoves, capturesOnly);
        int legalCount = 0;
        int us = state.sideToMove;

        for (int i = 0; i < pseudoCount; i++) {
            int move = pseudoMoves[i];
            state.makeMove(move);
            boolean inCheck = isKingInCheck(state, us);
            state.unmakeMove(move);

            if (!inCheck) {
                outMoves[legalCount++] = move;
            }
        }
        return legalCount;
    }

    /**
     * Sinh danh sách nước đi hợp lệ dạng đối tượng ChessMove (dùng cho UI hiển thị gợi ý).
     */
    public static List<ChessMove> generateLegalMoveObjects(BoardState state) {
        int[] buffer = new int[256];
        int count = generateLegalMoves(state, buffer, false);
        List<ChessMove> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(ChessMove.fromPacked(buffer[i]));
        }
        return result;
    }

    /**
     * Sinh nước đi giả hợp lệ (chưa lọc chiếu Vua) cực nhanh.
     */
    public static int generatePseudoLegalMoves(BoardState state, int[] moves, boolean capturesOnly) {
        int count = 0;
        int us = state.sideToMove;
        int them = Piece.oppositeColor(us);
        int[] board = state.board;

        for (int sq = 0; sq < 64; sq++) {
            int piece = board[sq];
            if (piece == Piece.NONE || !Piece.isColor(piece, us)) continue;

            int type = Piece.getType(piece);
            int r = sq >> 3;
            int c = sq & 7;

            if (type == Piece.PAWN) {
                count = addPawnMoves(state, sq, r, c, piece, us, them, capturesOnly, moves, count);
            } else if (type == Piece.KNIGHT) {
                for (int[] off : KNIGHT_OFFSETS) {
                    int nr = r + off[0];
                    int nc = c + off[1];
                    if ((nr & ~7) == 0 && (nc & ~7) == 0) {
                        int target = (nr << 3) | nc;
                        int targetPiece = board[target];
                        if (targetPiece == Piece.NONE) {
                            if (!capturesOnly) {
                                moves[count++] = ChessMove.pack(sq, target, piece, Piece.NONE, Piece.NONE, ChessMove.FLAG_NORMAL);
                            }
                        } else if (Piece.isColor(targetPiece, them)) {
                            moves[count++] = ChessMove.pack(sq, target, piece, targetPiece, Piece.NONE, ChessMove.FLAG_NORMAL);
                        }
                    }
                }
            } else if (type == Piece.KING) {
                for (int[] off : KING_OFFSETS) {
                    int nr = r + off[0];
                    int nc = c + off[1];
                    if ((nr & ~7) == 0 && (nc & ~7) == 0) {
                        int target = (nr << 3) | nc;
                        int targetPiece = board[target];
                        if (targetPiece == Piece.NONE) {
                            if (!capturesOnly) {
                                moves[count++] = ChessMove.pack(sq, target, piece, Piece.NONE, Piece.NONE, ChessMove.FLAG_NORMAL);
                            }
                        } else if (Piece.isColor(targetPiece, them)) {
                            moves[count++] = ChessMove.pack(sq, target, piece, targetPiece, Piece.NONE, ChessMove.FLAG_NORMAL);
                        }
                    }
                }
                if (!capturesOnly) {
                    count = addCastlingMoves(state, sq, piece, us, them, moves, count);
                }
            } else {
                int[][] dirs = (type == Piece.BISHOP) ? BISHOP_DIRS :
                        (type == Piece.ROOK) ? ROOK_DIRS : QUEEN_DIRS;
                for (int[] dir : dirs) {
                    int nr = r + dir[0];
                    int nc = c + dir[1];
                    while ((nr & ~7) == 0 && (nc & ~7) == 0) {
                        int target = (nr << 3) | nc;
                        int targetPiece = board[target];
                        if (targetPiece == Piece.NONE) {
                            if (!capturesOnly) {
                                moves[count++] = ChessMove.pack(sq, target, piece, Piece.NONE, Piece.NONE, ChessMove.FLAG_NORMAL);
                            }
                        } else {
                            if (Piece.isColor(targetPiece, them)) {
                                moves[count++] = ChessMove.pack(sq, target, piece, targetPiece, Piece.NONE, ChessMove.FLAG_NORMAL);
                            }
                            break;
                        }
                        nr += dir[0];
                        nc += dir[1];
                    }
                }
            }
        }
        return count;
    }

    private static int addPawnMoves(BoardState state, int sq, int r, int c, int piece, int us, int them,
                                    boolean capturesOnly, int[] moves, int count) {
        int[] board = state.board;
        int dir = (us == Piece.WHITE) ? -1 : 1;
        int startRow = (us == Piece.WHITE) ? 6 : 1;
        int promoRow = (us == Piece.WHITE) ? 0 : 7;

        int nr = r + dir;
        if ((nr & ~7) != 0) return count;

        // 1. Tiến thẳng 1 ô & 2 ô
        int forwardSq = (nr << 3) | c;
        if (board[forwardSq] == Piece.NONE) {
            if (nr == promoRow) {
                count = addPromotions(sq, forwardSq, piece, Piece.NONE, us, moves, count);
            } else if (!capturesOnly) {
                moves[count++] = ChessMove.pack(sq, forwardSq, piece, Piece.NONE, Piece.NONE, ChessMove.FLAG_NORMAL);
                if (r == startRow) {
                    int doubleSq = ((r + 2 * dir) << 3) | c;
                    if (board[doubleSq] == Piece.NONE) {
                        moves[count++] = ChessMove.pack(sq, doubleSq, piece, Piece.NONE, Piece.NONE, ChessMove.FLAG_DOUBLE_PAWN);
                    }
                }
            }
        }

        // 2. Ăn chéo & Bắt Tốt qua đường (En Passant)
        int[] capCols = {c - 1, c + 1};
        for (int nc : capCols) {
            if ((nc & ~7) != 0) continue;
            int target = (nr << 3) | nc;
            int targetPiece = board[target];

            if (targetPiece != Piece.NONE && Piece.isColor(targetPiece, them)) {
                if (nr == promoRow) {
                    count = addPromotions(sq, target, piece, targetPiece, us, moves, count);
                } else {
                    moves[count++] = ChessMove.pack(sq, target, piece, targetPiece, Piece.NONE, ChessMove.FLAG_NORMAL);
                }
            } else if (target == state.enPassantSquare) {
                int enemyPawn = them | Piece.PAWN;
                moves[count++] = ChessMove.pack(sq, target, piece, enemyPawn, Piece.NONE, ChessMove.FLAG_EN_PASSANT);
            }
        }
        return count;
    }

    private static int addPromotions(int from, int to, int pawn, int captured, int color, int[] moves, int count) {
        moves[count++] = ChessMove.pack(from, to, pawn, captured, color | Piece.QUEEN, ChessMove.FLAG_NORMAL);
        moves[count++] = ChessMove.pack(from, to, pawn, captured, color | Piece.ROOK, ChessMove.FLAG_NORMAL);
        moves[count++] = ChessMove.pack(from, to, pawn, captured, color | Piece.BISHOP, ChessMove.FLAG_NORMAL);
        moves[count++] = ChessMove.pack(from, to, pawn, captured, color | Piece.KNIGHT, ChessMove.FLAG_NORMAL);
        return count;
    }

    private static int addCastlingMoves(BoardState state, int kingSq, int kingPiece, int us, int them,
                                        int[] moves, int count) {
        int[] board = state.board;
        int rights = state.castlingRights;

        if (us == Piece.WHITE && kingSq == 60) {
            if ((rights & BoardState.CASTLE_WK) != 0 &&
                    board[61] == Piece.NONE && board[62] == Piece.NONE && board[63] == Piece.WHITE_ROOK) {
                if (!isSquareAttacked(state, 60, them) &&
                        !isSquareAttacked(state, 61, them) &&
                        !isSquareAttacked(state, 62, them)) {
                    moves[count++] = ChessMove.pack(60, 62, kingPiece, Piece.NONE, Piece.NONE, ChessMove.FLAG_CASTLE_KING);
                }
            }
            if ((rights & BoardState.CASTLE_WQ) != 0 &&
                    board[59] == Piece.NONE && board[58] == Piece.NONE && board[57] == Piece.NONE && board[56] == Piece.WHITE_ROOK) {
                if (!isSquareAttacked(state, 60, them) &&
                        !isSquareAttacked(state, 59, them) &&
                        !isSquareAttacked(state, 58, them)) {
                    moves[count++] = ChessMove.pack(60, 58, kingPiece, Piece.NONE, Piece.NONE, ChessMove.FLAG_CASTLE_QUEEN);
                }
            }
        } else if (us == Piece.BLACK && kingSq == 4) {
            if ((rights & BoardState.CASTLE_BK) != 0 &&
                    board[5] == Piece.NONE && board[6] == Piece.NONE && board[7] == Piece.BLACK_ROOK) {
                if (!isSquareAttacked(state, 4, them) &&
                        !isSquareAttacked(state, 5, them) &&
                        !isSquareAttacked(state, 6, them)) {
                    moves[count++] = ChessMove.pack(4, 6, kingPiece, Piece.NONE, Piece.NONE, ChessMove.FLAG_CASTLE_KING);
                }
            }
            if ((rights & BoardState.CASTLE_BQ) != 0 &&
                    board[3] == Piece.NONE && board[2] == Piece.NONE && board[1] == Piece.NONE && board[0] == Piece.BLACK_ROOK) {
                if (!isSquareAttacked(state, 4, them) &&
                        !isSquareAttacked(state, 3, them) &&
                        !isSquareAttacked(state, 2, them)) {
                    moves[count++] = ChessMove.pack(4, 2, kingPiece, Piece.NONE, Piece.NONE, ChessMove.FLAG_CASTLE_QUEEN);
                }
            }
        }
        return count;
    }
}