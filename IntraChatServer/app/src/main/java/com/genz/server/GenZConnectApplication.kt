package com.genz.server

import android.app.Application

class GenZConnectApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        //HttpsURLConnection.setDefaultSSLSocketFactory(TLSSocketFactory())
    }

}