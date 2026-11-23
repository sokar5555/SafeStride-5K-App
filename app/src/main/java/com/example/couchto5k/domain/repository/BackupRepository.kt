package com.example.couchto5k.domain.repository

import com.example.couchto5k.domain.model.AppBackupData
import com.example.couchto5k.domain.model.BackupSummary

interface BackupRepository {
    suspend fun exportBackupToUri(uriString: String): Result<BackupSummary>
    suspend fun parseAndValidateBackupFromUri(uriString: String): Result<AppBackupData>
    suspend fun restoreBackup(backup: AppBackupData): Result<BackupSummary>
}
