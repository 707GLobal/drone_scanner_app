# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# 腾讯位置服务地图 SDK（AAR 自带 consumer rules，通常无需额外配置；
# 若混淆后异常可取消注释以下规则）
#-keep class com.tencent.tencentmap.**{*;}
#-dontwarn com.tencent.tencentmap.**

# Compose（material3 / ui / icons-core 自带 consumer rules，无需额外配置）
