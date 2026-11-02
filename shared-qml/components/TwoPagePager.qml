import QtQuick

// The native base receives real release/cancel callbacks; no gesture-during-inertia timer.
PageFlickable {
    clip:true
    contentWidth:width*2
    contentHeight:height
    flickableDirection:"HorizontalFlick"
}
