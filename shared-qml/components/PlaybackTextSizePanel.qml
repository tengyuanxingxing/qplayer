import QtQuick
import miuix.Core

Rectangle {
    id: panel
    signal closed()
    width: 280; height: 70; radius: 20
    color: "#F0222B35"
    Row {
        x: 8; y: 10; spacing: 4
        Button { width: 80; height: 50; text: i18n.t("settings.playerTextSize.small"); onClicked: { settings.setValue("playerTextSize", 0); panel.closed() } }
        Button { width: 80; height: 50; text: i18n.t("settings.playerTextSize.medium"); onClicked: { settings.setValue("playerTextSize", 1); panel.closed() } }
        Button { width: 80; height: 50; text: i18n.t("settings.playerTextSize.large"); onClicked: { settings.setValue("playerTextSize", 2); panel.closed() } }
    }
}
