package io.nekohasekai.sagernet.ui.toolbar

import android.os.Build
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ui.bottomsheet.ProfileSettingsMenuBottomSheet

class ProfileSettingsMenuController(
    private val toolbar: Toolbar,
    private val fragmentManager: FragmentManager,
    private val listener: ProfileSettingsMenuBottomSheet.OnOptionClickListener
) {

    private var lastStateWasDirect: Boolean? = null

    init {
        refresh()
    }

    fun refresh() {
        val useDirectMenu = DataStore.disableBottomSheet
        
        if (lastStateWasDirect == useDirectMenu && !useDirectMenu) return

        lastStateWasDirect = useDirectMenu
        updateToolbar(useDirectMenu)
    }

    private fun updateToolbar(useDirectMenu: Boolean) {
        toolbar.menu.clear()
        toolbar.setOnMenuItemClickListener(null)

        if (useDirectMenu) {
            toolbar.inflateMenu(R.menu.profile_config_menu)
            
            validateMenuItems()

            toolbar.setOnMenuItemClickListener { item ->
                listener.onOptionClicked(item.itemId)
                true
            }
        } else {
            toolbar.inflateMenu(R.menu.uwu_menu_sheet)
            
            toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_open_sheet) {
                    ProfileSettingsMenuBottomSheet().show(fragmentManager, ProfileSettingsMenuBottomSheet.TAG)
                }
                true
            }
        }
    }

    private fun validateMenuItems() {
        val menu = toolbar.menu
        val moveItem = menu.findItem(R.id.action_move)
        val shortcutItem = menu.findItem(R.id.action_create_shortcut)

        moveItem?.isVisible = false
        shortcutItem?.isVisible = false

        if (Build.VERSION.SDK_INT >= 26 && DataStore.editingId != 0L) {
            shortcutItem?.isVisible = true
        }

        runOnDefaultDispatcher {
            val showMove = if (DataStore.editingId != 0L) {
                val group = SagerDatabase.groupDao.getById(DataStore.editingGroup)
                val basicGroupsCount = SagerDatabase.groupDao.allGroups().count { it.type == GroupType.BASIC }
                
                group?.type == GroupType.BASIC && basicGroupsCount > 1
            } else {
                false
            }

            onMainDispatcher {
                moveItem?.isVisible = showMove
            }
        }
    }
}
