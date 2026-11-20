package com.example.chesstactics.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.chesstactics.models.MatchRecord;
import java.util.ArrayList;
import java.util.List;

public class ChessDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "chess_tactics.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_MATCHES = "matches";
    private static final String COL_ID = "id";
    private static final String COL_MODE = "mode";
    private static final String COL_RESULT = "result";
    private static final String COL_REASON = "reason";
    private static final String COL_MOVES_UCI = "moves_uci";
    private static final String COL_MOVES_SAN = "moves_san";
    private static final String COL_PLAYER_COLOR = "player_color";
    private static final String COL_TOTAL_MOVES = "total_moves";
    private static final String COL_TIMESTAMP = "timestamp";

    public ChessDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_MATCHES + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MODE + " TEXT, " +
                COL_RESULT + " TEXT, " +
                COL_REASON + " TEXT, " +
                COL_MOVES_UCI + " TEXT, " +
                COL_MOVES_SAN + " TEXT, " +
                COL_PLAYER_COLOR + " INTEGER, " +
                COL_TOTAL_MOVES + " INTEGER, " +
                COL_TIMESTAMP + " INTEGER)";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MATCHES);
        onCreate(db);
    }

    public long insertMatch(MatchRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MODE, record.mode);
        cv.put(COL_RESULT, record.result);
        cv.put(COL_REASON, record.reason);
        cv.put(COL_MOVES_UCI, record.movesUci);
        cv.put(COL_MOVES_SAN, record.movesSan);
        cv.put(COL_PLAYER_COLOR, record.playerColor);
        cv.put(COL_TOTAL_MOVES, record.totalMoves);
        cv.put(COL_TIMESTAMP, record.timestamp);
        return db.insert(TABLE_MATCHES, null, cv);
    }

    public List<MatchRecord> getMatches(String resultFilter) {
        List<MatchRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = null;
        String[] selectionArgs = null;
        if (resultFilter != null && !resultFilter.equals("ALL")) {
            selection = COL_RESULT + " = ?";
            selectionArgs = new String[]{resultFilter};
        }

        Cursor cursor = db.query(TABLE_MATCHES, null, selection, selectionArgs,
                null, null, COL_TIMESTAMP + " DESC");

        if (cursor.moveToFirst()) {
            do {
                list.add(new MatchRecord(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_MODE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_RESULT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_REASON)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_MOVES_UCI)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_MOVES_SAN)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_PLAYER_COLOR)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_TOTAL_MOVES)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP))
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public void deleteMatch(long id) {
        getWritableDatabase().delete(TABLE_MATCHES, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        getWritableDatabase().delete(TABLE_MATCHES, null, null);
    }
}