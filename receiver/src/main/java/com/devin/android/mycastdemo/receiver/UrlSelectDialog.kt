package com.devin.android.mycastdemo.receiver

import android.app.AlertDialog
import android.content.Context

class UrlPickerDialog(
    private val context: Context,
    private val urls: List<String>,
    private val onSelected: (String) -> Unit,
) {
    private var selectedIndex = 0

    fun show() {
        if (urls.isEmpty()) return

        AlertDialog.Builder(context)
            .setTitle("选择推流地址")
            .setSingleChoiceItems(urls.toTypedArray(), selectedIndex) { dialog, which ->
                selectedIndex = which
                onSelected(urls[which])
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}