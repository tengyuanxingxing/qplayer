import QtQuick
import miuix.Core
import "../components"

Item {
    id:page
    objectName:"homeDiscoveryPage"
    property real bottomContentInset:0
    property var pendingPlaylist
    signal openPlaylist()
    signal openSearch()
    property var songs:player.qqGuessSongs
    onVisibleChanged:if(page.visible)player.ensureHomeLoaded()
    property real searchLastY:0
    property real searchTravel:0
    property int searchDirection:0
    property bool searchExpanded:true
    // A small reverse scroll reveals search at the current reading position.
    // It changes header visibility only: no refresh and no contentY jump.
    function followSearchScroll() {
        var current=Math.max(0,scroll.contentY)
        var delta=current-page.searchLastY
        page.searchLastY=current
        if(current<=68) {
            page.searchTravel=0
            page.searchDirection=0
            page.searchExpanded=current<=0
            return
        }
        if(Math.abs(delta)<0.05)return
        var direction=delta<0?-1:1
        if(direction!==page.searchDirection)page.searchTravel=0
        page.searchDirection=direction
        page.searchTravel+=Math.abs(delta)
        if(direction<0 && page.searchTravel>=6)page.searchExpanded=true
        if(direction>0 && page.searchTravel>=10)page.searchExpanded=false
    }
    Flickable {
        id:scroll
        objectName:"homeRecommendationsScroll"
        anchors.fill:parent;clip:true;contentWidth:width
        contentHeight:related.y+related.height+24+page.bottomContentInset
        onContentYChanged:page.followSearchScroll()
        Text { x:20;y:78;text:"推荐";font.pixelSize:25;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
        Rectangle { x:20;y:113;width:42;height:4;radius:2;color:Theme.color.primary }
        HomeHeroDeck { id:hero;x:0;y:136;width:scroll.width;height:172;visible:true;songs:page.songs||[];dailySongs:player.qqDailySongs||[];guessStatus:player.qqGuessStatus;dailyStatus:player.qqDailyStatus }
        HomeSongSection {
            id:daily
            x:0;y:hero.y+hero.height+(hero.visible?30:0);width:scroll.width
            title:"猜你喜欢";subtitle:player.qqGuessStatus+(page.songs.length>0?" · "+page.songs.length+"首":"");songs:page.songs||[];kind:"qq";pageActive:page.visible
        }
        HomeSongSection {
            id:hearts
            x:0;y:daily.y+daily.height+16;width:scroll.width
            title:"从你的红心歌曲开始探索";subtitle:player.heartExploreReason
            songs:player.heartExploreSongs;kind:"heart";pageActive:page.visible
            visible:player.heartExploreSongs.length>0
            height:visible?72+Math.min(3,songs.length)*76:0
        }
        HomeSongSection {
            id:related
            x:0;y:hearts.y+hearts.height+16;width:scroll.width
            title:player.title!==""?"继续听 · "+player.artist:"发现更多音乐"
            subtitle:player.playbackInfoStatus
            songs:player.playbackRelatedSongs;kind:"related";pageActive:page.visible
            visible:player.playbackRelatedSongs.length>0
            height:visible?72+Math.min(3,songs.length)*76:0
        }
    }
    Rectangle {
        id:searchHeader
        objectName:"homeQuickSearchHeader"
        z:10;width:page.width;height:68;clip:true
        y:scroll.contentY<=68?-Math.max(0,scroll.contentY):(page.searchExpanded?0:-68)
        color:Theme.color.surface
        Behavior on y {
            enabled:scroll.contentY>68
            NumberAnimation { duration:150;easing.type:Easing.OutCubic }
        }
        // The revealed header owns its entire hit area, including its padding.
        MouseArea { anchors.fill:parent }
        Rectangle {
            objectName:"homeQuickSearchButton"
            x:20;y:12;width:parent.width-92;height:44;radius:22;color:Theme.color.surfaceContainerHigh
            Icon { x:16;y:10;name:"search";color:Theme.color.onSurfaceVariantSummary }
            Text { x:52;y:12;width:parent.width-68;text:"搜索歌曲、歌手、专辑";font.pixelSize:14;color:Theme.color.onSurfaceVariantSummary;elide:Text.ElideRight }
            MouseArea { anchors.fill:parent;onClicked:page.openSearch() }
        }
        IconButton {
            objectName:"homeManualRefreshButton"
            anchors.right:parent.right;anchors.rightMargin:16;y:14;icon:"refresh"
            onClicked:{player.loadHome();player.refreshHeartExplore();player.refreshPlaybackInfo()}
        }
    }
}
