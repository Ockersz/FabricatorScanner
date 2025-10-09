package com.example.fabricatorscanner.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fabricatorscanner.R;

import java.util.List;

public class MattressAdapter extends RecyclerView.Adapter<MattressAdapter.ViewHolder> {

    private final List<String> mattressList;
    private final OnMattressChangeListener listener;

    // Callback interface
    public interface OnMattressChangeListener {
        void onMattressCountChanged(int count);
    }

    public MattressAdapter(List<String> mattressList, OnMattressChangeListener listener) {
        this.mattressList = mattressList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mattress, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String code = mattressList.get(position);
        holder.textMattress.setText(code);

        // Delete button action
        holder.buttonDelete.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                mattressList.remove(pos);
                notifyItemRemoved(pos);

                // Notify listener about the updated count
                if (listener != null) {
                    listener.onMattressCountChanged(mattressList.size());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mattressList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textMattress;
        Button buttonDelete;
        ViewHolder(View itemView) {
            super(itemView);
            textMattress = itemView.findViewById(R.id.text_mattress_item);
            buttonDelete = itemView.findViewById(R.id.button_delete);
        }
    }
}
