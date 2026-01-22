package io.nekohasekai.sagernet.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageView
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

private const val TRANSITION_DURATION = 300L

private fun View.toggleWithTransition(parentView: ViewGroup, isExpanding: Boolean) {
    TransitionManager.beginDelayedTransition(parentView, AutoTransition().setDuration(TRANSITION_DURATION))
    this.visibility = if (isExpanding) View.VISIBLE else View.GONE
}

private fun View.animateRotation(endDegree: Float) {
    this.animate()
        .rotation(endDegree)
        .setDuration(TRANSITION_DURATION)
        .start()
}

class OtherMenuBottomSheet : BottomSheetDialogFragment() {

    interface OnOtherOptionClickListener {
        fun onOtherOptionClicked(viewId: Int)
    }

    private var mListener: OnOtherOptionClickListener? = null
    
    private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"
    
    private var currentOrder: Int = GroupOrder.ORIGIN

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (parentFragment is OnOtherOptionClickListener) {
            mListener = parentFragment as OnOtherOptionClickListener
        } else {
            mListener = try {
                context as OnOtherOptionClickListener
            } catch (e: ClassCastException) {
                throw RuntimeException("$context must implement OnOtherOptionClickListener")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentOrder = arguments?.getInt(ARG_CURRENT_ORDER) ?: GroupOrder.ORIGIN
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_other_menu, container, false)
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

        val checkOrigin = view.findViewById<CheckedTextView>(R.id.action_order_origin)
        val checkName = view.findViewById<CheckedTextView>(R.id.action_order_by_name)
        val checkDelay = view.findViewById<CheckedTextView>(R.id.action_order_by_delay)

        when (currentOrder) {
            GroupOrder.ORIGIN -> checkOrigin?.isChecked = true
            GroupOrder.BY_NAME -> checkName?.isChecked = true
            GroupOrder.BY_DELAY -> checkDelay?.isChecked = true
        }
        
        val menuContainerParent = view.findViewById<ViewGroup>(R.id.menu_container_parent) ?: view as ViewGroup

        setupExpandable(
            parent = menuContainerParent,
            toggleHeader = view.findViewById(R.id.action_group_setting_1),
            expandableContent = view.findViewById(R.id.expandable_setting_content_1),
            arrowIcon = view.findViewById(R.id.arrow_icon_1)
        )
        
        setupExpandable(
            parent = menuContainerParent,
            toggleHeader = view.findViewById(R.id.action_group_setting_2),
            expandableContent = view.findViewById(R.id.expandable_setting_content_2),
            arrowIcon = view.findViewById(R.id.arrow_icon_2)
        )

        val clickListener = View.OnClickListener {
            mListener?.onOtherOptionClicked(it.id)
            dismiss()
        }

        val viewIds = listOf(
            R.id.action_update_subscription,
            R.id.action_clear_traffic_statistics,
            R.id.action_connection_test_clear_results,
            R.id.action_connection_test_delete_unavailable,
            R.id.action_remove_duplicate,
            R.id.action_connection_tcp_ping,
            R.id.action_connection_url_test,
            R.id.action_order_origin,
            R.id.action_order_by_name,
            R.id.action_order_by_delay
        )

        viewIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    private fun setupExpandable(parent: ViewGroup, toggleHeader: View?, expandableContent: View?, arrowIcon: ImageView?) {
        if (toggleHeader != null && expandableContent != null) {
            
            if (arrowIcon != null) {
                arrowIcon.rotation = if (expandableContent.visibility == View.VISIBLE) 90f else 0f
            }
            
            toggleHeader.setOnClickListener {
                val isExpanding = expandableContent.visibility == View.GONE

                if (isExpanding) {
                    expandableContent.toggleWithTransition(parent, true)
                    arrowIcon?.animateRotation(90f)
                } else {
                    expandableContent.toggleWithTransition(parent, false)
                    arrowIcon?.animateRotation(0f)
                }
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "OtherMenuBottomSheet"
        
        private const val ARG_CURRENT_ORDER = "current_order"

        fun newInstance(currentOrder: Int): OtherMenuBottomSheet {
            val fragment = OtherMenuBottomSheet()
            val args = Bundle()
            args.putInt(ARG_CURRENT_ORDER, currentOrder)
            fragment.arguments = args
            return fragment
        }
    }
}
