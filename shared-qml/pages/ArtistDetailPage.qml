import QtQuick
import QtQuick.Layouts
import miuix.Core
import "../components"

Rectangle {
    id: page
    objectName: "artistDetailPage"
    property real bottomContentInset: 0
    signal back()
    signal home()
    color: Theme.color.surface
    property bool albumsTab: false
    property bool previousAlbumsTab:false
    property real songScrollOffset:0
    property real albumScrollOffset:0
    onAlbumsTabChanged: {
        if(page.previousAlbumsTab===page.albumsTab)return
        if(page.previousAlbumsTab)page.albumScrollOffset=artistScroll.contentY
        else page.songScrollOffset=artistScroll.contentY
        page.previousAlbumsTab=page.albumsTab
        var rowsHeight=page.albumsTab?page.albums.length*92+72:page.songs.length*64+72
        artistScroll.contentY=Math.max(0,Math.min(page.albumsTab?page.albumScrollOffset:page.songScrollOffset,288+rowsHeight+page.bottomContentInset-artistScroll.height))
    }
    property string albumFilter: "all"
    property string songQuery: ""
    property bool filtersOpen: false
    property string artistWatch: player.openSourceArtistId
    onArtistWatchChanged: { albumsTab = false; albumFilter = "all"; songQuery = ""; filtersOpen = false; songScrollOffset=0;albumScrollOffset=0;artistScroll.contentY=0 }
    property var allSongs: player.openSourceArtistId !== "" ? player.sourceArtistSongs : player.artistSongs
    property var allAlbums: player.openSourceArtistId !== "" ? player.sourceArtistAlbums : player.artistAlbums
    property var songs: {
        var rows = page.allSongs || [], out = [], q = page.songQuery.trim().toLowerCase()
        for (var i=0; i<rows.length; i++) if (!q || (rows[i].name || "").toLowerCase().indexOf(q)>=0) out.push(rows[i])
        return out
    }
    property var albums: {
        var rows = page.allAlbums || [], out = []
        for (var i=0; i<rows.length; i++) if (page.albumFilter === "all" || rows[i].releaseType === page.albumFilter) out.push(rows[i])
        out.sort((a,b)=>(Number(b.publishTimeMs)||0)-(Number(a.publishTimeMs)||0))
        return out
    }
    function playSong(id) {
        for(var i=0;i<page.allSongs.length;i++) if(String(page.allSongs[i].id) === String(id)) { player.playArtistSong(i); return }
    }
    function filterLabel() {
        if (page.albumFilter === "album") return "专辑"
        if (page.albumFilter === "live") return "现场专辑"
        if (page.albumFilter === "ep") return "EP"
        if (page.albumFilter === "single") return "单曲"
        return "全部"
    }
    MouseArea { anchors.fill: parent }
    Item {
        width:page.width;height:56
        PageHeaderButtons { x:4; onBack:page.back(); onHome:page.home() }
        Text { x:100;y:16;width:parent.width-116;text:player.artistName;font.pixelSize:20;elide:Text.ElideRight;color:Theme.color.onSurfaceColor }
    }
    Flickable {
        id: artistScroll
        objectName:"artistDetailScroll"
        y:56;width:page.width;height:page.height-56;clip:true;contentWidth:width
        contentHeight:288+(page.albumsTab?page.albums.length*92+72:page.songs.length*64+72)+page.bottomContentInset
        property string filterWatch:page.albumFilter
        onFilterWatchChanged:if(page.albumsTab)contentY=Math.min(contentY,180)
        flickableDirection:"VerticalFlick"
        Rectangle {
            objectName:"artistHero"
            width:artistScroll.width;height:180;color:Theme.color.primaryContainer
            CoverImage { x:20;y:20;width:88;height:88;radius:44;source:player.artistCoverPath;icon:"person" }
            Text { x:128;y:28;width:parent.width-148;text:player.artistName;font.pixelSize:28;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor;elide:Text.ElideRight }
            Text { x:128;y:76;width:parent.width-148;text:"歌曲 / 专辑";font.pixelSize:14;color:Theme.color.onSurfaceVariantSummary }
            Text { x:20;y:126;width:parent.width-40;text:player.artistBriefDesc;maximumLineCount:2;wrapMode:Text.Wrap;elide:Text.ElideRight;font.pixelSize:13;color:Theme.color.onSurfaceVariantSummary }
        }
        TabRowWithContour {
            objectName:"artistContentTabs"
            y:180;width:artistScroll.width;height:48
            tabs:["歌曲", "专辑"]
            selectOnClick:false;selectedTabIndex:page.albumsTab?1:0
            onTabSelected:(index)=>page.albumsTab=(index===1)
        }
        Item {
            y:228;width:artistScroll.width;height:60
            TextField {
                x:16;y:6;width:parent.width-32;visible:!page.albumsTab;enabled:visible
                label:"搜索此歌手的歌曲";leadingIcon:"search";onTextChanged:page.songQuery=text
            }
            Button {
                objectName:"artistAlbumFilterButton"
                x:16;y:6;visible:page.albumsTab;enabled:visible;text:"筛选："+page.filterLabel()+"  ▾"
                onClicked:page.filtersOpen=true
            }
        }
        TwoPagePager {
            id:artistPager
            objectName:"artistSwipePager"
            y:288;width:artistScroll.width
            height:Math.max(page.songs.length*64+72,page.albums.length*92+72)
            pageIndex:page.albumsTab?1:0
            pageActive:page.visible
            onPageIndexChanged:page.albumsTab=(pageIndex===1)
        VirtualSongList {
            id:tracks
            y:0;width:artistPager.width;height:page.songs.length*64+72
            visible:page.visible;enabled:visible
            // The outer viewport owns the gesture, so the hero and songs move together.
            scrollViewport: songViewport
            list:page.songs;showLikeButton:true
            loadMoreEnabled:player.artistSongsHasMore && !player.artistMoreLoading
            onLoadMoreRequested:player.loadMoreArtist("songs")
            onActivated:page.playSong(tracks.list[tracks.activatedIndex].id)
        }
        Item {
            id:songViewport
            height:artistScroll.height
            property real scrollOffset:Math.max(0,artistScroll.contentY-288)
        }
        Item {
            id:albumList
            objectName:"artistAlbumList"
            x:artistPager.width;y:0;width:artistPager.width;height:page.albums.length*92+72
            visible:page.visible;enabled:visible
            Repeater {
                model:page.visible?page.albums:null
                windowStart:Math.max(0,Math.floor((artistScroll.contentY-288)/92)-2)
                windowCount:Math.ceil(artistScroll.height/92)+5
                Item {
                    objectName:"artistAlbumRow"
                    width:albumList.width;height:92;y:index*92
                    CoverImage { x:20;y:10;width:72;height:72;radius:12;source:modelData.coverUrl }
                    Text { objectName:"artistAlbumTitle";x:110;y:18;width:parent.width-138;text:modelData.name;font.pixelSize:19;elide:Text.ElideRight;color:Theme.color.onSurfaceColor }
                    Text { x:110;y:52;width:parent.width-138;text:(modelData.publishTimeMs>0?new Date(modelData.publishTimeMs).toISOString().slice(0,10)+"  ":"")+(modelData.trackCount>0?modelData.trackCount+" 首":"");font.pixelSize:13;color:Theme.color.onSurfaceVariantSummary }
                    Ripple { anchors.fill:parent;onClicked:if(tapAllowed)player.openMediaAlbum(modelData.id) }
                }
            }
            Button {
                x:20;y:page.albums.length*92+12;visible:player.artistAlbumsHasMore;enabled:!player.artistMoreLoading
                text:player.artistMoreLoading?"加载中…":"加载更多专辑";onClicked:player.loadMoreArtist("albums")
            }
            Text { x:20;y:30;visible:page.albums.length===0;text:page.albumFilter==="all"?"暂无专辑":"当前已加载专辑中没有此分类，可加载更多";width:parent.width-40;wrapMode:Text.Wrap;color:Theme.color.onSurfaceVariantSummary }
        }
        }
        LoadingIndicator { x:parent.width/2-16;y:300;visible:player.artistLoading;running:visible }
    }
    MouseArea { anchors.fill:parent;visible:page.filtersOpen;enabled:visible;z:20;onClicked:page.filtersOpen=false }
    Rectangle {
        objectName:"artistAlbumFilterSheet"
        visible:page.filtersOpen;enabled:visible;z:21
        anchors.left:parent.left;anchors.right:parent.right;anchors.bottom:parent.bottom
        anchors.bottomMargin:page.bottomContentInset
        height:320+settings.bottomInset;radius:24;color:Theme.color.surfaceContainerHigh
        Repeater {
            model:[{key:"all",label:"全部"},{key:"album",label:"专辑"},{key:"live",label:"现场专辑"},{key:"ep",label:"EP"},{key:"single",label:"单曲"}]
            Item {
                x:16;y:16+index*58;width:parent.width-32;height:58
                Text { x:12;y:16;text:modelData.label;font.pixelSize:19;color:Theme.color.onSurfaceColor }
                Icon { anchors.right:parent.right;anchors.rightMargin:16;y:16;name:"check_circle";color:Theme.color.primary;visible:page.albumFilter===modelData.key }
                Ripple { anchors.fill:parent;onClicked:if(tapAllowed){page.albumFilter=modelData.key;page.filtersOpen=false} }
            }
        }
    }
}
