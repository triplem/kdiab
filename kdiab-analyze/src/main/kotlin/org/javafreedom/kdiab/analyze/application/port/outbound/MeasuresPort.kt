package org.javafreedom.kdiab.analyze.application.port.outbound

import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure

interface MeasuresPort {
    suspend fun getMeasures(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<UpstreamMeasure>
}
