package com.example.android.wifidirect.wifip2pclient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDate

class MainActivity : AppCompatActivity(), ConnectionInfoListener {
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var serviceRequest: WifiP2pDnsSdServiceRequest

    class WiFiP2pService {
        var device: WifiP2pDevice? = null
        var instanceName: String? = null
        var serviceRegistrationType: String? = null
    }

    val testService: WiFiP2pService = WiFiP2pService()
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Fine location permission is not granted!")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "requestPermissions")
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_REQUEST_CODE
            )
        } else {
            Log.i(TAG, "discoverService")
            discoverService()
        }
    }

    private var receiver: BroadcastReceiver? = null
    override fun onResume() {
        super.onResume()

        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter
            .addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter
            .addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun connectP2p(service: WiFiP2pService) {
        Log.i(TAG, "connectP2p")

        Log.i(TAG, "deviceName:" + service.device!!.deviceName)
        //Log.i(TAG, "deviceName:" + service.device!!.wfdInfo?.toString())
        Log.i(TAG, "isGroupOwner:" + service.device!!.isGroupOwner)
        manager.removeServiceRequest(channel, serviceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(arg0: Int) {}
            })
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "ACCESS_FINE_LOCATION permission error")
            return
        }

        val config = WifiP2pConfig.Builder()
            .setNetworkName("DIRECT-TUNER14")
            .setPassphrase("pass1234")
            .enablePersistentMode(true)
            .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ)
            .build()

        //val config = WifiP2pConfig()
        config.deviceAddress = service.device!!.deviceAddress
        //config.passphrase
        Log.i(TAG, "config.deviceAddress:${config.deviceAddress}")
        //config.wps.setup =  WpsInfo.PBC
        //config.wps.pin = "1234"
        //config. ..passphrase = "pass1234"

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "connect onSuccess")
            }

            override fun onFailure(errorCode: Int) {
                Log.i(TAG, "connect onFailure")
            }
        })
    }

    private fun discoverService() {


        manager.setDnsSdResponseListeners(channel,
            { instanceName, registrationType, srcDevice ->
                Log.d(TAG, "setDnsSdResponseListeners1 $instanceName $registrationType $srcDevice")
                if (instanceName.equals(SERVICE_INSTANCE, ignoreCase = true)) {
                    Log.d(TAG, "THIS IS MY SERVICE")
                    testService.device = srcDevice
                    testService.instanceName = instanceName
                    testService.serviceRegistrationType = registrationType
                    connectP2p(testService)
                }
            },
            { fullDomainName, record, device ->
                Log.d(TAG, "setDnsSdResponseListeners2 $fullDomainName $record $device")
            })

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(
            channel,
            serviceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "addServiceRequeston Success ")
                }

                override fun onFailure(code: Int) {
                    // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    Log.i(TAG, "addServiceRequeston onFailure ")
                }
            }
        )
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "ACCESS_FINE_LOCATION !PERMISSION_GRANTED ")
            return
        }
        manager.discoverServices(
            channel,
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

    companion object {
        const val TAG = "P2PClient"

        const val SERVER_PORT = 4669
        const val PERMISSIONS_REQUEST_CODE = 1001

        //const val RECORD_PROP_AVAILABLE = "available"
        const val SERVICE_INSTANCE = "_wifidemotest"

        //const val SERVICE_REG_TYPE = "_presence._tcp"
        const val MESSAGE_READ = 0x400 + 1
        const val MY_HANDLE = 0x400 + 2
    }

    private val msgHandler = Handler(Looper.getMainLooper()) {
        Log.i(TAG, it.toString())
        when (it.what) {
            MESSAGE_READ -> {
                Log.i(TAG, "MESSAGE_READ" + it.arg1 + " " + it.arg2)
            }
        }
        return@Handler true
    }

    override fun onConnectionInfoAvailable(p2pInfo: WifiP2pInfo) {
        Log.i(TAG, "onConnectionInfoAvailable $p2pInfo")
        val handler: Thread = ClientSocketHandler(msgHandler, p2pInfo.groupOwnerAddress)
        handler.start()
    }

    class ClientSocketHandler(private val handler: Handler, private val mAddress: InetAddress) :
        Thread() {
        private var chat: ChatManager? = null
        override fun run() {
            val socket = Socket()
            try {
                socket.bind(null)
                socket.connect(
                    InetSocketAddress(
                        mAddress.hostAddress,
                        SERVER_PORT
                    ), 5000
                )
                Log.d(TAG, "${mAddress.hostAddress} $SERVER_PORT Launching the I/O handler")
                chat = ChatManager(socket, handler)
                Thread(chat).start()
            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    socket.close()
                } catch (e1: IOException) {
                    e1.printStackTrace()
                }
                return
            }
        }

        fun getChat(): ChatManager? {
            return chat
        }

        companion object {
            private const val TAG = "ClientSocketHandler"
        }
    }

    class ChatManager(private val socket: Socket, private val handler: Handler) :
        Runnable {
        //private val socket: Socket? = null
        //private val handler: Handler
        private lateinit var iStream: InputStream
        private lateinit var oStream: OutputStream
        @RequiresApi(Build.VERSION_CODES.O)
        override fun run() {
            try {
                iStream = socket.getInputStream()
                oStream = socket.getOutputStream()
                val buffer = ByteArray(1024)
                var bytes: Int
                //handler.obtainMessage(MY_HANDLE, this)
                //    .sendToTarget()
                write(LocalDate.now().toString())
                while (true) {
                    try {
                        // Read from the InputStream
                        bytes = iStream.read(buffer)
                        if (bytes == -1) {
                            break
                        }

                        // Send the obtained bytes to the UI Activity
                        Log.d(TAG, "Rec:" + String(buffer))
                        handler.obtainMessage(
                            MESSAGE_READ,
                            bytes, -1, buffer
                        ).sendToTarget()
                    } catch (e: IOException) {
                        Log.e(TAG, "disconnected", e)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        private fun write(msg: String) {
            val buffer = msg.toByteArray()
            val thread: Thread = object : Thread() {
                override fun run() {
                    try {
                        oStream.write(buffer)
                    } catch (e: IOException) {
                        Log.e(TAG, "Exception during write", e)
                    }
                }
            }
            thread.start()
        }

        companion object {
            private const val TAG = "ChatHandler"
        }

        init {
            //this.socket = socket
            //this.handler = handler
        }
    }


}