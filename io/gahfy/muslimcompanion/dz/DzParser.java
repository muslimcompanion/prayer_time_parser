package io.gahfy.muslimcompanion.dz;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import io.gahfy.muslimcompanion.utils.Download;

public class DzParser{
    private static final String URL_ADRAR = "https://www.marw.dz/media/calendrier/1444/ADRAR.pdf";
    private static final String MD5_ADRAR = "0b4c8e2416255bf5273306939407f5fe";
    private static final String URL_DJELFA = "https://www.marw.dz/media/calendrier/1444/djelfa.pdf";
    private static final String MD5_DJELFA = "4d5e3f0618d86f6e0e1345158de6a04a";
    private static final String URL_ALGIERS = "https://www.marw.dz/media/calendrier/1444/alger.pdf";
    private static final String MD5_ALGIERS = "afe7895773ed9f551e3682a2ae8bcbb5";

    private static final String FILENAME_ADRAR = "download/ADRAR.pdf";
    private static final String FILENAME_DJELFA = "download/djelfa.pdf";
    private static final String FILENAME_ALGIERS = "download/alger.pdf";

    private static final String REGEXP_TIME_MAIN = "^([\\d]{2}:[\\d]{2}) ([\\d]{2}:[\\d]{2}) ([\\d]{2}:[\\d]{2}) ([\\d]{2}:[\\d]{2}) ([\\d/]{2}:[\\d/]{2}) ([\\d]{2}:[\\d]{2}) ([\\d]{2}:[\\d]{2}) ([\\d]{3,4})/{0,1}([\\d]{2,3})/([\\d]{2}) ([\\d]{1,2}) .*$";
    private static final Pattern PATTERN_TIME_MAIN = Pattern.compile(REGEXP_TIME_MAIN);

