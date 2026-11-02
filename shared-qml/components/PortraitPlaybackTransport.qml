import QtQuick
import miuix.Core
import "."

Item {
    id: transport
    function fmt(ms) {
        var s = Math.floor(Math.max(0, ms) / 1000)
        return Math.floor(s / 60) + ":" + (s % 60 < 10 ? "0" : "") + s % 60
    }

    anchors.left: parent.left
    anchors.right: parent.right
    anchors.bottom: parent.bottom
    anchors.bottomMargin: settings.bottomInset + 12
    anchors.leftMargin: 28
    anchors.rightMargin: 28
    height: 160
    // progress (optional wave) + seek. The wavy phase is an infinite animation gated
    // on the bar's OWN `visible` (control.visible) — own visibility is not the
    // ancestor-effective one, so when the lyric page is closed (this whole
    // overlay invisible) the bar's own visible stayed true and the animation
    // kept ticking every frame, bumping the change version and defeating the
    // renderer's idle layout-skip. Tie its visibility to the page being shown.
    LyricProgress {
        id: progress
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.topMargin: 18
        wavy: false
        visible: player.lyricSlide > 0.001
        // While the next track loads, sweep instead of showing a frozen position.
        indeterminate: player.loading
        value: player.lyricProgress
    }
    MouseArea {
        anchors.fill: progress
        anchors.topMargin: -10
        anchors.bottomMargin: -10
        onPressed: if (player.durationMs > 0)
                       player.seek(Math.round(mouseX / width * player.durationMs))
        onPositionChanged: if (pressed && player.durationMs > 0)
                               player.seek(Math.round(Math.max(0, Math.min(width, mouseX)) / width * player.durationMs))
    }
    Text {
        anchors.left: parent.left
        anchors.top: progress.bottom
        anchors.topMargin: 6
        text: transport.fmt(player.positionMs)
        color: "#B3FFFFFF"
        fontSize: 11
    }
    Text {
        anchors.right: parent.right
        anchors.top: progress.bottom
        anchors.topMargin: 6
        text: transport.fmt(player.durationMs)
        color: "#B3FFFFFF"
        fontSize: 11
    }

    LyricTransportRow {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 14
        gap: 18
    }
}
