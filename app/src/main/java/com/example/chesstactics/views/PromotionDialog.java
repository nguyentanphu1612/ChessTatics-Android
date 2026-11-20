package com.example.chesstactics.views;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.example.chesstactics.R;
import com.example.chesstactics.engine.Piece;

public class PromotionDialog {

    public interface OnPromotionChosenListener {
        void onPieceChosen(int promotionPiece);
    }

    public static void show(Context context, int color, OnPromotionChosenListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_promotion, null);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(false)
                .create();

        TextView btnQueen = view.findViewById(R.id.btnPromoQueen);
        TextView btnRook = view.findViewById(R.id.btnPromoRook);
        TextView btnBishop = view.findViewById(R.id.btnPromoBishop);
        TextView btnKnight = view.findViewById(R.id.btnPromoKnight);

        btnQueen.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onPieceChosen(Piece.make(color, Piece.QUEEN));
        });
        btnRook.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onPieceChosen(Piece.make(color, Piece.ROOK));
        });
        btnBishop.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onPieceChosen(Piece.make(color, Piece.BISHOP));
        });
        btnKnight.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onPieceChosen(Piece.make(color, Piece.KNIGHT));
        });

        dialog.show();
    }
}