package com.blacksingularities.opencalc.engine

/**
 * Jednostka: przelicznik na jednostkę bazową kategorii. Dla temperatury i spalania
 * przeliczenie nie jest liniowe, więc kategoria ma własne funkcje.
 */
data class Measure(
    val id: String,
    val name: String,
    val symbol: String,
    val factor: Double = 1.0,
    val toBase: ((Double) -> Double)? = null,
    val fromBase: ((Double) -> Double)? = null,
) {
    fun toBaseValue(v: Double) = toBase?.invoke(v) ?: (v * factor)
    fun fromBaseValue(v: Double) = fromBase?.invoke(v) ?: (v / factor)
}

data class UnitCategory(val id: String, val name: String, val units: List<Measure>)

fun convert(value: Double, from: Measure, to: Measure) = to.fromBaseValue(from.toBaseValue(value))

object Units {
    private fun u(id: String, name: String, symbol: String, factor: Double) = Measure(id, name, symbol, factor)

    val categories: List<UnitCategory> = listOf(
        UnitCategory(
            "length", "Długość",
            listOf(
                u("nm", "Nanometr", "nm", 1e-9), u("um", "Mikrometr", "µm", 1e-6), u("mm", "Milimetr", "mm", 1e-3),
                u("cm", "Centymetr", "cm", 1e-2), u("dm", "Decymetr", "dm", 0.1), u("m", "Metr", "m", 1.0),
                u("km", "Kilometr", "km", 1000.0), u("in", "Cal", "in", 0.0254), u("ft", "Stopa", "ft", 0.3048),
                u("yd", "Jard", "yd", 0.9144), u("mi", "Mila", "mi", 1609.344), u("nmi", "Mila morska", "NM", 1852.0),
                u("au", "Jednostka astronomiczna", "au", 149_597_870_700.0), u("ly", "Rok świetlny", "ly", 9.4607304725808e15),
                u("pc", "Parsek", "pc", 3.0856775814913673e16),
            ),
        ),
        UnitCategory(
            "area", "Powierzchnia",
            listOf(
                u("mm2", "Milimetr kwadratowy", "mm²", 1e-6), u("cm2", "Centymetr kwadratowy", "cm²", 1e-4),
                u("m2", "Metr kwadratowy", "m²", 1.0), u("a", "Ar", "a", 100.0), u("ha", "Hektar", "ha", 10_000.0),
                u("km2", "Kilometr kwadratowy", "km²", 1e6), u("in2", "Cal kwadratowy", "in²", 0.00064516),
                u("ft2", "Stopa kwadratowa", "ft²", 0.09290304), u("ac", "Akr", "ac", 4046.8564224),
                u("mi2", "Mila kwadratowa", "mi²", 2_589_988.110336),
            ),
        ),
        UnitCategory(
            "volume", "Objętość",
            listOf(
                u("ml", "Mililitr", "ml", 1e-3), u("cl", "Centylitr", "cl", 1e-2), u("l", "Litr", "l", 1.0),
                u("cm3", "Centymetr sześcienny", "cm³", 1e-3), u("m3", "Metr sześcienny", "m³", 1000.0),
                u("tsp", "Łyżeczka", "łyżeczka", 0.005), u("tbsp", "Łyżka", "łyżka", 0.015), u("cup", "Szklanka", "szklanka", 0.25),
                u("galus", "Galon amerykański", "gal US", 3.785411784), u("galuk", "Galon brytyjski", "gal UK", 4.54609),
                u("flozus", "Uncja płynu (US)", "fl oz", 0.0295735295625), u("pint", "Pinta (UK)", "pt", 0.56826125),
                u("bbl", "Baryłka ropy", "bbl", 158.987294928),
            ),
        ),
        UnitCategory(
            "mass", "Masa",
            listOf(
                u("mg", "Miligram", "mg", 1e-6), u("g", "Gram", "g", 1e-3), u("dag", "Dekagram", "dag", 1e-2),
                u("kg", "Kilogram", "kg", 1.0), u("q", "Kwintal", "q", 100.0), u("t", "Tona", "t", 1000.0),
                u("ct", "Karat", "ct", 0.0002), u("oz", "Uncja", "oz", 0.028349523125), u("lb", "Funt", "lb", 0.45359237),
                u("st", "Kamień", "st", 6.35029318),
            ),
        ),
        UnitCategory(
            "temperature", "Temperatura",
            listOf(
                Measure("c", "Stopień Celsjusza", "°C"),
                Measure("f", "Stopień Fahrenheita", "°F", toBase = { (it - 32) * 5 / 9 }, fromBase = { it * 9 / 5 + 32 }),
                Measure("k", "Kelwin", "K", toBase = { it - 273.15 }, fromBase = { it + 273.15 }),
                Measure("r", "Stopień Rankine'a", "°R", toBase = { (it - 491.67) * 5 / 9 }, fromBase = { (it + 273.15) * 9 / 5 }),
            ),
        ),
        UnitCategory(
            "speed", "Prędkość",
            listOf(
                u("mps", "Metr na sekundę", "m/s", 1.0), u("kmh", "Kilometr na godzinę", "km/h", 1 / 3.6),
                u("mph", "Mila na godzinę", "mph", 0.44704), u("kn", "Węzeł", "kn", 1852.0 / 3600),
                u("fps", "Stopa na sekundę", "ft/s", 0.3048), u("mach", "Mach (na poziomie morza)", "Ma", 340.29),
                u("c", "Prędkość światła", "c", 299_792_458.0),
            ),
        ),
        UnitCategory(
            "time", "Czas",
            listOf(
                u("ns", "Nanosekunda", "ns", 1e-9), u("us", "Mikrosekunda", "µs", 1e-6), u("ms", "Milisekunda", "ms", 1e-3),
                u("s", "Sekunda", "s", 1.0), u("min", "Minuta", "min", 60.0), u("h", "Godzina", "h", 3600.0),
                u("d", "Doba", "d", 86_400.0), u("wk", "Tydzień", "tydz.", 604_800.0), u("mo", "Miesiąc (średni)", "mies.", 2_629_746.0),
                u("y", "Rok (365 dni)", "rok", 31_536_000.0), u("dec", "Dekada", "dekada", 315_360_000.0),
            ),
        ),
        UnitCategory(
            "data", "Dane",
            listOf(
                u("bit", "Bit", "b", 0.125), u("B", "Bajt", "B", 1.0), u("kB", "Kilobajt", "kB", 1e3), u("MB", "Megabajt", "MB", 1e6),
                u("GB", "Gigabajt", "GB", 1e9), u("TB", "Terabajt", "TB", 1e12), u("PB", "Petabajt", "PB", 1e15),
                u("KiB", "Kibibajt", "KiB", 1024.0), u("MiB", "Mebibajt", "MiB", 1_048_576.0),
                u("GiB", "Gibibajt", "GiB", 1_073_741_824.0), u("TiB", "Tebibajt", "TiB", 1_099_511_627_776.0),
            ),
        ),
        UnitCategory(
            "transfer", "Transfer danych",
            listOf(
                u("bps", "Bit na sekundę", "b/s", 1.0), u("kbps", "Kilobit na sekundę", "kb/s", 1e3),
                u("Mbps", "Megabit na sekundę", "Mb/s", 1e6), u("Gbps", "Gigabit na sekundę", "Gb/s", 1e9),
                u("Bps", "Bajt na sekundę", "B/s", 8.0), u("kBps", "Kilobajt na sekundę", "kB/s", 8e3),
                u("MBps", "Megabajt na sekundę", "MB/s", 8e6), u("GBps", "Gigabajt na sekundę", "GB/s", 8e9),
            ),
        ),
        UnitCategory(
            "pressure", "Ciśnienie",
            listOf(
                u("pa", "Paskal", "Pa", 1.0), u("hpa", "Hektopaskal", "hPa", 100.0), u("kpa", "Kilopaskal", "kPa", 1000.0),
                u("mpa", "Megapaskal", "MPa", 1e6), u("bar", "Bar", "bar", 1e5), u("atm", "Atmosfera", "atm", 101_325.0),
                u("at", "Atmosfera techniczna", "at", 98_066.5), u("mmhg", "Milimetr słupa rtęci", "mmHg", 133.322387415),
                u("psi", "Funt na cal kwadratowy", "psi", 6894.757293168),
            ),
        ),
        UnitCategory(
            "energy", "Energia",
            listOf(
                u("j", "Dżul", "J", 1.0), u("kj", "Kilodżul", "kJ", 1000.0), u("mj", "Megadżul", "MJ", 1e6),
                u("cal", "Kaloria", "cal", 4.184), u("kcal", "Kilokaloria", "kcal", 4184.0), u("wh", "Watogodzina", "Wh", 3600.0),
                u("kwh", "Kilowatogodzina", "kWh", 3.6e6), u("ev", "Elektronowolt", "eV", 1.602176634e-19),
                u("btu", "BTU", "BTU", 1055.05585262),
            ),
        ),
        UnitCategory(
            "power", "Moc",
            listOf(
                u("w", "Wat", "W", 1.0), u("kw", "Kilowat", "kW", 1000.0), u("mw", "Megawat", "MW", 1e6),
                u("km", "Koń mechaniczny", "KM", 735.49875), u("hp", "Horsepower (imperialny)", "hp", 745.69987158),
                u("btuh", "BTU na godzinę", "BTU/h", 0.29307107),
            ),
        ),
        UnitCategory(
            "force", "Siła",
            listOf(
                u("n", "Niuton", "N", 1.0), u("kn", "Kiloniuton", "kN", 1000.0), u("kgf", "Kilogram-siła", "kgf", 9.80665),
                u("lbf", "Funt-siła", "lbf", 4.4482216152605), u("dyn", "Dyna", "dyn", 1e-5),
            ),
        ),
        UnitCategory(
            "angle", "Kąt",
            listOf(
                u("deg", "Stopień", "°", Math.PI / 180), u("rad", "Radian", "rad", 1.0), u("grad", "Grad", "gon", Math.PI / 200),
                u("arcmin", "Minuta kątowa", "′", Math.PI / 10_800), u("arcsec", "Sekunda kątowa", "″", Math.PI / 648_000),
                u("turn", "Obrót", "obr.", 2 * Math.PI),
            ),
        ),
        UnitCategory(
            "frequency", "Częstotliwość",
            listOf(
                u("hz", "Herc", "Hz", 1.0), u("khz", "Kiloherc", "kHz", 1e3), u("mhz", "Megaherc", "MHz", 1e6),
                u("ghz", "Gigaherc", "GHz", 1e9), u("rpm", "Obroty na minutę", "obr/min", 1 / 60.0),
            ),
        ),
        UnitCategory(
            "fuel", "Spalanie",
            listOf(
                Measure("l100", "Litry na 100 km", "l/100 km"),
                Measure("kml", "Kilometry na litr", "km/l", toBase = { if (it == 0.0) Double.POSITIVE_INFINITY else 100 / it }, fromBase = { if (it == 0.0) Double.POSITIVE_INFINITY else 100 / it }),
                Measure("mpgus", "Mile na galon (US)", "mpg US", toBase = { 235.214583 / it }, fromBase = { 235.214583 / it }),
                Measure("mpguk", "Mile na galon (UK)", "mpg UK", toBase = { 282.480936 / it }, fromBase = { 282.480936 / it }),
            ),
        ),
        UnitCategory(
            "cooking", "Kuchnia (mąka/cukier)",
            listOf(
                u("g", "Gram", "g", 1.0), u("dag", "Dekagram", "dag", 10.0), u("kg", "Kilogram", "kg", 1000.0),
                u("cupflour", "Szklanka mąki (250 ml)", "szkl. mąki", 160.0), u("cupsugar", "Szklanka cukru (250 ml)", "szkl. cukru", 220.0),
                u("tbspflour", "Łyżka mąki", "łyżka mąki", 10.0), u("tbspsugar", "Łyżka cukru", "łyżka cukru", 15.0),
                u("tspsalt", "Łyżeczka soli", "łyżeczka soli", 6.0),
            ),
        ),
    )
}
