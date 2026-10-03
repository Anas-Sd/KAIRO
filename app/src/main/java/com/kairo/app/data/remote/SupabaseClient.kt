package com.kairo.app.data.remote

/**
 * Backward compatibility forwarder for [com.kairo.app.feature.sync.SupabaseClient].
 * All remote database communication now lives in the Cloud Sync feature box `com.kairo.app.feature.sync`.
 */
object SupabaseClient {

    suspend fun validateAccessCode(code: String): Result<com.kairo.app.feature.sync.AccessCodeDto> =
        com.kairo.app.feature.sync.SupabaseClient.validateAccessCode(code)

    suspend fun rotateAccessCode(oldCode: String, newCode: String): Result<Unit> =
        com.kairo.app.feature.sync.SupabaseClient.rotateAccessCode(oldCode, newCode)

    suspend fun deleteAllUserData(userCode: String): Result<Unit> =
        com.kairo.app.feature.sync.SupabaseClient.deleteAllUserData(userCode)

    suspend fun deleteUserCodeAndData(userCode: String): Result<Unit> =
        com.kairo.app.feature.sync.SupabaseClient.deleteUserCodeAndData(userCode)

    suspend fun getTasks(userCode: String?): Result<List<com.kairo.app.feature.sync.TaskDto>> =
        com.kairo.app.feature.sync.SupabaseClient.getTasks(userCode)

    suspend fun insertTask(taskDto: com.kairo.app.feature.sync.TaskDto): Result<Unit> =
        com.kairo.app.feature.sync.SupabaseClient.insertTask(taskDto)

    suspend fun updateTask(taskDto: com.kairo.app.feature.sync.TaskDto): Result<Unit> =
        com.kairo.app.feature.sync.SupabaseClient.updateTask(taskDto)

    suspend fun deleteTask(taskId: String): Result<Unit> =
        com.kairo.app.feature.sync.SupabaseClient.deleteTask(taskId)
}
