package com.neko.hosttoip

import android.app.Activity
import java.util.HashMap

class RequestNetwork(private val activity: Activity) {

    private var params = HashMap<String, Any>()
    private var headers = HashMap<String, Any>()
    private var requestType = 0

    interface RequestListener {
        fun onResponse(tag: String, response: String, responseHeaders: HashMap<String, Any>)
        fun onErrorResponse(tag: String, message: String)
    }

    fun setHeaders(headers: HashMap<String, Any>) {
        this.headers = headers
    }

    fun setParams(params: HashMap<String, Any>, requestType: Int) {
        this.params = params
        this.requestType = requestType
    }

    fun getParams(): HashMap<String, Any> {
        return params
    }

    fun getHeaders(): HashMap<String, Any> {
        return headers
    }

    fun getRequestType(): Int {
        return requestType
    }

    fun getActivity(): Activity {
        return activity
    }

    fun startRequestNetwork(method: String, url: String, tag: String, listener: RequestListener) {
        RequestNetworkController.getInstance().execute(this, method, url, tag, listener)
    }
}
