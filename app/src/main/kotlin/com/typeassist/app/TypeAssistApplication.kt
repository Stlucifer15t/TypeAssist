package com.typeassist.app

import android.app.Application
import com.typeassist.app.utils.CrashLog

/**
 * ============================== APP START ==============================
 *
 * The one thing that has to happen before anything else: install the crash reporter. It runs for
 * every process start, including the accessibility service being started without the app being
 * opened, which is where a silent crash would otherwise leave no trace at all.
 */
class TypeAssistApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
