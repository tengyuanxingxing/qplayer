import QtQuick
import miuix.Core
import "."

Flickable {
    id:deck
    objectName:"homePersonalCards"
    property var songs:[]
    property var dailySongs:[]
    property string guessStatus:""
    property string dailyStatus:""
    property real cardWidth:Math.min(250,width*0.56)
    contentWidth:cardWidth*2+42;contentHeight:height;clip:true
    flickableDirection:"HorizontalFlick"
    Repeater {
        model:2
        Rectangle {
            property bool daily:index===1
            property var rows:daily?deck.dailySongs:deck.songs
            property var first:rows.length?rows[0]:({})
            x:16+index*(deck.cardWidth+10);y:0;width:deck.cardWidth;height:deck.height
            radius:22;color:"#8FB8ED"
            CoverImage {
                anchors.right:parent.right;anchors.rightMargin:12;y:12;width:parent.width*0.48;height:width;radius:16
                source:first.coverThumbPath || first.coverUrl || first.artworkUrl || ""
            }
            Text { x:16;y:17;width:parent.width*0.40;text:daily?"Daily\n30":"For\nYou";font.pixelSize:24;font.weight:Font.DemiBold;color:"#FFFFFF" }
            Text { x:16;y:parent.height-52;width:parent.width-55;text:daily?"每日30首":"猜你喜欢 · 沉浸刷歌";font.pixelSize:14;font.weight:Font.DemiBold;color:"#FFFFFF";elide:Text.ElideRight }
            Text { x:16;y:parent.height-30;width:parent.width-55;text:rows.length?(first.name || first.title):(daily?deck.dailyStatus:deck.guessStatus);font.pixelSize:12;color:"#F0FFFFFF";elide:Text.ElideRight }
            Icon { anchors.right:parent.right;anchors.rightMargin:13;anchors.bottom:parent.bottom;anchors.bottomMargin:15;name:"play_circle";width:30;height:30;color:"#FFFFFF";opacity:rows.length?1:0.5 }
            Ripple { anchors.fill:parent;clipRadius:22;onClicked:if(tapAllowed){if(rows.length){if(daily)player.playQqDaily(0);else player.playQqGuess(0)}else player.loadHome()} }
        }
    }
}
