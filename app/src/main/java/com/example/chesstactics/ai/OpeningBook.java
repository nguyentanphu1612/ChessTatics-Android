package com.example.chesstactics.ai;

import com.example.chesstactics.engine.BoardState;
import com.example.chesstactics.engine.MoveGenerator;
import com.example.chesstactics.models.ChessMove;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sách khai cuộc chuẩn giải đấu quốc tế (Grandmaster Opening Repertoire):
 * - Tự động băm các biến thể khai cuộc bằng Zobrist Hash (hỗ trợ đảo thứ tự nước đi - Transpositions)
 * - Bao phủ: Ruy Lopez, Italian Game, Sicilian Najdorf/Dragon, French, Caro-Kann,
 *   Queen's Gambit Declined, Slav Defense, King's Indian Defense, English Opening.
 */
public final class OpeningBook {

    private static final Map<Long, List<String>> BOOK_MAP = new HashMap<>(512);
    private static final Random RNG = new Random();

    private static final String[][] LINES = {
            // Khai cuộc Cánh Vua (1. e4)
            {"e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1", "f8e7"}, // Ruy Lopez
            {"e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "c2c3", "g8f6", "d2d3", "d7d6"}, // Italian Giuoco Piano
            {"e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "d2d3", "f8e7", "e1g1", "e8g8"}, // Two Knights
            {"e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6"}, // Sicilian Najdorf
            {"e2e4", "c7c5", "g1f3", "b8c6", "d2d4", "c5d4", "f3d4", "g7g6", "b1c3", "f8g7"}, // Sicilian Dragon
            {"e2e4", "c7c5", "g1f3", "e7e6", "d2d4", "c5d4", "f3d4", "b8c6", "b1c3", "d8c7"}, // Sicilian Taimanov
            {"e2e4", "e7e6", "d2d4", "d7d5", "b1c3", "g8f6", "c1g5", "f8e7", "e4e5", "f6d7"}, // French Classical
            {"e2e4", "c7c6", "d2d4", "d7d5", "b1c3", "d5e4", "c3e4", "c8f5", "e4g3", "f5g6"}, // Caro-Kann Classical

            // Khai cuộc Cánh Hậu (1. d4)
            {"d2d4", "d7d5", "c2c4", "e7e6", "b1c3", "g8f6", "c1g5", "f8e7", "e2e3", "e8g8"}, // Queen's Gambit Declined
            {"d2d4", "d7d5", "c2c4", "c7c6", "g1f3", "g8f6", "b1c3", "d5c4", "a2a4", "c8f5"}, // Slav Defense
            {"d2d4", "g8f6", "c2c4", "g7g6", "b1c3", "f8g7", "e2e4", "d7d6", "g1f3", "e8g8"}, // King's Indian Defense
            {"d2d4", "g8f6", "c2c4", "e7e6", "b1c3", "f8b4", "e2e3", "e8g8", "f1d3", "d7d5"}, // Nimzo-Indian Defense
            {"d2d4", "g8f6", "c2c4", "e7e6", "g1f3", "b7b6", "g2g3", "c8b7", "f1g2", "f8e7"}, // Queen's Indian Defense

            // Khai cuộc Anh & Reti (1. c4 / 1. Nf3)
            {"c2c4", "e7e5", "b1c3", "g8f6", "g1f3", "b8c6", "g2g3", "d7d5", "c4d5", "f6d5"}, // English Four Knights
            {"g1f3", "d7d5", "d2d4", "g8f6", "c2c4", "e7e6", "g2g3", "f8e7", "f1g2", "e8g8"}  // Catalan Opening
    };

    static {
        for (String[] line : LINES) {
            BoardState sim = new BoardState();
            for (String uci : line) {
                long hash = sim.zobristHash;
                List<String> replies = BOOK_MAP.computeIfAbsent(hash, k -> new ArrayList<>());
                if (!replies.contains(uci)) {
                    replies.add(uci);
                }

                int move = findMoveByUci(sim, uci);
                if (move == 0) break;
                sim.makeMove(move);
            }
        }
    }

    private OpeningBook() {}

    /**
     * Tra cứu nước khai cuộc chuẩn cho thế cờ hiện tại. Trả về 0 nếu đã ra khỏi sách khai cuộc.
     */
    public static int getBookMove(BoardState state) {
        if (state.getHistoryPly() > 12) return 0;

        List<String> candidates = BOOK_MAP.get(state.zobristHash);
        if (candidates == null || candidates.isEmpty()) return 0;

        String chosenUci = candidates.get(RNG.nextInt(candidates.size()));
        return findMoveByUci(state, chosenUci);
    }

    private static int findMoveByUci(BoardState state, String uci) {
        int[] legalMoves = new int[256];
        int count = MoveGenerator.generateLegalMoves(state, legalMoves, false);
        for (int i = 0; i < count; i++) {
            ChessMove cm = ChessMove.fromPacked(legalMoves[i]);
            if (cm.toUciString().equals(uci)) {
                return legalMoves[i];
            }
        }
        return 0;
    }
}