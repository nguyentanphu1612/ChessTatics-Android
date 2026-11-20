package com.example.chesstactics.engine;

import com.example.chesstactics.ai.ChessZobristTT;
import com.example.chesstactics.models.ChessMove;

import java.util.Arrays;

/**
 * Biểu diễn toàn bộ trạng thái ván cờ Vua trên mảng 1 chiều 64 ô:
 * - Quy ước tọa độ: 0 = a8 (góc trên trái), 63 = h1 (góc dưới phải).
 * - Hỗ trợ makeMove / unmakeMove và makeNullMove / unmakeNullMove tốc độ O(1).
 * - Cập nhật Zobrist Hash 64-bit tự động qua từng nước đi.
 */
public class BoardState {

    // Cờ quyền nhập thành (Castling Rights Bitmask)
    public static final int CASTLE_WK = 1; // Trắng nhập thành gần (e1-g1)
    public static final int CASTLE_WQ = 2; // Trắng nhập thành xa (e1-c1)
    public static final int CASTLE_BK = 4; // Đen nhập thành gần (e8-g8)
    public static final int CASTLE_BQ = 8; // Đen nhập thành xa (e8-c8)

    private static final int MAX_GAME_PLIES = 1024;

    // Bảng mặt nạ tự động tước quyền nhập thành khi quân di chuyển hoặc bị ăn tại ô tương ứng
    private static final int[] CASTLING_MASK = new int[64];

    static {
        Arrays.fill(CASTLING_MASK, 15);
        CASTLING_MASK[60] = 15 & ~(CASTLE_WK | CASTLE_WQ); // Vua Trắng e1
        CASTLING_MASK[63] = 15 & ~CASTLE_WK;               // Xe Trắng h1
        CASTLING_MASK[56] = 15 & ~CASTLE_WQ;               // Xe Trắng a1
        CASTLING_MASK[4]  = 15 & ~(CASTLE_BK | CASTLE_BQ); // Vua Đen e8
        CASTLING_MASK[7]  = 15 & ~CASTLE_BK;               // Xe Đen h8
        CASTLING_MASK[0]  = 15 & ~CASTLE_BQ;               // Xe Đen a8
    }

    public final int[] board = new int[64];
    public int sideToMove = Piece.WHITE;
    public int castlingRights = 15;
    public int enPassantSquare = -1; // -1 nếu không có ô bắt Tốt qua đường
    public int halfMoveClock = 0;    // Đếm luật 50 nước hòa
    public int fullMoveNumber = 1;
    public int whiteKingSq = 60;
    public int blackKingSq = 4;
    public long zobristHash = 0L;

    // Ngăn xếp hoàn tác (Undo Stack) dùng mảng nguyên thủy tránh tạo rác GC
    private int historyPly = 0;
    private final int[] undoCastling = new int[MAX_GAME_PLIES];
    private final int[] undoEpSquare = new int[MAX_GAME_PLIES];
    private final int[] undoHalfMove = new int[MAX_GAME_PLIES];
    private final long[] undoHash = new long[MAX_GAME_PLIES];
    private final long[] repetitionTable = new long[MAX_GAME_PLIES];

    public BoardState() {
        setupStartingPosition();
    }

    /**
     * Sao chép sâu trạng thái bàn cờ (dùng để giao cho luồng AI chạy độc lập).
     */
    public BoardState copy() {
        BoardState clone = new BoardState();
        System.arraycopy(this.board, 0, clone.board, 0, 64);
        clone.sideToMove = this.sideToMove;
        clone.castlingRights = this.castlingRights;
        clone.enPassantSquare = this.enPassantSquare;
        clone.halfMoveClock = this.halfMoveClock;
        clone.fullMoveNumber = this.fullMoveNumber;
        clone.whiteKingSq = this.whiteKingSq;
        clone.blackKingSq = this.blackKingSq;
        clone.zobristHash = this.zobristHash;
        clone.historyPly = this.historyPly;
        int copyLen = Math.min(this.historyPly + 1, MAX_GAME_PLIES);
        System.arraycopy(this.undoCastling, 0, clone.undoCastling, 0, copyLen);
        System.arraycopy(this.undoEpSquare, 0, clone.undoEpSquare, 0, copyLen);
        System.arraycopy(this.undoHalfMove, 0, clone.undoHalfMove, 0, copyLen);
        System.arraycopy(this.undoHash, 0, clone.undoHash, 0, copyLen);
        System.arraycopy(this.repetitionTable, 0, clone.repetitionTable, 0, copyLen);
        return clone;
    }

