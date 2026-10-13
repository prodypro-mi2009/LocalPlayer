package com.localplayer.music.data.update

import android.content.Context
import com.localplayer.music.R

/** Version and repository of THIS app, read from the installed package and from res/values/config.xml. */
object AppInfo {
    fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** "owner/name" of the official GitHub repository, or "" when it has not been configured. */
    fun repo(context: Context): String =
        context.getString(R.string.github_repo).trim().takeIf { UpdateRules.isValidRepo(it) }.orEmpty()

    fun repoUrl(context: Context): String? = repo(context).takeIf { it.isNotEmpty() }?.let { "https://github.com/$it" }

    /** Identifies the app to LRCLIB / GitHub (both ask for a User-Agent). No device or user information. */
    fun userAgent(context: Context): String = "LocalPlayer/${versionName(context).ifEmpty { "1" }}"
}
