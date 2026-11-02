import QtQuick
import miuix.Core

// One bounded, display-resolution decode for both account avatar sizes. Keep
// the Image painting under the placeholder: qml4j adopts decoded images in
// Painter, so hiding the Image until Ready would prevent Ready altogether.
Item {
    id: avatar
    property string source: ""
    property int iconSize: 28
    property int decodeSize: 0
    property string readySource: ""
    clip: true

    function latchDecodeSize() {
        var size = Math.round(Math.max(width, height) * player.pixelRatio)
        if (size > 0 && (decodeSize === 0 || Math.abs(size - decodeSize) >= 32))
            decodeSize = size
    }
    onWidthChanged: latchDecodeSize()
    onHeightChanged: latchDecodeSize()
    onSourceChanged: readySource = ""
    Component.onCompleted: latchDecodeSize()

    Image {
        id: art
        objectName: "accountAvatarImage"
        width: avatar.width
        height: avatar.height
        radius: width / 2
        clip: true
        source: avatar.decodeSize > 0 ? avatar.source : ""
        sourceSize.width: avatar.decodeSize
        sourceSize.height: avatar.decodeSize
        fillMode: "PreserveAspectFit"
        onStatusChanged: {
            if (status === 1) avatar.readySource = avatar.source
        }
    }
    // Cover a previous account's image while a replacement is loading. This
    // is an opaque placeholder above the still-active decode/draw node.
    Rectangle {
        objectName: "accountAvatarPlaceholder"
        width: avatar.width
        height: avatar.height
        radius: width / 2
        visible: avatar.source === "" || avatar.readySource !== avatar.source || art.status !== 1
        color: Theme.color.surfaceContainerHigh
        Text {
            anchors.centerIn: parent
            text: "account_circle"
            font.family: Theme.iconFont.name
            font.pixelSize: avatar.iconSize
            color: Theme.color.onSurfaceVariantColor
        }
    }
}
