package com.onefilm.android.tv.ui.holder;

import androidx.annotation.NonNull;

import com.onefilm.android.tv.bean.Episode;
import com.onefilm.android.tv.databinding.AdapterEpisodeGridBinding;
import com.onefilm.android.tv.ui.adapter.EpisodeAdapter;
import com.onefilm.android.tv.ui.base.BaseEpisodeHolder;

public class EpisodeGridHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeGridBinding binding;

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void initView(Episode item) {
        binding.text.setSelected(item.isSelected());
        binding.text.setText(item.getDesc().concat(item.getName()));
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }
}
