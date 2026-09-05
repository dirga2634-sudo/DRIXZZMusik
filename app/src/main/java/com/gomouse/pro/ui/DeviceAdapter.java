package com.gomouse.pro.ui;

import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gomouse.pro.R;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private final List<InputDevice> devices = new ArrayList<>();

    public void submitList(List<InputDevice> newDevices) {
        devices.clear();
        devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InputDevice device = devices.get(position);
        holder.name.setText(device.getName());
        int sources = device.getSources();
        String typeLabel;
        int iconRes;
        if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            typeLabel = holder.itemView.getContext().getString(R.string.device_type_gamepad);
            iconRes = R.drawable.ic_gamepad;
        } else if ((sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            typeLabel = holder.itemView.getContext().getString(R.string.device_type_mouse);
            iconRes = R.drawable.ic_mouse;
        } else if ((sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) {
            typeLabel = holder.itemView.getContext().getString(R.string.device_type_keyboard);
            iconRes = R.drawable.ic_keyboard;
        } else {
            typeLabel = holder.itemView.getContext().getString(R.string.device_type_other);
            iconRes = R.drawable.ic_devices;
        }
        holder.type.setText(typeLabel);
        holder.icon.setImageResource(iconRes);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView type;
        final ImageView icon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_device_name);
            type = itemView.findViewById(R.id.text_device_type);
            icon = itemView.findViewById(R.id.icon_device_type);
        }
    }
}
