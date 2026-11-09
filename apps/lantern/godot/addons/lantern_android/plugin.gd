@tool
extends EditorPlugin
## Добавляет в Android-манифест ссылки lantern://join и lantern://friend (нужна Gradle-сборка).
## Отдельный exported activity-alias: сама GodotApp остаётся exported=false, как в шаблоне Godot.

var _exp: EditorExportPlugin


func _enter_tree() -> void:
	_exp = LanternAndroidExport.new()
	add_export_plugin(_exp)


func _exit_tree() -> void:
	remove_export_plugin(_exp)
	_exp = null


class LanternAndroidExport extends EditorExportPlugin:
	func _get_name() -> String:
		return "LanternAndroid"

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		return platform is EditorExportPlatformAndroid

	func _get_android_manifest_application_element_contents(_platform: EditorExportPlatform, _debug: bool) -> String:
		return """
        <activity-alias
            android:name=".LanternDeepLink"
            android:targetActivity=".GodotApp"
            android:exported="true">
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="lantern" android:host="join" />
                <data android:scheme="lantern" android:host="friend" />
            </intent-filter>
        </activity-alias>
"""
