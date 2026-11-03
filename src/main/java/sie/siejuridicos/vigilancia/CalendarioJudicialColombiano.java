package sie.siejuridicos.vigilancia;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

// Calendario de días hábiles judiciales de Colombia -- usado para inferir cuándo vence un
// término procesal (ver ClasificadorActuacionFundamental para "qué actuación corre un
// término", esta clase responde "para cuándo"). Nunca se afirma como una certificación legal
// exacta: los festivos de Ley Emiliani y el rango de vacancia judicial están implementados
// según el criterio general más citado (no se verificó contra el texto oficial de cada
// Acuerdo del Consejo Superior de la Judicatura) -- si una fecha de vencimiento calculada
// aquí alguna vez decide algo con consecuencias reales, un humano debe confirmarla contra la
// fuente oficial antes de actuar, igual que con cualquier otro dato de este módulo de
// vigilancia que no tiene SLA ni garantía oficial.
final class CalendarioJudicialColombiano {

    private CalendarioJudicialColombiano() {
    }

    // Vacancia judicial colectiva: del 19 de diciembre al 10 de enero, ambos inclusive (cruza
    // de un año a otro).
    static boolean esVacanciaJudicial(LocalDate fecha) {
        Month mes = fecha.getMonth();
        int dia = fecha.getDayOfMonth();
        if (mes == Month.DECEMBER && dia >= 19) {
            return true;
        }
        return mes == Month.JANUARY && dia <= 10;
    }

    static boolean esHabil(LocalDate fecha) {
        DayOfWeek dow = fecha.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        if (esVacanciaJudicial(fecha)) {
            return false;
        }
        return !festivos(fecha.getYear()).contains(fecha)
                && !festivos(fecha.getYear() - 1).contains(fecha)
                && !festivos(fecha.getYear() + 1).contains(fecha);
    }

    // Art. 118 CGP: el término empieza a correr el día HÁBIL SIGUIENTE a la notificación (acá,
    // la fecha de publicación del estado -- ver fechaRegistro en ActuacionJudicial). Cuenta
    // diasHabiles completos saltando fines de semana, festivos y vacancia judicial.
    static LocalDate calcularVencimiento(LocalDate fechaNotificacion, int diasHabiles) {
        LocalDate fecha = fechaNotificacion;
        int contados = 0;
        while (contados < diasHabiles) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha)) {
                contados++;
            }
        }
        return fecha;
    }

    // Festivos civiles de Colombia para un año dado, aplicando la Ley 51 de 1983 (Ley
    // Emiliani): los que caen entre semana y no son de fecha fija civil-religiosa se trasladan
    // al lunes siguiente. Se recalculan festivos de años vecinos en esHabil() porque la
    // vacancia judicial cruza diciembre-enero y un festivo de "enero" puede pertenecer al año
    // calculado con el año equivocado si solo se mirara fecha.getYear().
    private static Set<LocalDate> festivos(int anio) {
        Set<LocalDate> f = new HashSet<>();
        // Fijos, nunca se trasladan.
        f.add(LocalDate.of(anio, 1, 1));   // Año Nuevo
        f.add(LocalDate.of(anio, 5, 1));   // Día del Trabajo
        f.add(LocalDate.of(anio, 7, 20));  // Independencia
        f.add(LocalDate.of(anio, 8, 7));   // Batalla de Boyacá
        f.add(LocalDate.of(anio, 12, 8));  // Inmaculada Concepción
        f.add(LocalDate.of(anio, 12, 25)); // Navidad

        // Ley Emiliani: se trasladan al lunes siguiente si no caen ya en lunes.
        f.add(alLunesSiguiente(LocalDate.of(anio, 1, 6)));   // Reyes Magos
        f.add(alLunesSiguiente(LocalDate.of(anio, 3, 19)));  // San José
        f.add(alLunesSiguiente(LocalDate.of(anio, 6, 29)));  // San Pedro y San Pablo
        f.add(alLunesSiguiente(LocalDate.of(anio, 8, 15)));  // Asunción de la Virgen
        f.add(alLunesSiguiente(LocalDate.of(anio, 10, 12))); // Día de la Raza
        f.add(alLunesSiguiente(LocalDate.of(anio, 11, 1)));  // Todos los Santos
        f.add(alLunesSiguiente(LocalDate.of(anio, 11, 11))); // Independencia de Cartagena

        // Móviles según la Pascua, trasladados al lunes siguiente salvo Jueves/Viernes Santo
        // (esos nunca se trasladan, caen donde caigan).
        LocalDate pascua = domingoDePascua(anio);
        f.add(pascua.minusDays(3)); // Jueves Santo
        f.add(pascua.minusDays(2)); // Viernes Santo
        f.add(alLunesSiguiente(pascua.plusDays(39))); // Ascensión del Señor
        f.add(alLunesSiguiente(pascua.plusDays(60))); // Corpus Christi
        f.add(alLunesSiguiente(pascua.plusDays(68))); // Sagrado Corazón de Jesús
        return f;
    }

    private static LocalDate alLunesSiguiente(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.MONDAY
                ? fecha
                : fecha.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    // Algoritmo de Gauss/Meeus para el domingo de Pascua en el calendario gregoriano.
    private static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