    public void setupStartingPosition() {
        NotationConverter.loadFen(this, NotationConverter.STARTING_FEN);
    }

    public void recomputeZobristHash() {
        long h = 0L;
        for (int sq = 0; sq < 64; sq++) {
            int p = board[sq];
            if (p != Piece.NONE) {
                h ^= ChessZobristTT.PIECE_KEYS[p][sq];
            }
        }
        h ^= ChessZobristTT.CASTLING_KEYS[castlingRights];
        int epFile = (enPassantSquare != -1) ? (enPassantSquare & 7) : 8;
        h ^= ChessZobristTT.EP_FILE_KEYS[epFile];
        if (sideToMove == Piece.BLACK) {
            h ^= ChessZobristTT.BLACK_TO_MOVE_KEY;
        }
        zobristHash = h;
    }

    /**
     * Thực hiện nước đi trên bàn cờ và cập nhật Zobrist Hash O(1).
     */
    public void makeMove(int move) {
        int from = ChessMove.getFrom(move);
        int to = ChessMove.getTo(move);
        int piece = ChessMove.getPiece(move);
        int captured = ChessMove.getCaptured(move);
        int promotion = ChessMove.getPromotion(move);
        int flag = ChessMove.getFlag(move);

        // Lưu trạng thái hiện tại vào Undo Stack
        if (historyPly < MAX_GAME_PLIES) {
            undoCastling[historyPly] = castlingRights;
            undoEpSquare[historyPly] = enPassantSquare;
            undoHalfMove[historyPly] = halfMoveClock;
            undoHash[historyPly] = zobristHash;
            repetitionTable[historyPly] = zobristHash;
        }
        historyPly++;

        // Xóa hash cũ của quyền nhập thành & ô En Passant
        zobristHash ^= ChessZobristTT.CASTLING_KEYS[castlingRights];
        zobristHash ^= ChessZobristTT.EP_FILE_KEYS[(enPassantSquare != -1) ? (enPassantSquare & 7) : 8];

        // Cập nhật đồng hồ 50 nước
        if (Piece.getType(piece) == Piece.PAWN || captured != Piece.NONE) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        // Nhấc quân khỏi ô xuất phát
        board[from] = Piece.NONE;
        zobristHash ^= ChessZobristTT.PIECE_KEYS[piece][from];

        // Xử lý ăn quân
        if (flag == ChessMove.FLAG_EN_PASSANT) {
            int epPawnSq = Piece.isWhite(piece) ? (to + 8) : (to - 8);
            int enemyPawn = board[epPawnSq];
            board[epPawnSq] = Piece.NONE;
            zobristHash ^= ChessZobristTT.PIECE_KEYS[enemyPawn][epPawnSq];
        } else if (captured != Piece.NONE) {
            zobristHash ^= ChessZobristTT.PIECE_KEYS[captured][to];
        }

        // Đặt quân vào ô đích (hoặc quân phong cấp)
        int placedPiece = (promotion != Piece.NONE) ? promotion : piece;
        board[to] = placedPiece;
        zobristHash ^= ChessZobristTT.PIECE_KEYS[placedPiece][to];

        // Cập nhật vị trí Vua & di chuyển Xe nếu nhập thành
        if (Piece.getType(piece) == Piece.KING) {
            if (Piece.isWhite(piece)) whiteKingSq = to;
            else blackKingSq = to;

            if (flag == ChessMove.FLAG_CASTLE_KING) {
                int rookFrom = to + 1;
                int rookTo = to - 1;
                int rook = board[rookFrom];
                board[rookFrom] = Piece.NONE;
                board[rookTo] = rook;
                zobristHash ^= ChessZobristTT.PIECE_KEYS[rook][rookFrom];
                zobristHash ^= ChessZobristTT.PIECE_KEYS[rook][rookTo];
            } else if (flag == ChessMove.FLAG_CASTLE_QUEEN) {
                int rookFrom = to - 2;
                int rookTo = to + 1;
                int rook = board[rookFrom];
                board[rookFrom] = Piece.NONE;
                board[rookTo] = rook;
                zobristHash ^= ChessZobristTT.PIECE_KEYS[rook][rookFrom];
                zobristHash ^= ChessZobristTT.PIECE_KEYS[rook][rookTo];
            }
        }

        // Cập nhật ô En Passant mới
        if (flag == ChessMove.FLAG_DOUBLE_PAWN) {
            enPassantSquare = (from + to) >> 1;
        } else {
            enPassantSquare = -1;
        }

        // Cập nhật quyền nhập thành
        castlingRights &= CASTLING_MASK[from] & CASTLING_MASK[to];

        // Áp dụng hash mới của quyền nhập thành, En Passant và đổi lượt
        zobristHash ^= ChessZobristTT.CASTLING_KEYS[castlingRights];
        zobristHash ^= ChessZobristTT.EP_FILE_KEYS[(enPassantSquare != -1) ? (enPassantSquare & 7) : 8];
        zobristHash ^= ChessZobristTT.BLACK_TO_MOVE_KEY;

        if (sideToMove == Piece.BLACK) {
            fullMoveNumber++;
        }
        sideToMove = Piece.oppositeColor(sideToMove);
    }

