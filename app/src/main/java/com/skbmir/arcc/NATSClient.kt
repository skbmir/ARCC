package com.skbmir.arcc

import android.app.IntentService
import android.content.Intent
import android.content.Context
import io.nats.client.*
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageBufferPacker
import java.util.concurrent.CountDownLatch


// NATSClient actions
private const val ACTION_CONNECT = "com.skbmir.arcc.action.CONNECT"
private const val ACTION_DISCONNECT = "com.skbmir.arcc.action.DISCONNECT"
private const val ACTION_SEND_CMD = "com.skbmir.arcc.action.SEND_CMD"

// ACTION_CONNECT parameters
private const val EXTRA_ADDRESS = "com.skbmir.arcc.extra.ADDRESS"
private const val EXTRA_PORT = "com.skbmir.arcc.extra.PORT"
private const val EXTRA_USER_NAME = "com.skbmir.arcc.extra.USER_NAME"
private const val EXTRA_PASSWORD = "com.skbmir.arcc.extra.PASSWORD"
// ACTION_SEND_CMD parameters
private const val EXTRA_MODE = "com.skbmir.arcc.extra.MODE"
private const val EXTRA_GEAR = "com.skbmir.arcc.extra.GEAR"
private const val EXTRA_BRAKE = "com.skbmir.arcc.extra.BRAKE"
private const val EXTRA_CLUTCH = "com.skbmir.arcc.extra.CLUTCH"
private const val EXTRA_STEER = "com.skbmir.arcc.extra.STEER"

// Broadcast actions
const val CONNECT_REPORT = "com.skbmir.arcc.action.CONNECT_REPORT"
const val EXTRA_CONNECT_ERR_MSG = "com.skbmir.arcc.extra.CONNECT_ERR_MSG"

const val MSG_REPORT = "com.skbmir.arcc.action.MSG_REPORT"
const val EXTRA_SUBJECT = "com.skbmir.arcc.extra.SUBJECT"
const val EXTRA_DATA = "com.skbmir.arcc.extra.DATA"

// Control modes
const val MODE_HAND = 0
const val MODE_RC = 1
const val MODE_AUTO = 2

// Gear positions
const val GEAR_N = 0
const val GEAR_R = -1
const val GEAR_FIRST = 1
const val GEAR_SECOND = 2
const val GEAR_THIRD = 3
const val GEAR_FOURTH = 4
const val GEAR_FIFTH = 5

/**
 * An [IntentService] subclass for handling asynchronous NATS connection
 * as a service on a separate handler thread.
 */
class NATSClient : IntentService("NATSClient"), MessageHandler {

    //private lateinit var publisher:

    override fun onMessage(msg: Message?) {
        if (msg != null) {
            val subject = msg.subject
            val data = msg.data

            val report = Intent(MSG_REPORT).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_DATA, data)
            }
            sendBroadcast(report)
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                val port = intent.getStringExtra(EXTRA_PORT)
                val userName = intent.getStringExtra(EXTRA_USER_NAME)
                val password = intent.getStringExtra(EXTRA_PASSWORD)
                handleActionConnect(userName, password, address, port)
            }
            ACTION_DISCONNECT -> {
                handleActionDisconnect()
            }
            ACTION_SEND_CMD -> {
                var mode = intent.getIntExtra(EXTRA_MODE, MODE_HAND)
                if (mode < 0 || mode > 2)
                    mode = MODE_HAND

                var gear = intent.getIntExtra(EXTRA_GEAR, GEAR_N)
                if (gear < GEAR_R || gear > GEAR_FIFTH)
                    gear = GEAR_N

                var brake = intent.getFloatExtra(EXTRA_BRAKE, 0f)
                if (brake < 0f)
                    brake = 0f
                else if (brake > 1f)
                    brake = 1f

                var clutch = intent.getFloatExtra(EXTRA_CLUTCH, 0f)
                if (clutch < 0f)
                    clutch = 0f
                else if (clutch > 1f)
                    clutch = 1f

                var steer = intent.getFloatExtra(EXTRA_STEER, 0f)
                if (steer < -1f)
                    steer = -1f
                else if (steer > 1f)
                    steer = 1f

                handleActionSendCmd(mode, brake, clutch, gear, steer)
            }
        }
    }

    /**
     * Handle action connect in background
     */
    private fun handleActionConnect(userName:String, password: String, address: String, port: String) {
        val options = Options.Builder().server("nats://$address:$port").
                                    userInfo(userName, password).
                                    maxReconnects(-1).
                                    reconnectWait(Duration.ofMillis(500)).
                                    connectionTimeout(Duration.ofMillis(10000)).build()

        try {
            NATSClient.connection = Nats.connect(options)
            val report = Intent(CONNECT_REPORT).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(EXTRA_CONNECT_ERR_MSG, "")
            }
            sendBroadcast(report)

            NATSClient.latch = CountDownLatch(1)
            NATSClient.dispatcher = connection.createDispatcher(this)
        }
        catch (exc: Exception) {
            val report = Intent(CONNECT_REPORT).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(EXTRA_CONNECT_ERR_MSG, exc.toString())
            }
            sendBroadcast(report)
        }
    }

    private fun handleActionDisconnect() {
        NATSClient.connection.flush(Duration.ZERO)
        NATSClient.connection.close()
    }

    private fun handleActionSendCmd(mode: Int, brake: Float, clutch: Float, gear: Int, steer: Float) {
        val packer = MessagePack.newDefaultBufferPacker()

        packer.packMapHeader(5)
        // "mode" -> Int
        packer.packString("mode")
        packer.packInt(mode)
        // "brake" -> Float
        packer.packString("brake")
        packer.packFloat(brake)
        // "clutch" -> Float
        packer.packString("clutch")
        packer.packFloat(clutch)
        // "gear" -> Int
        packer.packString("gear")
        packer.packInt(gear)
        // "steer" -> Float
        packer.packString("steer")
        packer.packFloat(steer)
        packer.close()

        val data = packer.toByteArray()

        NATSClient.connection.publish("cmd", packer.toByteArray())
        NATSClient.connection.flush(Duration.ZERO)
    }

    companion object {
        // variable to handle NATS connection
        private lateinit var connection: Connection

        // latch to wait for messages from NATS server
        private lateinit var latch: CountDownLatch

        // received message dispatcher
        private lateinit var dispatcher: Dispatcher

        /**
         * Start this service to perform connection to NATS server. If the
         * service is already performing a task this action will be queued.
         * Must be performed before any other NATSClient actions.
         */
        @JvmStatic
        fun startActionConnect(context: Context, userName: String, password: String, address: String, port: String) {
            val intent = Intent(context, NATSClient::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_USER_NAME, userName)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_PORT, port)
            }
            context.startService(intent)
        }

        @JvmStatic
        fun startActionDisconnect(context: Context) {
            val intent = Intent(context, NATSClient::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }

        @JvmStatic
        fun startActionSendCmd(context: Context, mode: Int, brake: Float, clutch: Float, gear: Int, steer: Float) {
            val intent = Intent(context, NATSClient::class.java).apply {
                action = ACTION_SEND_CMD
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_GEAR, gear)
                putExtra(EXTRA_BRAKE, brake)
                putExtra(EXTRA_CLUTCH, clutch)
                putExtra(EXTRA_STEER, steer)
            }
            context.startService(intent)
        }
    }
}
