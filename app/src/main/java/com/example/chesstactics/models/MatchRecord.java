package com.example.chesstactics.models;

public class MatchRecord {
    public long id;
    public String mode;         // AI_EASY, AI_HARD, AI_EXTREME, PVP_LOCAL, ONLINE
    public String result;       // WIN, LOSS, DRAW
    public String reason;       // CHECKMATE, STALEMATE, TIMEOUT, RESIGN, etc.
    public String movesUci;     // Chuỗi nước đi UCI cách nhau bởi dấu cách: "e2e4 e7e5 ..."
    public String movesSan;     // Chuỗi biên bản SAN: "e4 e5 Nf3 ..."
    public int playerColor;     // Piece.WHITE (8) hoặc Piece.BLACK (16)
    public int totalMoves;
    public long timestamp;

    public MatchRecord(long id, String mode, String result, String reason,
                       String movesUci, String movesSan, int playerColor,
                       int totalMoves, long timestamp) {
        this.id = id;
        this.mode = mode;
        this.result = result;
        this.reason = reason;
        this.movesUci = movesUci;
        this.movesSan = movesSan;
        this.playerColor = playerColor;
        this.totalMoves = totalMoves;
        this.timestamp = timestamp;
    }
}