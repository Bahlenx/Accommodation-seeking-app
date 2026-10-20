package com.example.ikhaya_accommodation_seeking_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class FilterBottomSheetDialog extends BottomSheetDialogFragment {

    public interface FilterListener {
        void onFiltersApplied(String maxPrice, String roomType, boolean wifi, boolean water, boolean backupPower);
    }

    private FilterListener listener;

    public void setFilterListener(FilterListener listener) {
        this.listener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_filter_bottom_sheet, container, false);

        ChipGroup chipGroupPrice = view.findViewById(R.id.chipGroupPrice);
        ChipGroup chipGroupRoom = view.findViewById(R.id.chipGroupRoomType);
        Chip chipWifi = view.findViewById(R.id.chipUtilWifi);
        Chip chipWater = view.findViewById(R.id.chipUtilWater);
        Chip chipPower = view.findViewById(R.id.chipUtilBackupPower);

        view.findViewById(R.id.btnCloseDialog).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnResetFilters).setOnClickListener(v -> {
            chipGroupPrice.clearCheck();
            chipGroupRoom.clearCheck();
            chipWifi.setChecked(false);
            chipWater.setChecked(false);
            chipPower.setChecked(false);
        });

        view.findViewById(R.id.btnApplyFilters).setOnClickListener(v -> {
            if (listener != null) {
                // Determine max budget
                String maxPrice = "";
                int priceChipId = chipGroupPrice.getCheckedChipId();
                if (priceChipId == R.id.chipPrice1) maxPrice = "1500";
                else if (priceChipId == R.id.chipPrice2) maxPrice = "2500";
                else if (priceChipId == R.id.chipPrice3) maxPrice = "4000";

                // Determine selected room type
                String roomType = "";
                int roomChipId = chipGroupRoom.getCheckedChipId();
                if (roomChipId == R.id.chipTypeSingle) roomType = "Single";
                else if (roomChipId == R.id.chipTypeShared) roomType = "Shared";
                else if (roomChipId == R.id.chipTypeBachelor) roomType = "Bachelor";
                else if (roomChipId == R.id.chipTypeHouse) roomType = "House";

                listener.onFiltersApplied(
                        maxPrice,
                        roomType,
                        chipWifi.isChecked(),
                        chipWater.isChecked(),
                        chipPower.isChecked()
                );
            }
            dismiss();
        });

        return view;
    }
}