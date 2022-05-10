package com.example.android.wifidirect.wifip2pclient

import android.Manifest
import android.net.wifi.p2p.WifiP2pManager
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.os.Parcelable
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.WifiP2pDevice
import android.util.Log
import androidx.core.app.ActivityCompat

class WiFiDirectBroadcastReceiver
    (
    private val manager: WifiP2pManager, private val channel: WifiP2pManager.Channel,
    private val activity: Activity
) : BroadcastReceiver() {


    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, action!!)
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {
            val networkInfo = intent
                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?
            if (networkInfo!!.isConnected) {

                // we are connected with the other device, request connection
                // info to find group owner IP
                Log.d(TAG, "Connected to p2p network. Requesting network details")
                manager.requestConnectionInfo(
                    channel,
                    activity as ConnectionInfoListener
                )
            } else {
                // It's a disconnect
            }
        }
        if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
            val device = intent
                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice?
            Log.d(TAG, "Device status -" + device!!.status)
        }
        // val device = intent
        //     .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice?
        if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
            Log.d(TAG, "Device status -$intent")
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "requestPeers permission error")
                return
            }
            manager.requestPeers(channel) { peerList ->
                for (device in peerList.deviceList) {
                    Log.d(TAG, device.deviceName)
                }
            }
        }
    }

    companion object {
        private const val TAG = "P2PClient"
    }
}