package com.example.chesstactics.ai;

import java.util.Arrays;
import java.util.Random;

/**
 * Bảng băm Zobrist 64-bit, Transposition Table mảng nguyên thủy (Zero-GC)
 * và bảng sắp xếp nhánh Killer Heuristic / History Heuristic cho Cờ Vua.
 */
public class ChessZobristTT {

    public static final int MAX_PLY = 64;

    public static final byte FLAG_NONE = 0;
    public static final byte FLAG_EXACT = 1;
    public static final byte FLAG_LOWERBOUND = 2; // Beta cutoff
    public static final byte FLAG_UPPERBOUND = 3; // Alpha fail-low

    // 2^19 = 524,288 entries (~9MB RAM, cực tối ưu cho Android)
    private static final int TT_BITS = 19;
    private static final int TT_SIZE = 1 << TT_BITS;
    private static final int TT_MASK = TT_SIZE - 1;

    public static final long[][] PIECE_KEYS = new long[24][64];
    public static final long[] CASTLING_KEYS = new long[16];
    public static final long[] EP_FILE_KEYS = new long[9];
    public static final long BLACK_TO_MOVE_KEY;

    static {
        Random rng = new Random(20261120L);
        for (int p = 0; p < 24; p++) {
            for (int sq = 0; sq < 64; sq++) {
                PIECE_KEYS[p][sq] = rng.nextLong();
            }
        }
        for (int i = 0; i < 16; i++) {
            CASTLING_KEYS[i] = rng.nextLong();
        }
        for (int i = 0; i < 9; i++) {
            EP_FILE_KEYS[i] = rng.nextLong();
        }
        BLACK_TO_MOVE_KEY = rng.nextLong();
    }

    private final long[] ttHash = new long[TT_SIZE];
    private final int[] ttScore = new int[TT_SIZE];
    private final byte[] ttDepth = new byte[TT_SIZE];
    private final byte[] ttFlag = new byte[TT_SIZE];
    private final int[] ttBestMove = new int[TT_SIZE];

    // 2 nước sát thủ (Killer Moves) ở mỗi độ sâu ply
    private final int[] killer1 = new int[MAX_PLY];
    private final int[] killer2 = new int[MAX_PLY];

    // Lịch sử cắt tỉa [colorIndex (0=White, 1=Black)][fromSq][toSq]
    private final int[][][] history = new int[2][64][64];

    public ChessZobristTT() {
        clearHeuristics();
    }

    public void clearAll() {
        Arrays.fill(ttFlag, FLAG_NONE);
        clearHeuristics();
    }

    public void clearHeuristics() {
        Arrays.fill(killer1, 0);
        Arrays.fill(killer2, 0);
        for (int c = 0; c < 2; c++) {
            for (int sq = 0; sq < 64; sq++) {
                Arrays.fill(history[c][sq], 0);
            }
        }
    }

    public int probeIndex(long hash) {
        int idx = (int) (hash & TT_MASK);
        if (ttFlag[idx] != FLAG_NONE && ttHash[idx] == hash) {
            return idx;
        }
        return -1;
    }

    public byte getFlag(int idx) { return ttFlag[idx]; }
    public int getDepth(int idx) { return ttDepth[idx]; }
    public int getScore(int idx) { return ttScore[idx]; }
    public int getBestMove(int idx) { return ttBestMove[idx]; }

    public void store(long hash, int depth, int score, byte flag, int bestMove) {
        int idx = (int) (hash & TT_MASK);
        if (ttFlag[idx] == FLAG_NONE || ttHash[idx] != hash || depth >= ttDepth[idx]) {
            ttHash[idx] = hash;
            ttScore[idx] = score;
            ttDepth[idx] = (byte) depth;
            ttFlag[idx] = flag;
            ttBestMove[idx] = bestMove;
        }
    }

    public void recordKiller(int ply, int move) {
        if (ply < 0 || ply >= MAX_PLY) return;
        if (killer1[ply] != move) {
            killer2[ply] = killer1[ply];
            killer1[ply] = move;
        }
    }

    public int getKillerBonus(int ply, int move) {
        if (ply < 0 || ply >= MAX_PLY) return 0;
        if (killer1[ply] == move) return 90_000;
        if (killer2[ply] == move) return 80_000;
        return 0;
    }

    public void recordHistory(int sideColor, int from, int to, int depth) {
        int colorIdx = (sideColor == 8) ? 0 : 1;
        history[colorIdx][from][to] += depth * depth;
        if (history[colorIdx][from][to] > 70_000) {
            history[colorIdx][from][to] = 70_000;
        }
    }

    public int getHistoryScore(int sideColor, int from, int to) {
        int colorIdx = (sideColor == 8) ? 0 : 1;
        return history[colorIdx][from][to];
    }
}