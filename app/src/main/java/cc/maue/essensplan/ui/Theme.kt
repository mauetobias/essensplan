package cc.maue.essensplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Frisch, gesund, fit: Minzgrün, Limette, ein Spritzer Orange auf hellem Grund. */
object Fresh {
    val Leaf = Color(0xFF16A34A)
    val Forest = Color(0xFF0E7A55)
    val Mint = Color(0xFFE7F8EE)
    val Lime = Color(0xFFD9F99D)
    val Citrus = Color(0xFFFB923C)
    val Peach = Color(0xFFFFEDD5)
    val Berry = Color(0xFFE11D48)
    val Ink = Color(0xFF0F2A1D)
    val Soft = Color(0xFF5B7065)
    val Bg = Color(0xFFF6FBF7)
    val Card = Color.White

    val Hero = Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF0E7A55)))
    val Sunrise = Brush.linearGradient(listOf(Color(0xFFFDBA74), Color(0xFFFB923C)))
}

private val scheme = lightColorScheme(
    primary = Fresh.Leaf, onPrimary = Color.White,
    primaryContainer = Fresh.Mint, onPrimaryContainer = Fresh.Forest,
    secondary = Fresh.Citrus, secondaryContainer = Fresh.Peach,
    background = Fresh.Bg, surface = Fresh.Card, onSurface = Fresh.Ink, onBackground = Fresh.Ink,
    surfaceVariant = Fresh.Mint, onSurfaceVariant = Fresh.Soft, outline = Color(0xFFCFE3D6),
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun FreshTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = type, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    ), content = content)

/** Großer Verlaufs-Kopf mit Titel und Untertitel. */
@Composable
fun HeroHeader(title: String, subtitle: String, emoji: String = "🥗", extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Fresh.Hero)
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .85f))
            }
        }
        extra()
    }
}

/** Weiße, weich schattierte Karte. */
@Composable
fun FreshCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Fresh.Forest.copy(alpha = .15f), spotColor = Fresh.Forest.copy(alpha = .15f))
            .clip(RoundedCornerShape(24.dp))
            .background(Fresh.Card)
    ) {
        if (accent != null) Box(Modifier.fillMaxWidth().height(5.dp).background(accent))
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** Kleine Kennzahl-Kachel (kcal, Protein, Schlaf …). */
@Composable
fun StatPill(label: String, value: String, tint: Color = Fresh.Leaf, bg: Color = Fresh.Mint, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(value, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = Fresh.Soft, fontSize = 12.sp)
    }
}

/** Haupt-Aktion mit Verlauf. */
@Composable
fun GradientButton(text: String, enabled: Boolean = true, brush: Brush = Fresh.Hero, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = PaddingValues(), shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        Box(Modifier.fillMaxSize().background(if (enabled) brush else Brush.linearGradient(listOf(Color(0xFFBFD8C8), Color(0xFFBFD8C8)))), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontSize = 17.sp)
        }
    }
}

@Composable
fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = Fresh.Forest, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp))
