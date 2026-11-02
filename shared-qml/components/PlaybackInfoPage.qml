import QtQuick
import miuix.Core
import "."

Item {
    id: page
    property real topPad: settings.topInset + 60
    Flickable {
        id: scroll
        x: 0; y: page.topPad
        width: parent.width
        height: Math.max(0, parent.height - y - settings.bottomInset - 224)
        clip: true; contentWidth: width
        contentHeight: info.height + 48 + player.playbackRelatedSongs.length * 72 + 40
        property string songWatch: player.title
        onSongWatchChanged: { contentY = 0; if (page.visible) player.refreshPlaybackInfo() }
        Item {
            id: info
            x: 24; y: 8; width: scroll.width - 48
            height: credits.y + credits.height + 84
            Text {
                id: title
                width: parent.width; text: player.title
                color: "white"; font.pixelSize: 24 + settings.value("playerTextSize") * 4
                wrapMode: Text.Wrap
            }
            Text {
                id: released
                y: title.height + 12; width: parent.width
                text: player.playbackRelease === "" ? "歌曲信息" : player.playbackRelease + " 发行"
                color: "#A9BCCB"; font.pixelSize: 14
            }
            CoverImage {
                id: art
                x: 0; y: released.y + released.height + 24
                width: 60; height: 60; radius: 12; source: player.coverPath
            }
            Text {
                x: 76; y: art.y + 6; width: parent.width - 76
                text: "歌手：" + player.artist; color: "#E0EBF1"; font.pixelSize: 17
                wrapMode: Text.Wrap
            }
            MouseArea {
                x: 0; y: art.y; width: parent.width; height: 64
                onClicked: player.openPlayingArtist()
            }
            Text {
                id: album
                y: art.y + 84; width: parent.width
                text: "专辑：" + (player.playbackAlbumName || "暂无专辑信息") + (player.playbackAlbumId !== "" ? "  ›" : "")
                color: "#D3E3EE"; font.pixelSize: 17; wrapMode: Text.Wrap
            }
            MouseArea {
                x: 0; y: album.y - 8; width: parent.width; height: album.height + 16
                enabled: player.playbackAlbumId !== ""
                onClicked: player.openPlaybackAlbum()
            }
            Text {
                id: credits
                y: album.y + album.height + 22; width: parent.width
                text: player.playbackCredits
                color: "#A9BCCB"; font.pixelSize: 15; wrapMode: Text.Wrap
            }
            Rectangle {
                x: 0; y: credits.y + credits.height + 22; width: 112; height: 38; radius: 19
                color: "#334A5B"
                Text { anchors.centerIn: parent; text: "查看评论  ›"; font.pixelSize: 14; color: "#DFEAF1" }
                MouseArea { anchors.fill: parent; onClicked: player.openCurrentComments() }
            }
            Text {
                x: 128; y: credits.y + credits.height + 32; width: parent.width - 128
                text: player.playbackGenre; color: "#A9BCCB"; font.pixelSize: 13; elide: Text.ElideRight
            }
        }
        Text {
            x: 24; y: info.height + 12
            text: "继续听 · " + player.artist
            color: "white"; font.pixelSize: 19; font.weight: Font.DemiBold
        }
        Repeater {
            model: player.playbackRelatedSongs
            SongRow {
                width: scroll.width; y: info.height + 52 + index * 72; height: 72
                rowTitle: modelData.name; rowArtist: modelData.artist
                coverThumbPath: modelData.coverThumbPath || modelData.coverUrl
                song: modelData; showLikeButton: true
                darkSurface: true
                likeable: player.isMediaLikeable(modelData.id)
                liked: player.likedRevision >= 0 && player.isMediaLiked(modelData.id)
                onActivated: player.playPlaybackRelated(index)
                onLikeToggled: player.toggleLikeMedia(modelData.id)
            }
        }
        Text {
            x: 24; y: info.height + 58 + player.playbackRelatedSongs.length * 72
            text: player.playbackInfoStatus; color: "#A9BCCB"; font.pixelSize: 13
        }
    }
}
