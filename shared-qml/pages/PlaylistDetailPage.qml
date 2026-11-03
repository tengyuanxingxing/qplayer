import QtQuick
import QtQuick.Layouts
import miuix.Core
import "."
import "../components"

// Drill-in playlist view: header with back + title, then the tracks.
Rectangle {
    id: page
    objectName:"playlistDetailPage"
    property real bottomContentInset: 0
    signal back()
    signal home()
    signal openQueue()
    color: Theme.color.surface

    // Reset the scroll to the top whenever a new playlist starts loading, so the
    // previous playlist's scroll position doesn't carry over.
    property string playlistWatch: String(player.openSourcePlaylistId || player.openPlaylistId)
    onPlaylistWatchChanged: {
        page.favoriteSearchOpen=false
        favoriteSearch.reset()
        tracks.contentY = 0
        page.filterText = ""
        page.albumsTab = false
        page.exitSelectMode()
    }

    readonly property bool unified: player.openSourcePlaylistId === "qplayer:playlist:unified-favorites"
    readonly property bool favorites: {
        var projection=player.myPlaylists
        return page.unified || player.isFavoritesPlaylist(player.openSourcePlaylistId)
    }
    property bool favoriteSearchOpen:false
    function handleLocalBack() {
        if(!page.favoriteSearchOpen)return false
        page.favoriteSearchOpen=false
        return true
    }
    property bool albumsTab:false
    onAlbumsTabChanged: {
        if(page.albumsTab)player.ensureFavoriteAlbumsLoaded()
    }
    property string albumsSourceWatch:player.unifiedFavoritesSource
    onAlbumsSourceWatchChanged:if(page.albumsTab)player.ensureFavoriteAlbumsLoaded()
    property var accountsWatch:player.sourceAccounts
    onAccountsWatchChanged:if(page.albumsTab)player.ensureFavoriteAlbumsLoaded()
    property string filterText: ""
    property var filteredTracks: {
        var all = player.openSourcePlaylistId !== ""
                  ? player.sourcePlaylistTracks : player.playlistTracks
        if (!all) return all
        var q = page.filterText.trim().toLowerCase()
        if (q === "") return all
        var out = []
        for (var i = 0; i < all.length; i++) {
            var t = all[i]
            var hit = (t.name && t.name.toLowerCase().indexOf(q) >= 0)
                   || (t.artist && t.artist.toLowerCase().indexOf(q) >= 0)
            if (hit) out.push(t)
        }
        return out
    }

    Menu {
        id: sourceMenu
        function entry(provider, label) {
            return { text: label, action: function() { player.setUnifiedFavoritesSource(provider) } }
        }
    }

    // Swallow taps on empty areas so they don't reach the page beneath.
    MouseArea { anchors.fill: parent }

    readonly property bool compactHeader: page.width < 600
    // Method calls alone are not reactive. Read the account projection as well
    // so a secondary provider signing in/out refreshes its own action gates.
    readonly property bool targetMutable: {
        var accounts = player.sourceAccounts
        return player.openSourcePlaylistId !== ""
            && player.canMutateMediaPlaylist(player.openSourcePlaylistId)
    }
    readonly property bool targetHeart: {
        var accounts = player.sourceAccounts
        return player.openSourcePlaylistId !== ""
            && player.canHeartMediaPlaylist(player.openSourcePlaylistId)
    }
    readonly property bool targetSignedIn: {
        var accounts = player.sourceAccounts
        return player.openSourcePlaylistId !== ""
            && player.providerLoggedIn(player.openSourcePlaylistId.split(":")[0])
    }
    readonly property bool canHeart: !player.playlistLoading && page.targetHeart
                                     && tracks.list && tracks.list.length > 0
    readonly property bool canFollow: !page.unified && !player.playlistLoading && player.openSourcePlaylistId !== "" && !page.unified
    readonly property bool canSubscribe: !player.playlistLoading && !player.playlistOwned
                                         && page.targetMutable
    readonly property bool canChangeCover: !player.playlistLoading && player.playlistOwned
                                           && player.sourcePlaylistCoverAvailable && page.targetSignedIn
    readonly property bool canDelete: !player.playlistLoading && player.playlistDeletable
                                      && page.targetMutable
    readonly property bool hasHeaderActions: page.canHeart || page.canFollow || page.canSubscribe
                                             || page.canChangeCover || page.canDelete || page.canSelect
    readonly property bool canSelect: !player.playlistLoading && page.filteredTracks
                                      && page.filteredTracks.length > 0
    property bool selectMode: false

    function toggleSelectMode() {
        page.selectMode = !page.selectMode
        if (!page.selectMode) tracks.clearSelection()
    }
    function selectedCsv() { return tracks.selectedKey }
    function exitSelectMode() {
        page.selectMode = false
        tracks.clearSelection()
    }

    function startHeart() {
        if (player.openSourcePlaylistId !== "")
            player.startMediaIntelligenceMode(player.openSourcePlaylistId)
        else player.startIntelligenceMode(player.openPlaylistId)
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Custom header: qml4j can't set a sub-property of TopAppBar's
        // navigationIcon alias (navigationIcon.icon) via grouped binding.
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 64
            Layout.leftMargin: 4
            Layout.rightMargin: 16
            spacing: 4
            PageHeaderButtons {
                Layout.alignment: Qt.AlignVCenter
                onHome: page.home()
                onBack: page.back()
            }
            Text {
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignVCenter
                text: page.favorites ? "我的收藏" : i18n.t("playlist.title")
                color: Theme.color.onSurfaceColor
                font.pixelSize: 24
                font.weight: Font.DemiBold
                elide: Text.ElideRight
            }
            IconButton {
                Layout.alignment: Qt.AlignVCenter
                type: "standard"
                visible: page.canHeart && !page.compactHeader
                enabled: !player.intelligenceLoading
                icon: "auto_awesome"
                contentColor: player.intelligenceLoading
                              ? Theme.color.primary
                              : Theme.color.onSurfaceVariantColor
                onClicked: page.startHeart()
            }
            // Pull this whole playlist into a local one, which then follows it.
            // Unlike collecting below, this works signed out and across sources —
            // it copies the songs into a list QPlayer owns.
            IconButton {
                id: followButton
                objectName: "followIntoLocalButton"
                Layout.alignment: Qt.AlignVCenter
                type: "standard"
                visible: page.canFollow && !page.compactHeader
                icon: "library_add"
                onClicked: {
                    followMenu.rebuild()
                    followMenu.open(followButton, 0, followButton.height)
                }
            }
            // Collect (subscribe) this playlist. Shown only once loaded and only for
            // playlists that aren't the user's own; filled when already collected. The
            // initial state comes from playlist/detail, so it's correct on open.
            IconButton {
                Layout.alignment: Qt.AlignVCenter
                type: "standard"
                visible: page.canSubscribe && !page.compactHeader
                icon: player.playlistSubscribed ? "bookmark" : "bookmark_border"
                contentColor: player.playlistSubscribed ? Theme.color.primary : Theme.color.onSurfaceColor
                onClicked: player.togglePlaylistSubscribe()
            }
            // Change cover — own playlists only, and only when the playlist's own
            // source can do it (built-in netease, or a plugin that advertises
            // playlistCover). Both hosts install the same picker callback: Android
            // keeps its system gallery picker, while desktop opens a cross-platform
            // image file chooser.
            IconButton {
                objectName: "playlistDetailCoverButton"
                Layout.alignment: Qt.AlignVCenter
                type: "standard"
                visible: page.canChangeCover && !page.compactHeader
                icon: "image"
                onClicked: player.pickPlaylistCover()
            }
            // Delete — only your own playlists, and never the default liked-songs
            // (the first playlist, which can't be removed). Confirms first.
            IconButton {
                Layout.alignment: Qt.AlignVCenter
                type: "standard"
                visible: page.canDelete && !page.compactHeader
                icon: "delete"
                onClicked: deleteDialog.open()
            }
            IconButton {
                objectName: "playlistDetailSelectButton"
                Layout.alignment: Qt.AlignVCenter
                type: "standard"
                visible: page.canSelect && (!page.compactHeader || page.selectMode)
                icon: page.selectMode ? "close" : "checklist"
                onClicked: page.toggleSelectMode()
            }
            IconButton {
                objectName:"favoriteSearchButton"
                Layout.alignment:Qt.AlignVCenter
                type:"standard";visible:page.favorites;icon:"search"
                onClicked:{page.albumsTab=false;page.favoriteSearchOpen=true}
            }
            IconButton {
                id: overflowButton
                objectName: "playlistDetailOverflowButton"
                Layout.alignment: Qt.AlignVCenter
                type: "standard"
                visible: page.unified || (page.compactHeader && page.hasHeaderActions)
                icon: "more_vert"
                onClicked: {
                    overflowMenu.rebuild()
                    overflowMenu.open(overflowButton, 0, overflowButton.height)
                }
            }
        }

        TabRowWithContour {
            objectName:"favoritesContentTabs"
            Layout.fillWidth:true;Layout.preferredHeight:48
            visible:page.favorites
            tabs:["歌曲", "专辑"]
            selectOnClick:false;selectedTabIndex:page.albumsTab?1:0
            onTabSelected:(index)=>page.albumsTab=(index===1)
        }

        TwoPagePager {
            id:collectionPager
            objectName:"collectionSwipePager"
            Layout.fillWidth:true;Layout.fillHeight:true
            contentWidth:page.favorites?width*2:width
            interactive:page.favorites
            pageActive:page.visible
            pageIndex:page.albumsTab?1:0
            onPageIndexChanged:page.albumsTab=(pageIndex===1)

            VirtualSongList {
                id: tracks
                objectName:"playlistDetailTracks"
                visible:page.favorites || !page.albumsTab
                enabled:visible
                x:0;y:0;width:collectionPager.width;height:collectionPager.height
                flickableDirection:"VerticalFlick"
                bottomContentInset: page.bottomContentInset
                // Playlist metadata and filtering belong to the scroll content.
                // On a phone this gives the track list the full viewport after the
                // first swipe instead of permanently reserving half the screen.
                headerHeight: player.playlistLoading || page.favorites ? 0 : 212
                header: Component {
                    Item {
                        width: tracks.width
                        height: tracks.headerHeight
                        visible: !player.playlistLoading && !page.favorites

                        CoverImage {
                            id: playlistCover
                            objectName: "playlistFirstSongCover"
                            x: 16
                            y: 8
                            width: 128
                            height: 128
                            radius: 20
                            source: page.unified && player.sourcePlaylistTracks.length > 0
                                    ? (player.sourcePlaylistTracks[0].coverThumbPath || player.sourcePlaylistTracks[0].coverUrl || player.sourcePlaylistTracks[0].artworkUrl)
                                    : player.playlistCoverPath
                            icon: "queue_music"
                        }

                        Text {
                            id: playlistName
                            anchors.left: playlistCover.right
                            anchors.leftMargin: 16
                            anchors.right: parent.right
                            anchors.rightMargin: 16
                            anchors.top: playlistCover.top
                            text: player.playlistTitle
                            font.pixelSize: page.width >= 600 ? 28 : 22
                            font.weight: Font.DemiBold
                            color: Theme.color.onSurfaceColor
                            wrapMode: Text.Wrap
                            maximumLineCount: 2
                            elide: Text.ElideRight
                        }

                        Text {
                            anchors.left: playlistName.left
                            anchors.right: playlistName.right
                            anchors.top: playlistName.bottom
                            anchors.topMargin: 4
                            text: i18n.t("common.songCount",
                                         page.filteredTracks ? page.filteredTracks.length : 0)
                            font.pixelSize: 14
                            color: Theme.color.onSurfaceVariantSummary
                            elide: Text.ElideRight
                        }

                        Button {
                            id: playAllBtn
                            anchors.left: playlistName.left
                            anchors.bottom: playlistCover.bottom
                            text: i18n.t("playlist.playAll")
                            icon: "play_arrow"
                            verticalPadding: 8
                            enabled: page.filteredTracks && page.filteredTracks.length > 0
                            onClicked: {
                                var first = page.filteredTracks[0]
                                var all = player.openSourcePlaylistId !== ""
                                          ? player.sourcePlaylistTracks : player.playlistTracks
                                for (var i = 0; i < all.length; i++) {
                                    if (String(all[i].id) === String(first.id)) {
                                        player.playPlaylistTrack(i)
                                        return
                                    }
                                }
                            }
                        }
                        IconButton {
                            anchors.left: playAllBtn.right
                            anchors.leftMargin: 8
                            anchors.verticalCenter: playAllBtn.verticalCenter
                            type: "standard"
                            icon: "queue_music"
                            onClicked: page.openQueue()
                        }

                        TextField {
                            overlayField: true
                            x: 16
                            y: 148
                            width: parent.width - 32
                            height: implicitHeight
                            label: i18n.t("playlist.searchInside")
                            leadingIcon: "search"
                            text: page.filterText
                            onTextChanged: page.filterText = text
                        }
                    }
                }
                // Drop the row delegates when the detail page is closed (see
                // QueuePage): an invisible detail otherwise keeps the whole
                // playlist's SongRows alive after you return home.
                list: page.visible && (page.favorites || !page.albumsTab) ? page.filteredTracks : null
                // Long-press a track → add to another playlist, and (in your own
                // playlist) remove it from this one. Not login-gated: adding to the queue
                // (local list) works signed-out too.
                songMenu: true
                ownedPlaylist: player.playlistOwned
                showOfflineBadge: player.playlistOffline
                // Own playlist, on a source that can save an order, and unfiltered —
                // a drop position means nothing while rows are hidden.
                selectMode: page.selectMode
                reorderable: !page.selectMode && player.sourcePlaylistReorderable && page.filterText === ""
                onMoveRequested: player.moveSourcePlaylistTrack(tracks.moveFrom, tracks.moveTo)
                onReorderCommitted: player.commitSourcePlaylistOrder()
                onActivated: {
                    var selected = tracks.list[tracks.activatedIndex]
                    var all = player.openSourcePlaylistId !== ""
                              ? player.sourcePlaylistTracks : player.playlistTracks
                    if (!selected || !all) return
                    for (var i = 0; i < all.length; i++) {
                        if (String(all[i].id) === String(selected.id)) {
                            player.playPlaylistTrack(i)
                            return
                        }
                    }
                }
            }

            FavoriteAlbumsList {
                x:collectionPager.width;y:0;width:collectionPager.width;height:collectionPager.height
                visible:page.favorites;enabled:visible
                bottomContentInset:page.bottomContentInset
            }

            LoadingIndicator {
                objectName: "detailLoadingIndicator"
                anchors.centerIn: parent
                visible: !page.albumsTab && player.playlistLoading && (!page.filteredTracks || page.filteredTracks.length === 0)
                running: visible
            }

            Text {
                anchors.centerIn: parent
                visible: !player.playlistLoading && page.filterText !== ""
                         && page.filteredTracks && page.filteredTracks.length === 0
                text: i18n.t("playlist.noMatches")
                fontSize: 16
                color: Theme.color.onSurfaceVariantColor
            }

            ScrollToTopFab {
                objectName: "playlistScrollTopButton"
                anchors.right: parent.right
                anchors.bottom: parent.bottom
                anchors.margins: 16
                anchors.bottomMargin: 16 + page.bottomContentInset
                target: tracks
            }
        }

        Item {
            objectName: "playlistDetailSelectBar"
            Layout.fillWidth: true
            Layout.preferredHeight: 56
            visible: page.selectMode
            Rectangle {
                width: parent.width
                height: parent.height
                y: -page.bottomContentInset
                color: Theme.color.surfaceContainer
                Text {
                    anchors.left: parent.left
                    anchors.leftMargin: 16
                    anchors.verticalCenter: parent.verticalCenter
                    text: i18n.t("playlist.selectCount", tracks.selectedN)
                    color: Theme.color.onSurfaceColor
                    font.pixelSize: 14
                }
                Row {
                    anchors.right: parent.right
                    anchors.rightMargin: 8
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 4
                    IconButton {
                        type: "standard"
                        icon: "done_all"
                        onClicked: tracks.selectAll()
                    }
                    IconButton {
                        objectName: "playlistDetailUnlikeSelected"
                        type: "standard"
                        icon: "favorite_border"
                        enabled: tracks.selectedN > 0
                        onClicked: {
                            player.unlikeMediaMany(tracks.selectedKey)
                            page.exitSelectMode()
                        }
                    }
                    IconButton {
                        objectName: "playlistDetailDeleteSelected"
                        type: "standard"
                        icon: "delete"
                        visible: player.playlistOwned
                        enabled: tracks.selectedN > 0
                        onClicked: {
                            player.removeMediaManyFromCurrentPlaylist(tracks.selectedKey)
                            page.exitSelectMode()
                        }
                    }
                }
            }
        }
    }

    Menu {
        id: overflowMenu
        objectName: "favoritesOverflowMenu"
        outlined: true
        function rebuild() {
            var items = []
            if (page.unified) {
                var platforms = [sourceMenu.entry("", "全部平台")]
                var accounts = player.sourceAccounts
                for (var i=0;i<accounts.length;i++) if(accounts[i].loggedIn)
                    platforms.push(sourceMenu.entry(accounts[i].providerId, accounts[i].sourceName))
                items.push({text:"按平台查看",icon:"filter_list",subItems:platforms})
                items.push({text:player.unifiedFavoritesSyncing?"正在同步…":"刷新全部收藏",icon:"sync",
                    enabled:!player.unifiedFavoritesSyncing,action:function(){player.refreshUnifiedFavorites()}})
                items.push({text:"同步状态",icon:"info",action:function(){favoriteStatus.open()}})
                items.push({text:player.favoritePreferredSource==="qq"?"重复歌曲：QQ 优先":"重复歌曲：网易云优先",subItems:[
                    {text:"QQ 音乐优先",action:function(){player.setFavoritePreferredSource("qq")}},
                    {text:"网易云优先",action:function(){player.setFavoritePreferredSource("netease")}}]})
                items.push({text:player.favoriteMixMode==="off"?"分组穿插：关闭":player.favoriteMixMode==="interleave"?"分组穿插："+player.favoriteMixRatio:"分组穿插：分段接续",subItems:[
                    {text:"关闭，保持主序来源原顺序",action:function(){player.setFavoriteMixMode("off")}},
                    {text:"按比例穿插两个区段",action:function(){player.setFavoriteMixMode("interleave")}},
                    {text:"两个区段接在一起",action:function(){player.setFavoriteMixMode("blocks")}},
                    {text:"主序平台："+page.providerName(player.favoriteMixBase),subItems:page.mixSources("base")},
                    {text:"参与平台："+page.providerName(player.favoriteMixInsert),subItems:page.mixSources("insert")},
                    {text:"穿插比例："+player.favoriteMixRatio,action:function(){mixRatioDialog.open()}},
                    {text:"主序平台整个收藏参与",action:function(){player.setFavoriteMixWhole("base")}},
                    {text:"参与平台整个收藏参与",action:function(){player.setFavoriteMixWhole("insert")}},
                    {text:"区段设置与说明",action:function(){mixHelp.open()}}]})
                items.push({type:"separator"})
            }
            if (page.canHeart) {
                items.push({
                    text: i18n.t("playlist.heart"), icon: "auto_awesome",
                    action: function() { page.startHeart() }
                })
            }
            if (page.canFollow) {
                items.push({
                    text: i18n.t("playlist.local.addPlaylistTo"), icon: "library_add",
                    action: function() {
                        followMenu.rebuild()
                        followMenu.open(overflowButton, 0, overflowButton.height)
                    }
                })
            }
            if (page.canSubscribe) {
                items.push({
                    text: player.playlistSubscribed
                          ? i18n.t("menu.unsubscribePlaylist") : i18n.t("playlist.subscribe"),
                    icon: player.playlistSubscribed ? "bookmark" : "bookmark_border",
                    action: function() { player.togglePlaylistSubscribe() }
                })
            }
            if (page.canSelect) {
                items.push({
                    text: i18n.t("playlist.select"), icon: "checklist",
                    action: function() { page.toggleSelectMode() }
                })
            }
            if (page.canChangeCover) {
                items.push({
                    text: i18n.t("playlist.local.pickCover"), icon: "image",
                    action: function() { player.pickPlaylistCover() }
                })
            }
            if (page.canDelete) {
                items.push({ type: "separator" })
                items.push({
                    text: i18n.t("common.delete"), icon: "delete",
                    action: function() { deleteDialog.open() }
                })
            }
            overflowMenu.model = items
        }
    }

    Dialog {
        id: favoriteStatus
        objectName: "favoritesSyncStatusDialog"
        topInset: settings.topInset; bottomInset: settings.bottomInset
        title: "收藏同步状态"; text: player.unifiedFavoritesStatus
        showAcceptButton: false; rejectText: "关闭"
    }
    function providerName(id) {
        var accounts=player.sourceAccounts
        for(var i=0;i<accounts.length;i++)if(accounts[i].providerId===id)return accounts[i].sourceName
        return id
    }
    function mixSourceEntry(role,id,label) {
        return {text:label,action:function(){player.setFavoriteMixProvider(role,id)}}
    }
    function mixSources(role) {
        var accounts=player.sourceAccounts,out=[]
        for(var i=0;i<accounts.length;i++)if(accounts[i].loggedIn && player.canMixFavoritesSource(accounts[i].providerId) && (role==="base" || accounts[i].providerId!==player.favoriteMixBase))
            out.push(page.mixSourceEntry(role,accounts[i].providerId,accounts[i].sourceName))
        return out
    }
    Dialog {
        id: mixHelp
        topInset: settings.topInset; bottomInset: settings.bottomInset
        title: "分组穿插"
        text: "主序平台保留前后歌曲的顺序，参与平台提供要穿插的歌曲。先选择两边来源，再按平台查看收藏，长按歌曲设置各自区段的起点、终点。主序只设终点时，从第一首开始；参与平台默认全部。\n\n只在所选区段穿插，主序前后其余歌曲保持先后顺序。未参与穿插的其他歌曲放在后面。重复歌曲只保留一个播放源；设置不会修改平台歌单。\n\n" + player.favoriteMixSummary
        showAcceptButton: false; rejectText: "关闭"
    }
    Dialog {
        id: mixRatioDialog
        topInset: settings.topInset; bottomInset: settings.bottomInset
        title: "每轮各播放几首"; acceptText: "应用"; rejectText: "取消"
        onOpenedChanged:if(opened){var ratio=player.favoriteMixRatio.split("∶");baseRatio.text=ratio[0];insertRatio.text=ratio[1]}
        onAccepted: player.setFavoriteMixRatio(parseInt(baseRatio.text)||1,parseInt(insertRatio.text)||1)
        Column {
            width:parent.width;spacing:12
            Text {width:parent.width;text:"填写 1–10。每轮先放主序平台歌曲，再放参与平台歌曲。";wrapMode:Text.Wrap;color:Theme.color.onSurfaceColor;font.pixelSize:14}
            TextField {id:baseRatio;width:parent.width;placeholderText:"主序平台每轮首数";text:"1"}
            TextField {id:insertRatio;width:parent.width;placeholderText:"参与平台每轮首数";text:"1"}
        }
    }

    // Destinations for the follow button: every local playlist, toggling, plus a
    // "new playlist" entry that names itself after this playlist.
    Menu {
        id: followMenu
        outlined: true
        function rebuild() {
            var sourceId = "" + player.openSourcePlaylistId
            var items = []
            var lists = player.localPlaylists
            var n = lists ? lists.length : 0
            for (var i = 0; i < n; i++) items.push(followMenu._item(sourceId, lists[i]))
            if (n > 0) items.push({ type: "separator" })
            items.push({ text: i18n.t("playlist.local.newPlaylist"), icon: "playlist_add",
                         action: followMenu._newAction(sourceId) })
            followMenu.model = items
        }
        function _item(sourceId, pl) {
            var pid = "" + pl.id
            var followed = player.isSourcePlaylistFollowed(pid, sourceId)
            return {
                text: pl.name,
                icon: followed ? "check" : "queue_music",
                action: followed ? followMenu._unfollow(pid, sourceId)
                                 : followMenu._follow(pid, sourceId)
            }
        }
        function _follow(pid, sourceId) {
            return function() { player.followSourcePlaylist(pid, sourceId) }
        }
        function _unfollow(pid, sourceId) {
            return function() { player.unfollowSourcePlaylist(pid, sourceId) }
        }
        function _newAction(sourceId) {
            return function() { player.followSourcePlaylistInNewLocalPlaylist(sourceId) }
        }
    }

    // Delete confirmation. On accept the controller removes it and refreshes the library;
    // we drill back out since this playlist no longer exists.
    Dialog {
        topInset: settings.topInset
        bottomInset: settings.bottomInset
        id: deleteDialog
        icon: "delete"
        title: i18n.t("playlist.delete.title")
        text: i18n.t("playlist.delete.body", player.playlistTitle)
        acceptText: i18n.t("common.delete")
        rejectText: i18n.t("common.cancel")
        onAccepted: {
            if (player.openSourcePlaylistId !== "")
                player.deleteMediaPlaylist(player.openSourcePlaylistId)
            else player.deletePlaylist(player.openPlaylistId)
            page.back()
        }
    }

    FavoriteSongSearch {
        id:favoriteSearch
        anchors.fill:parent;z:30
        visible:page.visible && page.favoriteSearchOpen
        enabled:visible
        bottomContentInset:page.bottomContentInset
        onClose:page.favoriteSearchOpen=false
    }

}
