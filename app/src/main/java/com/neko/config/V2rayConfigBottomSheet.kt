package com.neko.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import kotlinx.coroutines.*
import java.net.URL
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.group.RawUpdater
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import androidx.fragment.app.FragmentActivity

class V2rayConfigBottomSheet : BottomSheetDialogFragment() {

    private lateinit var btnGenerate: Button
    private lateinit var editLimit: EditText
    private lateinit var editServer: EditText
    private lateinit var editCountry: EditText
    private lateinit var autoCompleteProtocol: AutoCompleteTextView
    private lateinit var autoCompleteTls: AutoCompleteTextView
    private lateinit var autoCompleteSni: AutoCompleteTextView    
    private lateinit var inputLayoutSni: TextInputLayout 

    private var selectedProtocol: String = ""
    private var selectedTls: String = "true"
    private var selectedSni: String = "false"
    private var listProtocols: List<String> = emptyList()

    companion object {
        const val TAG = "V2rayConfigBottomSheet"
        const val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

        private val SOURCE_URLS = listOf(
            "https://www.afrcloud.site/api/subscription/v2ray?type=mix&domain=all",
            "https://afrcloud07.vercel.app/api/subscription/v2ray?type=mix&domain=all",
            "https://www.afrcloud.me/api/subscription/v2ray?type=mix&domain=all",
            "https://www.xlaxiata.biz.id/api/subscription/v2ray?type=mix&domain=all"
        )

        private val PROTOCOL_PREFIXES = listOf("vmess://", "vless://", "trojan://", "ss://")

        fun newInstance(): V2rayConfigBottomSheet {
            return V2rayConfigBottomSheet()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_free_config, container, false)
    }

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog
        sheetDialog?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val bannerImageView = view.findViewById<ImageView>(R.id.img_banner_sheet)
        
        if (bannerImageView != null) {
        	bannerImageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val bannerUriString = DataStore.configurationStore.getString("custom_sheet_banner_uri", null)
            val targetTag = if (bannerUriString.isNullOrBlank()) TAG_SHEET_DEFAULT else bannerUriString
            val currentTag = bannerImageView.tag
            if (currentTag != targetTag) {
                if (!bannerUriString.isNullOrBlank()) {
                	val bannerSavedUriString = Uri.parse(bannerUriString)
                    Glide.with(this)
                        .load(bannerSavedUriString)
                        .downsample(DownsampleStrategy.NONE)
                        .set(GifOptions.DECODE_FORMAT, DecodeFormat.PREFER_ARGB_8888)
                        .format(DecodeFormat.PREFER_ARGB_8888)
                        .override(Target.SIZE_ORIGINAL)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .skipMemoryCache(false)
                        .error(R.drawable.uwu_banner_image_about)
                        .into(bannerImageView)
                } else {
                    Glide.with(this).clear(bannerImageView)
                    bannerImageView.setImageResource(R.drawable.uwu_banner_image_about)
                }
                bannerImageView.tag = targetTag
            }
        }
        
        val particlesView = view.findViewById<View>(R.id.ParticlesView)
        if (particlesView != null) {
            if (DataStore.disableParticlesSheet) {
                particlesView.visibility = View.GONE
            } else {
                particlesView.visibility = View.VISIBLE
            }
        }
        
