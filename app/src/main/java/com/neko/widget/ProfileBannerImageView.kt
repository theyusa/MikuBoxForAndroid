package com.neko.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceDataStore
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener

class ProfileBannerImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr), OnPreferenceDataStoreChangeListener {

    private val KEY_URI = "profile_banner_uri"
    private val KEY_SHAPE = "profile_banner_shape"
    private val TAG_PROFILE_DEFAULT = "DEFAULT_BANNER_PROFILE"

    private var currentShapeId: Int = R.raw.uwu_shape_cookie

    override fun createImageViewHelper(): ShaderHelper {
        val shapeId = resolveShapeId()
        currentShapeId = shapeId
        return SvgShader(shapeId)
    }

    init {
        scaleType = ScaleType.CENTER_CROP
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        loadImage()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.registerChangeListener(this)
            checkAndUpdateShape()
            loadImage() 
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.unregisterChangeListener(this)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            KEY_URI -> post { loadImage() }
            KEY_SHAPE -> post { checkAndUpdateShape() }
        }
    }

    private fun resolveShapeId(): Int {
        return try {
            val shapeKey = DataStore.profileBannerShape
            when (shapeKey) {
                "uwu_shape_clover" -> R.raw.uwu_shape_clover
                "uwu_shape_circle" -> R.raw.uwu_shape_circle
                "uwu_shape_diamond" -> R.raw.uwu_shape_diamond
                "uwu_shape_pentagon" -> R.raw.uwu_shape_pentagon
                "uwu_shape_hexagon" -> R.raw.uwu_shape_hexagon
                "uwu_shape_octagon" -> R.raw.uwu_shape_octagon
                "uwu_shape_rounded_square" -> R.raw.uwu_shape_rounded_square
                "uwu_shape_squircle" -> R.raw.uwu_shape_squircle
                "uwu_shape_heart" -> R.raw.uwu_shape_heart
                else -> R.raw.uwu_shape_cookie
            }
        } catch (e: Exception) {
            R.raw.uwu_shape_cookie
        }
    }

    private fun checkAndUpdateShape() {
        try {
            val newShapeId = resolveShapeId()
            if (currentShapeId != newShapeId) {
                currentShapeId = newShapeId
                reloadShape()
                invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadImage() {
        try {
            val bannerUriString = DataStore.configurationStore.getString(KEY_URI, null)            
            val targetTag = if (bannerUriString.isNullOrEmpty()) TAG_PROFILE_DEFAULT else bannerUriString
            val currentTag = this.tag
            if (currentTag != targetTag) {        
                if (!bannerUriString.isNullOrEmpty()) {
                    val savedUri = Uri.parse(bannerUriString)
                    Glide.with(this)
                        .asBitmap()
                        .load(savedUri)
                        .downsample(DownsampleStrategy.NONE)
                        .format(DecodeFormat.PREFER_ARGB_8888)
                        .override(Target.SIZE_ORIGINAL)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .dontAnimate()
                        .error(R.drawable.uwu_banner_profile)
                        .into(this)
                } else {
                    loadDefault()
                }

                this.tag = targetTag
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (this.tag != TAG_PROFILE_DEFAULT) {
                loadDefault()
                this.tag = TAG_PROFILE_DEFAULT
            }
        }
    }
    private fun loadDefault() {
        Glide.with(this).clear(this)
        setImageResource(R.drawable.uwu_banner_profile)
    }
}
