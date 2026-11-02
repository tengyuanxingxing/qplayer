import QtQuick
import miuix.Core
import "."

Item {
    id: panel
    signal showArtists()
    property var candidate: player.suggestedArtists.length > 0 ? player.suggestedArtists[0] : null
    CoverImage {
        x: 20; y: 12; width: 64; height: 64; radius: 32
        source: panel.candidate ? panel.candidate.artworkUrl : ""; icon: "person"
    }
    Text {
        x: 100; y: 18; width: parent.width - 170
        text: panel.candidate ? "歌手：" + panel.candidate.name : ""
        font.pixelSize: 19; font.weight: Font.DemiBold
        color: Theme.color.onSurfaceColor; elide: Text.ElideRight
    }
    Text {
        x: 100; y: 50; width: parent.width - 170
        text: panel.candidate ? (panel.candidate.songCount > 0 || panel.candidate.albumCount > 0
              ? "歌曲 " + panel.candidate.songCount + " · 专辑 " + panel.candidate.albumCount : "查看歌手的歌曲与专辑") : ""
        font.pixelSize: 13; color: Theme.color.onSurfaceVariantSummary; elide: Text.ElideRight
    }
    Ripple {
        x: 12; y: 6; width: parent.width - 76; height: 80; clipRadius: 18
        onClicked: if (tapAllowed && panel.candidate) player.openMediaArtist(panel.candidate.id)
    }
    IconButton {
        anchors.right: parent.right; anchors.rightMargin: 16; y: 24
        icon: "chevron_right"; onClicked: panel.showArtists()
    }
}
