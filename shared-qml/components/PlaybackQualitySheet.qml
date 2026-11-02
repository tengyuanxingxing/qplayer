import QtQuick
import miuix.Core
import "."

Item {
    id: sheet
    objectName: "playbackQualitySheet"
    signal closed()
    property int selection: settings.value("playbackQuality")
    property bool available: { var media=player.currentMediaId; return player.canSelectPlaybackQuality() }
    Rectangle { anchors.fill:parent;color:"#66000000";MouseArea { anchors.fill:parent;onClicked:sheet.closed() } }
    Rectangle {
        objectName:"playbackQualityCard"
        anchors.bottom:parent.bottom
        width:parent.width;height:Math.min(parent.height-settings.topInset-12,326+settings.bottomInset)
        radius:24;color:"#FF202020"
        MouseArea { anchors.fill:parent }
        Text { x:24;y:20;text:"音质设置";font.pixelSize:21;font.weight:Font.DemiBold;color:"#FFFFFF" }
        IconButton { anchors.right:parent.right;anchors.rightMargin:12;y:12;icon:"close";contentColor:"#FFFFFF";onClicked:sheet.closed() }
        Text {
            x:24;y:57;width:parent.width-48
            text:sheet.available?player.playbackQualityStatus:"当前音源或本地文件不支持音质切换"
            font.pixelSize:12;color:"#BFFFFFFF";elide:Text.ElideRight
        }
        Repeater {
            model:3
            Item {
                objectName:"playbackQualityChoice"
                property int quality:2-index
                x:12;y:86+index*68;width:parent.width-24;height:64
                opacity:sheet.available?1:0.45
                Text { x:12;y:10;text:quality===2?"SQ 无损品质":(quality===1?"HQ 高品质":"标准品质");font.pixelSize:20;color:"#FFFFFF" }
                Text { x:12;y:39;text:quality===2?"优先 FLAC，视账号与曲目支持":(quality===1?"优先高品质音频":"较小体积，节省流量");font.pixelSize:12;color:"#99FFFFFF" }
                Icon { anchors.right:parent.right;anchors.rightMargin:16;y:19;name:"check_circle";color:"#00D57E";visible:sheet.selection===quality;width:25;height:25 }
                MouseArea { anchors.fill:parent;enabled:sheet.available;onClicked:{settings.setValue("playbackQuality",quality);sheet.closed()} }
            }
        }
    }
}
