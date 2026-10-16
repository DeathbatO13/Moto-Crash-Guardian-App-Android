package com.motocrashguardian.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motocrashguardian.R

/**
 * Logo de SafeRide. El texto del logo es azul marino, asi que se dibuja sobre una pastilla
 * blanca para que se lea con el tema oscuro de la app.
 *
 * @param height alto del logo; el ancho sale de su proporcion.
 * @param markOnly solo el isotipo "SR", sin el nombre.
 */
@Composable
fun SafeRideLogo(
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    markOnly: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(12.dp)
    ) {
        Image(
            painter = painterResource(
                if (markOnly) R.drawable.logo_mark else R.drawable.logo_saferide
            ),
            contentDescription = "SafeRide",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .height(height)
        )
    }
}
