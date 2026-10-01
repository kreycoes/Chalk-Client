# Keep Fabric's entry point and the two active mixins addressable by the names
# stored in fabric.mod.json and chalk-client.mixins.json.
-keep public class de.example.totemautoinv.TotemAutoInvAddon { public *; }
-keep class de.example.totemautoinv.mixin.** { *; }

# Meteor and Orbit call public lifecycle and event methods dynamically. Keep
# those method names while obfuscating implementation class names and private
# implementation details.
-keepclassmembers class de.example.totemautoinv.** {
    public *;
    protected *;
}

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keepdirectories
-renamesourcefileattribute Chalk
-dontshrink
-dontoptimize
-dontwarn **
-ignorewarnings
