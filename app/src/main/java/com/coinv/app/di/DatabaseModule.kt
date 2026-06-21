package com.coinv.app.di

import android.content.Context
import androidx.room.Room
import com.coinv.app.data.local.CoinVDatabase
import com.coinv.app.data.local.MIGRATION_4_5
import com.coinv.app.data.local.MIGRATION_5_6
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoinVDatabase =
        Room.databaseBuilder(context, CoinVDatabase::class.java, "coinv.db")
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides fun provideConversationDao(db: CoinVDatabase) = db.conversationDao()
    @Provides fun provideMessageDao(db: CoinVDatabase) = db.messageDao()
    @Provides fun provideVaultMemoryDao(db: CoinVDatabase) = db.vaultMemoryDao()
    @Provides fun provideSemanticMemoryDao(db: CoinVDatabase) = db.semanticMemoryDao()
    @Provides fun provideGoalDao(db: CoinVDatabase) = db.goalDao()
    @Provides fun provideTaskDao(db: CoinVDatabase) = db.taskDao()
    @Provides fun provideDecisionDao(db: CoinVDatabase) = db.decisionDao()
    @Provides fun provideInsightDao(db: CoinVDatabase) = db.insightDao()
    @Provides fun provideLearningDao(db: CoinVDatabase) = db.learningDao()
    @Provides fun provideVoiceSessionDao(db: CoinVDatabase) = db.voiceSessionDao()
    @Provides fun provideProfileDao(db: CoinVDatabase) = db.profileDao()
    @Provides fun provideAnalyticsDao(db: CoinVDatabase) = db.analyticsDao()
    @Provides fun provideTimelineDao(db: CoinVDatabase) = db.timelineDao()
    @Provides fun provideRecommendationDao(db: CoinVDatabase) = db.recommendationDao()
    @Provides fun provideSeedDao(db: CoinVDatabase) = db.seedDao()
    @Provides fun provideContextEventDao(db: CoinVDatabase) = db.contextEventDao()
    @Provides fun provideFeedbackEventDao(db: CoinVDatabase) = db.feedbackEventDao()
    @Provides fun providePreferenceProfileDao(db: CoinVDatabase) = db.preferenceProfileDao()
    @Provides fun provideModeHistoryDao(db: CoinVDatabase) = db.modeHistoryDao()
    @Provides fun provideSuggestionScoreDao(db: CoinVDatabase) = db.suggestionScoreDao()
    @Provides fun provideInterventionDao(db: CoinVDatabase) = db.interventionDao()
    @Provides fun providePromiseDao(db: CoinVDatabase) = db.promiseDao()
}