    /**
     * Hoàn tác chính xác nước đi vừa thực hiện.
     */
    public void unmakeMove(int move) {
        historyPly--;
        sideToMove = Piece.oppositeColor(sideToMove);
        if (sideToMove == Piece.BLACK) {
            fullMoveNumber--;
        }

        if (historyPly >= 0 && historyPly < MAX_GAME_PLIES) {
            castlingRights = undoCastling[historyPly];
            enPassantSquare = undoEpSquare[historyPly];
            halfMoveClock = undoHalfMove[historyPly];
            zobristHash = undoHash[historyPly];
        }

        int from = ChessMove.getFrom(move);
        int to = ChessMove.getTo(move);
        int piece = ChessMove.getPiece(move);
        int captured = ChessMove.getCaptured(move);
        int flag = ChessMove.getFlag(move);

        // Trả quân di chuyển (luôn dùng `piece` gốc kể cả khi có phong cấp)
        board[from] = piece;

        if (flag == ChessMove.FLAG_EN_PASSANT) {
            board[to] = Piece.NONE;
            int epPawnSq = Piece.isWhite(piece) ? (to + 8) : (to - 8);
            board[epPawnSq] = captured;
        } else {
            board[to] = captured;
        }

        if (Piece.getType(piece) == Piece.KING) {
            if (Piece.isWhite(piece)) whiteKingSq = from;
            else blackKingSq = from;

            if (flag == ChessMove.FLAG_CASTLE_KING) {
                int rookFrom = to + 1;
                int rookTo = to - 1;
                board[rookFrom] = board[rookTo];
                board[rookTo] = Piece.NONE;
            } else if (flag == ChessMove.FLAG_CASTLE_QUEEN) {
                int rookFrom = to - 2;
                int rookTo = to + 1;
                board[rookFrom] = board[rookTo];
                board[rookTo] = Piece.NONE;
            }
        }
    }

    /**
     * Nhường lượt rỗng (Null Move) dùng cho thuật toán Null-Move Pruning trong AI.
     */
    public void makeNullMove() {
        if (historyPly < MAX_GAME_PLIES) {
            undoCastling[historyPly] = castlingRights;
            undoEpSquare[historyPly] = enPassantSquare;
            undoHalfMove[historyPly] = halfMoveClock;
            undoHash[historyPly] = zobristHash;
            repetitionTable[historyPly] = zobristHash;
        }
        historyPly++;

        zobristHash ^= ChessZobristTT.EP_FILE_KEYS[(enPassantSquare != -1) ? (enPassantSquare & 7) : 8];
        enPassantSquare = -1;
        zobristHash ^= ChessZobristTT.EP_FILE_KEYS[8];
        zobristHash ^= ChessZobristTT.BLACK_TO_MOVE_KEY;

        sideToMove = Piece.oppositeColor(sideToMove);
    }

    public void unmakeNullMove() {
        historyPly--;
        sideToMove = Piece.oppositeColor(sideToMove);
        if (historyPly >= 0 && historyPly < MAX_GAME_PLIES) {
            enPassantSquare = undoEpSquare[historyPly];
            zobristHash = undoHash[historyPly];
        }
    }

    /**
     * Kiểm tra lặp thế cờ (dùng để phát hiện hòa 3 lần lặp hoặc ngăn AI lặp nước vô ích).
     */
    public int getRepetitionCount() {
        int count = 1;
        int start = Math.max(0, historyPly - halfMoveClock);
        int end = Math.min(historyPly, MAX_GAME_PLIES);
        for (int i = end - 2; i >= start; i -= 2) {
            if (repetitionTable[i] == zobristHash) {
                count++;
            }
        }
        return count;
    }

    public int getHistoryPly() {
        return historyPly;
    }
}