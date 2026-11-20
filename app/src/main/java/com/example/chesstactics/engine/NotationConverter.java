package com.example.chesstactics.engine;

import com.example.chesstactics.models.ChessMove;

import java.util.Arrays;

/**
 * Chuyển đổi qua lại giữa bàn cờ với chuỗi FEN và biên bản đại số chuẩn quốc tế SAN
 * (vd: Nf3, O-O, exd8=Q+, R1e2#).
 */
public final class NotationConverter {

    public static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private NotationConverter() {}

    /**
     * Nạp thế cờ từ chuỗi FEN chuẩn vào BoardState.
     */
    public static void loadFen(BoardState state, String fen) {
        Arrays.fill(state.board, Piece.NONE);
        state.castlingRights = 0;
        state.enPassantSquare = -1;
        state.halfMoveClock = 0;
        state.fullMoveNumber = 1;

        String[] parts = fen.trim().split("\\s+");
        String[] rows = parts[0].split("/");

        for (int r = 0; r < 8 && r < rows.length; r++) {
            int c = 0;
            for (int i = 0; i < rows[r].length(); i++) {
                char ch = rows[r].charAt(i);
                if (Character.isDigit(ch)) {
                    c += ch - '0';
                } else {
                    int piece = Piece.fromFenChar(ch);
                    int sq = (r << 3) | c;
                    state.board[sq] = piece;
                    if (piece == Piece.WHITE_KING) state.whiteKingSq = sq;
                    else if (piece == Piece.BLACK_KING) state.blackKingSq = sq;
                    c++;
                }
            }
        }

        if (parts.length > 1) {
            state.sideToMove = parts[1].equals("b") ? Piece.BLACK : Piece.WHITE;
        }
        if (parts.length > 2 && !parts[2].equals("-")) {
            for (int i = 0; i < parts[2].length(); i++) {
                char ch = parts[2].charAt(i);
                if (ch == 'K') state.castlingRights |= BoardState.CASTLE_WK;
                else if (ch == 'Q') state.castlingRights |= BoardState.CASTLE_WQ;
                else if (ch == 'k') state.castlingRights |= BoardState.CASTLE_BK;
                else if (ch == 'q') state.castlingRights |= BoardState.CASTLE_BQ;
            }
        }
        if (parts.length > 3 && !parts[3].equals("-")) {
            state.enPassantSquare = ChessMove.coordToSquare(parts[3]);
        }
        if (parts.length > 4) {
            state.halfMoveClock = Integer.parseInt(parts[4]);
        }
        if (parts.length > 5) {
            state.fullMoveNumber = Integer.parseInt(parts[5]);
        }

        state.recomputeZobristHash();
    }

    /**
     * Xuất trạng thái bàn cờ hiện tại thành chuỗi FEN.
     */
    public static String toFen(BoardState state) {
        StringBuilder sb = new StringBuilder(90);
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                int p = state.board[(r << 3) | c];
                if (p == Piece.NONE) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(Piece.toFenChar(p));
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append('/');
        }

        sb.append(state.sideToMove == Piece.WHITE ? " w " : " b ");

        if (state.castlingRights == 0) {
            sb.append('-');
        } else {
            if ((state.castlingRights & BoardState.CASTLE_WK) != 0) sb.append('K');
            if ((state.castlingRights & BoardState.CASTLE_WQ) != 0) sb.append('Q');
            if ((state.castlingRights & BoardState.CASTLE_BK) != 0) sb.append('k');
            if ((state.castlingRights & BoardState.CASTLE_BQ) != 0) sb.append('q');
        }

        sb.append(' ');
        sb.append(state.enPassantSquare == -1 ? "-" : ChessMove.squareToCoord(state.enPassantSquare));
        sb.append(' ').append(state.halfMoveClock);
        sb.append(' ').append(state.fullMoveNumber);
        return sb.toString();
    }

    /**
     * Chuyển đổi nước đi sang ký hiệu SAN quốc tế (gọi TRƯỚC khi thực hiện state.makeMove).
     */
    public static String moveToSan(BoardState state, int move) {
        int from = ChessMove.getFrom(move);
        int to = ChessMove.getTo(move);
        int piece = ChessMove.getPiece(move);
        int captured = ChessMove.getCaptured(move);
        int promotion = ChessMove.getPromotion(move);
        int flag = ChessMove.getFlag(move);
        int type = Piece.getType(piece);

        StringBuilder san = new StringBuilder();

        if (flag == ChessMove.FLAG_CASTLE_KING) {
            san.append("O-O");
        } else if (flag == ChessMove.FLAG_CASTLE_QUEEN) {
            san.append("O-O-O");
        } else {
            boolean isCapture = (captured != Piece.NONE || flag == ChessMove.FLAG_EN_PASSANT);

            if (type == Piece.PAWN) {
                if (isCapture) {
                    san.append((char) ('a' + (from & 7)));
                }
            } else {
                san.append(Character.toUpperCase(Piece.toFenChar(piece)));

                // Kiểm tra trùng lặp nếu 2 quân cùng loại có thể đi tới cùng 1 ô
                int[] legalMoves = new int[256];
                int count = MoveGenerator.generateLegalMoves(state, legalMoves, false);
                boolean sameFile = false;
                boolean sameRank = false;
                boolean ambiguity = false;

                for (int i = 0; i < count; i++) {
                    int other = legalMoves[i];
                    if (other != move && ChessMove.getPiece(other) == piece && ChessMove.getTo(other) == to) {
                        ambiguity = true;
                        int otherFrom = ChessMove.getFrom(other);
                        if ((otherFrom & 7) == (from & 7)) sameFile = true;
                        if ((otherFrom >> 3) == (from >> 3)) sameRank = true;
                    }
                }

                if (ambiguity) {
                    if (!sameFile) {
                        san.append((char) ('a' + (from & 7)));
                    } else if (!sameRank) {
                        san.append((char) ('8' - (from >> 3)));
                    } else {
                        san.append(ChessMove.squareToCoord(from));
                    }
                }
            }

            if (isCapture) {
                san.append('x');
            }
            san.append(ChessMove.squareToCoord(to));

            if (promotion != Piece.NONE) {
                san.append('=').append(Character.toUpperCase(Piece.toFenChar(promotion)));
            }
        }

        // Kiểm tra nước đi có gây Chiếu (+) hoặc Chiếu bí (#) không
        state.makeMove(move);
        if (MoveGenerator.isKingInCheck(state, state.sideToMove)) {
            int[] replies = new int[256];
            int replyCount = MoveGenerator.generateLegalMoves(state, replies, false);
            san.append(replyCount == 0 ? '#' : '+');
        }
        state.unmakeMove(move);

        return san.toString();
    }
}