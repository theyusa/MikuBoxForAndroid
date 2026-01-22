package com.neko.hosttoip

import com.google.gson.Gson
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class RequestNetworkController {

    companion object {
        const val GET = "GET"
        const val POST = "POST"
        const val PUT = "PUT"
        const val DELETE = "DELETE"

        const val REQUEST_PARAM = 0
        const val REQUEST_BODY = 1

        private var mInstance: RequestNetworkController? = null

        @Synchronized
        fun getInstance(): RequestNetworkController {
            if (mInstance == null) {
                mInstance = RequestNetworkController()
            }
            return mInstance!!
        }
    }

    private val client: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()

        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        builder.connectTimeout(15, TimeUnit.SECONDS)
        builder.readTimeout(25, TimeUnit.SECONDS)
        builder.writeTimeout(25, TimeUnit.SECONDS)

        client = builder.build()
    }

    fun execute(
        requestNetwork: RequestNetwork,
        method: String,
        url: String,
        tag: String,
        listener: RequestNetwork.RequestListener
    ) {
        val reqBuilder = Request.Builder()
        val headerBuilder = Headers.Builder()

        requestNetwork.getHeaders().forEach { (key, value) ->
            headerBuilder.add(key, value.toString())
        }

        try {
            if (requestNetwork.getRequestType() == REQUEST_PARAM) {
                if (method == GET) {
                    // FIX 1: HttpUrl.parse -> url.toHttpUrlOrNull()
                    val httpUrl = url.toHttpUrlOrNull()?.newBuilder()
                    if (httpUrl != null) {
                        requestNetwork.getParams().forEach { (key, value) ->
                            httpUrl.addQueryParameter(key, value.toString())
                        }
                        reqBuilder.url(httpUrl.build())
                        reqBuilder.headers(headerBuilder.build())
                        reqBuilder.get()
                    }
                } else {
                    val formBody = FormBody.Builder()
                    requestNetwork.getParams().forEach { (key, value) ->
                        formBody.add(key, value.toString())
                    }
                    reqBuilder.url(url)
                    reqBuilder.headers(headerBuilder.build())
                    reqBuilder.method(method, formBody.build())
                }
            } else {
                val jsonBody = Gson().toJson(requestNetwork.getParams())
                
                val mediaType = "application/json".toMediaTypeOrNull()
                
                val body = jsonBody.toRequestBody(mediaType)

                if (method == GET) {
                    reqBuilder.url(url)
                        .headers(headerBuilder.build())
                        .get()
                } else {
                    reqBuilder.url(url)
                        .headers(headerBuilder.build())
                        .method(method, body)
                }
            }

            client.newCall(reqBuilder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    requestNetwork.getActivity().runOnUiThread {
                        listener.onErrorResponse(tag, e.message ?: "Unknown Error")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val bodyString = response.body?.string()?.trim() ?: ""
                    
                    val headersMap = HashMap<String, Any>()
                    
                    response.headers.names().forEach { name ->
                        headersMap[name] = response.headers[name] ?: "null"
                    }

                    requestNetwork.getActivity().runOnUiThread {
                        listener.onResponse(tag, bodyString, headersMap)
                    }
                }
            })

        } catch (e: Exception) {
            listener.onErrorResponse(tag, e.message ?: "Error Executing Request")
        }
    }
}
