package com.example.chesstactics.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chesstactics.R;
import com.example.chesstactics.online.FirebaseChessManager;

public class OnlineLobbyActivity extends AppCompatActivity {

    private EditText etRoomCode;
    private Button btnCreateRoom, btnJoinRoom;
    private ProgressBar progressLobby;

    private FirebaseChessManager firebaseManager;
    private int selectedTimeMinutes = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_lobby);

        selectedTimeMinutes = getIntent().getIntExtra(GameActivity.EXTRA_TIME_MINUTES, 10);
        firebaseManager = new FirebaseChessManager();

        etRoomCode = findViewById(R.id.etRoomCode);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        progressLobby = findViewById(R.id.progressLobby);

        findViewById(R.id.btnBackLobby).setOnClickListener(v -> finish());

        btnCreateRoom.setOnClickListener(v -> handleCreateRoom());
        btnJoinRoom.setOnClickListener(v -> handleJoinRoom());
    }

    private void handleCreateRoom() {
        setLoadingState(true);
        firebaseManager.createRoom(selectedTimeMinutes, new FirebaseChessManager.RoomCallback() {
            @Override
            public void onSuccess(String roomCode, int assignedColor, int timeLimitMinutes) {
                setLoadingState(false);
                copyRoomCodeToClipboard(roomCode);
                Toast.makeText(OnlineLobbyActivity.this,
                        getString(R.string.room_code_banner, roomCode), Toast.LENGTH_LONG).show();
                launchOnlineGame(roomCode, assignedColor, timeLimitMinutes);
            }

            @Override
            public void onError(String message) {
                setLoadingState(false);
                Toast.makeText(OnlineLobbyActivity.this, getLocalizedLobbyError(message), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleJoinRoom() {
        String code = etRoomCode.getText().toString().trim();
        if (TextUtils.isEmpty(code) || code.length() != 6) {
            Toast.makeText(this, R.string.invalid_room_code, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true);
        firebaseManager.joinRoom(code, new FirebaseChessManager.RoomCallback() {
            @Override
            public void onSuccess(String roomCode, int assignedColor, int timeLimitMinutes) {
                setLoadingState(false);
                launchOnlineGame(roomCode, assignedColor, timeLimitMinutes);
            }

            @Override
            public void onError(String message) {
                setLoadingState(false);
                Toast.makeText(OnlineLobbyActivity.this, getLocalizedLobbyError(message), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getLocalizedLobbyError(String rawError) {
        if ("ROOM_NOT_FOUND".equals(rawError)) {
            return getString(R.string.error_room_not_found);
        } else if ("ROOM_FULL".equals(rawError)) {
            return getString(R.string.error_room_full);
        } else if ("NETWORK_TIMEOUT".equals(rawError)) {
            return getString(R.string.error_network_timeout);
        }
        return rawError != null ? rawError : "";
    }

    private void launchOnlineGame(String roomCode, int playerColor, int timeMinutes) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_MODE, "ONLINE");
        intent.putExtra(GameActivity.EXTRA_ROOM_CODE, roomCode);
        intent.putExtra(GameActivity.EXTRA_PLAYER_COLOR, playerColor);
        intent.putExtra(GameActivity.EXTRA_TIME_MINUTES, timeMinutes);
        startActivity(intent);
        finish();
    }

    private void copyRoomCodeToClipboard(String roomCode) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("ChessRoomCode", roomCode);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void setLoadingState(boolean isLoading) {
        progressLobby.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnCreateRoom.setEnabled(!isLoading);
        btnJoinRoom.setEnabled(!isLoading);
        etRoomCode.setEnabled(!isLoading);
    }
}