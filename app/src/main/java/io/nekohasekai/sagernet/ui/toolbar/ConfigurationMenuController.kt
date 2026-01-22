package io.nekohasekai.sagernet.ui.toolbar

import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.bottomsheet.ProfileMenuBottomSheet
import io.nekohasekai.sagernet.ui.bottomsheet.OtherMenuBottomSheet
import io.nekohasekai.sagernet.ui.ConfigurationFragment

class ConfigurationMenuController(
    private val toolbar: Toolbar,
    private val fragmentManager: FragmentManager,
    private val fragment: ConfigurationFragment
) {

    private var lastStateWasDirect: Boolean? = null

    init {
        refresh()
    }

    fun refresh() {
        val useDirectMenu = DataStore.disableBottomSheetHome
        if (lastStateWasDirect == useDirectMenu) return

        lastStateWasDirect = useDirectMenu
        updateToolbar(useDirectMenu)
    }

    private fun updateToolbar(useDirectMenu: Boolean) {
        toolbar.menu.clear()
        toolbar.setOnMenuItemClickListener(null)

        if (useDirectMenu) {
            toolbar.inflateMenu(R.menu.configuration_menu)
            
            toolbar.setOnMenuItemClickListener { item ->
                val anchorView = toolbar.findViewById<View>(item.itemId) ?: toolbar

                when (item.itemId) {
                    R.id.trigger_add_popup -> {
                        showAddPopup(anchorView)
                    }
                    R.id.trigger_other_popup -> {
                        showOtherPopup(anchorView)
                    }
                }
                true
            }
        } else {
            toolbar.inflateMenu(R.menu.uwu_configuration_menu_sheet)
            toolbar.setOnMenuItemClickListener(fragment)
        }
        
        fragment.setupSearchView()
    }

    private fun showAddPopup(anchor: View) {
        val popup = PopupMenu(fragment.requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.popup_profile_add, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            fragment.onOptionClicked(item.itemId)
            true
        }
        popup.show()
    }

    private fun showOtherPopup(anchor: View) {
        val popup = PopupMenu(fragment.requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.popup_profile_other, popup.menu)

        val currentGroupFragment = fragment.getCurrentGroupFragment()
        val currentOrder = currentGroupFragment?.proxyGroup?.order

        if (currentOrder != null) {
            when (currentOrder) {
                GroupOrder.ORIGIN -> 
                    popup.menu.findItem(R.id.action_order_origin)?.isChecked = true
                GroupOrder.BY_NAME -> 
                    popup.menu.findItem(R.id.action_order_by_name)?.isChecked = true
                GroupOrder.BY_DELAY -> 
                    popup.menu.findItem(R.id.action_order_by_delay)?.isChecked = true
            }
        }
        
        popup.setOnMenuItemClickListener { item ->
            fragment.onOtherOptionClicked(item.itemId)
            true
        }
        popup.show()
    }
}