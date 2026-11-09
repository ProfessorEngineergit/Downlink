package io.github.professorengineergit.downlink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/* --- Empfang & Zubehör: kompakte Einkaufshilfe. Recherche-Stand 08.11.2026. --- */
private data class Offer(val title: String, val shop: String, val price: String, val rating: String, val note: String, val url: String, val img: String, val tag: String? = null)
private data class Region(val name: String, val setName: String, val total: String, val blurb: String, val core: List<Offer>, val plus: List<Offer>)

private const val V3_KIT_IMG = "https://m.media-amazon.com/images/I/71TXUQcJ4PL._AC_SL1500_.jpg"

private val EU = Region(
    "Europa", "RTL-SDR Blog V3 + Dipol + OTG-Adapter", "58,83 €",
    "Der Hersteller liefert nicht direkt in die EU, deshalb über seinen eBay-Shop (MwSt. inklusive).",
    listOf(
        Offer("RTL-SDR Blog V3 mit Antennen-Kit", "eBay · Hersteller rtl-sdr-blog", "53,35 €", "99,9 % positiv · 13.982 Bewertungen", "46,94 € plus 6,41 € Versand. Lieferung 9.–23. Dez. Lieferumfang vor dem Kauf prüfen.", "https://www.ebay.de/itm/283144516411", "https://i.ebayimg.com/images/g/dFcAAOSwiuBj6YHJ/s-l1600.webp", "Günstigster Weg"),
        Offer("UGREEN OTG-Adapter USB-C auf USB-A (2 Stück)", "Amazon.de", "5,48 €", "4,7 · 23.450 Bewertungen", "Fester Sitz, Lieferung morgen.", "https://www.amazon.de/dp/B0B9N3QSL3", "https://m.media-amazon.com/images/I/61DCiauYJ8L._AC_SL1500_.jpg"),
        Offer("RTL-SDR Blog V3 + Dipol-Kit", "Amazon.de · Versand aus den USA", "65,81 €", "4,4 · 6.237 Bewertungen", "Alternative, falls du Amazon bevorzugst. Lieferung 7.–22. Dez.", "https://www.amazon.de/dp/B0BMKB3L47", V3_KIT_IMG),
        Offer("Nooelec NESDR SMArt v5 Bundle", "Amazon.de · Nooelec", "58,36 €", "4,4 · 3.993 Bewertungen", "Morgen da, aber nur Magnetfuß statt Dipol: gut für die ISS, schwächer bei Meteor.", "https://www.amazon.de/dp/B01GDN1T4S", "https://m.media-amazon.com/images/I/71nQQh3SxRL._AC_SL1500_.jpg"),
    ),
    listOf(
        Offer("Nooelec SAWbird+ NOAA (LNA)", "Amazon.de", "53,41 €", "4,4 · 286 Bewertungen", "Verstärker mit 137-MHz-Filter. Nur für Meteor, für die ISS weglassen.", "https://www.amazon.de/dp/B07TWPR871", "https://m.media-amazon.com/images/I/81qZzza-x6L._AC_SL1500_.jpg"),
        Offer("SMA-Kabel 1 m, RG316 (2 Stück)", "Amazon.de", "10,85 €", "4,8 · 38 Bewertungen", "Verbindet LNA und Stick. SMA, nicht RP-SMA. No-Name, wenige Bewertungen.", "https://www.amazon.de/dp/B09FXN7SS8", "https://m.media-amazon.com/images/I/710+6ubhNNL._SL1500_.jpg"),
    ),
)

private val US = Region(
    "USA", "RTL-SDR Blog V3 + Dipol + OTG-Adapter", "54,39 $",
    "Direkt vom Hersteller über Amazon, plus Sales Tax. Gratis Versand ab 35 $.",
    listOf(
        Offer("RTL-SDR Blog V3 + Dipol-Kit", "Amazon.com · Verkauf durch RTL-SDR Blog", "47,95 $", "4,4 · 6.500 Bewertungen", "Vom Hersteller selbst, also kein Fälschungsrisiko.", "https://www.amazon.com/dp/B0BMKB3L47", V3_KIT_IMG, "Original"),
        Offer("Amazon Basics OTG-Adapter USB-C auf USB-A", "Amazon.com", "6,44 $", "4,6 · 59.758 Bewertungen", "Das kurze Kabel entlastet die Handybuchse.", "https://www.amazon.com/dp/B01GGKYXVE", "https://m.media-amazon.com/images/I/51WcFRLlhQL._AC_SL1500_.jpg"),
    ),
    listOf(
        Offer("Nooelec SAWbird+ NOAA (LNA)", "Amazon.com · Nooelec", "49,95 $", "4,5 · 284 Bewertungen", "Säubert das 137-MHz-Band. Für die ISS weglassen.", "https://www.amazon.com/dp/B07TWPR871", "https://m.media-amazon.com/images/I/81qZzza-x6L._AC_SL1500_.jpg"),
    ),
)

