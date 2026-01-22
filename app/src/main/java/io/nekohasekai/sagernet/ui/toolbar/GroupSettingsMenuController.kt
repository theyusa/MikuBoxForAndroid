package io.nekohasekai.sagernet.ui.toolbar

import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.bottomsheet.GroupSettingsMenuBottomSheet

class GroupSettingsMenuController(
    private val toolbar: Toolbar,
    private val fragmentManager: FragmentManager,
    private val listener: GroupSettingsMenuBottomSheet.OnOptionClickListener
) {

    private var lastStateWasDirect: Boolean? = null

    init {
        refresh()
    }

    fun refresh() {
        val useDirectMenu = DataStore.disableBottomSheet

        if (lastStateWasDirect == useDirectMenu) return

        lastStateWasDirect = useDirectMenu
        updateToolbar(useDirectMenu)
    }

    private fun updateToolbar(useDirectMenu: Boolean) {
        toolbar.menu.clear()
        toolbar.setOnMenuItemClickListener(null)

        if (useDirectMenu) {
            toolbar.inflateMenu(R.menu.apply_delete_menu)
            
            toolbar.setOnMenuItemClickListener { item ->
                listener.onOptionClicked(item.itemId)
                true
            }
        } else {
            toolbar.inflateMenu(R.menu.uwu_menu_sheet)
            
            toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_open_sheet) {
                    GroupSettingsMenuBottomSheet().show(fragmentManager, GroupSettingsMenuBottomSheet.TAG)
                }
                true
            }
        }
    }
}
