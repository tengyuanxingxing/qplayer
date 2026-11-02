import QtQuick
import miuix.Core
import "."

// This sheet stays in lyricChrome: a root-reparented Dialog is below the
// host-composited playback surface and cannot be used here.
Item {
    id: sheet
    visible:player.songArtistPickerOpen
    enabled:visible
    property var artists:player.songArtistPickerList || []
    Rectangle { anchors.fill:parent;color:"#77000000";MouseArea { anchors.fill:parent;onClicked:player.closeSongArtistPicker() } }
    Rectangle {
        x:12;width:parent.width-24
        height:Math.min(sheet.height-settings.topInset-32,124+sheet.artists.length*64+settings.bottomInset)
        anchors.bottom:parent.bottom;anchors.bottomMargin:12
        radius:24;color:Theme.color.surfaceContainerHigh
        MouseArea { anchors.fill:parent }
        Text { x:20;y:20;text:"选择歌手";font.pixelSize:22;color:Theme.color.onSurfaceColor }
        IconButton { anchors.right:parent.right;anchors.rightMargin:12;y:12;icon:"close";onClicked:player.closeSongArtistPicker() }
        Flickable {
            id:rows
            x:12;y:64;width:parent.width-24;height:parent.height-76-settings.bottomInset
            clip:true;contentWidth:width;contentHeight:sheet.artists.length*64
            Repeater {
                model:sheet.artists
                ArtistRow {
                    objectName:"playbackArtistChoice"
                    y:index*64;width:rows.width;height:64
                    artistId:modelData.id;name:modelData.name
                    coverUrl:modelData.coverUrl||"";coverThumbPath:modelData.coverThumbPath||""
                    onActivated: {
                        player.closeSongArtistPicker()
                        if(modelData.mediaId)player.openMediaArtist(modelData.mediaId)
                        else player.openArtist(modelData.id)
                    }
                }
            }
        }
    }
}
