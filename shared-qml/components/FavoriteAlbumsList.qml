import QtQuick
import miuix.Core
import "."

Flickable {
    id:list
    objectName:"favoriteAlbumsList"
    property real bottomContentInset:0
    property var albums:player.favoriteAlbums || []
    readonly property int windowSize:Math.min(albums.length,Math.ceil(height/92)+7)
    readonly property int first:Math.max(0,Math.min(albums.length-windowSize,Math.floor(contentY/92)-3))
    flickableDirection:"VerticalFlick"
    clip:true;contentWidth:width;contentHeight:albums.length*92+bottomContentInset+64
    Repeater {
        model:list.visible?list.windowSize:0
        Item {
            objectName:"favoriteAlbumRow"
            property int sourceIndex:list.first+((index-list.first%Math.max(1,list.windowSize)+list.windowSize)%Math.max(1,list.windowSize))
            property var album:list.albums[sourceIndex] || ({})
            x:0;y:sourceIndex*92;width:list.width;height:92
            CoverImage { x:20;y:10;width:72;height:72;radius:10;source:album.coverThumbPath || album.coverUrl }
            Text { x:108;y:17;width:parent.width-140;text:album.name;font.pixelSize:18;color:Theme.color.onSurfaceColor;elide:Text.ElideRight }
            Text { x:108;y:49;width:parent.width-140;text:(album.trackCount>0?album.trackCount+" 首 · ":"")+album.artistName+" · "+(String(album.id).indexOf("qq:")===0?"QQ 音乐":"网易云音乐");font.pixelSize:13;color:Theme.color.onSurfaceVariantSummary;elide:Text.ElideRight }
            Icon { anchors.right:parent.right;anchors.rightMargin:16;y:36;width:16;height:16;name:"chevron_right";color:Theme.color.onSurfaceVariantSummary }
            Ripple { anchors.fill:parent;onClicked:if(tapAllowed)player.openMediaAlbum(album.id) }
        }
    }
    Item {
        x:16;y:list.albums.length*92+8;width:parent.width-32;height:48
        Text { width:parent.width-90;text:player.favoriteAlbumsStatus;font.pixelSize:14;wrapMode:Text.Wrap;color:Theme.color.onSurfaceVariantSummary }
        Button { anchors.right:parent.right;text:player.favoriteAlbumsLoading?"加载中…":"刷新";enabled:!player.favoriteAlbumsLoading;onClicked:player.refreshFavoriteAlbums() }
    }
    ViewportScrollBar { target:list;bottomInset:list.bottomContentInset }
}
