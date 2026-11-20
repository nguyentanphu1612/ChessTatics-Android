package com.example.chesstactics.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chesstactics.R;
import com.example.chesstactics.models.MatchRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MatchHistoryAdapter extends RecyclerView.Adapter<MatchHistoryAdapter.HistoryViewHolder> {

    public interface OnMatchItemListener {
        void onReplayClicked(MatchRecord record);
        void onDeleteClicked(MatchRecord record);
    }

    private final List<MatchRecord> items;
    private final OnMatchItemListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public MatchHistoryAdapter(List<MatchRecord> items, OnMatchItemListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_match_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        MatchRecord record = items.get(position);
        Context ctx = holder.itemView.getContext();

        int badgeColor;
        if ("WIN".equals(record.result)) {
            holder.tvResultBadge.setText(ctx.getString(R.string.result_win));
            badgeColor = Color.parseColor("#2E7D32");
        } else if ("LOSS".equals(record.result)) {
            holder.tvResultBadge.setText(ctx.getString(R.string.result_loss));
            badgeColor = Color.parseColor("#C62828");
        } else {
            holder.tvResultBadge.setText(ctx.getString(R.string.result_draw));
            badgeColor = Color.parseColor("#EF6C00");
        }
        holder.tvResultBadge.setBackgroundTintList(ColorStateList.valueOf(badgeColor));

        holder.tvMode.setText(getModeLabel(ctx, record.mode));
        holder.tvDetails.setText(ctx.getString(R.string.history_moves_format,
                record.totalMoves, sdf.format(new Date(record.timestamp))));

        holder.itemView.setOnClickListener(v -> listener.onReplayClicked(record));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClicked(record));
    }

    private String getModeLabel(Context ctx, String mode) {
        switch (mode) {
            case "AI_EASY":
                return ctx.getString(R.string.mode_ai_easy);
            case "AI_HARD":
                return ctx.getString(R.string.mode_ai_hard);
            case "AI_EXTREME":
                return ctx.getString(R.string.mode_ai_extreme);
            case "ONLINE":
                return ctx.getString(R.string.mode_online);
            default:
                return ctx.getString(R.string.mode_pvp);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final TextView tvResultBadge, tvMode, tvDetails;
        final ImageButton btnDelete;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvResultBadge = itemView.findViewById(R.id.tvResultBadge);
            tvMode = itemView.findViewById(R.id.tvMode);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            btnDelete = itemView.findViewById(R.id.btnDeleteMatch);
        }
    }
}