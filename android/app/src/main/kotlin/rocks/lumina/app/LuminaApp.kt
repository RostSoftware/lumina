package rocks.lumina.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import rocks.lumina.app.core.designsystem.component.BottomBarItem
import rocks.lumina.app.core.designsystem.component.LuminaBottomBar
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.icon.CreateIcon
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.NostrLink
import rocks.lumina.app.core.ui.post.PostNavigation
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.util.LocalSnackbarHostState
import rocks.lumina.app.feature.auth.LoginRoute
import rocks.lumina.app.feature.create.CreateRoute
import rocks.lumina.app.feature.discover.DiscoverRoute
import rocks.lumina.app.feature.feed.HomeRoute
import rocks.lumina.app.feature.messages.ChatRoute
import rocks.lumina.app.feature.messages.ConversationsRoute
import rocks.lumina.app.feature.notifications.NotificationsRoute
import rocks.lumina.app.feature.post.MediaViewerRoute
import rocks.lumina.app.feature.post.PostDetailRoute
import rocks.lumina.app.feature.profile.EditProfileRoute
import rocks.lumina.app.feature.profile.FollowListRoute
import rocks.lumina.app.feature.profile.ProfileRoute
import rocks.lumina.app.feature.settings.AccountRoute
import rocks.lumina.app.feature.settings.AppearanceRoute
import rocks.lumina.app.feature.settings.ContentRoute
import rocks.lumina.app.feature.settings.MediaServersRoute
import rocks.lumina.app.feature.settings.RelaysRoute
import rocks.lumina.app.feature.settings.SettingsPage
import rocks.lumina.app.feature.settings.SettingsRoute
import rocks.lumina.app.feature.settings.WalletRoute
import rocks.lumina.app.navigation.Route

private enum class Tab { HOME, DISCOVER, CREATE, NOTIFICATIONS, PROFILE }

private fun NavDestination?.tab(): Tab? = when {
    this == null -> null
    hierarchy.any { it.hasRoute<Route.Home>() } -> Tab.HOME
    hierarchy.any { it.hasRoute<Route.Discover>() } -> Tab.DISCOVER
    hierarchy.any { it.hasRoute<Route.Notifications>() } -> Tab.NOTIFICATIONS
    hierarchy.any { it.hasRoute<Route.MyProfile>() } -> Tab.PROFILE
    else -> null
}

/** Full-screen flows hide the bottom bar (root AGENTS.md "Navigation & Layout"). */
private fun NavDestination?.hidesBottomBar(): Boolean = this != null && (
    hasRoute<Route.Create>() || hasRoute<Route.Chat>() || hasRoute<Route.Media>() || hasRoute<Route.Login>()
)

