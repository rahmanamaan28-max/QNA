package com.offlinestudy.solver.util

/**
 * Defense-in-depth, not the primary guarantee: the primary guarantee is that
 * AndroidManifest.xml omits android.permission.INTERNET entirely, which the
 * OS enforces at the socket layer for the whole process regardless of what
 * application code does. This object exists only as a documented, greppable
 * assertion point: any future contributor who is tempted to add a network
 * call should trip over this comment first.
 *
 * CI/lint check suggestion (see README "Offline verification"): fail the
 * build if `android.permission.INTERNET` ever reappears in the merged
 * manifest, and fail if any new dependency pulls in Retrofit/OkHttp/Ktor/etc.
 */
object NetworkGuard {
    const val INTERNET_PERMISSION_MUST_NEVER_BE_ADDED = "android.permission.INTERNET"
}
