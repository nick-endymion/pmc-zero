package org.endy.pmczero.model

enum class LocationType(val idx: Int, val scheme: Scheme) {

    MAIN_HTTP(1, Scheme.HTTP),
    MAIN_FS(2, Scheme.FS),
    TN_HTTP(3, Scheme.HTTP),
    TN_FS(4, Scheme.FS)

}
