package com.noxsip.app.ui.navigation

sealed class Screen(val route: String) {
    data object Dialer : Screen("dialer")
    data object CallHistory : Screen("call_history")
    data object Contacts : Screen("contacts")
    data object PushToTalk : Screen("push_to_talk")
    data object Account : Screen("account")
    data object InCall : Screen("in_call")
}
