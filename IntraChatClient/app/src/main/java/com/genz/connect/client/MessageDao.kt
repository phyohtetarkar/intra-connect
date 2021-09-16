package com.genz.connect.client

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {

   @Insert
   suspend fun insert(message: Message)

   @Query("DELETE FROM Message")
   suspend fun deleteAll()

   @Query("SELECT * FROM Message WHERE id = :id LIMIT 1")
   suspend fun findById(id: String): Message?

   @Query("SELECT * FROM Message ORDER BY send_at ASC")
   fun findAll(): DataSource.Factory<Int, Message>

}