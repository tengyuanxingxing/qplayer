import QtQuick
import miuix.Core

Rectangle {
    id: panel
    color: "#17202D"
    property var rows: player.commentsOpen ? player.songComments : []
    property var rowHeights: []
    onRowsChanged: rowHeights = []
    function rowY(at) {
        var y=0
        for(var i=0;i<at;i++) y+=(rowHeights[i] || 104)+16
        return y
    }
    function rememberHeight(at, value) {
        if(at<0 || value<60 || Math.abs((rowHeights[at] || 0)-value)<0.5) return
        var next=rowHeights.slice();next[at]=value;rowHeights=next
    }
    // Opaque overlay with its own input surface; the music continues underneath.
    MouseArea { anchors.fill: parent }
    IconButton {
        x: 8; y: settings.topInset + 8
        icon: "arrow_back"; contentColor: "white"
        onClicked: player.closeComments()
    }
    Text {
        x: 58; y: settings.topInset + 18; width: parent.width - 116
        text: player.commentsTitle; elide: Text.ElideRight
        color: "white"; font.pixelSize: 20
    }
    IconButton {
        anchors.right: parent.right; anchors.rightMargin: 8
        y: settings.topInset + 8
        icon: "refresh"; contentColor: "white"; enabled: !player.commentsLoading
        onClicked: player.refreshComments()
    }
    Row {
        x: 16; y: settings.topInset + 60; spacing: 8
        Button { text: "最新"; onClicked: player.setCommentsSort("new") }
        Button { text: "热评"; onClicked: player.setCommentsSort("hot") }
    }
    Flickable {
        id: scroll
        x: 16; y: settings.topInset + 116
        width: parent.width - 32
        height: parent.height - y - settings.bottomInset - 12
        clip: true
        contentWidth: width; contentHeight: panel.rowY(panel.rows.length)+statusText.height+80
        property string requestKey: player.commentsTitle + player.commentsSort
        onRequestKeyChanged: contentY = 0
            Repeater {
                model: panel.rows
                delegate: Rectangle {
                    objectName: "songCommentCard"
                    y: panel.rowY(index)
                    width: scroll.width
                    height: body.y + body.height + 34
                    onHeightChanged: panel.rememberHeight(index, height)
                    color: "#222D3C"; radius: 16
                    Text {
                        x: 14; y: 12; width: parent.width - 28
                        text: modelData.author; elide: Text.ElideRight
                        color: "#B8D7EE"; font.pixelSize: 14
                    }
                    Text {
                        id: body
                        x: 14; y: 38; width: parent.width - 28
                        text: modelData.content
                        wrapMode: Text.Wrap
                        color: "#F3F6FA"; font.pixelSize: 14 + settings.value("playerTextSize") * 2
                    }
                    Text {
                        x: 14; anchors.bottom: parent.bottom; anchors.bottomMargin: 10
                        width: parent.width - 28
                        text: modelData.time + " · " + modelData.likeCount + " 赞"
                        color: "#90A2B8"; font.pixelSize: 12
                    }
                }
            }
            Text {
                id: statusText
                y: panel.rowY(panel.rows.length); width: scroll.width; text: player.commentsStatus
                wrapMode: Text.Wrap; color: "#A9BBCC"; font.pixelSize: 14
            }
            Button {
                y: panel.rowY(panel.rows.length)+statusText.height+16
                visible: player.commentsHasMore
                enabled: !player.commentsLoading
                text: player.commentsLoading ? "加载中…" : "加载更多 / 重试"
                onClicked: player.loadMoreComments()
            }
    }
}
