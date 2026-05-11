package net.rsprot.protocol.loginprot.incoming.util

import net.rsprot.protocol.common.client.OldSchoolClientType

/**
 * Compatibility shim for legacy applet-style 228 clients.
 *
 * The upstream rsprot model expects the login-block client type to start at
 * DESKTOP = 1, but the RuneLite applet path we use during the Dodian client
 * bootstrap writes 0. Keep the normal ids intact and map that legacy value to
 * desktop before NetworkServiceFactory applies its stricter supported-client
 * checks.
 */
public enum class LoginClientType(
    public val id: Int,
) {
    LEGACY_DESKTOP(0),
    DESKTOP(1),
    ANDROID(2),
    IOS(3),
    ENHANCED_WINDOWS(4),
    ENHANCED_MAC(5),
    ENHANCED_ANDROID(7),
    ENHANCED_IOS(8),
    ENHANCED_LINUX(10),
    ;

    public fun toOldSchoolClientType(): OldSchoolClientType? {
        return when (this) {
            LEGACY_DESKTOP -> OldSchoolClientType.DESKTOP
            DESKTOP -> OldSchoolClientType.DESKTOP
            ENHANCED_WINDOWS -> OldSchoolClientType.DESKTOP
            ENHANCED_LINUX -> OldSchoolClientType.DESKTOP
            ENHANCED_MAC -> OldSchoolClientType.DESKTOP
            ENHANCED_ANDROID -> OldSchoolClientType.ANDROID
            ENHANCED_IOS -> OldSchoolClientType.IOS
            else -> null
        }
    }

    public companion object {
        public operator fun get(id: Int): LoginClientType =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown client type: $id")
    }
}
