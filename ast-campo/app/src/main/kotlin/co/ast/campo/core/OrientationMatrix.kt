package co.ast.campo.core

/**
 * La marca se dibuja sobre los píxeles "crudos" del JPEG (sin rotarlos, para no duplicar memoria con
 * fotos de 50 MP) y la orientación EXIF se conserva. Para que la marca quede abajo a la izquierda de la
 * imagen YA ORIENTADA hace falta transformar coordenadas de "imagen vista" a "píxeles crudos".
 */
object OrientationMatrix {
    /** Indica si la orientación EXIF intercambia ancho y alto (5 a 8). */
    fun swapsAxes(orientation: Int): Boolean = orientation in 5..8

    /**
     * Coeficientes (a, b, c, d, e, f) con x' = a·x + b·y + c ; y' = d·x + e·y + f, que llevan un punto
     * de la imagen orientada (ancho [w], alto [h]) al bitmap crudo. Orientaciones EXIF 1 a 8.
     */
    fun displayToRaw(orientation: Int, w: Float, h: Float): FloatArray = when (orientation) {
        2 -> floatArrayOf(-1f, 0f, w, 0f, 1f, 0f)
        3 -> floatArrayOf(-1f, 0f, w, 0f, -1f, h)
        4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, h)
        5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f)
        6 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, w)
        7 -> floatArrayOf(0f, -1f, h, -1f, 0f, w)
        8 -> floatArrayOf(0f, -1f, h, 1f, 0f, 0f)
        else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
    }
}
