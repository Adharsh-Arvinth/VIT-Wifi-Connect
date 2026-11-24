# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the widget provider
-keep public class com.vitwificonnect.widget.VitWifiWidgetProvider {
    public protected *;
}

# Keep the tile service
-keep public class com.vitwificonnect.widget.VitWifiTileService {
    public protected *;
}
