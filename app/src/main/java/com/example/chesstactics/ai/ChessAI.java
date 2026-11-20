package com.example.chesstactics.ai;

import com.example.chesstactics.engine.BoardState;
import com.example.chesstactics.engine.MoveGenerator;
import com.example.chesstactics.engine.NotationConverter;
import com.example.chesstactics.engine.Piece;
import com.example.chesstactics.models.ChessMove;

import java.util.Random;

/**
 * Động cơ Trí tuệ nhân tạo Cờ Vua Grandmaster Engine:
 * - Cấp độ 1 (Dễ): Tìm kiếm độ sâu 2 + Độ nhiễu nhẹ phù hợp người mới chơi.
 * - Cấp độ 2 (Khó): Opening Book + PVS độ sâu 5 + Quiescence Search.
 * - Cấp độ 3 (Cực hạn - Siêu trí tuệ):
 *     1. Grandmaster Opening Book (Zobrist Transposition)
 *     2. Iterative Deepening (Tăng dần độ sâu tới 12+ plies trong ngân sách thời gian)
 *     3. Principal Variation Search (PVS) + Aspiration Windows
 *     4. Null-Move Pruning (NMP) + Late Move Reductions (LMR) + Check Extensions
 *     5. Quiescence Search (Chống hiệu ứng chân trời bằng tìm kiếm chuỗi ăn quân/phong cấp)
 *     6. MVV-LVA + Transposition Table + Killer Moves + History Heuristic
 */
public class ChessAI {

    private static final int INF = 100_000;
    private static final int MATE_SCORE = 49_000;
    private static final int MATE_THRESHOLD = 48_000;

    private static final long BUDGET_EASY_MS = 300L;
    private static final long BUDGET_HARD_MS = 1_100L;
    private static final long BUDGET_EXTREME_MS = 2_800L;

    private static final Random RNG = new Random();

    private final BoardState state;
    private final int difficulty;
    private final long deadlineNanos;
    private final ChessZobristTT tt;

    private boolean searchAborted = false;
    private int nodesSearched = 0;

    private ChessAI(BoardState sourceState, int difficulty, long timeBudgetMs) {
        this.state = sourceState.copy();
        this.difficulty = difficulty;
        this.deadlineNanos = System.nanoTime() + timeBudgetMs * 1_000_000L;
        this.tt = new ChessZobristTT();
    }

    /**
     * Hàm chính gọi từ GameActivity để AI tính toán nước đi tối ưu nhất.
     */
    public static ChessMove findBestMove(BoardState currentState, int difficulty) {
        long budget = (difficulty == 1) ? BUDGET_EASY_MS :
                (difficulty == 2) ? BUDGET_HARD_MS : BUDGET_EXTREME_MS;
        return new ChessAI(currentState, difficulty, budget).search();
    }

    /**
     * Gợi ý nước đi chuẩn Cực hạn cho người chơi (tính năng Nhắc đường đi).
     */
    public static ChessMove getHintMove(BoardState currentState) {
        return new ChessAI(currentState, 3, 1_500L).search();
    }

