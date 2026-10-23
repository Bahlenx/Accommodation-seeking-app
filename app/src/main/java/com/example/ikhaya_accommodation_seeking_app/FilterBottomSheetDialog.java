package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;

import java.util.ArrayList;
import java.util.List;

public class FilterBottomSheetDialog extends BottomSheetDialogFragment {

    public interface FilterListener {
        void onFiltersApplied(String offerType, float minPrice, float maxPrice, String propertyType, String arrangement, List<String> utilities);
    }

    private FilterListener listener;
    private boolean isBuyingMode = false;

    public void setFilterListener(FilterListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_filter_bottom_sheet, container, false);

        RangeSlider sliderPrice = view.findViewById(R.id.sliderPriceRange);
        TextView tvPriceDisplay = view.findViewById(R.id.tvPriceRangeDisplay);

        ChipGroup chipGroupOffer = view.findViewById(R.id.chipGroupOfferType);
        ChipGroup chipGroupProperty = view.findViewById(R.id.chipGroupPropertyType);
        ChipGroup chipGroupArrangement = view.findViewById(R.id.chipGroupArrangement);
        ChipGroup chipGroupUtilities = view.findViewById(R.id.chipGroupUtilities);

        Button btnApply = view.findViewById(R.id.btnApplyFilters);
        Button btnReset = view.findViewById(R.id.btnResetFilters);

        // Dynamic Slider Scaling Based on Rent vs Buy
        if (chipGroupOffer != null && sliderPrice != null) {
            chipGroupOffer.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipOfferBuy)) {
                    isBuyingMode = true;
                    sliderPrice.setValueTo(5000000.0f);
                    sliderPrice.setStepSize(50000.0f);
                    sliderPrice.setValues(0.0f, 5000000.0f);
                } else {
                    isBuyingMode = false;
                    sliderPrice.setValueTo(20000.0f);
                    sliderPrice.setStepSize(500.0f);
                    sliderPrice.setValues(0.0f, 20000.0f);
                }
            });
        }

        if (sliderPrice != null && tvPriceDisplay != null) {
            sliderPrice.setValues(0.0f, 20000.0f);
            sliderPrice.addOnChangeListener((slider, value, fromUser) -> {
                List<Float> values = slider.getValues();
                int min = Math.round(values.get(0));
                int max = Math.round(values.get(1));

                String maxLabel = (max == slider.getValueTo()) ? max + "+" : String.valueOf(max);
                tvPriceDisplay.setText("R " + min + " - R " + maxLabel);
            });
        }

        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                if (chipGroupOffer != null) chipGroupOffer.check(R.id.chipOfferRent);
                if (chipGroupProperty != null) chipGroupProperty.clearCheck();
                if (chipGroupArrangement != null) chipGroupArrangement.clearCheck();
                if (chipGroupUtilities != null) chipGroupUtilities.clearCheck();
                if (sliderPrice != null) sliderPrice.setValues(0.0f, 20000.0f);
            });
        }

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                if (listener != null) {
                    String offerType = isBuyingMode ? "sale" : "rent";
                    float minPrice = 0f;
                    float maxPrice = isBuyingMode ? 5000000f : 20000f;

                    if (sliderPrice != null) {
                        List<Float> values = sliderPrice.getValues();
                        minPrice = values.get(0);
                        maxPrice = values.get(1);
                    }

                    String propertyType = "";
                    if (chipGroupProperty != null) {
                        int checkedId = chipGroupProperty.getCheckedChipId();
                        if (checkedId == R.id.chipPropHouse) propertyType = "House";
                        else if (checkedId == R.id.chipPropRoom) propertyType = "Room";
                        else if (checkedId == R.id.chipPropLand) propertyType = "Land";
                    }

                    String arrangement = "";
                    if (chipGroupArrangement != null) {
                        int checkedId = chipGroupArrangement.getCheckedChipId();
                        if (checkedId == R.id.chipArrSingle) arrangement = "Single";
                        else if (checkedId == R.id.chipArrShared) arrangement = "Shared";
                        else if (checkedId == R.id.chipArrBachelor) arrangement = "Bachelor";
                    }

                    List<String> utilities = new ArrayList<>();
                    if (chipGroupUtilities != null) {
                        List<Integer> checkedUtilIds = chipGroupUtilities.getCheckedChipIds();
                        if (checkedUtilIds.contains(R.id.chipUtilWifi)) utilities.add("Wi-Fi");
                        if (checkedUtilIds.contains(R.id.chipUtilWater)) utilities.add("Water");
                        if (checkedUtilIds.contains(R.id.chipUtilPower)) utilities.add("Power");
                    }

                    listener.onFiltersApplied(offerType, minPrice, maxPrice, propertyType, arrangement, utilities);
                }
                dismiss();
            });
        }

        return view;
    }
}