package com.example.chesstactics.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chesstactics.R;
import java.util.ArrayList;
import java.util.List;

public class MoveNotationAdapter extends RecyclerView.Adapter<MoveNotationAdapter.MoveViewHolder> {

    public static class MovePair {
        public final int moveNumber;
        public final String whiteSan;
        public String blackSan;

        public MovePair(int moveNumber, String whiteSan, String blackSan) {
            this.moveNumber = moveNumber;
            this.whiteSan = whiteSan;
            this.blackSan = blackSan;
        }
    }

    private final List<MovePair> pairs = new ArrayList<>();

    public void setMoves(List<String> sanList) {
        pairs.clear();
        for (int i = 0; i < sanList.size(); i += 2) {
            String w = sanList.get(i);
            String b = (i + 1 < sanList.size()) ? sanList.get(i + 1) : "";
            pairs.add(new MovePair((i / 2) + 1, w, b));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MoveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_move_notation, parent, false);
        return new MoveViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MoveViewHolder holder, int position) {
        MovePair pair = pairs.get(position);
        String text = pair.moveNumber + ". " + pair.whiteSan +
                (pair.blackSan.isEmpty() ? "" : "  " + pair.blackSan);
        holder.tvMovePair.setText(text);
    }

    @Override
    public int getItemCount() {
        return pairs.size();
    }

    static class MoveViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMovePair;
        MoveViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMovePair = itemView.findViewById(R.id.tvMovePair);
        }
    }
}