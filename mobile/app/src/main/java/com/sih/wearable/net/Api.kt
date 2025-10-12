package com.sih.wearable.net

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Api {
    var baseUrl = "http://10.0.2.2:8000" // emulator -> host
    var apiKey = "dev-secret-key"

    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    fun ingest(uid: String, team: String, packets: List<JSONObject>): Boolean {
        val body = JSONObject().apply {
            put("uid", uid); put("team", team); put("packets", JSONArray(packets))
        }.toString()
        val req = Request.Builder()
            .url("${baseUrl}/ingest?api_key=${apiKey}")
            .post(RequestBody.create(MediaType.parse("application/json"), body))
            .build()
        client.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }
}
