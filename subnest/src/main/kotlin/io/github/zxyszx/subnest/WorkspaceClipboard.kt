package io.github.zxyszx.subnest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

fun copyWorkspaceText(context: Context, value: String, sensitive: Boolean = false) {
    if (value.isBlank()) return
    val clip = ClipData.newPlainText(context.getString(R.string.app_name), value)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
