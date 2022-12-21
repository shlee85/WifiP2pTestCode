package com.example.android.wifidirect.wifip2pclient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity(), ConnectionInfoListener {
    private var strGatewayIp = ""
    private var bPermissionGranted = false
    var isWifiP2pEnabled = false
    private var p2pHandler: Handler? = null
    fun setWifiP2pEnable(bEnabled: Boolean) {
        if (isWifiP2pEnabled != bEnabled) {
            isWifiP2pEnabled = bEnabled
            Log.i(TAG, "setWifiP2pEnable:$isWifiP2pEnabled")
            p2pHandler?.removeMessages(P2P_HANDLER_MSG_CONNECT)
            p2pHandler?.removeMessages(P2P_HANDLER_MSG_GROUP_INFO)
            if (isWifiP2pEnabled) {
                startSearchServer()
                p2pHandler?.sendEmptyMessageDelayed(P2P_HANDLER_MSG_GROUP_INFO, 200)
            } else {
                stopSearchServer()
                strGatewayIp = ""
            }
        }
    }
    fun setWifiP2pConnect(bConnect: Boolean) {
        stopSearchServer()
        strGatewayIp = ""
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bPermissionGranted = true
                p2pStart()
            } else {
                Log.e(TAG, "Fine location permission is not granted!")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE
                ),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            bPermissionGranted = true
            Log.i(TAG, "bPermissionGranted")
        }
        p2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        p2pChannel = p2pManager.initialize(this, mainLooper, null)
        if (p2pHandler == null) {
            p2pHandler = Handler(Looper.getMainLooper()) { msg ->
                when (msg.what) {
                    P2P_HANDLER_MSG_GROUP_INFO -> {
                        p2pManager.requestGroupInfo(p2pChannel) { group ->
                            //Log.i(TAG, "requestGroupInfo:$group")
                            if (group?.networkName == NETWORK_NAME) {
                                p2pManager.requestConnectionInfo(p2pChannel) { info ->
                                    Log.i(TAG, "requestConnectionInfo:$info")
                                    if (info?.groupOwnerAddress?.hostAddress?.isNotBlank() == true) {
                                        val strGatewayIP = info.groupOwnerAddress?.hostAddress.toString()
                                        p2pState = P2P_STATE_FIND_GATEWAY
                                        Log.i(TAG, "FIND_GATEWAY:$strGatewayIP")
                                        strGatewayIp = strGatewayIP
                                    } else {
                                        p2pHandler?.sendEmptyMessageDelayed(P2P_HANDLER_MSG_GROUP_INFO, 300)
                                    }
                                }
                            } else {
                                p2pHandler?.sendEmptyMessageDelayed(P2P_HANDLER_MSG_GROUP_INFO, 300)
                            }
                        }
                    }
                    P2P_HANDLER_MSG_CONNECT -> {
                        val config = WifiP2pConfig.Builder()
                            .setNetworkName("DIRECT-TUNER14")
                            .setPassphrase("pass1234")
                            .enablePersistentMode(true)
                            .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ)
                            .build()
                        config.deviceAddress = msg.obj as String // device.deviceAddress
                        Log.i(TAG, "config.deviceAddress:${config.deviceAddress}")
                        p2pManager.connect(
                            p2pChannel, config,
                            object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    Log.i(TAG, "connect onSuccess")
                                }

                                override fun onFailure(errorCode: Int) {
                                    Log.i(TAG, "connect onFailure")
                                    if (msg.arg1 > 0) {
                                        Message().let {
                                            it.what =
                                                P2P_HANDLER_MSG_CONNECT // onFailure 시 재시도를 위해 핸들러로 처리한다.
                                            it.arg1 = msg.arg1 - 1 // retry count
                                            it.obj = msg.obj
                                            p2pHandler?.sendMessageDelayed(it, 300)
                                        }
                                    }
                                }
                            })
                    }
                }
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (bPermissionGranted) {
            Log.i(TAG, "p2pStart:onResume")
            p2pStart()
        }
    }

    override fun onPause() {
        super.onPause()
        p2pStop()
    }

    companion object {
        const val TAG = "P2PClient"
        const val PERMISSIONS_REQUEST_CODE = 1001
        const val P2P_STATE_WIFI_TURNED_OFF = 0
        const val P2P_STATE_NO_GATEWAY = 1
        const val P2P_STATE_FIND_GATEWAY = 2
        const val NETWORK_NAME = "DIRECT-TUNER14"
        const val SERVICE_INSTANCE = "_wifidemotest"
        const val P2P_HANDLER_MSG_CONNECT = 0
        const val P2P_HANDLER_MSG_GROUP_INFO = 1
    }

    override fun onConnectionInfoAvailable(p2pInfo: WifiP2pInfo) {
        Log.i(TAG, "onConnectionInfoAvailable $p2pInfo")
        //p2pState = P2P_STATE_FIND_GATEWAY
        //Log.i(TAG, "*FIND_GATEWAY:${p2pInfo.groupOwnerAddress.hostAddress}")
    }


    private var p2pState = P2P_STATE_WIFI_TURNED_OFF
    private lateinit var p2pChannel: WifiP2pManager.Channel
    private lateinit var p2pManager: WifiP2pManager

    private var p2pBroadcastReceiver: WiFiDirectBroadcastReceiver1? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    private fun p2pStart() {
        p2pBroadcastReceiver = WiFiDirectBroadcastReceiver1(p2pManager, p2pChannel, this)
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        registerReceiver(p2pBroadcastReceiver, intentFilter)
    }

    private fun p2pStop() {
        unregisterReceiver(p2pBroadcastReceiver)
    }

    private fun startSearchServer() {
        Log.i(TAG, "startSearchServer")
        p2pManager.setDnsSdResponseListeners(
            p2pChannel,
            { instanceName, registrationType, device ->
                Log.d(
                    TAG,
                    "DnsSdResponseListeners $instanceName $registrationType $device"
                )
                if (instanceName.equals(SERVICE_INSTANCE, ignoreCase = true)) {
                    Message().let {
                        it.what = P2P_HANDLER_MSG_CONNECT // onFailure 시 재시도를 위해 핸들러로 처리한다.
                        it.arg1 = 3
                        it.obj = device.deviceAddress
                        p2pHandler?.sendMessageDelayed(it, 200)
                    }
                }
            },
            { fullDomainName, record, device ->
                Log.d(
                    TAG,
                    "setDnsSdResponseListeners2 $fullDomainName $record $device"
                )
            })
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        p2pManager.addServiceRequest(
            p2pChannel, serviceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "addServiceRequest onSuccess")
                }

                override fun onFailure(code: Int) {
                    Log.i(TAG, "addServiceRequest onFailure")
                }
            }
        )
        p2pManager.discoverServices(
            p2pChannel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "discoverServices onSuccess ")
                }

                override fun onFailure(code: Int) {
                    Log.i(TAG, "discoverServices onFailure ")
                }
            }
        )
    }

    private fun stopSearchServer() {
        if (serviceRequest != null) {
            p2pManager.removeServiceRequest(
                p2pChannel, serviceRequest,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(arg0: Int) {}
                })
            serviceRequest = null
        }
    }

    class WiFiDirectBroadcastReceiver1(
        private val manager: WifiP2pManager, private val channel: WifiP2pManager.Channel,
        private val activity: MainActivity
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let { Log.d(TAG, it) }
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    activity.setWifiP2pEnable(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    //Log.d(TAG, "Device status -$intent")
                    manager.requestPeers(channel) { peerList ->
                        for (device in peerList.deviceList) {
                            Log.d(TAG, "device.status:${device.deviceName} ${device.status}") // 0이면 connect
                            //Log.d(TAG, device.toString())
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent
                        .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?
                    if (networkInfo?.isConnected == true)
                        manager.requestConnectionInfo(channel, activity as ConnectionInfoListener)
                    activity.setWifiP2pConnect(networkInfo?.isConnected == true)

                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent
                        .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice?
                    Log.d(TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:" + device!!.status)
                }
            }
        }
    }
}

