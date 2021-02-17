package core.util;

import com.install4j.runtime.installer.InstallerContextImpl;
import core.db.DBManager;
import core.model.HOVerwaltung;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;


/**
   Class allowing coherent representation of time between HT (CET) and user localized value
   This class also have method to convert a datetime into HTWeek and HTSeason
   Timestamps and Instants within the are all localized in CET (UTC would have be a better choice but we have to deal with HO history and with the fact that CHPP datetime refers to CET)
   ZonedDateTime should only be used for GUI display and not in code logic
 */
public class HTDatetime {

    private static ZoneId m_UserSystemZoneID = ZoneId.systemDefault();
    private static ZoneId m_UserZoneID;
    private static int m_UserSeasonOffsetDefault = HOVerwaltung.instance().getModel().getBasics().getSeasonOffset();
    private static int m_UserSeasonOffset;
    private static ZoneId m_HTzoneID = ZoneId.of("Europe/Stockholm");
    private final static Instant cl_LastUpdate = HOVerwaltung.instance().getModel().getBasics().getDatum().toInstant();

    // This is a bit unclear from HT side but latest discussion suggest that HTweeks are calculated as if ORIGIN_HT_DATE happened
    // at different time accross the world, i.e. no timezone attached

    //private static ZonedDateTime ORIGIN_HT_DATE = ZonedDateTime.of(1997, 9, 22, 0, 0, 0, 0, m_HTzoneID);
    private static LocalDate ORIGIN_HT_DATE = LocalDate.of(1997, 9, 22);
    private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Timestamp m_tsHT_CET;  // for compatibility only
    private Timestamp m_tsLocalized;  // for compatibility only
    private Instant m_iHT_CET;
    private Instant m_iLocalized;
    private ZonedDateTime m_zdtHT_CET;
    private ZonedDateTime m_zdtLocalized;
    private String m_sHT_CET; // String as provided by Hattrick in xml files
    private int m_HTweekLocalized;
    private int m_HTseasonLocalized;


    @Deprecated
    public Timestamp getUserLocalizedTimeAsTimestamp() {
        return m_tsLocalized;
    }

    @Deprecated
    public Timestamp getHattrickTimeAsTimestamp() {
        return m_tsHT_CET;
    }

    public ZonedDateTime getUserLocalizedTime() {
        return m_zdtLocalized;
    }

    public ZonedDateTime getHattrickTime() {
        return m_zdtHT_CET;
    }

    public String getHattrickTimeAsString() {
        return m_sHT_CET;
    }

    public int getHTSeasonLocalized() {
        return m_HTseasonLocalized;
    }

    public int getHTWeekLocalized() {
        return m_HTweekLocalized;
    }

    /**
     * By default resolves to System ZoneID
     * @param sDateTime as of "yyyy-MM-dd HH:mm:ss"
     **/
    public HTDatetime(String sDateTime) {
        this(sDateTime, m_UserSystemZoneID, m_UserSeasonOffsetDefault);
    }


    /**
     * @param sDateTime as of "yyyy-MM-dd HH:mm:ss"
     **/
    public HTDatetime(String sDateTime, ZoneId zoneID, Integer seasonOffset) {
        m_UserZoneID = zoneID;
        m_UserSeasonOffset = seasonOffset;
        m_sHT_CET = cleanDateTimeString(sDateTime);
        LocalDateTime _htDateTimeNonLocalized = LocalDateTime.parse(m_sHT_CET, dtf);
        m_zdtHT_CET = ZonedDateTime.of(_htDateTimeNonLocalized, m_HTzoneID);
        m_zdtLocalized = m_zdtHT_CET.withZoneSameInstant(m_UserZoneID);
        m_iHT_CET = m_zdtHT_CET.toInstant();
        m_iLocalized = m_zdtLocalized.toInstant();
        m_tsHT_CET = Timestamp.from(m_iHT_CET);
        m_tsLocalized = Timestamp.from(m_iLocalized);

        // This is a bit unclear from HT side but latest discussion suggest that HTweeks are calculated as if ORIGIN_HT_DATE happened
        // at different time accross the world, i.e. no timezone attached
//        ZonedDateTime origin_ht_date_localized = ORIGIN_HT_DATE.withZoneSameInstant(m_UserZoneID);
        ZonedDateTime origin_ht_date_localized = ORIGIN_HT_DATE.atStartOfDay(m_UserZoneID);

        long nbDays = ChronoUnit.DAYS.between(origin_ht_date_localized.toLocalDate(), m_zdtLocalized.toLocalDate());
        long nbWeeks = nbDays / 7;

        m_HTseasonLocalized = (int)Math.floorDiv(nbWeeks, 16) + 1 + m_UserSeasonOffset;
        m_HTweekLocalized = (int)(nbWeeks % 16) + 1;
    }


    public static boolean isInTheFuture(ZonedDateTime zdt){
        ZonedDateTime now = ZonedDateTime.now(m_HTzoneID);
        return zdt.isAfter(now);
    }

    public static boolean isAfterLastUpdate(ZonedDateTime zdt){
        return zdt.toInstant().isAfter(cl_LastUpdate);
    }

    public boolean isInTheFuture(){
        ZonedDateTime zdt = ZonedDateTime.now(m_HTzoneID);
        return m_zdtHT_CET.isAfter(zdt);
    }

    public boolean isAfter(Instant refTimne){
        return m_iHT_CET.isAfter(refTimne);
    }

    public boolean isPassed(){
        return !isInTheFuture();
    }


    public HTDatetime(Timestamp ts) {
        this(ts.toInstant());
    }

    /**
       This constructor is supposed to be used for instant representing time in HT tZ
     */
    public HTDatetime(Instant instant) {
        this(DateTimeUtils.InstantToSQLtimeStamp(instant));
    }


    private String cleanDateTimeString(String sDateTime) {

            int dateInputSize = sDateTime.length();

            if (dateInputSize < 10){
                throw new IllegalArgumentException("The provided string is not recognized as a valid DateTime: " + sDateTime);
            }

            if(sDateTime.length()>19){
                return sDateTime.substring(0, 19);
            }

            if (sDateTime.length()==19){
                return sDateTime;
            }

            return sDateTime.substring(0, 10) + " 00:00:00";

    }


}