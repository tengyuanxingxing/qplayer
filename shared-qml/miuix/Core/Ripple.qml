import QtQuick
import miuix.Core

// Retains the existing MouseArea API while using Miuix's flat indication.
MouseArea {
    id: indicationRoot
    property color rippleColor: Theme.color.onBackground
    property real rippleOpacity: 0.10
    property real clipRadius: 0
    property alias clipTopLeftRadius: stateLayer.topLeftRadius
    property alias clipTopRightRadius: stateLayer.topRightRadius
    property alias clipBottomLeftRadius: stateLayer.bottomLeftRadius
    property alias clipBottomRightRadius: stateLayer.bottomRightRadius
    property bool longPressEnabled: false
    property int longPressMs: 480
    property bool tapAllowed: false
    property real pressedAt: 0
    property real pressX: 0
    property real pressY: 0
    signal longPressed()
    // Desktop right-click. Distinct from longPressed so a reorderable row can
    // still open its menu instead of revealing the drag grip.
    signal contextMenuRequested()

    hoverEnabled: true
    acceptedButtons: longPressEnabled ? (Qt.LeftButton | Qt.RightButton) : Qt.LeftButton

    Rectangle {
        id: stateLayer
        width: indicationRoot.width
        height: indicationRoot.height
        radius: indicationRoot.clipRadius
        color: indicationRoot.rippleColor
        opacity: !indicationRoot.enabled ? 0
            : (indicationRoot.containsMouse ? 0.06 : 0)
                + (indicationRoot.activeFocus ? 0.08 : 0)
                + (indicationRoot.pressed ? indicationRoot.rippleOpacity : 0)
        Behavior on opacity {
            NumberAnimation { duration: indicationRoot.pressed ? 120 : 200; easing.type: Easing.OutCubic }
        }
    }
    Timer {
        id: holdTimer
        interval: indicationRoot.longPressMs
        repeat: false
        onTriggered: { indicationRoot.tapAllowed = false; indicationRoot.longPressed() }
    }
    onPressed: (mouse) => {
        indicationRoot.tapAllowed = true
        indicationRoot.pressedAt = Date.now()
        indicationRoot.pressX = mouse.x
        indicationRoot.pressY = mouse.y
        if (indicationRoot.longPressEnabled && mouse.button === Qt.RightButton) {
            indicationRoot.contextMenuRequested()
        } else if (indicationRoot.longPressEnabled) {
            holdTimer.restart()
        }
    }
    onPositionChanged: (mouse) => {
        var moveX = mouse.x - indicationRoot.pressX
        var moveY = mouse.y - indicationRoot.pressY
        if (moveX * moveX + moveY * moveY > 64) indicationRoot.tapAllowed = false
        if (holdTimer.running) {
            var dx = mouse.x - indicationRoot.pressX
            var dy = mouse.y - indicationRoot.pressY
            if (dx * dx + dy * dy > 100) holdTimer.stop()
        }
    }
    onReleased: { holdTimer.stop(); indicationRoot.tapAllowed = indicationRoot.tapAllowed && Date.now() - indicationRoot.pressedAt < 400 }
    onCanceled: { holdTimer.stop(); indicationRoot.tapAllowed = false }
    onEnabledChanged: if (!enabled) holdTimer.stop()
}
