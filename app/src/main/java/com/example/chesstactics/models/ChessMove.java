package com.example.chesstactics.models;

import com.example.chesstactics.engine.Piece;

/**
 * Mô hình nước đi Cờ Vua, hỗ trợ nén/giải nén 32-bit tốc độ cao cho Engine AI
 * và lưu trữ thông tin hiển thị/đồng bộ Firebase cho tầng UI.
 */
public class ChessMove {

    public static final int FLAG_NORMAL = 0;
    public static final int FLAG_DOUBLE_PAWN = 1;
    public static final int FLAG_EN_PASSANT = 2;
    public static final int FLAG_CASTLE_KING = 3;
    public static final int FLAG_CASTLE_QUEEN = 4;

    public final int from;          // 0..63
    public final int to;            // 0..63
    public final int piece;         // Mã quân di chuyển
    public final int capturedPiece; // Mã quân bị ăn (0 nếu không ăn)
    public final int promotionPiece;// Mã quân phong cấp (0 nếu không phong cấp)
    public final int flag;          // Cờ nước đi đặc biệt
    public String sanNotation;      // Ký hiệu chuẩn quốc tế (vd: Nf3, O-O, exd8=Q#)

    public ChessMove(int from, int to, int piece, int capturedPiece, int promotionPiece, int flag) {
        this.from = from;
        this.to = to;
        this.piece = piece;
        this.capturedPiece = capturedPiece;
        this.promotionPiece = promotionPiece;
        this.flag = flag;
    }

    public int getFromRow() { return from >> 3; }
    public int getFromCol() { return from & 7; }
    public int getToRow() { return to >> 3; }
    public int getToCol() { return to & 7; }

    public boolean isCapture() {
        return capturedPiece != Piece.NONE || flag == FLAG_EN_PASSANT;
    }

    public boolean isPromotion() {
        return promotionPiece != Piece.NONE;
    }

    public boolean isCastling() {
        return flag == FLAG_CASTLE_KING || flag == FLAG_CASTLE_QUEEN;
    }

    /**
     * Nén nước đi thành số nguyên 32-bit:
     * [0..5]: from | [6..11]: to | [12..16]: piece | [17..21]: captured | [22..26]: promotion | [27..29]: flag
     */
    public static int pack(int from, int to, int piece, int captured, int promotion, int flag) {
        return (from & 0x3F)
                | ((to & 0x3F) << 6)
                | ((piece & 0x1F) << 12)
                | ((captured & 0x1F) << 17)
                | ((promotion & 0x1F) << 22)
                | ((flag & 0x7) << 27);
    }

    public int toPacked() {
        return pack(from, to, piece, capturedPiece, promotionPiece, flag);
    }

    public static ChessMove fromPacked(int packed) {
        return new ChessMove(
                getFrom(packed),
                getTo(packed),
                getPiece(packed),
                getCaptured(packed),
                getPromotion(packed),
                getFlag(packed)
        );
    }

    public static int getFrom(int packed) { return packed & 0x3F; }
    public static int getTo(int packed) { return (packed >> 6) & 0x3F; }
    public static int getPiece(int packed) { return (packed >> 12) & 0x1F; }
    public static int getCaptured(int packed) { return (packed >> 17) & 0x1F; }
    public static int getPromotion(int packed) { return (packed >> 22) & 0x1F; }
    public static int getFlag(int packed) { return (packed >> 27) & 0x7; }

    public String toUciString() {
        String base = squareToCoord(from) + squareToCoord(to);
        if (promotionPiece != Piece.NONE) {
            int type = Piece.getType(promotionPiece);
            if (type == Piece.QUEEN) base += "q";
            else if (type == Piece.ROOK) base += "r";
            else if (type == Piece.BISHOP) base += "b";
            else if (type == Piece.KNIGHT) base += "n";
        }
        return base;
    }

    public static String squareToCoord(int sq) {
        char file = (char) ('a' + (sq & 7));
        char rank = (char) ('8' - (sq >> 3));
        return "" + file + rank;
    }

    public static int coordToSquare(String coord) {
        if (coord == null || coord.length() < 2) return -1;
        int col = coord.charAt(0) - 'a';
        int row = '8' - coord.charAt(1);
        if (row < 0 || row > 7 || col < 0 || col > 7) return -1;
        return (row << 3) | col;
    }
}