import QtQuick
import QtQuick.Layouts
import miuix.Core
import "."
import "../components"

Rectangle {
    id: page
    objectName: "settingsHome"
    property real bottomContentInset: 0
    property string activeCategory: ""
    signal back()
    signal home()
    signal openDebugLog()
    color: Theme.color.surface
    function handleBack() {
        if (page.activeCategory.length > 0) { page.activeCategory="";return true }
        return false
    }
    MouseArea { anchors.fill:parent }
    Item {
        anchors.fill:parent
        visible: page.activeCategory.length === 0
        enabled:visible
        Item {
            id:header
            width:parent.width;height:64
            IconButton { x:12;y:8;icon:"arrow_back";onClicked:page.back() }
            Text { anchors.centerIn:parent;text:i18n.t("settings.title");font.pixelSize:22;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
        }
        Flickable {
            id:scroll
            objectName:"settingsHomeScroll"
            anchors.top:header.bottom;anchors.bottom:parent.bottom;width:parent.width
            clip:true;contentWidth:width;contentHeight:content.implicitHeight+32+page.bottomContentInset
            ColumnLayout {
                id:content
                width:Math.min(840,scroll.width-40);x:(scroll.width-width)/2;spacing:18
                Repeater {
                    model: [
                        {label:"歌词", ids:["lyric","overlayLyric","desktopLyric"]},
                        {label:"播放与显示", ids:["playback","appearance","pages"]},
                        {label:"存储与应用", ids:["local","plugins","about"]}
                    ]
                    ColumnLayout {
                        property var section:modelData
                        property int sectionIndex:index
                        Layout.fillWidth:true;spacing:10
                        Text { text:section.label;font.pixelSize:14;color:Theme.color.onSurfaceVariant }
                        Rectangle {
                            Layout.fillWidth:true
                            implicitHeight:rows.implicitHeight+16
                            radius:16;color:Theme.color.surfaceContainerLowest
                            ColumnLayout {
                                id:rows
                                x:8;y:8;width:parent.width-16;spacing:0
                                Repeater {
                                    model:section.ids
                                    SuperArrow {
                                        objectName:"settingsCategory_"+modelData
                                        Layout.fillWidth:true
                                        visible:settings.categories().indexOf(modelData)>=0
                                        title:i18n.t("settings.category."+modelData)
                                        onClicked:page.activeCategory=modelData
                                    }
                                }
                                SuperSwitch {
                                    objectName:"settingsAutoRotate"
                                    Layout.fillWidth:true
                                    visible:sectionIndex===1 && settings.categories().indexOf("overlayLyric")>=0
                                    title:"播放页旋转自动进入横屏模式"
                                    summary:"横向保持 1 秒后切换"
                                    property bool stored:settings.value("playbackAutoRotate")===true
                                    checked:stored
                                    onStoredChanged:checked=stored
                                    onClicked:settings.setValue("playbackAutoRotate",checked)
                                }
                            }
                        }
                    }
                }
            }
            ViewportScrollBar { target:scroll;bottomInset:page.bottomContentInset }
        }
    }
    SettingsDetailPage {
        anchors.fill:parent
        visible:page.activeCategory.length>0;enabled:visible
        bottomContentInset:page.bottomContentInset
        currentCategory:page.activeCategory
        onBack:page.activeCategory=""
        onHome:page.home()
        onOpenDebugLog:page.openDebugLog()
    }
}
