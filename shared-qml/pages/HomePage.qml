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
    property var songs:player.sourceContentActive?player.sourceRecommendations:player.recommendations
    property var playlists:player.sourceContentActive?player.sourceRecommendPlaylists:player.recommendPlaylists
    property int playlistCount:playlists?playlists.length:0
    property int columns:Math.max(2,Math.floor(width/180))
    property real tile:(width-40-(columns-1)*14)/columns
    property int favoriteWatch:player.unifiedFavoritesCount
    onFavoriteWatchChanged:if(page.visible)player.refreshHeartExplore()
    onVisibleChanged:if(page.visible){player.loadHome();player.refreshHeartExplore();player.refreshPlaybackInfo()}
    Flickable {
        id:scroll
        objectName:"homeRecommendationsScroll"
        anchors.fill:parent;clip:true;contentWidth:width
        contentHeight:cards.y+Math.ceil(page.playlistCount/page.columns)*(page.tile+72)+32+page.bottomContentInset
        Rectangle {
            x:20;y:12;width:parent.width-92;height:44;radius:22;color:Theme.color.surfaceContainerHigh
            Icon { x:16;y:10;name:"search";color:Theme.color.onSurfaceVariantSummary }
            Text { x:52;y:12;width:parent.width-68;text:"搜索歌曲、歌手、专辑";font.pixelSize:14;color:Theme.color.onSurfaceVariantSummary;elide:Text.ElideRight }
            MouseArea { anchors.fill:parent;onClicked:page.openSearch() }
        }
        IconButton { anchors.right:parent.right;anchors.rightMargin:16;y:14;icon:"refresh";onClicked:{player.loadHome();player.refreshHeartExplore();player.refreshPlaybackInfo()} }
        Text { x:20;y:78;text:"推荐";font.pixelSize:25;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
        Rectangle { x:20;y:113;width:42;height:4;radius:2;color:Theme.color.primary }
        HomeHeroDeck { x:0;y:136;width:scroll.width;height:170;songs:page.songs||[] }
        HomeSongSection {
            id:daily
            x:0;y:336;width:scroll.width
            title:"猜你喜欢";subtitle:"来自当前音源的推荐";songs:page.songs||[];kind:"home";pageActive:page.visible
        }
        HomeSongSection {
            id:hearts
            x:0;y:daily.y+daily.height+16;width:scroll.width
            title:"从你的红心歌曲开始探索";subtitle:player.heartExploreReason
            songs:player.heartExploreSongs;kind:"heart";pageActive:page.visible
        }
        HomeSongSection {
            id:related
            x:0;y:hearts.y+hearts.height+16;width:scroll.width
            title:player.title!==""?"继续听 · "+player.artist:"发现更多音乐"
            subtitle:player.playbackInfoStatus
            songs:player.playbackRelatedSongs;kind:"related";pageActive:page.visible
        }
        Text { id:playlistTitle;x:20;y:related.y+related.height+20;text:"精选歌单";font.pixelSize:22;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
        Item {
            id:cards;x:0;y:playlistTitle.y+48;width:scroll.width
            height:Math.ceil(page.playlistCount/page.columns)*(page.tile+72)
            Repeater {
                model:page.visible?page.playlists:null
                windowStart:Math.max(0,Math.floor((scroll.contentY-cards.y)/(page.tile+72))-1)*page.columns
                windowCount:(Math.ceil(scroll.height/(page.tile+72))+3)*page.columns
                PlaylistCard {
                    x:20+(index%page.columns)*(page.tile+14);y:Math.floor(index/page.columns)*(page.tile+72)
                    tile:page.tile;name:modelData.name;count:modelData.trackCount;coverUrl:modelData.coverUrl;coverThumbPath:modelData.coverThumbPath||""
                    onClicked:{page.pendingPlaylist=modelData;page.openPlaylist()}
                }
            }
        }
    }
}
