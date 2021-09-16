package com.intrachat.connect

import android.app.Application
import androidx.room.Room
import com.intrachat.connect.client.AppDatabase

class GenZConnectApplication : Application() {

    private lateinit var _db: AppDatabase

    val db: AppDatabase
        get() = _db

    override fun onCreate() {
        super.onCreate()

        _db = Room.databaseBuilder(this, AppDatabase::class.java, "genz-connect").build()
    }

}