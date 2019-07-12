package com.skbmir.arcc

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_main_menu.*

class MainMenu : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        NATSClient.startActionSendCmd(this, MODE_HAND, 0f, 0f, GEAR_N, 0f)

        seekBar2.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar,
                                               progress: Int,
                                               fromUser: Boolean)
                {
                    if (fromUser) {
                        NATSClient.startActionSendCmd(applicationContext, progress, 0f, 0f, GEAR_N, 0f)
                    }
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {
                    //To change body of created functions use File | Settings | File Templates.
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                    //To change body of created functions use File | Settings | File Templates.
                }
            }
        )
    }

    fun openGearControl(view: View) {
        val intent = Intent(this, GearControlActivity::class.java)
        startActivity(intent)
    }
}
