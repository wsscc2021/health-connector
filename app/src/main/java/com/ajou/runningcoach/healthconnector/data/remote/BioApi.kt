package com.ajou.runningcoach.healthconnector.data.remote

import com.ajou.runningcoach.healthconnector.data.model.BioEventRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BioApi {
    @POST("/v1/bio/events")
    suspend fun postBioEvents(@Body request: BioEventRequest): Response<Unit>
}
