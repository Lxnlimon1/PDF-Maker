package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferences
import com.example.data.repository.PdfLibraryRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfMasterApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: UserPreferences
        private set

    lateinit var repository: PdfLibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "pdf_master.db"
        ).build()
        preferences = UserPreferences(applicationContext)
        repository = PdfLibraryRepository(
            context = applicationContext,
            docDao = database.pdfDocumentDao(),
            signatureDao = database.savedSignatureDao()
        )
    }
}