    public static void main(String[] args) {
        Download.main(new String[]{
            URL_ADRAR, MD5_ADRAR,
            URL_DJELFA, MD5_DJELFA,
            URL_ALGIERS, MD5_ALGIERS
        });

        PDDocument pdDoc = null;
        PDFTextStripper pdfStripper;

        try {
            pdfStripper = new PDFTextStripper();
            pdDoc = PDDocument.load(new File(FILENAME_ADRAR));
            String parsedText = pdfStripper.getText(pdDoc);
            performParsing(parsedText, ADRAR_CITIES, true);

            pdfStripper = new PDFTextStripper();
            pdDoc = PDDocument.load(new File(FILENAME_DJELFA));
            parsedText = pdfStripper.getText(pdDoc);
            performParsing(parsedText, DJELFA_CITIES, false);

            pdfStripper = new PDFTextStripper();
            pdDoc = PDDocument.load(new File(FILENAME_ALGIERS));
            parsedText = pdfStripper.getText(pdDoc);
            performParsing(parsedText, ALGIERS_CITIES, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (pdDoc != null)
                pdDoc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void performParsing(String pdfContent, String[] cities, boolean twoMonthlyAdditions) {
        // 0 - Expecting additions for the first half of the month, or for the whole month if `twoMonthlyAdditions` is false
        // 1 - Expecting additions for the second half of the month
        // 2 - Expecting prayer times of the main city
        int status = 0;
        // 1 - Muharram
        // 2 - Safar
        // 3 - Rabi' al-Awwal
        // 4 - Rabi' al-Thani / Rabiʽ al-Akhir
        // 5 - Jumada al-Awwal
        // 6 - Jumada al-Thani / Jumada al-Akhirah
        // 7 - Rajab
        // 8 - Sha'ban
        // 9 - Ramadan
        // 10 - Shawwal
        // 11 - Dhu al-Qadah
        // 12 - Dhu al-Hijja
        int hajriMonth = 1;
        // 0 - Fajr
        // 1 - Shorouq
        // 2 - Joual
        // 3 - Asr
        // 4 - Maghrib
        // 5 - Isha
        int currentPrayer = 0;
        Pattern arrayPattern = getArrayPattern(cities.length-1);

        // Contains the timestamp (in milliseconds) of midnight CET as key, and the hajri date (format: dd/MM/yyyy) as value
        Map<Long, String> hajriCalendar = new HashMap<>();
        // Prayer times, in the following format:
        // City
        //     Timestamp
        //         Prayer (see `currentPrayer`)
        //         Time of the prayer (format HH:mm)
        Map<String, Map<Long, Map<Integer, String>>> times = new HashMap<>();

        // Prayer times additions, in the following format:
        // City
        //     Half of month (0 for first half (or for the full month if `twoMonthlyAdditions` is false), 1 for second half)
        //         Prayer (see `currentPrayer`)
        //         Time (in minutes) to add to the prayer time of the main city
        Map<String, Map<Integer, Map<Integer, Integer>>> additions = new HashMap<>();

        // Initialisation of the HashMaps
        for(String city: cities) {
            times.put(city, new HashMap<>());
            additions.put(city, new HashMap<>());
        }

        // Initialisation of the variables
        boolean firstArrayFound = false;
        boolean secondArrayFound = false;
        boolean mainTimesFound = false;

        // Splitting pdfContent into lines
        String[] lines = pdfContent.split("\n");

        // Arraylist of all the timestamp, in order to be able to sort them in the final result
        List<Long> allTimes = new ArrayList<>();

        for(String line: lines) {
            // Trying to match the main pattern
            Matcher matcher = PATTERN_TIME_MAIN.matcher(line);

            // Trying to match the array pattern
            Matcher arrayMatcher = arrayPattern.matcher(line);

            // If we are expecting the first array and the line matches an array line
            if(status == 0 && arrayMatcher.matches()) {
                // If it's the first line of the first array of the page
                if(!firstArrayFound) {
                    // Resetting the additions map
                    for(String currentCity: cities) {
                        additions.put(currentCity, new HashMap<>());
                        additions.get(currentCity).put(0, new HashMap<>());
                        additions.get(currentCity).put(1, new HashMap<>());
                    }
                }
                firstArrayFound = true;
                // We set additions of the main city to be 0.
                additions.get(cities[0]).get(0).put(currentPrayer, 0);
                // We add the additional time for all the cities
                for(int i=1; i<cities.length; i++) {
                    additions.get(cities[i]).get(0).put(currentPrayer, parseInt(arrayMatcher.group(i)));
                }
                // We increment currentPrayer (next line will be next prayer)
                currentPrayer += 1;
            }
            // If we just finished parsing the first array
            else if(firstArrayFound && status == 0) {
                // We reset the variables
                firstArrayFound = false;
                // We will set the status to expect the array of the second half of the month, or to the main times if time addition is not splitted
                status += twoMonthlyAdditions ? 1 : 2;
                // We reset the current prayer
                currentPrayer = 0;
            }
            // If we expect the second array and found it
            else if(status == 1 && arrayMatcher.matches()) {
                secondArrayFound = true;
                // We set additions of the main city to be 0.
                additions.get(cities[0]).get(1).put(currentPrayer, 0);
                // We add the additional time for all the cities
                for(int i=1; i<cities.length; i++) {
                    additions.get(cities[i]).get(1).put(currentPrayer, parseInt(arrayMatcher.group(i)));
                }
                // We increment currentPrayer (next line will be next prayer)
                currentPrayer += 1;
            }
            // If we just finished parsing the second array
            else if(secondArrayFound && status == 1) {
                // We reset the variables
                secondArrayFound = false;
                status += 1;
                // We reset the current prayer
                currentPrayer = 0;
            }
            // If we expect the main times and found a line to match it
            else if(status == 2 && matcher.matches()) {
                mainTimesFound = true;
                // Extracting calendar data from the regex
                int hajriDay = parseInt(matcher.group(11));
                int gregorianDay = parseInt(matcher.group(10));
                String gregorianMonthString = matcher.group(9);
                String gregorianYearString = matcher.group(8);
                // Fixing some errors in pdfs:
                // Date in ADRAR shown as 202304/25 instead of 2023/04/25
                // Date in ADRAR shown as 202/305/25 instead of 2023/05/25
                if(gregorianYearString.length() == 3) {
                    gregorianYearString = String.format("%s%c", gregorianYearString, gregorianMonthString.charAt(0));
                    gregorianMonthString = gregorianMonthString.substring(1);
                }
                int gregorianYear = parseInt(gregorianYearString);
                int gregorianMonth = parseInt(gregorianMonthString);
                long dateTimestampInMilliseconds = getTimeInMillis(gregorianDay, gregorianMonth, gregorianYear);

                // Extracting prayer times from the regex
                String fajr = matcher.group(7);
                String shorouq = matcher.group(6);
                String qibla = matcher.group(5);
                String dohr = matcher.group(4);
                String asr = matcher.group(3);
                String maghrib = matcher.group(2);
                String isha = matcher.group(1);

                // Adding the hajri date extracted from regex to the map if it does not contain it already
                if(!hajriCalendar.containsKey(dateTimestampInMilliseconds)) {
                    hajriCalendar.put(dateTimestampInMilliseconds, String.format("%02d/%02d/%d", hajriDay, hajriMonth, 1444));
                }

                // Adding prayer times to the main map
                for(String key: times.keySet()) {
                    Map<Integer, String> prayer = new HashMap<>();
                    prayer.put(0, addTime(fajr, additions.get(key).get(hajriDay <= 15 || !twoMonthlyAdditions ? 0 : 1).get(0)));
                    prayer.put(1, addTime(shorouq, additions.get(key).get(hajriDay <= 15 || !twoMonthlyAdditions ? 0 : 1).get(1)));
                    prayer.put(2, addTime(dohr, additions.get(key).get(hajriDay <= 15 || !twoMonthlyAdditions ? 0 : 1).get(2)));
                    prayer.put(3, addTime(asr, additions.get(key).get(hajriDay <= 15 || !twoMonthlyAdditions ? 0 : 1).get(3)));
                    prayer.put(4, addTime(maghrib, additions.get(key).get(hajriDay <= 15 || !twoMonthlyAdditions ? 0 : 1).get(4)));
                    prayer.put(5, addTime(isha, additions.get(key).get(hajriDay <= 15 || !twoMonthlyAdditions ? 0 : 1).get(5)));

                    if(!allTimes.contains(dateTimestampInMilliseconds)) {
                        allTimes.add(dateTimestampInMilliseconds);
                    }
                    times.get(key).put(dateTimestampInMilliseconds, prayer);
                }
            }
            // If we just finished parsing the main times
            else if(status == 2 && mainTimesFound){
                // Resetting variables
                mainTimesFound = false;
                status = 0;
                hajriMonth += 1;
            }
        }

        // Sorting times
        Collections.sort(allTimes);

        // Writing map to files
        try{
            writeFiles(times, hajriCalendar, allTimes);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the timestamp (in milliseconds) of the given date at midnight CET.
     * @param day the day of the date for which to get the timestamp
     * @param month the month of the date for which to get the timestamp
     * @param year the year of the date for which to get the timestamp
     * @return the timestamp (in milliseconds) of the given date at midnight CET
     */
    private static long getTimeInMillis(int day, int month, int year) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeZone(TimeZone.getTimeZone("CET"));
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.MONTH, month-1);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * Returns the pattern of an addition array containing the given number of columns. The pattern is as follows:
     * 
     * - `([\d+-]+) ` repeated `columnNumbers` times.
     * - .* added at the end
     * 
     * @param columnNumbers the number of columns of the array for which to get the pattern.
     * @return the pattern of an addition array containing the given number of columns
     */
    private static Pattern getArrayPattern(int columnNumbers) {
        char[] arrayRegexpChars = new char[columnNumbers*10 + 2];
        for(int i=0; i<arrayRegexpChars.length-10; i+=10) {
            arrayRegexpChars[i] = '(';
            arrayRegexpChars[i+1] = '[';
            arrayRegexpChars[i+2] = '\\';
            arrayRegexpChars[i+3] = 'd';
            arrayRegexpChars[i+4] = '+';
            arrayRegexpChars[i+5] = '-';
            arrayRegexpChars[i+6] = ']';
            arrayRegexpChars[i+7] = '+';
            arrayRegexpChars[i+8] = ')';
            arrayRegexpChars[i+9] = ' ';
        }
        arrayRegexpChars[arrayRegexpChars.length-2] = '.';
        arrayRegexpChars[arrayRegexpChars.length-1] = '*';
        String arrayRegexp = new String(arrayRegexpChars);
        return Pattern.compile(arrayRegexp);
    }

    /**
     * Writes the prayer times in their dedicated files.
     * 
     * File path: Algeria/[Wilaya].bin or Algeria/[Wilaya]/[Daira].bin
     * 
     * Content of the file:
     * - 2 Bytes for the gregorian day (5b for the day, 4b for the month, 7b for the year%100)
     * - 2 Bytes for the islamic day (5b for the day, 4b for the month, 7b for the year%100)
     * - 10B for prayer times: 2 Bytes for each prayer time (1B for hour, 1B for minutes)
     * 
     * @param times the prayer times to write in files.
     * @param hajriCalendar the map which matches timestamps in milliseconds and hajri day in calendar
     * @param allTimes the list of timestamps sorted
     * @throws IOException if an exception occurs while reading the files
     */
    private static void writeFiles(Map<String, Map<Long, Map<Integer, String>>> times, Map<Long, String> hajriCalendar, List<Long> allTimes) throws IOException {
        for(String filename: times.keySet()) {
            String[] filenameSplitted = filename.split("/");
            String prayerFilePath = "prayer_times";
            File currentFile = new File(prayerFilePath);
            String currentFilePath = prayerFilePath+"/"+filenameSplitted[0];
            if(!currentFile.exists()) {
                currentFile.mkdir();
            }
            currentFile = new File(currentFilePath);
            if(!currentFile.exists()) {
                currentFile.mkdir();
            }
            for(int i=1; i<filenameSplitted.length - 1; i++) {
                currentFilePath = currentFilePath+"/"+filenameSplitted[i];
                currentFile = new File(currentFilePath);
                if(!currentFile.exists()) {
                    currentFile.mkdir();
                }
            }

            File file = new File(prayerFilePath+"/"+filename+".bin");
            FileOutputStream fos = new FileOutputStream(file);
            for(long timeInMillis: allTimes) {
                GregorianCalendar calendar = new GregorianCalendar();
                calendar.setTimeZone(TimeZone.getTimeZone("CET"));
                calendar.setTimeInMillis(timeInMillis);
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                int month = calendar.get(Calendar.MONTH)+1;
                int year = calendar.get(Calendar.YEAR);
                String date = String.format(Locale.US, "%02d/%02d/%d", day, month, year);
                String hajriDate = hajriCalendar.get(timeInMillis);
                String fajr = times.get(filename).get(timeInMillis).get(0);
                String shorouq = times.get(filename).get(timeInMillis).get(1);
                String dohr = times.get(filename).get(timeInMillis).get(2);
                String asr = times.get(filename).get(timeInMillis).get(3);
                String maghrib = times.get(filename).get(timeInMillis).get(4);
                String isha = times.get(filename).get(timeInMillis).get(5);
                fos.write(convertDate(date));
                fos.write(convertDate(hajriDate));
                fos.write(convertTime(fajr));
                fos.write(convertTime(shorouq));
                fos.write(convertTime(dohr));
                fos.write(convertTime(asr));
                fos.write(convertTime(maghrib));
                fos.write(convertTime(isha));
            }
            fos.close();
        }
    }

    /**
     * Returns the given time to which the given minutes have been added.
     * @param time the time on which to add minutes (In format "HH:mm")
     * @param addition the minutes to add (may be negative)
     * @return the given time to which the given minutes have been added
     */
    private static String addTime(String time, int addition) {
        int hour = parseInt(time.substring(0, 2));
        int minute = parseInt(time.substring(3, 5));
        if(addition >= 0) {
            while(minute+addition >= 60) {
                addition -= (60 - minute);
                hour += 1;
                minute = 0;
            }
        } else {
            while(minute + addition < 0) {
                addition += minute;
                hour -= 1;
                minute = 60;
            }
        }
        return String.format("%02d:%02d", hour, minute + addition);
    }

    /**
     * Returns the integer value of the given string.
     * 
     * If the integer starts with +- (as we can find some in adrar.pdf), then we do not consider the +.
     * @param value the integer value in the form of a String
     * @return the integer value of the given string
     */
    private static int parseInt(String value) {
        // An error found in the pdf, but we want to keep it unique, so we can find other mistakes easily
        if(value.equals("17+")) {
            return 17;
        }
        if(value.startsWith("+-")) {
            return Integer.parseInt(value.substring(1));
        } else {
            return Integer.parseInt(value);
        }
    }

    /**
     * Returns the encoding into two bytes of the given date.
     * 
     * 5b for the day followed by 4b for the month, followed by 7b for the year%100.
     * 
     * @param date the date for which to get the encoding
     * @return the encoding into two bytes of the given date
     */
    private static byte[] convertDate(String date) {
        byte[] result = new byte[2];
        String[] split = date.split("/");
        int day = Integer.parseInt(split[0]);
        int month = Integer.parseInt(split[1]);
        int year = Integer.parseInt(split[2]) % 100;
        result[0] = (byte) (((byte) day) << 3);
        result[0] += (month >> 1);
        result[1] = (byte) year;
        result[1] += (month << 7);
        return result;
    }

    /**
     * Returns the encoding into two bytes of the given time.
     * 
     * 1B for the hours followed by 1B for the minutes.
     * 
     * @param time the time for which to get the encoding
     * @return the encoding into two bytes of the given time.
     */
    private static byte[] convertTime(String time) {
        byte[] result = new byte[2];
        String[] split = time.split(":");
        result[0] = (byte) Integer.parseInt(split[0]);
        result[1] = (byte) Integer.parseInt(split[1]);
        return result;
    }

    private static final String ADRAR = "Algeria/Adrar";
    private static final String BENI_OUNIF = "Algeria/Béchar/Beni Ounif";
    private static final String TINDOUF = "Algeria/Tindouf";
    private static final String BECHAR = "Algeria/Béchar";
    private static final String TIMIMOUN = "Algeria/Timimoun";
    private static final String REGGANE = "Algeria/Adrar/Reggane";
    private static final String BORDJ_BADJI_MOKHTAR = "Algeria/Bordj Badji Mokhtar";
    private static final String BENI_ABBES = "Algeria/Béni Abbès";
    private static final String IN_SALAH = "Algeria/In Salah";
    private static final String EL_MENIAA = "Algeria/El Meniaa";
    private static final String GUARDAIA = "Algeria/Ghardaïa";
    private static final String OUARGLA = "Algeria/Ouargla";
    private static final String TAMANRASSET = "Algeria/Tamanrasset";
    private static final String IN_GUEZZAM = "Algeria/In Guezzam";
    private static final String ILLIZI = "Algeria/Illizi";
    private static final String DJANET = "Algeria/Djanet";
    private static final String IN_AMENAS = "Algeria/Illizi/In Amenas";
    private static final String DJELFA = "Algeria/Djelfa";
    private static final String TEBESSA = "Algeria/Tébessa";
    private static final String BIR_EL_ATER = "Algeria/Tébessa/Bir El Ater";
    private static final String KHENCHELA = "Algeria/Khenchela";
    private static final String EL_OUED = "Algeria/El Oued";
    private static final String BATNA = "Algeria/Batna";
    private static final String TOUGGOURT = "Algeria/Touggourt";
    private static final String OULED_DJELLAL = "Algeria/Ouled Djellal";
    private static final String EL_M_GHAIR = "Algeria/El M'Ghair";
    private static final String BISKRA = "Algeria/Biskra";
    private static final String BOU_SAADA = "Algeria/M'Sila/Bou Saâda";
    private static final String AIN_EL_MELH = "Algeria/M'Sila/Aïn El Melh";
    private static final String HASSI_R_MEL = "Algeria/Laghouat/Hassi R'Mel";
    private static final String LAGHOUAT = "Algeria/Laghouat";
    private static final String AIN_OUSSERA = "Algeria/Delfa/Aïn Oussera";
    private static final String TISSEMSILT = "Algeria/Tissemsilt";
    private static final String TIARET = "Algeria/Tiaret";
    private static final String EL_BAYADH = "Algeria/El Bayadh";
    private static final String SAIDA = "Algeria/Saïda";
    private static final String MASCARA = "Algeria/Mascara";
    private static final String NAAMA = "Algeria/Naâma";
    private static final String SIDI_BEL_ABBES = "Algeria/Sidi Bel Abbès";
    private static final String BEN_BADIS = "Algeria/Sidi Bel Abbès/Ben Badis";
    private static final String TLEMCEN = "Algeria/Tlemcen";
    private static final String AIN_TEMOUCHENT = "Algeria/Aïn Témouchent";
    private static final String SEBDOU = "Algeria/Tlemcen/Sebdou";
    private static final String MAGHNIA = "Algeria/Tlemcen/Maghnia";
    private static final String ALGIERS = "Algeria/Algiers";
    private static final String ORAN = "Algeria/Oran";
    private static final String MOSTAGANEM = "Algeria/Mostaganem";
    private static final String RELIZANE = "Algeria/Relizane";
    private static final String CHLEF = "Algeria/Chlef";
    private static final String AIN_DEFLA = "Algeria/Aïn Defla";
    private static final String TIPAZA = "Algeria/Tipaza";
    private static final String MEDEA = "Algeria/Médéa";
    private static final String BLIDA = "Algeria/Blida";
    private static final String BOUMERDES = "Algeria/Boumerdes";
    private static final String BOUIRA = "Algeria/Bouïra";
    private static final String DELLYS = "Algeria/Boumerdes/Dellys";
    private static final String TIZI_OUZOU = "Algeria/Tizi Ouzou";
    private static final String M_SILA = "Algeria/M'Sila";
    private static final String BORDJ_BOU_ARRERIDJ = "Algeria/Bordj Bou Arreridj";
    private static final String BEJAIA = "Algeria/Béjaïa";
    private static final String SETIF = "Algeria/Setif";
    private static final String JIJEL = "Algeria/Jijel";
    private static final String MILA = "Algeria/Mila";
    private static final String CONSTANTINE = "Algeria/Constantine";
    private static final String SKIKDA = "Algeria/Skikda";
    private static final String OUM_EL_BOUAGHI = "Algeria/Oum El-Bouaghi";
    private static final String GUELMA = "Algeria/Guelma";
    private static final String ANNABA = "Algeria/Annaba";
    private static final String SOUK_AHRAS = "Algeria/Souk Ahras";
    private static final String EL_TARF = "Algeria/El Tarf";



    private static final String[] ADRAR_CITIES = new String[]{
        ADRAR,
        BENI_OUNIF,
        TINDOUF,
        BECHAR,
        TIMIMOUN,
        REGGANE,
        BORDJ_BADJI_MOKHTAR,
        BENI_ABBES,
        IN_SALAH,
        EL_MENIAA,
        GUARDAIA,
        OUARGLA,
        TAMANRASSET,
        IN_GUEZZAM,
        ILLIZI,
        DJANET,
        IN_AMENAS
    };

    private static final String[] DJELFA_CITIES = new String[]{
        DJELFA,
        MAGHNIA,
        SEBDOU,
        AIN_TEMOUCHENT,
        TLEMCEN,
        BEN_BADIS,
        SIDI_BEL_ABBES,
        NAAMA,
        MASCARA,
        SAIDA,
        EL_BAYADH,
        TIARET,
        TISSEMSILT,
        AIN_OUSSERA,
        LAGHOUAT,
        HASSI_R_MEL,
        AIN_EL_MELH,
        BOU_SAADA,
        BISKRA,
        EL_M_GHAIR,
        OULED_DJELLAL,
        TOUGGOURT,
        BATNA,
        EL_OUED,
        KHENCHELA,
        BIR_EL_ATER,
        TEBESSA,
    };

    private static final String[] ALGIERS_CITIES = new String[]{
        ALGIERS,
        ORAN,
        MOSTAGANEM,
        RELIZANE,
        CHLEF,
        AIN_DEFLA,
        TIPAZA,
        MEDEA,
        BLIDA,
        BOUMERDES,
        BOUIRA,
        DELLYS,
        TIZI_OUZOU,
        M_SILA,
        BORDJ_BOU_ARRERIDJ,
        BEJAIA,
        SETIF,
        JIJEL,
        MILA,
        CONSTANTINE,
        SKIKDA,
        OUM_EL_BOUAGHI,
        GUELMA,
        ANNABA,
        SOUK_AHRAS,
        EL_TARF
    };
}