    private ChessMove search() {
        int[] rootMoves = new int[256];
        int moveCount = MoveGenerator.generateLegalMoves(state, rootMoves, false);

        if (moveCount == 0) return null;
        if (moveCount == 1) {
            return buildAnnotatedMove(rootMoves[0]);
        }

        // 1. Tra cứu Sách Khai Cuộc (Opening Book) cho cấp độ Khó & Cực hạn
        if (difficulty >= 2) {
            int bookMove = OpeningBook.getBookMove(state);
            if (bookMove != 0) {
                return buildAnnotatedMove(bookMove);
            }
        }

        // Sắp xếp sơ bộ các nước đi ở gốc
        int[] moveScores = new int[moveCount];
        scoreMoves(rootMoves, moveScores, moveCount, 0, 0);
        sortMoves(rootMoves, moveScores, moveCount);

        // Chế độ Dễ: Chọn ngẫu nhiên trong top 3 nước đi ở độ sâu 2
        if (difficulty == 1) {
            int bestEasyMove = rootMoves[0];
            int[] topScores = new int[moveCount];
            for (int i = 0; i < moveCount; i++) {
                state.makeMove(rootMoves[i]);
                topScores[i] = -quiescence(-INF, INF);
                state.unmakeMove(rootMoves[i]);
            }
            sortMoves(rootMoves, topScores, moveCount);
            int pool = Math.min(3, moveCount);
            bestEasyMove = rootMoves[RNG.nextInt(pool)];
            return buildAnnotatedMove(bestEasyMove);
        }

        int maxTargetDepth = (difficulty == 2) ? 5 : 12;
        int bestOverallMove = rootMoves[0];
        int bestOverallScore = -INF;

        // 2. Vòng lặp tăng dần độ sâu (Iterative Deepening)
        for (int currentDepth = 1; currentDepth <= maxTargetDepth; currentDepth++) {
            if (isTimeUp()) break;

            searchAborted = false;
            int alpha = -INF;
            int beta = INF;

            // Sử dụng cửa sổ kỳ vọng (Aspiration Windows) từ độ sâu 4 trở lên
            if (currentDepth >= 4 && Math.abs(bestOverallScore) < MATE_THRESHOLD) {
                alpha = bestOverallScore - 35;
                beta = bestOverallScore + 35;
            }

            int iterBestMove = 0;
            int iterBestScore = -INF;

            while (true) {
                int localAlpha = alpha;
                iterBestScore = -INF;
                iterBestMove = 0;

                for (int i = 0; i < moveCount; i++) {
                    int m = rootMoves[i];
                    state.makeMove(m);

                    int score;
                    if (i == 0) {
                        score = -pvs(currentDepth - 1, -beta, -localAlpha, 1, true);
                    } else {
                        // Null-Window Search cho các nhánh phụ
                        score = -pvs(currentDepth - 1, -localAlpha - 1, -localAlpha, 1, true);
                        if (!searchAborted && score > localAlpha && score < beta) {
                            score = -pvs(currentDepth - 1, -beta, -localAlpha, 1, true);
                        }
                    }

                    state.unmakeMove(m);
                    if (searchAborted) break;

                    moveScores[i] = score;
                    if (score > iterBestScore) {
                        iterBestScore = score;
                        iterBestMove = m;
                    }
                    if (score > localAlpha) {
                        localAlpha = score;
                    }
                }

                if (searchAborted) break;

                // Nếu kết quả vượt ngoài Aspiration Window -> Mở rộng cửa sổ tìm lại tầng này
                if (iterBestScore <= alpha) {
                    alpha = -INF;
                } else if (iterBestScore >= beta) {
                    beta = INF;
                } else {
                    break;
                }
            }

            if (!searchAborted && iterBestMove != 0) {
                bestOverallMove = iterBestMove;
                bestOverallScore = iterBestScore;
                sortMoves(rootMoves, moveScores, moveCount);

                // Dừng sớm nếu đã tìm thấy đường chiếu bí chắc chắn
                if (bestOverallScore >= MATE_THRESHOLD) {
                    break;
                }
            } else {
                break;
            }
        }

        return buildAnnotatedMove(bestOverallMove);
    }

