package com.example.android.wifidirect.wifip2pclient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.android.wifidirect.wifip2pclient.databinding.ActivityMainBinding
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.timer


class MainActivity : AppCompatActivity(), ConnectionInfoListener {
    private lateinit var binding: ActivityMainBinding
    private var bPermissionGranted = false
    private var isWifiP2pEnabled = false
    private var p2pHandler: Handler? = null
    private var mList: ArrayList<String> = ArrayList<String>()
    private var mPeerArray: ArrayList<String> = ArrayList<String>()
    private var pList = arrayListOf<SimpleModel>()

    private var gCnt = 1

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
                //p2pStart()
                Log.d(TAG, "Request Permission.")
                restartApp()
            } else {
                Log.d(TAG, "Fine location permission is not granted!")
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
        if (bPermissionGranted) {
            Log.i(TAG, "p2pStart:onCreate")
            p2pStart()
        }

        binding.testBtn.setOnClickListener {
            Log.d(TAG, "테스트 버튼")
            Log.d(TAG, "[WIFI-P2P] wifi connect status : ${WifiP2pDevice.CONNECTED}")
            p2pManager.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "[DISCOVER]removeGroup. onSuccess()")
                    p2pManager.cancelConnect(p2pChannel, null)
                    p2pManager.clearLocalServices(p2pChannel, null)
                    p2pManager.clearServiceRequests(p2pChannel, null)
                    p2pManager.stopPeerDiscovery(p2pChannel, null)
                }

                override fun onFailure(p0: Int) {
                    Log.d(TAG, "[DISCOVER]removeGroup. onFailure():$p0")
                }
            })

        }

        binding.ShowList.setOnClickListener {
            Log.d(TAG, "Show List click., count [$gCnt]")
            getPeerList().forEach {
                Log.d(TAG, "peer : $it")
            }

            p2pManager.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "[DISCOVER]discoverPeers. onSuccess()")
                }

                override fun onFailure(p0: Int) {
                    Log.d(TAG, "[DISCOVER]discoverPeers. onFailure():$p0")
                }
            })

            binding.directList.layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = DirectListAdapter(this, pList, mPeerArray)
            binding.directList.adapter = adapter

            adapter.setMyItemClickListener(object : DirectListAdapter.MyItemClickListener {
                override fun onItemClick(pos: Int, name: String?) {
                    Log.d(TAG, "onItemClick. [$pos - $name]")
                    if (name != null) {
                        mDeviceName = name
                    }
                    stopSearchServer()

                    startSearchServer()
                    p2pHandler?.sendEmptyMessage(P2P_HANDLER_MSG_STATE_CONNECTING)
                }

                override fun onItemSelected(pos: Int) {
                    Log.d(TAG, "onItemSelected")
                }
            })
        }
    }

    private lateinit var adapter: DirectListAdapter
    private var chList = arrayListOf<SimpleModel>()

    private fun setPeerList() {
        mList.distinct().forEach {
            mPeerArray.add(it)
            pList.add(SimpleModel(it))
        }
    }

    fun getPeerList() : ArrayList<String> {
        return mPeerArray
    }

    fun resetPeerList() {
        pList.clear()
        mPeerArray.clear()
    }

    override fun onStart() {
        Log.i(TAG, "POWER:onStart")

        super.onStart()
    }
    override fun onStop() {
        Log.i(TAG, "POWER:onStop")
        super.onStop()
    }
    override fun onResume() {
        Log.i(TAG, "POWER:onResume")
        super.onResume()
    }
    override fun onPause() {
        Log.i(TAG, "POWER:onPause")
        super.onPause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
        Log.i(TAG, "p2pStop:onDestroy")
        p2pStop()
    }

    companion object {
        const val TAG = "SHLEE-DIRECT"
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

    private var p2pBroadcastReceiver: BroadcastReceiver? = null
    private var p2pServiceRequest: WifiP2pDnsSdServiceRequest? = null

    private var testPingService: Timer? = null // 테스트용 핑
    private var mDeviceName: String = "DIRECT-LOWASIS-"

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
        val strMessage = "Connected $ip : $mDeviceName"

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
                    //Log.i(TAG, strText)
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
                        Log.d(TAG, "SHLEE : obj = ${msg.obj}, ${msg.what}, ip : ${msg.obj.toString().split(";")[0]}")
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
                            Log.i(TAG, "requestGroupInfo:$group, device name : $mDeviceName")
                            //if (group?.networkName == NETWORK_NAME) {
                            if (group?.networkName == mDeviceName) {
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
                        Log.d(TAG, "[WIFI-P2P] in MSG_CONNECT, name : $mDeviceName")
                        val config = WifiP2pConfig.Builder()
                            //.setNetworkName(NETWORK_NAME)
                            .setNetworkName(mDeviceName)
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
        p2pBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.action?.let { Log.d(TAG, it) }
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        setWifiP2pEnable(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                        Log.d(TAG, "[WIFI-P2P] WIFI 상태 변화체크.")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        resetPeerList()
                        Log.d(TAG, "[WIFI-P2P] Peers List변화 Device status -$intent")
                        p2pManager.requestPeers(p2pChannel) { peerList ->
                            for (device in peerList.deviceList) { // status:0이면 connect
                                Log.d(TAG, "[WIFI-P2P] device name :${device.deviceName} ${getStatus(device.status)}, ${device.status}")
                                if(device.deviceName.contains("LOWASIS")) {
                                    mList.add(device.deviceName)
                                    //pList.add(SimpleModel(device.deviceName))
                                    //setPeerList(device.deviceName)
                                }
                            }
                        }
                        setPeerList()
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        Log.d(TAG, "[WIFI-P2P] WIFI 상태변화")
                        p2pManager.requestConnectionInfo(p2pChannel) { info ->
                            Log.i(TAG, "*requestConnectionInfo:$info")
                            setWifiP2pConnect(info?.groupOwnerAddress?.hostAddress?.isNotBlank() == true)
                        }
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        Log.d(TAG, "[WIFI-P2P] 현재 Device상태 변화. ")
                    }
                }
            }
        }
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

    //람다.
    private val getStatus : (Int) -> (String) = {
        when(it) {
            WifiP2pDevice.AVAILABLE -> "사용가능"
            WifiP2pDevice.CONNECTED -> "연결"
            WifiP2pDevice.FAILED -> "실패"
            else -> "잘못됨."
        }
    }

    private fun startSearchServer() {
        if (p2pServiceRequest != null) {
            Log.i(TAG, "startSearchServer already run")
            return
        }
        gCnt++
        Log.i(TAG, "startSearchServer")
        p2pServiceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        p2pManager.setDnsSdResponseListeners(
            p2pChannel,
            { instanceName, registrationType, device ->
                Log.d(
                    TAG,
                    "[WIFI-P2P]start search server->DnsSdResponseListeners $instanceName $registrationType $device"
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

        //해당 메세지는 discoverPeer를 호출 하면 굳이 필요가 없을 것으로 판단된다.
        //테스트를 진행해보자. 애초에 연결할 목록이 있는지 주기적으로 요청해서 requestPeer가 호출되도록 하는 목적이었음.
        p2pHandler?.sendEmptyMessage(P2P_HANDLER_MSG_DISCOVER_SERVICE)
    }

    private fun stopSearchServer() {
        Log.d(TAG,"[WIFI-P2P] stopSerarchServer()")
        if (p2pServiceRequest != null) {
            p2pManager.removeServiceRequest(p2pChannel, p2pServiceRequest, null)
            p2pServiceRequest = null
        }
    }

    private fun restartApp() {
        val componentName = (this.packageManager).getLaunchIntentForPackage(packageName)?.component
        startActivity(Intent.makeRestartActivityTask(componentName))
        System.exit(0)
    }
}

