package com.cruciblelab.trafficlogger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val TrafficShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Ekranlar arasında birbirinden habersiz seçilmiş, birbirine yakın ama tam eşleşmeyen
 * köşe yarıçapları (12/14/16/18/20dp karışık kullanılıyordu) tek bir 3 katmanlı ölçeğe
 * indirgendi. Her yeni Card/Button burada tanımlı üç değerden birini kullanmalı; yeni bir
 * "neredeyse aynı" değer eklemek yerine en yakın katmana yuvarla.
 *
 * - [CardShapeLarge]: ekranın ana/birincil içerik kartları (tam genişlik, en dış kart).
 * - [CardShapeMedium]: ikincil/daha küçük kartlar (grid karoları, banner'lar).
 * - [CardShapeSmall]: buton, arama kutusu, tıklanabilir liste satırı gibi kontrol öğeleri.
 */
val CardShapeLarge = RoundedCornerShape(20.dp)
val CardShapeMedium = RoundedCornerShape(16.dp)
val CardShapeSmall = RoundedCornerShape(12.dp)
