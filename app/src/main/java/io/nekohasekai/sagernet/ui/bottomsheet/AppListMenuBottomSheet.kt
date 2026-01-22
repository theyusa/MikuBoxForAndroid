package io.nekohasekai.sagernet.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

class AppListMenuBottomSheet : BottomSheetDialogFragment() {

    interface OnOptionClickListener {
        fun onOptionClicked(viewId: Int)
    }

    private var mListener: OnOptionClickListener? = null
    
    private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnOptionClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_app_manager_menu, container, false)
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

        val clickListener = View.OnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }

        val actionIds = listOf(
            R.id.action_invert_selections,
            R.id.action_clear_selections,
            R.id.action_export_clipboard,
            R.id.action_import_clipboard
        )

        actionIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "AppListMenuBottomSheet"
    }
}
