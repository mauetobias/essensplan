package cc.maue.essensplan.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cc.maue.essensplan.data.Settings
import cc.maue.essensplan.ui.PlanActivity

/** Homescreen-Knopf: tippen öffnet die Zeitraumauswahl. Zeigt den letzten Plan-Zeitraum. */
class PlanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val last = Settings(context).lastPlanTitle
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp)
                    .background(ColorProvider(Color(0xFF16A34A))).padding(12.dp)
                    .clickable(actionStartActivity<PlanActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🥗  Essensplan", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(
                    if (last.isBlank()) "Tippen zum Erstellen" else "Zuletzt: $last",
                    style = TextStyle(color = ColorProvider(Color(0xFFD9F99D)), fontSize = 12.sp),
                )
            }
        }
    }
}

class PlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlanWidget()
}
