package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.graphics.Color;

import com.google.android.material.card.MaterialCardView;

public final class UiCardHelper {

    private UiCardHelper() {}

    public static MaterialCardView createAppCard(Context context, int cornerRadiusDp, int elevationDp) {
        float density = context.getResources().getDisplayMetrics().density;
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(Math.round(cornerRadiusDp * density));
        card.setCardElevation(Math.round(elevationDp * density));
        card.setStrokeColor(Color.parseColor("#EAE3DC"));
        card.setStrokeWidth(Math.round(1f * density));
        return card;
    }
}
