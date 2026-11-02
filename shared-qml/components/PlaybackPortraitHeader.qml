import QtQuick
import miuix.Core
import "."

Item {
    id: header
    property int textSize: 1
    readonly property bool pageMoving: player.playbackDragging || Math.abs(player.playbackPagePosition-player.playbackPage)>0.002
    height: 88
    MarqueeText {
        id: title
        scrollingEnabled: !header.pageMoving
        x:28;width:parent.width-116
        text:player.title;textColor:"white"
        fontSize:24+header.textSize*4;fontWeight:Font.DemiBold
    }
    MarqueeText {
        id: artist
        scrollingEnabled: !header.pageMoving
        x:28;y:title.height+4;width:parent.width-56
        text:player.artist;textColor:"#B3FFFFFF";fontSize:14+header.textSize*2
    }
    MouseArea {
        anchors.fill:artist
        enabled:player.playingArtistIdsCsv!=="" || player.playingArtistId!==0
        onClicked:player.openPlayingArtist()
    }
    IconButton {
        x:parent.width-78;y:-8;width:56;height:56;iconSize:36
        icon:player.currentLiked?"favorite":"favorite_border"
        contentColor:player.currentLiked?"#FFFF5277":"#CCFFFFFF"
        enabled:player.currentLikeable;onClicked:player.toggleLike()
    }
}
