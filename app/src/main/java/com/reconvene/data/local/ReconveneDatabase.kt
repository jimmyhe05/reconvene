package com.reconvene.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Version 2 added host uid and members, version 3 groups and their chat. Room is only a cache of
// Firestore, so AppContainer rebuilds it on a version change instead of migrating; everything
// comes back on the next refresh or sync.
@Database(entities = [PodEntity::class, GroupEntity::class, MessageEntity::class], version = 3)
@TypeConverters(Converters::class)
abstract class ReconveneDatabase : RoomDatabase() {
    abstract fun podDao(): PodDao

    abstract fun groupDao(): GroupDao

    abstract fun messageDao(): MessageDao
}
