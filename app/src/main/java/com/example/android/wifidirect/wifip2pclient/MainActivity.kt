package com.example.android.wifidirect.wifip2pclient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.android.wifidirect.wifip2pclient.databinding.ActivityMainBinding
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer

class MainActivity : AppCompatActivity(), ConnectionInfoListener {
    private lateinit var binding: ActivityMainBinding
    private var bPermissionGranted = false
    private var isWifiP2pEnabled = false
    private var p2pHandler: Handler? = null
    fun setWifiP2pEnable(bEnabled: Boolean) {
        if (isWifiP2pEnabled != bEnabled) {
            isWifiP2pEnabled = bEnabled
            Log.i(TAG, "setWifiP2pEnable:$isWifiP2pEnabled")
            clearP2pHandlerMessage()
            if (isWifiP2pEnabled) {
                startSearchServer()
                p2pHandler?.sendEmptyMessage(P2P_HANDLER_MSG_STATE_CONNECTING)
            } else {
                stopSearchServer()
                p2pHandler?.sendEmptyMessage(P2P_HANDLER_MSG_STATE_WIFI_OFF)
            }
        }
    }

    fun setWifiP2pConnect(bConnect: Boolean) {
        Log.i(TAG, "setWifiP2pConnect:$bConnect")
        if (!bConnect) { // 연결중인 서버가 종료되면 bConnect = false로 동작한다.
            clearP2pHandlerMessage()
            if (isWifiP2pEnabled) {
                startSearchServer()
                p2pHandler?.sendEmptyMessage(P2P_HANDLER_MSG_STATE_CONNECTING)
            } else {
                stopSearchServer()
                p2pHandler?.sendEmptyMessage(P2P_HANDLER_MSG_STATE_WIFI_OFF)
            }
        }
    }

    private fun clearP2pHandlerMessage() {
        p2pHandler?.removeMessages(P2P_HANDLER_MSG_CONNECT)
        p2pHandler?.removeMessages(P2P_HANDLER_MSG_GROUP_INFO)
        p2pHandler?.removeMessages(P2P_HANDLER_MSG_DISCOVER_SERVICE)
        p2pHandler?.removeMessages(P2P_HANDLER_MSG_STATE_CONNECTING)
        p2pHandler?.removeMessages(P2P_HANDLER_MSG_STATE_WIFI_OFF)
        p2pHandler?.removeMessages(P2P_HANDLER_MSG_STATE_CONNECTED)
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        const val NETWORK_NAME = "DIRECT-LOWASIS-GW"
        const val NETWORK_PASS_PHRASE = "SLy!x*8E"
        const val SERVICE_INSTANCE = "_lowasis_gw"
        const val P2P_HANDLER_MSG_CONNECT = 0
        const val P2P_HANDLER_MSG_GROUP_INFO = 1
        const val P2P_HANDLER_MSG_DISCOVER_SERVICE = 2
        const val P2P_HANDLER_MSG_STATE_CONNECTING = 3 // 상태 메시지 연결중
        const val P2P_HANDLER_MSG_STATE_WIFI_OFF = 4 // 상태 메시지 WIFI OFF
        const val P2P_HANDLER_MSG_STATE_CONNECTED = 5 // 상태 메시지 연결됨
    }

    override fun onConnectionInfoAvailable(p2pInfo: WifiP2pInfo) {
        Log.i(TAG, "onConnectionInfoAvailable $p2pInfo")
    }

    private lateinit var p2pChannel: WifiP2pManager.Channel
    private lateinit var p2pManager: WifiP2pManager

    private var p2pBroadcastReceiver: WiFiDirectBroadcastReceiver? = null
    private var p2pServiceRequest: WifiP2pDnsSdServiceRequest? = null

    private var testPingService: Timer? = null // 테스트용 핑

    private fun p2pStateConnecting() {
        val strMessage = "Connecting..."
        binding.tvMessage.text = strMessage
        if (testPingService != null) {
            testPingService?.cancel()
            testPingService = null
        }
    }

    private fun p2pStateWiFiOff() {
        val strMessage = "Wi-Fi disconnected"
        binding.tvMessage.text = strMessage
        if (testPingService != null) {
            testPingService?.cancel()
            testPingService = null
        }
    }

