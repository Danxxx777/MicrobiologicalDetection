package com.example.microbiologicaldetection.data

import java.text.Normalizer

object EquipmentKnowledge {

    fun displayName(label: String): String = canonicalLabel(label)
        .trim()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    fun contextFor(label: String): String? = guides[canonicalLabel(label)]

    fun sheetFor(label: String): EquipmentSheet? {
        val guide = contextFor(label) ?: return null
        return EquipmentSheet(
            name = displayName(label),
            procedure = guide,
            practices = "Información base tomada del instructivo institucional. Para parámetros específicos, consulte el manual del fabricante y el protocolo del laboratorio."
        )
    }

    private fun canonicalLabel(value: String): String {
        val normalized = normalize(value)
        return aliases[normalized] ?: normalized
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .trim()
        .lowercase()
        .replace(Regex("[ _-]+"), "_")

    private val aliases = mapOf(
        "agitadoor" to "agitador"
    )

    private val guides = mapOf(
        "agitador" to """Mezcla, homogeneiza o agita líquidos mediante movimientos oscilatorios o rotatorios. Se usa para mezclar reactivos, mantener partículas en suspensión y preparar muestras. Uso básico: centrar y asegurar el recipiente, encender, ajustar las RPM, vigilar la estabilidad y apagar antes de retirarlo.""",
        "agitador_orbital" to """Realiza un movimiento circular uniforme para mezclar líquidos y cultivos. Se usa en cultivos celulares y microbianos, preparación de soluciones y pruebas ambientales o químicas. Uso básico: centrar el recipiente, encender, ajustar tiempo y RPM evitando salpicaduras, comprobar estabilidad y retirar la muestra con el equipo apagado.""",
        "autoclave" to """Esteriliza con vapor a alta presión materiales resistentes al calor. Se usa para destruir bacterias, hongos, virus y esporas. Debe cargarse sin superar el 80 %, cerrarse de forma uniforme y nunca abrirse hasta que la presión haya bajado. No debe operar en seco y siempre prevalece el manual del fabricante.""",
        "balanza_analitica" to """Mide masa con alta precisión, hasta 0,0001 g según el instructivo. Se usa para pesar reactivos, control de calidad y preparación de estándares. Debe estar nivelada y estable; se calibra si corresponde, se tara el recipiente, se añade la muestra lentamente y se limpia el plato con una brocha suave.""",
        "balanza_electrica" to """Mide masa electrónicamente, normalmente con menor sensibilidad y mayor capacidad que una balanza analítica. Se usa para medios de cultivo, muestras biológicas e inventario. Debe colocarse en una superficie estable, encenderse y estabilizarse en cero, tararse con el recipiente y limpiarse después del pesaje.""",
        "bano_maria" to """Calienta muestras indirecta y uniformemente en agua a temperatura controlada. Se usa para incubar muestras, mantener agar fundido y descongelar de forma controlada. Debe llenarse al nivel indicado, estabilizar la temperatura antes de introducir las muestras y evitar que los recipientes toquen el fondo caliente.""",
        "cabina_flujo_laminar" to """Genera un área limpia mediante aire filtrado para proteger la muestra. Se usa al preparar medios, sembrar microorganismos inocuos e inocular muestras sensibles. Se desinfecta con alcohol al 70 %, la luz UV debe apagarse antes de trabajar y el flujo HEPA debe estabilizarse. Este equipo protege la muestra, no necesariamente al operador.""",
        "calentador_agua" to """Calienta agua o soluciones acuosas de forma controlada para preparar medios, disolver reactivos o lavar material. Debe llenarse al nivel indicado, configurarse a la temperatura necesaria, esperarse la señal de estabilización y manipular el agua caliente con precaución.""",
        "centrifuga" to """Separa componentes de mezclas líquidas por fuerza centrífuga. Se usa para separar suero o plasma, concentrar microorganismos y separar precipitados. Los tubos deben equilibrarse por pares con masas iguales, la tapa debe cerrarse antes de iniciar y no debe abrirse hasta que el rotor se detenga por completo.""",
        "contador_colonias" to """Cuantifica colonias desarrolladas en placas de Petri. Se usa en recuento bacteriano, control microbiológico y validación de siembras. Se enciende la iluminación y el contador, se coloca la placa, se marca cada colonia, se registra el total y se limpia la superficie con alcohol al 70 %.""",
        "deshidratadora" to """Elimina humedad mediante aire caliente controlado. Se usa para preservar muestras, análisis gravimétrico y secado de vidrio. Las muestras se distribuyen sin superponer, se ajustan tiempo y temperatura según el protocolo, se supervisan y se retiran con guantes antes de almacenarlas en recipientes herméticos.""",
        "estereoscopio" to """Proporciona una imagen tridimensional ampliada a baja magnificación. Se usa para observar colonias, diseccionar tejidos e identificar insectos o parásitos. Se selecciona la iluminación, se coloca la muestra, se ajustan zoom, enfoque y distancia interpupilar, y luego se registra la observación.""",
        "incubadora" to """Mantiene condiciones controladas para cultivar o almacenar muestras biológicas y microbiológicas. Se usa en cultivos bacterianos y fúngicos, investigación analítica y fermentación. Debe nivelarse, configurarse según el protocolo, estabilizarse antes de cargar y evitar la sobrecarga para conservar la circulación interna.""",
        "microscopio" to """Permite observar microorganismos, células y estructuras microscópicas mediante lentes y una fuente de luz. Se inicia con el objetivo de menor aumento, se enfoca primero con el control macrométrico y luego con el micrométrico, se aumenta progresivamente y se limpian los objetivos con papel óptico al terminar.""",
        "microtomo_automatico" to """Realiza cortes finos y uniformes de muestras embebidas en parafina o resina. Se usa para preparación histológica, patología e investigación biomédica. Hay que fijar y orientar el bloque, asegurar la cuchilla, programar el grosor, desbastar y recoger las secciones con herramientas adecuadas. La cuchilla exige protección y manejo especializado.""",
        "placa_calentamiento" to """Proporciona calor uniforme sobre una superficie para calentar, hervir o disolver sustancias. Se usa al preparar medios, disolver reactivos y evaporar soluciones. Debe ubicarse lejos de inflamables, configurarse según el proceso, supervisarse constantemente y dejarse enfriar después de apagarla.""",
        "refrigerador" to """Conserva muestras, medios y reactivos normalmente entre 2 °C y 8 °C. Debe instalarse con ventilación, estabilizarse antes de cargar, organizar y etiquetar el contenido, comprobar la temperatura diariamente y evitar sobrellenarlo para mantener la circulación de aire."""
    )
}
