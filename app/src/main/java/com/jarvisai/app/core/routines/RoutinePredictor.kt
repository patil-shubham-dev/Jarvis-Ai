package com.jarvisai.app.core.routines

import com.jarvisai.app.api.context.ContextEngine
import com.jarvisai.app.data.local.dao.RoutineDao
import com.jarvisai.app.data.models.RoutineEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutinePredictor @Inject constructor(
    private val contextEngine: ContextEngine,
    private val routineDao: RoutineDao
) {
    suspend fun getSuggestedRoutine(): RoutineEntity? {
        val app = contextEngine.getCurrentContext().lowercase()
        
        return when {
            app.contains("spotify") -> routineDao.findRoutine("%music%")
            app.contains("whatsapp") -> routineDao.findRoutine("%message%")
            app.contains("instagram") -> routineDao.findRoutine("%social%")
            else -> null
        }
    }
}