    private fun p2pStateConnected(ip: String) {
        val strMessage = "Connected $ip"
        binding.tvMessage.text = strMessage
        if (testPingService == null) { // 이 타이머는 서버와의 연결 확인 테스트 용임
            testPingService = timer(period = 1000, initialDelay = 1000)
            {
                try {
                    val address: InetAddress = InetAddress.getByName(ip)
                    val reachable: Boolean = address.isReachable(500)
                    val currentTime = SimpleDateFormat(
                        "hh:mm:ss",
                        Locale.getDefault()
                    ).format(Date(System.currentTimeMillis()))
                    val strText = "$currentTime  ping:$reachable"
                    runOnUiThread {
                        binding.tvPing.setTextColor(if (reachable) Color.BLUE else Color.RED)
                        binding.tvPing.text = strText
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun p2pStart() {
        if (p2pHandler == null) {
            p2pHandler = Handler(Looper.getMainLooper()) { msg ->
                val strMessage = when (msg.what) {
                    P2P_HANDLER_MSG_CONNECT -> "P2P_HANDLER_MSG_CONNECT"
                    P2P_HANDLER_MSG_GROUP_INFO -> "P2P_HANDLER_MSG_GROUP_INFO"
                    P2P_HANDLER_MSG_DISCOVER_SERVICE -> "P2P_HANDLER_MSG_DISCOVER_SERVICE"
                    P2P_HANDLER_MSG_STATE_CONNECTING -> "P2P_HANDLER_MSG_STATE_CONNECTING"
                    P2P_HANDLER_MSG_STATE_WIFI_OFF -> "P2P_HANDLER_MSG_STATE_WIFI_OFF"
                    P2P_HANDLER_MSG_STATE_CONNECTED -> "P2P_HANDLER_MSG_STATE_CONNECTED"
                    else -> "UNKNOWN"
                }
                Log.i(TAG, strMessage)
                when (msg.what) {
                    P2P_HANDLER_MSG_STATE_CONNECTING -> {
                        p2pHandler?.sendEmptyMessageDelayed(P2P_HANDLER_MSG_GROUP_INFO, 300)
                        p2pStateConnecting()
                    }
                    P2P_HANDLER_MSG_STATE_WIFI_OFF -> {
                        p2pStateWiFiOff()
                    }
                    P2P_HANDLER_MSG_STATE_CONNECTED -> {
                        p2pStateConnected(msg.obj as String)
                    }
                    P2P_HANDLER_MSG_DISCOVER_SERVICE -> {
                        p2pHandler?.sendEmptyMessageDelayed(P2P_HANDLER_MSG_DISCOVER_SERVICE, 3000)
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
                    P2P_HANDLER_MSG_GROUP_INFO -> {
                        p2pManager.requestGroupInfo(p2pChannel) { group ->
                            //Log.i(TAG, "requestGroupInfo:$group")
                            if (group?.networkName == NETWORK_NAME) {
                                p2pManager.requestConnectionInfo(p2pChannel) { info ->
                                    Log.i(TAG, "requestConnectionInfo:$info")
                                    if (info?.groupOwnerAddress?.hostAddress?.isNotBlank() == true) {
                                        stopSearchServer()
                                        clearP2pHandlerMessage()
                                        Message().let {
                                            it.what = P2P_HANDLER_MSG_STATE_CONNECTED
                                            it.obj = info.groupOwnerAddress?.hostAddress.toString()
                                            p2pHandler?.sendMessage(it)
                                        }
                                    } else {
                                        p2pHandler?.sendEmptyMessageDelayed(
                                            P2P_HANDLER_MSG_GROUP_INFO, 300
                                        )
                                    }
                                }
                            } else {
                                p2pHandler?.sendEmptyMessageDelayed(P2P_HANDLER_MSG_GROUP_INFO, 300)
                            }
                        }
                    }
                    P2P_HANDLER_MSG_CONNECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val config = WifiP2pConfig.Builder()
                            .setNetworkName(NETWORK_NAME)
                            .setPassphrase(NETWORK_PASS_PHRASE)
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
                                    p2pHandler?.removeMessages(P2P_HANDLER_MSG_CONNECT)
                                }

                                override fun onFailure(errorCode: Int) {
                                    Log.i(TAG, "connect onFailure")
                                }
                            })
                    }
                }
                true
            }
        }
        p2pBroadcastReceiver = WiFiDirectBroadcastReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        registerReceiver(p2pBroadcastReceiver, intentFilter)
    }

    private fun p2pStop() {
        unregisterReceiver(p2pBroadcastReceiver)
        if (p2pHandler != null) {
            p2pHandler?.removeCallbacksAndMessages(null)
            p2pHandler = null
        }
    }

    private fun startSearchServer() {
        if (p2pServiceRequest != null) {
            Log.i(TAG, "startSearchServer already run")
            return
        }
        Log.i(TAG, "startSearchServer")
        p2pServiceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        p2pManager.setDnsSdResponseListeners(
            p2pChannel,
            { instanceName, registrationType, device ->
                Log.d(
                    TAG,
                    "DnsSdResponseListeners $instanceName $registrationType $device"
                )
                if (instanceName.equals(SERVICE_INSTANCE, ignoreCase = true)) {
                    for (i in 1..5) {
                        Message().let { msg ->
                            msg.what = P2P_HANDLER_MSG_CONNECT // onFailure 시 재시도를 위해 핸들러로 처리한다.
                            msg.arg1 = i
                            msg.obj = device.deviceAddress
                            // 한번에 다 보내고 성공하면 모두 취소한다.
                            // connect 할 때 error 발생이 많다.
                            p2pHandler?.sendMessageDelayed(msg, 250L * i)
                        }
                    }
                }
            }, null
        )
        p2pManager.addServiceRequest(
            p2pChannel, p2pServiceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "addServiceRequest onSuccess")
                }

                override fun onFailure(code: Int) {
                    Log.i(TAG, "addServiceRequest onFailure")
                }
            }
        )
        p2pHandler?.sendEmptyMessage(P2P_HANDLER_MSG_DISCOVER_SERVICE)
    }

    private fun stopSearchServer() {
        if (p2pServiceRequest != null) {
            p2pManager.removeServiceRequest(p2pChannel, p2pServiceRequest, null)
            p2pServiceRequest = null
        }
    }

    inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let { Log.d(TAG, it) }
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    setWifiP2pEnable(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    //Log.d(TAG, "Device status -$intent")
                    p2pManager.requestPeers(p2pChannel) { peerList ->
                        for (device in peerList.deviceList) // status:0이면 connect
                            Log.d(TAG, "device.status:${device.deviceName} ${device.status}")
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    p2pManager.requestConnectionInfo(p2pChannel) { info ->
                        Log.i(TAG, "*requestConnectionInfo:$info")
                        setWifiP2pConnect(info?.groupOwnerAddress?.hostAddress?.isNotBlank() == true)
                    }
                }
            }
        }
    }
}

