package com.example.drivingassistantapp.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.drivingassistantapp.data.DefaultDataRepository

class RideOnReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RideOnReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        DefaultDataRepository.initialize(context.applicationContext)
        val repository = DefaultDataRepository.getInstance()

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED, Intent.ACTION_POWER_CONNECTED -> {
                val triggerSource = if (action == Intent.ACTION_POWER_CONNECTED) "Pengisi Daya (Charger)" else "Koneksi Bluetooth"
                repository.addLog("Auto-Start dipicu oleh: $triggerSource")
                Log.d(TAG, "Starting AssistantService via auto-start trigger: $triggerSource")
                
                val serviceIntent = Intent(context, AssistantService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                val triggerSource = if (action == Intent.ACTION_POWER_DISCONNECTED) "Pengisi Daya dicabut" else "Koneksi Bluetooth terputus"
                repository.addLog("Auto-Stop dipicu oleh: $triggerSource")
                Log.d(TAG, "Stopping AssistantService via auto-stop trigger: $triggerSource")
                
                val serviceIntent = Intent(context, AssistantService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
