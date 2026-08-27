package com.coinv.app.data.local

import com.coinv.app.data.local.dao.ProfileDao
import com.coinv.app.data.local.dao.PreferenceProfileDao
import com.coinv.app.data.local.dao.SeedDao
import com.coinv.app.data.local.entity.PreferenceProfileEntity
import com.coinv.app.data.local.entity.UserProfileEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileBootstrap @Inject constructor(
    private val seedDao: SeedDao,
    private val profileDao: ProfileDao,
    private val preferenceProfileDao: PreferenceProfileDao
) {
    suspend fun ensureProfile() {
        if (seedDao.profileCount() > 0) return
        profileDao.insert(
            UserProfileEntity(
                name = "Shyam",
                cognitiveState = "Ready",
                activeCoach = "Founder Coach",
                listeningMode = "push_to_talk"
            )
        )
        preferenceProfileDao.upsert(PreferenceProfileEntity())
    }
}
