package com.example.chesstactics.models;

import android.graphics.Color;
import com.example.chesstactics.R;
import java.util.ArrayList;
import java.util.List;

public class BoardTheme {
    public final String id;
    public final int nameResId;
    public final int lightSquareColor;
    public final int darkSquareColor;
    public final int highlightColor;
    public final int selectedColor;

    public BoardTheme(String id, int nameResId, int lightSquareColor, int darkSquareColor,
                      int highlightColor, int selectedColor) {
        this.id = id;
        this.nameResId = nameResId;
        this.lightSquareColor = lightSquareColor;
        this.darkSquareColor = darkSquareColor;
        this.highlightColor = highlightColor;
        this.selectedColor = selectedColor;
    }

    public static List<BoardTheme> getAllThemes() {
        List<BoardTheme> list = new ArrayList<>();
        list.add(new BoardTheme("tournament_green", R.string.theme_green,
                Color.parseColor("#EBECD0"), Color.parseColor("#739552"),
                Color.parseColor("#80F5F682"), Color.parseColor("#99BACA2B")));
        list.add(new BoardTheme("classic_wood", R.string.theme_wood,
                Color.parseColor("#F0D9B5"), Color.parseColor("#B58863"),
                Color.parseColor("#80CDD26A"), Color.parseColor("#99AAA23A")));
        list.add(new BoardTheme("royal_blue", R.string.theme_blue,
                Color.parseColor("#DEE3E6"), Color.parseColor("#5D8AA8"),
                Color.parseColor("#8064B5F6"), Color.parseColor("#992196F3")));
        list.add(new BoardTheme("dark_marble", R.string.theme_marble,
                Color.parseColor("#D8D8D8"), Color.parseColor("#4A4E54"),
                Color.parseColor("#80FFD54F"), Color.parseColor("#99FFB300")));
        return list;
    }

    public static BoardTheme getThemeById(String id) {
        for (BoardTheme theme : getAllThemes()) {
            if (theme.id.equals(id)) return theme;
        }
        return getAllThemes().get(0);
    }
}