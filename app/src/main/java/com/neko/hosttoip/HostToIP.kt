package com.neko.hosttoip

import android.os.Bundle
import android.widget.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import org.json.JSONObject
import java.util.HashMap
import com.neko.hosttoip.RequestNetwork
import com.neko.hosttoip.RequestNetwork.RequestListener

class HostToIP : ThemedActivity() {

    private lateinit var hostInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var resultText: TextView

    private val requestNetwork: RequestNetwork by lazy { RequestNetwork(this) }

    private val requestListener = object : RequestNetwork.RequestListener {
        override fun onResponse(tag: String, response: String, responseHeaders: HashMap<String, Any>) {
            try {
                val json = JSONObject(response)
                
                val status = json.optString("status")

                if (status == "success") {
                    val ip = json.optString("query")
                    val country = json.optString("country", "Unknown")
                    val city = json.optString("city", "Unknown")
                    
                    val ispName = json.optString("isp", json.optString("org", "Unknown"))

                    val displayText = getString(
                        R.string.hip_result_format,
                        hostInput.text.toString(),
                        ip,
                        country,
                        city,
                        ispName
                    )

                    resultText.text = displayText
                } else {
                    val msg = json.optString("message", "Unknown Error")
                    resultText.text = "API Failed: $msg"
                }
            } catch (e: Exception) {
                resultText.text = "Parse Error: ${e.message}"
            }
        }

        override fun onErrorResponse(tag: String, message: String) {
            resultText.text = "Network Error: $message"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.uwu_host_to_ip)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.host_to_ip)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        hostInput = findViewById(R.id.host_input)
        resolveButton = findViewById(R.id.resolve_button)
        resultText = findViewById(R.id.result_text)

        resolveButton.setOnClickListener {
            val host = hostInput.text.toString().trim()
            if (host.isNotEmpty()) {
                resolveHostInfo(host)
            } else {
                Toast.makeText(this, getString(R.string.hip_error_empty), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveHostInfo(host: String) {
        resultText.text = getString(R.string.hip_resolving)

        val url = "http://ip-api.com/json/$host"

        requestNetwork.startRequestNetwork(
            RequestNetworkController.GET,
            url,
            "TAG_CHECK_IP",
            requestListener
        )
    }
}