    /**
     * Thuật toán Principal Variation Search (PVS) kết hợp:
     * - Transposition Table
     * - Check Extensions
     * - Null-Move Pruning
     * - Late Move Reductions (LMR)
     */
    private int pvs(int depth, int alpha, int beta, int ply, boolean allowNullMove) {
        nodesSearched++;
        if ((nodesSearched & 2047) == 0 && isTimeUp()) {
            searchAborted = true;
            return 0;
        }

        // Phát hiện hòa lặp 3 lần hoặc luật 50 nước ngay trong cây tìm kiếm
        if (ply > 0 && (state.halfMoveClock >= 100 || state.getRepetitionCount() >= 2)) {
            return 0;
        }

        boolean inCheck = MoveGenerator.isKingInCheck(state, state.sideToMove);

        // Check Extension: Kéo dài độ sâu +1 khi Vua đang bị chiếu để tính hết đòn phối hợp
        if (inCheck && ply < ChessZobristTT.MAX_PLY - 2) {
            depth++;
        }

        // Chuyển sang Quiescence Search tại nút lá để triệt tiêu hiệu ứng chân trời
        if (depth <= 0) {
            return quiescence(alpha, beta);
        }

        int origAlpha = alpha;
        int ttBestMove = 0;

        // 1. Tra cứu Transposition Table
        int ttIdx = tt.probeIndex(state.zobristHash);
        if (ttIdx != -1) {
            ttBestMove = tt.getBestMove(ttIdx);
            if (tt.getDepth(ttIdx) >= depth && ply > 0) {
                int ttVal = tt.getScore(ttIdx);
                if (ttVal > MATE_THRESHOLD) ttVal -= ply;
                else if (ttVal < -MATE_THRESHOLD) ttVal += ply;

                byte flag = tt.getFlag(ttIdx);
                if (flag == ChessZobristTT.FLAG_EXACT) return ttVal;
                if (flag == ChessZobristTT.FLAG_LOWERBOUND && ttVal > alpha) alpha = ttVal;
                else if (flag == ChessZobristTT.FLAG_UPPERBOUND && ttVal < beta) beta = ttVal;

                if (alpha >= beta) return ttVal;
            }
        }

        // 2. Null-Move Pruning (Cắt tỉa nước rỗng ở chế độ Cực hạn)
        if (difficulty == 3 && allowNullMove && !inCheck && depth >= 3 &&
                BoardEvaluator.hasNonPawnMaterial(state, state.sideToMove)) {
            int R = (depth >= 6) ? 3 : 2;
            state.makeNullMove();
            int nullScore = -pvs(depth - 1 - R, -beta, -beta + 1, ply + 1, false);
            state.unmakeNullMove();

            if (searchAborted) return 0;
            if (nullScore >= beta) {
                return beta;
            }
        }

        // 3. Sinh & sắp xếp toàn bộ nước đi hợp lệ
        int[] moves = new int[256];
        int count = MoveGenerator.generateLegalMoves(state, moves, false);
        if (count == 0) {
            if (inCheck) {
                return -MATE_SCORE + ply; // Bị chiếu bí (ưu tiên trì hoãn lâu nhất)
            }
            return 0; // Hòa Pat (Stalemate)
        }

        int[] scores = new int[count];
        scoreMoves(moves, scores, count, ply, ttBestMove);
        sortMoves(moves, scores, count);

        int bestScore = -INF;
        int bestMove = moves[0];

        for (int i = 0; i < count; i++) {
            int m = moves[i];
            boolean isTactical = (ChessMove.getCaptured(m) != Piece.NONE) ||
                    (ChessMove.getPromotion(m) != Piece.NONE);

            state.makeMove(m);

            int score;
            if (i == 0) {
                score = -pvs(depth - 1, -beta, -alpha, ply + 1, true);
            } else {
                // Late Move Reductions (LMR): Giảm độ sâu tìm kiếm cho các nước yên tĩnh xếp cuối
                int reduction = 0;
                if (difficulty == 3 && depth >= 3 && i >= 4 && !inCheck && !isTactical) {
                    reduction = (i >= 10 && depth >= 5) ? 2 : 1;
                }

                score = -pvs(depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, true);

                // Nếu nhánh rút gọn bất ngờ vượt alpha -> Tìm lại ở độ sâu đầy đủ
                if (!searchAborted && reduction > 0 && score > alpha) {
                    score = -pvs(depth - 1, -alpha - 1, -alpha, ply + 1, true);
                }

                // Nếu vượt alpha trong cửa sổ hẹp -> Tìm lại với cửa sổ [alpha, beta] đầy đủ
                if (!searchAborted && score > alpha && score < beta) {
                    score = -pvs(depth - 1, -beta, -alpha, ply + 1, true);
                }
            }

            state.unmakeMove(m);
            if (searchAborted) return 0;

            if (score > bestScore) {
                bestScore = score;
                bestMove = m;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                // Ghi nhận Killer & History cho các nước yên tĩnh gây cắt tỉa Beta
                if (!isTactical) {
                    tt.recordKiller(ply, m);
                    tt.recordHistory(state.sideToMove, ChessMove.getFrom(m), ChessMove.getTo(m), depth);
                }
                break;
            }
        }

        // 4. Lưu kết quả vào Transposition Table
        byte storeFlag = (bestScore <= origAlpha) ? ChessZobristTT.FLAG_UPPERBOUND :
                (bestScore >= beta) ? ChessZobristTT.FLAG_LOWERBOUND : ChessZobristTT.FLAG_EXACT;

        int storeVal = bestScore;
        if (storeVal > MATE_THRESHOLD) storeVal += ply;
        else if (storeVal < -MATE_THRESHOLD) storeVal -= ply;

        tt.store(state.zobristHash, depth, storeVal, storeFlag, bestMove);
        return bestScore;
    }

