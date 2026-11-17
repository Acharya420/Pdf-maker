package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.PdfHistoryRepository
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.CloudSyncRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfMakerApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var pdfHistoryRepository: PdfHistoryRepository
        private set
    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    val cloudSyncRepository: CloudSyncRepository by lazy {
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            FirebaseApp.initializeApp(applicationContext)
        }
        val firestoreDbId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(firestoreDbId)
        CloudSyncRepository(firestore)
    }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        pdfHistoryRepository = PdfHistoryRepository(database.pdfDocumentDao())
        userPreferencesRepository = UserPreferencesRepository(applicationContext)
    }
}
