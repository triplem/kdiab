package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.domain.model.Timeline

interface TimelineOperation {
    suspend fun getTimeline(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        correlationId: String,
    ): Timeline
}
