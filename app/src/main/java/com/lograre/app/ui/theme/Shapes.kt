package com.lograre.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object ZenShapes {
    val Card = RoundedCornerShape(ZenDimens.CardRadius)
    val Pill = CircleShape
    val WaterCapsule = RoundedCornerShape(ZenDimens.WaterCapsuleRadius)
    val Dialog = RoundedCornerShape(ZenDimens.CardRadius)
    val BottomSheet = RoundedCornerShape(
        topStart = ZenDimens.CardRadius,
        topEnd = ZenDimens.CardRadius
    )
}

val ZenMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = ZenShapes.Pill,
    medium = ZenShapes.WaterCapsule,
    large = ZenShapes.Card,
    extraLarge = ZenShapes.Card
)
