package com.reconvene.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PodEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class ReconveneDatabase : RoomDatabase() {
    abstract fun podDao(): PodDao
}
