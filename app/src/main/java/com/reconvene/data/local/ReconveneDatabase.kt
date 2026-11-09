package com.reconvene.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Version 2 added host uid and members. Room is only a cache of Firestore, so AppContainer
// rebuilds it on a version change instead of migrating; pods come back on the next refresh.
@Database(entities = [PodEntity::class], version = 2)
@TypeConverters(Converters::class)
abstract class ReconveneDatabase : RoomDatabase() {
    abstract fun podDao(): PodDao
}