    /**
     * Quiescence Search: Duyệt sâu chuỗi ăn quân & phong cấp cho tới khi bàn cờ ổn định,
     * giúp AI không bao giờ mắc bẫy trao đổi quân ngắn hạn.
     */
    private int quiescence(int alpha, int beta) {
        nodesSearched++;
        if ((nodesSearched & 2047) == 0 && isTimeUp()) {
            searchAborted = true;
            return 0;
        }

        int standPat = BoardEvaluator.evaluate(state);
        if (standPat >= beta) {
            return beta;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }

        int[] captureMoves = new int[128];
        int count = MoveGenerator.generateLegalMoves(state, captureMoves, true);
        if (count == 0) {
            return standPat;
        }

        int[] scores = new int[count];
        for (int i = 0; i < count; i++) {
            scores[i] = scoreCaptureMvvLva(captureMoves[i]);
        }
        sortMoves(captureMoves, scores, count);

        for (int i = 0; i < count; i++) {
            int m = captureMoves[i];

            // Delta Pruning: Bỏ qua nước ăn quân nếu ngay cả khi ăn xong vẫn kém alpha quá xa
            int capturedVal = BoardEvaluator.PIECE_VALUES[Piece.getType(ChessMove.getCaptured(m))];
            int promoVal = (ChessMove.getPromotion(m) != Piece.NONE) ? 800 : 0;
            if (standPat + capturedVal + promoVal + 200 < alpha) {
                continue;
            }

            state.makeMove(m);
            int score = -quiescence(-beta, -alpha);
            state.unmakeMove(m);

            if (searchAborted) return 0;

            if (score >= beta) {
                return beta;
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return alpha;
    }

    /**
     * Sắp xếp thứ tự duyệt nước đi (Move Ordering):
     * 1. Nước tốt nhất từ Transposition Table (1,000,000)
     * 2. Nước ăn quân theo MVV-LVA (100,000 + Victim*10 - Attacker)
     * 3. Nước phong cấp Hậu (150,000)
     * 4. Killer Moves (90,000 / 80,000)
     * 5. History Heuristic (0..70,000)
     */
    private void scoreMoves(int[] moves, int[] scores, int count, int ply, int ttMove) {
        for (int i = 0; i < count; i++) {
            int m = moves[i];
            if (m == ttMove) {
                scores[i] = 1_000_000;
                continue;
            }

            int score = 0;
            int captured = ChessMove.getCaptured(m);
            int promo = ChessMove.getPromotion(m);

            if (captured != Piece.NONE) {
                score += 100_000 + scoreCaptureMvvLva(m);
            }
            if (promo != Piece.NONE) {
                score += (Piece.getType(promo) == Piece.QUEEN) ? 150_000 : 110_000;
            }
            if (score == 0) {
                score += tt.getKillerBonus(ply, m);
                score += tt.getHistoryScore(state.sideToMove, ChessMove.getFrom(m), ChessMove.getTo(m));
            }
            scores[i] = score;
        }
    }

    private int scoreCaptureMvvLva(int move) {
        int victim = Piece.getType(ChessMove.getCaptured(move));
        int attacker = Piece.getType(ChessMove.getPiece(move));
        return BoardEvaluator.PIECE_VALUES[victim] * 10 - BoardEvaluator.PIECE_VALUES[attacker];
    }

    private void sortMoves(int[] moves, int[] scores, int count) {
        for (int i = 1; i < count; i++) {
            int keyMove = moves[i];
            int keyScore = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < keyScore) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = keyMove;
            scores[j + 1] = keyScore;
        }
    }

    private ChessMove buildAnnotatedMove(int packedMove) {
        ChessMove cm = ChessMove.fromPacked(packedMove);
        cm.sanNotation = NotationConverter.moveToSan(state, packedMove);
        return cm;
    }

    private boolean isTimeUp() {
        return System.nanoTime() > deadlineNanos || Thread.currentThread().isInterrupted();
    }
}