        initializeViews(view)
        setupMaterialDropdown()
        setupTlsDropdown()
        setupSniDropdown()
        setupButtonListeners()
    }

    private fun initializeViews(view: View) {
        btnGenerate = view.findViewById(R.id.btnGenerate) 
        editLimit = view.findViewById(R.id.editLimit)
        editServer = view.findViewById(R.id.editServer)
        editCountry = view.findViewById(R.id.editCountry)
        autoCompleteProtocol = view.findViewById(R.id.autoCompleteProtocol)
        autoCompleteTls = view.findViewById(R.id.autoCompleteTls)
        autoCompleteSni = view.findViewById(R.id.autoCompleteSni)
        inputLayoutSni = view.findViewById(R.id.inputLayoutSni)
    }

    private fun setupMaterialDropdown() {
        val labelAll = getString(R.string.protocol_all)
        listProtocols = listOf(labelAll, "VMess", "VLESS", "Trojan", "Shadowsocks")
        selectedProtocol = labelAll
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listProtocols)
        autoCompleteProtocol.setAdapter(adapter)
        autoCompleteProtocol.setText(selectedProtocol, false)
        autoCompleteProtocol.setOnItemClickListener { _, _, position, _ ->
            selectedProtocol = listProtocols[position]
        }
    }

    private fun setupTlsDropdown() {
        val labelTrue = getString(R.string.option_tls_true)
        val labelFalse = getString(R.string.option_tls_false)
        val tlsOptions = listOf(labelTrue, labelFalse)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tlsOptions)
        autoCompleteTls.setAdapter(adapter) 
        autoCompleteTls.setText(labelTrue, false)
        selectedTls = "true"
        inputLayoutSni.visibility = View.VISIBLE  
        autoCompleteTls.setOnItemClickListener { _, _, position, _ ->
            selectedTls = if (position == 0) "true" else "false"              
            if (selectedTls == "true") {
                inputLayoutSni.visibility = View.VISIBLE
            } else {
                inputLayoutSni.visibility = View.GONE
                selectedSni = "false"
                autoCompleteSni.setText(getString(R.string.option_sni_false), false)
            }
        }
    }

    private fun setupSniDropdown() {
        val labelTrue = getString(R.string.option_sni_true)
        val labelFalse = getString(R.string.option_sni_false)
        val sniOptions = listOf(labelFalse, labelTrue)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sniOptions)
        autoCompleteSni.setAdapter(adapter)    
        autoCompleteSni.setText(labelFalse, false)
        selectedSni = "false"
        autoCompleteSni.setOnItemClickListener { _, _, position, _ ->
            selectedSni = if (position == 1) "true" else "false"
        }
    }

    private fun setupButtonListeners() {
        btnGenerate.setOnClickListener {
            val protocol = if (selectedProtocol.isNotEmpty()) selectedProtocol else autoCompleteProtocol.text.toString()
            val limitStr = editLimit.text.toString()
            val limit = limitStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val tlsMode = if (selectedTls.isNotEmpty()) selectedTls else "true"
            val sniMode = if (tlsMode == "false") "false" else (if (selectedSni.isNotEmpty()) selectedSni else "false")
            val serverHost = editServer.text.toString().trim().ifEmpty { "www.google.com" }
            val countryCode = editCountry.text.toString().trim().ifEmpty { "SG" }
            val labelAll = getString(R.string.protocol_all)
            val parentActivity = requireActivity()
            val rootView = parentActivity.findViewById<View>(android.R.id.content)  
            Snackbar.make(rootView, R.string.msg_generating, Snackbar.LENGTH_SHORT).show()    
            dismiss() 
            fetchV2rayConfig(parentActivity, rootView, protocol, limit, tlsMode, sniMode, serverHost, countryCode, labelAll)
        }
    }

    private fun getPrefixForProtocol(protocolName: String): String {
        return when (protocolName) {
            "VMess" -> "vmess://"
            "VLESS" -> "vless://"
            "Trojan" -> "trojan://"
            "Shadowsocks" -> "ss://"
            else -> ""
        }
    }

    private fun fetchV2rayConfig(
        activity: FragmentActivity,
        rootView: View,
        protocol: String, 
        limit: Int, 
        tlsMode: String, 
        sniMode: String,
        serverHost: String,
        countryCode: String,
        labelAll: String
    ) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val requestLimit = if (limit < 10) 10 else limit
            var finalResult: String? = null
            var lastErrorMsg = ""
            for (baseUrl in SOURCE_URLS) {
                try {
                    val fullUrl = "$baseUrl&server=$serverHost&country=$countryCode&limit=$requestLimit&tls=$tlsMode&sni=$sniMode"
                    val rawContent = URL(fullUrl).openStream().bufferedReader().use { it.readText() }
                    val decodedContent = tryDecodeSubscription(rawContent)
                    val result = selectConfigs(decodedContent, protocol, limit, labelAll)
                    if (result != null) {
                        finalResult = result
                        break
                    }
                } catch (e: Exception) {
                    lastErrorMsg = e.message ?: "Unknown error"
                }
            }

            if (finalResult != null) {
                autoImportConfig(activity, rootView, finalResult)
            } else {
                withContext(Dispatchers.Main) {
                    val errorMsg = activity.getString(R.string.msg_fetch_failed, lastErrorMsg)
                    Snackbar.make(rootView, errorMsg, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun autoImportConfig(activity: FragmentActivity, rootView: View, configText: String) {
        activity.lifecycleScope.launch(Dispatchers.Default) {
            try {
                val proxies = RawUpdater.parseRaw(configText)         
                if (!proxies.isNullOrEmpty()) {
                    val targetId = DataStore.selectedGroup      
                    for (proxy in proxies) {
                        ProfileManager.createProfile(targetId, proxy)
                    }
                    withContext(Dispatchers.Main) {
                        Snackbar.make(rootView, R.string.msg_import_success, Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(rootView, R.string.msg_no_proxies, Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = activity.getString(R.string.msg_import_failed, e.message)
                    Snackbar.make(rootView, errorMsg, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tryDecodeSubscription(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.contains(" ") && !trimmed.contains("\n") && trimmed.length > 20) {
            return try {
                String(Base64.decode(trimmed, Base64.DEFAULT))
            } catch (e: Exception) { content }
        }
        return content
    }

    private fun selectConfigs(content: String, protocol: String, limit: Int, labelAll: String): String? {
        val targetPrefix = getPrefixForProtocol(protocol)
        
        val validLines = content.lines().asSequence().map { it.trim() }.filter { it.isNotBlank() }
            .filter { line ->
                if (protocol == labelAll) PROTOCOL_PREFIXES.any { line.startsWith(it) }
                else line.startsWith(targetPrefix)
            }.toList()

        if (validLines.isEmpty()) return null
        return validLines.shuffled().take(limit).joinToString("\n")
    }
}