private val WORLD = Region(
    "Weltweit", "RTL-SDR Blog V3 + Dipol, dazu lokaler OTG-Adapter", "45–50 $",
    "Der Herstellershop liefert gratis aus China. Das dauert 1–8 Wochen, vor Weihnachten länger.",
    listOf(
        Offer("RTL-SDR Blog V3 + Dipol-Kit", "rtl-sdr.com · Herstellershop", "44,95–49,95 $", "Hersteller, keine Bewertungen im Shop", "Im China-Lager derzeit ausverkauft, Nachschub Anfang bis Mitte November. Stick 34,95 $ plus Kit 17,95 $ einzeln geht auch.", "https://www.rtl-sdr.com/buy-rtl-sdr-dvb-t-dongles/", V3_KIT_IMG),
        Offer("Mit Käuferschutz: RTLSDRBlog Store", "AliExpress · offizieller Shop", "ab 42,59 €", "4,9 · 89 Bewertungen", "V3C mit USB-C, nur der Stick. Der Dipol-Kit ist in Deutschland gesperrt.", "https://de.aliexpress.com/item/1005009176860935.html", "https://ae-pic-a1.aliexpress-media.com/kf/Sda872ac7f86a4f3c82d1d9c00be00dd7o.jpg"),
    ),
    emptyList(),
)

private val REGIONS = listOf(EU, US, WORLD)

private val GOOD_TO_KNOW = listOf(
    "Nimm den V3, nicht den V4. Der V4 ist kaum lieferbar, und der V4L läuft unter Android noch nicht zuverlässig.",
    "Für Meteor lohnt der Dipol. Ein Magnetfuß reicht für die ISS, Meteor-Bilder werden damit spürbar schlechter.",
    "Ein LNA mit 137-MHz-Filter hilft Meteor, schwächt aber die ISS auf 145,8 MHz. Für die ISS weglassen.",
    "Meide Angebote mit „V4 R860“ im Titel und No-Name-Dipole mit wenigen Bewertungen. Das sind meist Nachbauten.",
)

@Composable
fun GearShopScreen(bottom: Dp, onBack: () -> Unit) {
    var r by remember { mutableIntStateOf(0) }
    val reg = REGIONS[r]
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize().background(Ink.space)) {
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(Color(0xFF16305A), Ink.space))))
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", false, onBack)
                }
            }
            item {
                Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
                    Text("Empfang & Zubehör", style = MaterialTheme.typography.headlineMedium, color = Ink.text)
                    Text("Wetterbilder von Meteor-M und die ISS mit Handy, SDR-Stick und SatDump. Stand 08.11.2026, Preise ohne Gewähr.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                }
            }
            item {
                Row(Modifier.padding(start = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REGIONS.forEachIndexed { i, x -> Chip(x.name, i == r) { r = i } }
                }
            }
            item {
                Glass(Modifier.fillMaxWidth(), radius = 26.dp, tint = Mint.primary.copy(alpha = 0.14f), outline = Ink.outlineOn) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Sensors, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("EMPFOHLENES SET", style = MaterialTheme.typography.labelSmall, color = Mint.primary)
                            Text(reg.setName, style = MaterialTheme.typography.titleSmall, color = Ink.text)
                            Text(reg.blurb, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                        }
                        Text(reg.total, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Mint.primary)
                    }
                }
            }
            items(reg.core, key = { it.url }) { o -> OfferCard(o) { uri.openUri(o.url) } }
            if (reg.plus.isNotEmpty()) {
                item { SectionTitle("Plus: besserer Empfang für Meteor") }
                items(reg.plus, key = { it.url }) { o -> OfferCard(o) { uri.openUri(o.url) } }
            }
            item { SectionTitle("Gut zu wissen") }
            item {
                GlassGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GOOD_TO_KNOW.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink.text) }
                    }
                }
            }
            item {
                Text("Preise und Bewertungen stammen von den Händlerseiten (Amazon, eBay, AliExpress, Hersteller) am 08.11.2026, mit Lieferadresse Deutschland. Die Sterne von Amazon sind Durchschnitte, keine Garantie. Bilder gehören den Händlern und werden nur zur Orientierung online geladen. Downlink verdient an keinem dieser Links.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.faint, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
private fun OfferCard(o: Offer, onOpen: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), radius = 22.dp, tint = Color(0x1FFFFFFF), onClick = onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                AsyncImage(o.img, o.title, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (o.tag != null) Pill(o.tag, Mint.primary)
                Text(o.title, style = MaterialTheme.typography.titleSmall, color = Ink.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, tint = Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(o.rating, style = MaterialTheme.typography.labelSmall, color = Ink.text.copy(alpha = 0.85f))
                }
                Text(o.shop, style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                Text(o.note, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(o.price, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink.text, maxLines = 1, softWrap = false)
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Öffnen", tint = Mint.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
