package com.altstudio.kirana.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Product::class, Invoice::class, SaleItem::class, Repayment::class, Customer::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class KiranaDatabase : RoomDatabase() {
    abstract fun kiranaDao(): KiranaDao

    companion object {
        @Volatile
        private var Instance: KiranaDatabase? = null

        fun getDatabase(context: Context): KiranaDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, KiranaDatabase::class.java, "kirana_database")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
