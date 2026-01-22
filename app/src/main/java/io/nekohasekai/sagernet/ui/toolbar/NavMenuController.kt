package io.nekohasekai.sagernet.ui.toolbar

import androidx.core.view.GravityCompat
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity

class NavMenuController(private val activity: MainActivity) {

    fun showMenu() {
        val useDrawer = DataStore.disableBottomSheetHome

        if (useDrawer) {
            activity.binding.drawerLayout.openDrawer(GravityCompat.START)
        } else {
            activity.showOriginalNavigationSheet()
        }
    }
}