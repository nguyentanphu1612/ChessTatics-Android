package com.example.chesstactics.engine;

/**
 * Định nghĩa mã quân cờ bằng Bitmask tối ưu cho tính toán:
 * - Bit 0..2: Loại quân (1..6)
 * - Bit 3: Quân Trắng (8)
 * - Bit 4: Quân Đen (16)
 */
public final class Piece {

    public static final int NONE = 0;
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    public static final int WHITE = 8;
    public static final int BLACK = 16;

    public static final int TYPE_MASK = 0b00111;
    public static final int COLOR_MASK = 0b11000;

    public static final int WHITE_PAWN = WHITE | PAWN;
    public static final int WHITE_KNIGHT = WHITE | KNIGHT;
    public static final int WHITE_BISHOP = WHITE | BISHOP;
    public static final int WHITE_ROOK = WHITE | ROOK;
    public static final int WHITE_QUEEN = WHITE | QUEEN;
    public static final int WHITE_KING = WHITE | KING;

    public static final int BLACK_PAWN = BLACK | PAWN;
    public static final int BLACK_KNIGHT = BLACK | KNIGHT;
    public static final int BLACK_BISHOP = BLACK | BISHOP;
    public static final int BLACK_ROOK = BLACK | ROOK;
    public static final int BLACK_QUEEN = BLACK | QUEEN;
    public static final int BLACK_KING = BLACK | KING;

    private Piece() {}

    public static int make(int color, int type) {
        return color | type;
    }

    public static int getType(int piece) {
        return piece & TYPE_MASK;
    }

    public static int getColor(int piece) {
        return piece & COLOR_MASK;
    }

    public static boolean isWhite(int piece) {
        return (piece & WHITE) != 0;
    }

    public static boolean isBlack(int piece) {
        return (piece & BLACK) != 0;
    }

    public static boolean isColor(int piece, int color) {
        return (piece & COLOR_MASK) == color;
    }

    public static int oppositeColor(int color) {
        return color ^ COLOR_MASK;
    }

    public static boolean isSlidingPiece(int piece) {
        int type = getType(piece);
        return type == BISHOP || type == ROOK || type == QUEEN;
    }

    public static char toFenChar(int piece) {
        char c;
        switch (getType(piece)) {
            case PAWN:   c = 'p'; break;
            case KNIGHT: c = 'n'; break;
            case BISHOP: c = 'b'; break;
            case ROOK:   c = 'r'; break;
            case QUEEN:  c = 'q'; break;
            case KING:   c = 'k'; break;
            default:     return ' ';
        }
        return isWhite(piece) ? Character.toUpperCase(c) : c;
    }

    public static int fromFenChar(char c) {
        int color = Character.isUpperCase(c) ? WHITE : BLACK;
        switch (Character.toLowerCase(c)) {
            case 'p': return color | PAWN;
            case 'n': return color | KNIGHT;
            case 'b': return color | BISHOP;
            case 'r': return color | ROOK;
            case 'q': return color | QUEEN;
            case 'k': return color | KING;
            default:  return NONE;
        }
    }
}