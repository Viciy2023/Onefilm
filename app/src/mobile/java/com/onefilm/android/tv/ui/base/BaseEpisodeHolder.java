package com.onefilm.android.tv.ui.base;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.onefilm.android.tv.bean.Episode;

public abstract class BaseEpisodeHolder extends RecyclerView.ViewHolder {

    public BaseEpisodeHolder(@NonNull View itemView) {
        super(itemView);
    }

    public abstract void initView(Episode item);
}
