package com.example.chesstactics.online;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.chesstactics.engine.Piece;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class FirebaseChessManager {

    private static final String DATABASE_URL =
            "https://chesstactics-dc466-default-rtdb.asia-southeast1.firebasedatabase.app";

    private static final long NETWORK_TIMEOUT_MS = 10_000L;

    public interface RoomCallback {
        void onSuccess(String roomCode, int assignedColor, int timeLimitMinutes);
        void onError(String message);
    }

    public interface RoomEventListener {
        void onOpponentJoined();
        void onRemoteMoveReceived(String moveUci, int moveCount, long whiteTimeMs, long blackTimeMs);
        void onRemoteGameEnded(String winnerSide, String reason);
    }

    private final DatabaseReference roomsRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private DatabaseReference activeRoomRef;
    private ValueEventListener activeRoomListener;

    public FirebaseChessManager() {
        FirebaseDatabase database = FirebaseDatabase.getInstance(DATABASE_URL);
        this.roomsRef = database.getReference("rooms");
    }

    public void createRoom(int timeLimitMinutes, RoomCallback callback) {
        final boolean[] isCompleted = {false};

        Runnable timeoutTask = () -> {
            if (!isCompleted[0]) {
                isCompleted[0] = true;
                callback.onError("NETWORK_TIMEOUT");
            }
        };
        mainHandler.postDelayed(timeoutTask, NETWORK_TIMEOUT_MS);

        tryCreateUniqueRoom(timeLimitMinutes, 0, isCompleted, timeoutTask, callback);
    }

    private void tryCreateUniqueRoom(int timeLimitMinutes, int attempt,
                                     boolean[] isCompleted, Runnable timeoutTask, RoomCallback callback) {
        if (isCompleted[0]) return;
        if (attempt >= 5) {
            isCompleted[0] = true;
            mainHandler.removeCallbacks(timeoutTask);
            callback.onError("CREATE_ROOM_FAILED");
            return;
        }

        String roomCode = String.format(Locale.US, "%06d", random.nextInt(1_000_000));
        DatabaseReference roomRef = roomsRef.child(roomCode);

        roomRef.get().addOnCompleteListener(task -> {
            if (isCompleted[0]) return;

            if (!task.isSuccessful()) {
                isCompleted[0] = true;
                mainHandler.removeCallbacks(timeoutTask);
                String err = (task.getException() != null && task.getException().getMessage() != null)
                        ? task.getException().getMessage() : "FIREBASE_ERROR";
                callback.onError(err);
                return;
            }

            DataSnapshot snapshot = task.getResult();
            if (snapshot != null && snapshot.exists()) {
                tryCreateUniqueRoom(timeLimitMinutes, attempt + 1, isCompleted, timeoutTask, callback);
                return;
            }

            long initialTimeMs = (timeLimitMinutes > 0) ? timeLimitMinutes * 60_000L : -1L;

            Map<String, Object> roomData = new HashMap<>();
            roomData.put("roomCode", roomCode);
            roomData.put("status", "WAITING");
            roomData.put("timeMinutes", timeLimitMinutes);
            roomData.put("whiteJoined", true);
            roomData.put("blackJoined", false);
            roomData.put("moveCount", 0);
            roomData.put("lastMoveUci", "");
            roomData.put("lastMoveBy", 0);
            roomData.put("whiteTimeMs", initialTimeMs);
            roomData.put("blackTimeMs", initialTimeMs);
            roomData.put("winner", "");
            roomData.put("endReason", "");
            roomData.put("createdAt", ServerValue.TIMESTAMP);

            roomRef.setValue(roomData)
                    .addOnSuccessListener(unused -> {
                        if (isCompleted[0]) return;
                        isCompleted[0] = true;
                        mainHandler.removeCallbacks(timeoutTask);
                        callback.onSuccess(roomCode, Piece.WHITE, timeLimitMinutes);
                    })
                    .addOnFailureListener(e -> {
                        if (isCompleted[0]) return;
                        isCompleted[0] = true;
                        mainHandler.removeCallbacks(timeoutTask);
                        callback.onError(e.getMessage() != null ? e.getMessage() : "WRITE_FAILED");
                    });
        });
    }

    public void joinRoom(String roomCode, RoomCallback callback) {
        final boolean[] isCompleted = {false};

        Runnable timeoutTask = () -> {
            if (!isCompleted[0]) {
                isCompleted[0] = true;
                callback.onError("NETWORK_TIMEOUT");
            }
        };
        mainHandler.postDelayed(timeoutTask, NETWORK_TIMEOUT_MS);

        DatabaseReference roomRef = roomsRef.child(roomCode);
        roomRef.get().addOnCompleteListener(task -> {
            if (isCompleted[0]) return;

            if (!task.isSuccessful()) {
                isCompleted[0] = true;
                mainHandler.removeCallbacks(timeoutTask);
                String err = (task.getException() != null && task.getException().getMessage() != null)
                        ? task.getException().getMessage() : "FIREBASE_ERROR";
                callback.onError(err);
                return;
            }

            DataSnapshot snapshot = task.getResult();
            if (snapshot == null || !snapshot.exists()) {
                isCompleted[0] = true;
                mainHandler.removeCallbacks(timeoutTask);
                callback.onError("ROOM_NOT_FOUND");
                return;
            }

            String status = snapshot.child("status").getValue(String.class);
            Boolean blackJoined = snapshot.child("blackJoined").getValue(Boolean.class);
            if (!"WAITING".equals(status) || Boolean.TRUE.equals(blackJoined)) {
                isCompleted[0] = true;
                mainHandler.removeCallbacks(timeoutTask);
                callback.onError("ROOM_FULL");
                return;
            }

            Long timeMinutesLong = snapshot.child("timeMinutes").getValue(Long.class);
            int timeMinutes = (timeMinutesLong != null) ? timeMinutesLong.intValue() : 10;

            Map<String, Object> updates = new HashMap<>();
            updates.put("blackJoined", true);
            updates.put("status", "PLAYING");

            roomRef.updateChildren(updates)
                    .addOnSuccessListener(unused -> {
                        if (isCompleted[0]) return;
                        isCompleted[0] = true;
                        mainHandler.removeCallbacks(timeoutTask);
                        callback.onSuccess(roomCode, Piece.BLACK, timeMinutes);
                    })
                    .addOnFailureListener(e -> {
                        if (isCompleted[0]) return;
                        isCompleted[0] = true;
                        mainHandler.removeCallbacks(timeoutTask);
                        callback.onError(e.getMessage() != null ? e.getMessage() : "JOIN_FAILED");
                    });
        });
    }

    public void listenToRoom(String roomCode, int myColor, RoomEventListener listener) {
        detachListener();
        activeRoomRef = roomsRef.child(roomCode);

        Map<String, Object> disconnectUpdate = new HashMap<>();
        disconnectUpdate.put("status", "ENDED");
        disconnectUpdate.put("winner", myColor == Piece.WHITE ? "BLACK" : "WHITE");
        disconnectUpdate.put("endReason", "DISCONNECT");
        activeRoomRef.onDisconnect().updateChildren(disconnectUpdate);

        activeRoomListener = new ValueEventListener() {
            private boolean opponentJoinedNotified = false;
            private int lastProcessedMoveCount = 0;
            private boolean gameEndedNotified = false;

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Boolean blackJoined = snapshot.child("blackJoined").getValue(Boolean.class);
                if (Boolean.TRUE.equals(blackJoined) && !opponentJoinedNotified) {
                    opponentJoinedNotified = true;
                    if (myColor == Piece.WHITE) {
                        listener.onOpponentJoined();
                    }
                }

                Long moveCountLong = snapshot.child("moveCount").getValue(Long.class);
                int moveCount = (moveCountLong != null) ? moveCountLong.intValue() : 0;

                if (moveCount > lastProcessedMoveCount) {
                    lastProcessedMoveCount = moveCount;
                    String lastMoveUci = snapshot.child("lastMoveUci").getValue(String.class);
                    Long lastMoveByLong = snapshot.child("lastMoveBy").getValue(Long.class);
                    int lastMoveBy = (lastMoveByLong != null) ? lastMoveByLong.intValue() : 0;

                    Long wTimeLong = snapshot.child("whiteTimeMs").getValue(Long.class);
                    Long bTimeLong = snapshot.child("blackTimeMs").getValue(Long.class);
                    long wTime = (wTimeLong != null) ? wTimeLong : -1L;
                    long bTime = (bTimeLong != null) ? bTimeLong : -1L;

                    if (!TextUtils.isEmpty(lastMoveUci) && lastMoveBy != myColor) {
                        listener.onRemoteMoveReceived(lastMoveUci, moveCount, wTime, bTime);
                    }
                }

                String status = snapshot.child("status").getValue(String.class);
                if ("ENDED".equals(status) && !gameEndedNotified) {
                    gameEndedNotified = true;
                    String winner = snapshot.child("winner").getValue(String.class);
                    String reason = snapshot.child("endReason").getValue(String.class);
                    listener.onRemoteGameEnded(
                            winner != null ? winner : "DRAW",
                            reason != null ? reason : "RESIGN"
                    );
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };

        activeRoomRef.addValueEventListener(activeRoomListener);
    }

    public void sendMove(String roomCode, String moveUci, int moveCount,
                         long whiteTimeMs, long blackTimeMs) {
        if (TextUtils.isEmpty(roomCode)) return;
        int sideJustMoved = (moveCount % 2 == 1) ? Piece.WHITE : Piece.BLACK;

        Map<String, Object> updates = new HashMap<>();
        updates.put("moveCount", moveCount);
        updates.put("lastMoveUci", moveUci);
        updates.put("lastMoveBy", sideJustMoved);
        updates.put("whiteTimeMs", whiteTimeMs);
        updates.put("blackTimeMs", blackTimeMs);

        roomsRef.child(roomCode).updateChildren(updates);
    }

    public void endRoomGame(String roomCode, String winnerSide, String reason) {
        if (TextUtils.isEmpty(roomCode)) return;
        DatabaseReference roomRef = roomsRef.child(roomCode);
        roomRef.onDisconnect().cancel();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "ENDED");
        updates.put("winner", winnerSide);
        updates.put("endReason", reason);

        roomRef.updateChildren(updates);
    }

    public void detachListener() {
        if (activeRoomRef != null) {
            activeRoomRef.onDisconnect().cancel();
            if (activeRoomListener != null) {
                activeRoomRef.removeEventListener(activeRoomListener);
            }
        }
        activeRoomRef = null;
        activeRoomListener = null;
    }
}