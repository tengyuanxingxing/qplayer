import QtQuick
import miuix.Core

// Pause is pinned to the row centre; prev/next sit at the same gap either
// side so the trio is a centred group. Play-mode hugs previous; like hugs
// next. Five buttons, matching upstream (the lyric-page queue action is gone).
// Fit from 2.5 buttons + 2 gaps from centre so like cannot run off-screen,
// then shrink the buttons if even a zero gap would overflow.
Item {
    id: row
    property real gap: 18
    property bool compact: false
    readonly property real btnSize: Math.max(1, Math.min(compact ? 48 : 56, (width - playSize) / 4))
    readonly property real playSize: Math.min(compact ? 56 : 72, width * 0.225)
    height: playSize
    implicitHeight: compact ? 56 : 72
    implicitWidth: 300
    readonly property real fitGap: Math.max(0, (width - playSize - 4 * btnSize) / 4)

    IconButton {
        id: pauseBtn
        objectName: "lyricPauseButton"
        width: row.playSize; height: row.playSize
        iconSize: row.compact ? 32 : 40
        containerColor: "#F4FFFFFF"
        contentColor: "#172018"
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.verticalCenter: parent.verticalCenter
        type: "filled"
        icon: player.playing ? "pause" : "play_arrow"
        onClicked: player.toggle()
    }
    IconButton {
        id: prevBtn
        objectName: "lyricPrevButton"
        width: row.btnSize; height: row.btnSize
        anchors.right: pauseBtn.left
        anchors.rightMargin: row.fitGap
        anchors.verticalCenter: parent.verticalCenter
        iconSize: row.compact ? 30 : 38
        type: "standard"; icon: "skip_previous"
        contentColor: "#FFFFFFFF"
        onClicked: player.prev()
    }
    IconButton {
        id: nextBtn
        objectName: "lyricNextButton"
        width: row.btnSize; height: row.btnSize
        anchors.left: pauseBtn.right
        anchors.leftMargin: row.fitGap
        anchors.verticalCenter: parent.verticalCenter
        iconSize: row.compact ? 30 : 38
        type: "standard"; icon: "skip_next"
        contentColor: "#FFFFFFFF"
        onClicked: player.next()
    }
    IconButton {
        objectName: "lyricPlayModeButton"
        iconSize: row.compact ? 28 : 32
        width: row.btnSize; height: row.btnSize
        anchors.right: prevBtn.left
        anchors.rightMargin: row.fitGap
        anchors.verticalCenter: parent.verticalCenter
        type: "standard"
        icon: player.playMode === 1 ? "shuffle"
              : (player.playMode === 2 ? "repeat_one" : "repeat")
        contentColor: player.playMode === 0 ? "#99FFFFFF" : "#FF82B1FF"
        onClicked: player.cyclePlayMode()
    }
    IconButton {
        objectName: "lyricLikeButton"
        iconSize: row.compact ? 28 : 34
        width: row.btnSize; height: row.btnSize
        anchors.left: nextBtn.right
        anchors.leftMargin: row.fitGap
        anchors.verticalCenter: parent.verticalCenter
        type: "standard"
        enabled: player.currentLikeable
        icon: player.currentLiked ? "favorite" : "favorite_border"
        contentColor: player.currentLiked ? "#FFFF5277" : "#99FFFFFF"
        onClicked: player.toggleLike()
    }
}
