package moe.matsuri.nb4a.ui

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.flaviofaria.kenburnsview.KenBurnsView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

class CustomBannerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private val TAG_PREFERENCE_DEFAULT = "DEFAULT_BANNER_PREFERENCE"

    init {
        layoutResource = R.layout.uwu_banner_theme
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.setIsRecyclable(false)

        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false

        val bannerImageView = holder.findViewById(R.id.img_banner_preference) as? KenBurnsView

        if (bannerImageView != null) {
            bannerImageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val bannerUriString = DataStore.configurationStore.getString("custom_preference_banner_uri", null)
            val targetTag = if (bannerUriString.isNullOrBlank()) TAG_PREFERENCE_DEFAULT else bannerUriString
            val currentTag = bannerImageView.tag
            if (currentTag != targetTag) {
                if (!bannerUriString.isNullOrBlank()) {
                    val bannerSavedUriString = Uri.parse(bannerUriString)
                    Glide.with(context)
                        .load(bannerSavedUriString)
                        .downsample(DownsampleStrategy.NONE)
                        .set(GifOptions.DECODE_FORMAT, DecodeFormat.PREFER_ARGB_8888)
                        .format(DecodeFormat.PREFER_ARGB_8888)
                        .override(Target.SIZE_ORIGINAL)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .skipMemoryCache(false)
                        .error(R.drawable.uwu_banner_image)
                        .into(bannerImageView)
                } else {
                    Glide.with(context).clear(bannerImageView)
                    bannerImageView.setImageResource(R.drawable.uwu_banner_image)
                }
                bannerImageView.tag = targetTag
            }
        }

        (holder.findViewById(R.id.uwu_version_name_summary) as? TextView)?.text = BuildConfig.VERSION_NAME
        (holder.findViewById(R.id.uwu_version_code_summary) as? TextView)?.text = BuildConfig.VERSION_CODE.toString()
        (holder.findViewById(R.id.uwu_build_date_summary) as? TextView)?.text = BuildConfig.BUILD_DATE
        (holder.findViewById(R.id.uwu_package_name_summary) as? TextView)?.text = BuildConfig.APPLICATION_ID

        val clickTarget = holder.findViewById(R.id.onClick)
        clickTarget?.setOnClickListener {
            this.performClick()
        }
    }
}
