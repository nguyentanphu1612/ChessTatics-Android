package com.example.chesstactics.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chesstactics.R;
import com.example.chesstactics.models.BoardTheme;
import java.util.List;

public class SkinAdapter extends RecyclerView.Adapter<SkinAdapter.SkinViewHolder> {

    public interface OnThemeSelectedListener {
        void onThemeSelected(BoardTheme theme);
    }

    private final List<BoardTheme> themes;
    private String activeThemeId;
    private final OnThemeSelectedListener listener;

    public SkinAdapter(List<BoardTheme> themes, String activeThemeId, OnThemeSelectedListener listener) {
        this.themes = themes;
        this.activeThemeId = activeThemeId;
        this.listener = listener;
    }

    public void setActiveThemeId(String id) {
        this.activeThemeId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SkinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_skin, parent, false);
        return new SkinViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SkinViewHolder holder, int position) {
        BoardTheme theme = themes.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvName.setText(ctx.getString(theme.nameResId));
        holder.swatchLight.setBackgroundColor(theme.lightSquareColor);
        holder.swatchDark.setBackgroundColor(theme.darkSquareColor);

        boolean isSelected = theme.id.equals(activeThemeId);
        holder.tvStatus.setText(isSelected ? ctx.getString(R.string.skin_in_use) : ctx.getString(R.string.skin_apply));

        holder.itemView.setOnClickListener(v -> listener.onThemeSelected(theme));
    }

    @Override
    public int getItemCount() {
        return themes.size();
    }

    static class SkinViewHolder extends RecyclerView.ViewHolder {
        final View swatchLight, swatchDark;
        final TextView tvName, tvStatus;

        SkinViewHolder(@NonNull View itemView) {
            super(itemView);
            swatchLight = itemView.findViewById(R.id.swatchLight);
            swatchDark = itemView.findViewById(R.id.swatchDark);
            tvName = itemView.findViewById(R.id.tvSkinName);
            tvStatus = itemView.findViewById(R.id.tvSkinStatus);
        }
    }
}