package it.gov.pagopa.nodetsworker.util;

public class StatusUtil {

    public static String statoByReStatus(String reStatus){
        switch (reStatus) {
            case "payment_PAID":
            case "payment_PAID_NORPT":
            case "payment_NOTICE_GENERATED":
            case "payment_NOTICE_STORED":
            case "payment_NOTICE_SENT":
            case "payment_NOTIFIED":
            case "RT_ACCETTATA_PA":
                return "completed";
            case "payment_CANCELLED":
            case "payment_CANCELLED_NORPT":
            case "payment_PAYING":
            case "payment_PAYING_RPT":
            case "RPT_RISOLTA_OK":
            case "RPT_RISOLTA_KO":
            case "RPT_RICEVUTA_NODO":
            case "RPT_ACCETTATA_NODO":
            case "RPT_ERRORE_INVIO_A_PSP":
            case "RPT_INVIATA_A_PSP":
            case "RPT_ACCETTATA_PSP":
            case "RT_GENERATA_NODO":
            case "RT_RICEVUTA_NODO":
            case "RT_RIFIUTATA_NODO":
            case "RT_ACCETTATA_NODO":
            case "RT_ESITO_SCONOSCIUTO_PA":
            case "RT_INVIATA_PA":
            case "RPT_ESITO_SCONOSCIUTO_PSP":
            case "RPT_PARCHEGGIATA_NODO":
            case "RPT_PARCHEGGIATA_NODO_MOD3":
            case "RT_ERRORE_INVIO_A_PA":
                return "in progress";
            case "RPT_RIFIUTATA_PSP":
            case "RT_RIFIUTATA_PA":
            case "RPT_RIFIUTATA_NODO":
            case "payment_FAILED":
            case "payment_FAILED_NORPT":
                return "failed";



        }
        return null;
    }
}
