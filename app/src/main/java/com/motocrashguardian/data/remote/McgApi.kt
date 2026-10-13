package com.motocrashguardian.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * API v1 del backend. Devuelve [Response] para que la sincronizacion distinga 401, errores
 * permanentes (4xx) y transitorios (5xx/429) segun specs/features/data-synchronization.md.
 * Las respuestas que no se usan llegan como [ResponseBody] y se descartan.
 */
interface McgApi {
    @POST("api/v1/installations")
    suspend fun registerInstallation(
        @Body request: RegisterInstallationRequest
    ): Response<RegisterInstallationResponse>

    @PUT("api/v1/me/config")
    suspend fun putConfig(@Body request: UserConfigRequest): Response<ResponseBody>

    @PUT("api/v1/me/contacts")
    suspend fun putContacts(@Body request: UpdateContactsRequest): Response<ResponseBody>

    @PUT("api/v1/me/incidents/{id}")
    suspend fun putIncident(
        @Path("id") id: String,
        @Body request: IncidentUpsertRequest
    ): Response<ResponseBody>

    @PUT("api/v1/me/incidents/{id}/trace")
    suspend fun putTrace(
        @Path("id") id: String,
        @Body request: IncidentTraceRequest
    ): Response<ResponseBody>

    /** Borra la instalacion y todos sus datos en el servidor (derecho de supresion). */
    @DELETE("api/v1/me")
    suspend fun deleteInstallation(): Response<ResponseBody>
}
