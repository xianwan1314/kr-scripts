package com.projectkr.shell

import android.app.Activity
import android.view.View
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper

class DialogPower(var context: Activity) {
    fun showPowerMenu() {
        val view = context.layoutInflater.inflate(R.layout.dialog_power_operation, null)
        val dialog = DialogHelper.customDialog(context, view)
        fun onclick(cmd:String){
            dialog.dismiss()
            KeepShellPublic.doCmdSync(cmd)
        }
        view.findViewById<View>(R.id.power_shutdown).setOnClickListener { onclick(context.getString(R.string.power_shutdown_cmd)) }
        view.findViewById<View>(R.id.power_reboot).setOnClickListener { onclick(context.getString(R.string.power_reboot_cmd)) }
        view.findViewById<View>(R.id.power_hot_reboot).setOnClickListener { onclick(context.getString(R.string.power_hot_reboot_cmd)) }
        view.findViewById<View>(R.id.power_recovery).setOnClickListener { onclick(context.getString(R.string.power_recovery_cmd)) }
        view.findViewById<View>(R.id.power_fastboot).setOnClickListener { onclick(context.getString(R.string.power_fastboot_cmd)) }
        view.findViewById<View>(R.id.power_emergency).setOnClickListener { onclick(context.getString(R.string.power_emergency_cmd)) }
    }
}
