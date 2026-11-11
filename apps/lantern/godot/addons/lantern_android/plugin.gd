@tool
extends EditorPlugin
## Добавляет в Android-манифест ссылки lantern://join и lantern://friend (нужна Gradle-сборка)
## и службу «мир открыт для друзей» (LanternRelayService: держит мост, пока игрок в Minecraft).
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

	## Java-код службы кладём в Gradle-проект перед сборкой (сам проект res://android не хранится в git).
	func _export_begin(_features: PackedStringArray, _is_debug: bool, _path: String, _flags: int) -> void:
		var src := "res://addons/lantern_android/android/LanternRelayService.java"
		var dst_dir := "res://android/build/src/main/java/com/godot/game"
		if not DirAccess.dir_exists_absolute(dst_dir):
			return
		var f := FileAccess.open(dst_dir.path_join("LanternRelayService.java"), FileAccess.WRITE)
		if f:
			f.store_string(FileAccess.get_file_as_string(src))
			f.close()

	func _get_android_manifest_element_contents(_platform: EditorExportPlatform, _debug: bool) -> String:
		return """
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
"""

	func _get_android_manifest_application_element_contents(_platform: EditorExportPlatform, _debug: bool) -> String:
		return """
        <service
            android:name="com.godot.game.LanternRelayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Relays the user's own Minecraft LAN world to invited friends over the internet while the user plays in Minecraft" />
        </service>
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
