import QtQuick
import miuix.Core
import "."

Flickable {
    id:list
    objectName:"favoriteAlbumsList"
    property real bottomContentInset:0
    property var albums:player.favoriteAlbums || []
    readonly property int first:Math.max(0,Math.floor(contentY/92)-1)
    readonly property int end:Math.min(albums.length,first+Math.ceil(height/92)+3)
    property var visibleAlbums:albums.slice(first,end)
    clip:true;contentWidth:width;contentHeight:albums.length*92+bottomContentInset+64
    Repeater {
        model:list.visible?list.visibleAlbums:[]
        Item {
            objectName:"favoriteAlbumRow"
            x:0;y:(list.first+index)*92;width:list.width;height:92
            CoverImage { x:20;y:10;width:72;height:72;radius:10;source:modelData.coverThumbPath || modelData.coverUrl }
            Text { x:108;y:17;width:parent.width-140;text:modelData.name;font.pixelSize:18;color:Theme.color.onSurfaceColor;elide:Text.ElideRight }
            Text { x:108;y:49;width:parent.width-140;text:(modelData.trackCount>0?modelData.trackCount+" 首 · ":"")+modelData.artistName+" · "+(String(modelData.id).indexOf("qq:")===0?"QQ 音乐":"网易云音乐");font.pixelSize:13;color:Theme.color.onSurfaceVariantSummary;elide:Text.ElideRight }
            Icon { anchors.right:parent.right;anchors.rightMargin:16;y:36;width:16;height:16;name:"chevron_right";color:Theme.color.onSurfaceVariantSummary }
            Ripple { anchors.fill:parent;onClicked:if(tapAllowed)player.openMediaAlbum(modelData.id) }
        }
    }
    Item {
        x:16;y:list.albums.length*92+8;width:parent.width-32;height:48
        Text { width:parent.width-90;text:player.favoriteAlbumsStatus;font.pixelSize:14;wrapMode:Text.Wrap;color:Theme.color.onSurfaceVariantSummary }
        Button { anchors.right:parent.right;text:player.favoriteAlbumsLoading?"加载中…":"刷新";enabled:!player.favoriteAlbumsLoading;onClicked:player.refreshFavoriteAlbums() }
    }
    ViewportScrollBar { target:list;bottomInset:list.bottomContentInset }
}
