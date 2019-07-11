package com.skbmir.arcc

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    inner class ConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connErrMsg = intent.getStringExtra(EXTRA_CONNECT_ERR_MSG)
            if (connErrMsg.isNullOrEmpty()) {
                val intent = Intent(context, MainMenu::class.java)
                startActivity(intent)
            }
            else {
                val alert = AlertDialog.Builder(context).apply {
                    setTitle(getString(R.string.error_title))
                    setMessage(getString(R.string.connection_error_msg) + "\nОшибка:\n\t$connErrMsg")
                    setCancelable(true)
                }.create()
                alert.show()
            }
        }
    }

    var connectionReceiver = ConnectionReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filter = IntentFilter(CONNECT_REPORT).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        registerReceiver(connectionReceiver, filter)
    }

    fun connectToServer(view: View) {
        val address = addressBox.text.toString()
        val port = portBox.text.toString()
        val userName = userNameBox.text.toString()
        val password = passwordBox.text.toString()
        NATSClient.startActionConnect(this, userName, password, address, port)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(connectionReceiver)
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter(CONNECT_REPORT).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        registerReceiver(connectionReceiver, filter)
    }
}