@Composable
fun LuminaApp(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activePubkey by appViewModel.activePubkey.collectAsStateWithLifecycle()
    val unread by appViewModel.unreadNotifications.collectAsStateWithLifecycle()
    val signerWaiting by appViewModel.signerWaiting.collectAsStateWithLifecycle()
    val incoming by appViewModel.incoming.collectAsStateWithLifecycle()
    val unsupportedLinkText = stringResource(R.string.link_not_supported)
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    LaunchedEffect(Unit) {
        appViewModel.loginRequests.collect {
            if (navController.currentDestination?.hasRoute<Route.Login>() != true) navController.navigate(Route.Login) { launchSingleTop = true }
        }
    }
    LaunchedEffect(incoming) {
        when (val i = incoming) {
            is IncomingIntent.Open -> navController.navigate(i.route) { launchSingleTop = true }
            is IncomingIntent.Share -> navController.navigate(Route.Create) { launchSingleTop = true }
            IncomingIntent.Unsupported -> snackbar.showSnackbar(unsupportedLinkText)
            null -> Unit
        }
        if (incoming != null) appViewModel.consumeIncoming()
    }

    val postNav = remember(navController) {
        PostNavigation(
            openProfile = { pk -> navController.openProfile(pk, appViewModel.activePubkey.value) },
            openPost = { post -> navController.navigate(Route.Post(post.id, author = post.pubkey)) },
            openMedia = { post, index -> navController.navigate(Route.Media(post.id, index)) },
            openHashtag = { tag -> navController.navigate(Route.Discover(tag)) },
            openLink = { link ->
                when (link) {
                    is NostrLink.Profile -> navController.openProfile(link.pubkey, appViewModel.activePubkey.value)
                    else -> DeepLinks.routeFor(link)?.let { navController.navigate(it) }
                }
            },
        )
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Scaffold(
            containerColor = LuminaTheme.colors.background,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = LuminaTheme.colors.foreground,
                        contentColor = LuminaTheme.colors.background,
                        shape = LuminaShapes.control,
                    )
                }
            },
            bottomBar = {
                if (!destination.hidesBottomBar()) {
                    BottomBar(
                        selected = destination.tab(),
                        activePubkey = activePubkey,
                        unread = unread > 0,
                        onSelect = { tab -> navController.selectTab(tab) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
                LuminaNavHost(navController, appViewModel, postNav)
                SignerBanner(signerWaiting, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private fun NavHostController.openProfile(pubkey: String, me: String?) {
    if (pubkey == me) selectTab(Tab.PROFILE) else navigate(Route.Profile(pubkey))
}

private fun NavHostController.selectTab(tab: Tab) {
    val route: Route = when (tab) {
        Tab.HOME -> Route.Home
        Tab.DISCOVER -> Route.Discover()
        Tab.CREATE -> {
            navigate(Route.Create) { launchSingleTop = true }
            return
        }
        Tab.NOTIFICATIONS -> Route.Notifications
        Tab.PROFILE -> Route.MyProfile
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(selected: Tab?, activePubkey: String?, unread: Boolean, onSelect: (Tab) -> Unit) {
    val items = listOf(
        BottomBarItem(Tab.HOME.name, stringResource(R.string.nav_home), { active -> LuminaIcon(LucideGlyphs.House, null, filled = active) }),
        BottomBarItem(Tab.DISCOVER.name, stringResource(R.string.nav_discover), { active ->
            // A filled magnifier is unreadable; active search uses a heavier stroke (same as web).
            LuminaIcon(LucideGlyphs.Search, null, strokeWidth = if (active) 2.75f else 1.75f)
        }),
        BottomBarItem(Tab.CREATE.name, stringResource(R.string.nav_create), { active -> CreateIcon(active, null) }),
        BottomBarItem(Tab.NOTIFICATIONS.name, stringResource(R.string.nav_notifications), { active ->
            LuminaIcon(LucideGlyphs.Heart, null, filled = active)
        }, showBadge = unread),
        BottomBarItem(Tab.PROFILE.name, stringResource(R.string.nav_profile), { active ->
            if (activePubkey != null) {
                ProfileAvatar(activePubkey, LuminaDimens.avatarBottomBar, ring = active)
            } else {
                LuminaIcon(LucideGlyphs.User, null, filled = active)
            }
        }),
    )
    LuminaBottomBar(items = items, selectedKey = selected?.name, onSelect = { onSelect(Tab.valueOf(it.key)) })
}

@Composable
private fun SignerBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier.statusBarsPadding().padding(top = 64.dp), enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier
                .background(LuminaTheme.colors.foreground, LuminaShapes.pill)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spinner(size = 16.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(rocks.lumina.app.core.ui.R.string.signer_waiting), style = LuminaTheme.typography.username, color = LuminaTheme.colors.background)
        }
    }
}

@Composable
private fun LuminaNavHost(navController: NavHostController, appViewModel: AppViewModel, postNav: PostNavigation) {
    val homeList = rememberLazyListState()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController, startDestination = Route.Home) {
        composable<Route.Home> {
            HomeRoute(
                nav = postNav,
                onOpenMessages = { navController.navigate(Route.Messages) },
                onOpenDiscover = { navController.selectTab(Tab.DISCOVER) },
                listState = homeList,
            )
        }
        composable<Route.Discover> { entry ->
            DiscoverRoute(initialHashtag = entry.toRoute<Route.Discover>().hashtag, nav = postNav)
        }
        composable<Route.Create> {
            val shared by appViewModel.sharedUris.collectAsStateWithLifecycle()
            CreateRoute(
                sharedUris = shared,
                onClose = {
                    appViewModel.consumeShared()
                    if (!navController.popBackStack()) navController.selectTab(Tab.HOME)
                },
                onPublished = {
                    appViewModel.consumeShared()
                    navController.navigate(Route.MyProfile) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<Route.Notifications> { NotificationsRoute(nav = postNav) }
        composable<Route.MyProfile> {
            ProfileRoute(
                pubkey = null,
                onBack = null,
                nav = postNav,
                onOpenSettings = { navController.navigate(Route.Settings) },
                onEditProfile = { navController.navigate(Route.EditProfile) },
                onMessage = { navController.navigate(Route.Chat(it)) },
                onOpenFollows = { pk, followers -> navController.navigate(Route.FollowList(pk, followers)) },
            )
        }
        composable<Route.Profile> { entry ->
            ProfileRoute(
                pubkey = entry.toRoute<Route.Profile>().pubkey,
                onBack = back,
                nav = postNav,
                onOpenSettings = { navController.navigate(Route.Settings) },
                onEditProfile = { navController.navigate(Route.EditProfile) },
                onMessage = { navController.navigate(Route.Chat(it)) },
                onOpenFollows = { pk, followers -> navController.navigate(Route.FollowList(pk, followers)) },
            )
        }
        composable<Route.Post> { entry ->
            val route = entry.toRoute<Route.Post>()
            PostDetailRoute(route.id, route.relays, route.author, route.focusComment, onBack = back, nav = postNav)
        }
        composable<Route.Media> { entry ->
            val route = entry.toRoute<Route.Media>()
            MediaViewerRoute(route.postId, route.index, onBack = back)
        }
        composable<Route.Login> { LoginRoute(onDone = back, onBack = back) }
        composable<Route.Messages> { ConversationsRoute(onBack = back, onOpenChat = { navController.navigate(Route.Chat(it)) }) }
        composable<Route.Chat> { entry ->
            ChatRoute(entry.toRoute<Route.Chat>().pubkey, onBack = back, onOpenProfile = { postNav.openProfile(it) })
        }
        composable<Route.Settings> {
            SettingsRoute(
                onBack = back,
                onOpen = { page ->
                    navController.navigate(
                        when (page) {
                            SettingsPage.Relays -> Route.SettingsRelays
                            SettingsPage.Media -> Route.SettingsMedia
                            SettingsPage.Wallet -> Route.SettingsWallet
                            SettingsPage.Appearance -> Route.SettingsAppearance
                            SettingsPage.Content -> Route.SettingsContent
                            SettingsPage.Account -> Route.SettingsAccount
                        },
                    )
                },
                onEditProfile = { navController.navigate(Route.EditProfile) },
            )
        }
        composable<Route.SettingsRelays> { RelaysRoute(onBack = back) }
        composable<Route.SettingsMedia> { MediaServersRoute(onBack = back) }
        composable<Route.SettingsWallet> { WalletRoute(onBack = back) }
        composable<Route.SettingsAppearance> { AppearanceRoute(onBack = back) }
        composable<Route.SettingsContent> { ContentRoute(onBack = back, onOpenProfile = { postNav.openProfile(it) }) }
        composable<Route.SettingsAccount> { AccountRoute(onBack = back, onAddAccount = { navController.navigate(Route.Login) }) }
        composable<Route.EditProfile> { EditProfileRoute(onBack = back) }
        composable<Route.FollowList> { entry ->
            val route = entry.toRoute<Route.FollowList>()
            FollowListRoute(route.pubkey, route.followers, onBack = back, onOpenProfile = { postNav.openProfile(it) })
        }
    }
}
