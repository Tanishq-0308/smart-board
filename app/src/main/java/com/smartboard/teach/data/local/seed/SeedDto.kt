package com.smartboard.teach.data.local.seed

import kotlinx.serialization.Serializable

/**
 * Shape of the JSON files in assets/seed.
 *
 * These stand in for the ERP payloads until Phase 2. Field names mirror what a
 * typical school ERP returns so the eventual DTO mapping is a small step.
 */

@Serializable
data class SeedTeacher(
    val id: String,
    val username: String,
    val displayName: String,
    /** Plain text in the seed file only; hashed before it reaches the DB. */
    val password: String,
    val email: String? = null,
    val remoteId: String? = null,
)

@Serializable
data class SeedClass(
    val id: String,
    val name: String,
    val section: String? = null,
    val subject: String? = null,
    val teacherId: String,
    val remoteId: String? = null,
    val studentIds: List<String> = emptyList(),
)

@Serializable
data class SeedStudent(
    val id: String,
    val rollNumber: String,
    val fullName: String,
    val remoteId: String? = null,
)

@Serializable
data class SeedMaterial(
    val id: String,
    val teacherId: String,
    val classId: String? = null,
    val title: String,
    val kind: String,
    /** Filename inside assets/seed/files, copied to app storage on demand. */
    val assetFile: String? = null,
    val remoteUrl: String? = null,
    val remoteId: String? = null,
)
