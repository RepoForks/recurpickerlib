package com.maltaisn.recurpicker;


import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Recurrence implements Parcelable {

    private static final String TAG = Recurrence.class.getSimpleName();

    private static final int BYTE_ARRAY_LENGTH = 41;
    private static final int BYTE_ARRAY_KEY = 100;

    public static final int NONE = -1;
    public static final int DAILY = 0;
    public static final int WEEKLY = 1;
    public static final int MONTHLY = 2;
    public static final int YEARLY = 3;

    public static final int SAME_DAY_OF_MONTH = 0;
    public static final int SAME_DAY_OF_WEEK = 1;
    public static final int LAST_DAY_OF_MONTH = 2;

    public static final int END_NEVER = 0;
    public static final int END_BY_DATE = 1;
    public static final int END_BY_COUNT = 2;
    public static final int END_BY_DATE_OR_COUNT = 3;

    public static final int EVERY_DAY_OF_WEEK = 0b11111110;

    // If recurrence is default, it will have a simpler text format
    private boolean isDefault;

    private Calendar startDate;
    private int period;  // Daily, weekly, ...
    private int frequency;  // Repeat every x periods...
    private int daySetting;  // Extra setting, either day of week or day of month

    private int endType;
    private int endCount;
    private Calendar endDate;

    // Two calendars used for finding next recurrence
    private Calendar from;
    private Calendar current;


    /**
     * Create a default recurrence that never ends and with frequency of 1
     * Recurrence can then be customized with {@link Recurrence#setFrequency(int)},
     * {@link Recurrence#setEndByDate(long)} for example. This constructor acts as a builder.
     * @param start date of first event
     * @param period any of NONE, DAILY, WEEKLY, MONTHLY or YEARLY
     *               If setting weekly period, recurrence will happen on same day of week as start date
     *               If setting monthly period, recurrence will happen on same day of month as start date
     */
    public Recurrence(long start, int period) {
        startDate = Calendar.getInstance();
        startDate.setTimeInMillis(start);
        setPeriod(period, true);
        isDefault = true;
        frequency = 1;
        endType = END_NEVER;
    }

    /**
     * Create a copy of another recurrence
     * @param r Recurrence to copy
     */
    public Recurrence(@NonNull Recurrence r) {
        isDefault = r.isDefault;
        startDate = Calendar.getInstance();
        startDate.setTimeInMillis(r.startDate.getTimeInMillis());
        period = r.period;
        frequency = r.frequency;
        daySetting = r.daySetting;
        endType = r.endType;
        if (r.endType == END_BY_DATE) {
            endDate = Calendar.getInstance();
            endDate.setTimeInMillis(r.endDate.getTimeInMillis());
        }
        endCount = r.endCount;
    }

    /**
     * Create a recurrence from a byte array
     * @param array byte array obtained with {@link Recurrence#}
     * @param index recurrence object position in byte array
     */
    public Recurrence(byte[] array, int index) {
        if (array.length < BYTE_ARRAY_LENGTH) {
            throw new IllegalArgumentException("Byte array does not represent a Recurrence object");
        } else if (index < 0 || index > array.length - BYTE_ARRAY_LENGTH) {
            throw new IllegalArgumentException("Byte array index is invalid");
        }

        ByteBuffer bb = ByteBuffer.allocate(BYTE_ARRAY_LENGTH);
        bb.put(array, index, BYTE_ARRAY_LENGTH);
        bb.position(0);
        if (bb.getInt() != BYTE_ARRAY_KEY) {
            throw new IllegalArgumentException("Byte array does not represent a Recurrence object");
        }

        isDefault = bb.get() == 1;
        startDate = Calendar.getInstance();
        startDate.setTimeInMillis(bb.getLong());
        period = bb.getInt();
        frequency = bb.getInt();
        daySetting = bb.getInt();
        endType = bb.getInt();
        endCount = bb.getInt();
        long end = bb.getLong();
        if (end != 0) {
            endDate = Calendar.getInstance();
            endDate.setTimeInMillis(end);
        }
    }

    /**
     * Change the start date of the recurrence
     * If recurrence is default and repeating weekly, the day of week on which it is repeating will also change
     * @param date time in millis on which to start recurring
     * @return the recurrence
     */
    public Recurrence setStartDate(long date) {
        startDate.setTimeInMillis(date);

        if (period == WEEKLY && isDefault) {
            // If weekly recurrence is default, recurring day of week to the same as start date
            daySetting = 1 << startDate.get(Calendar.DAY_OF_WEEK);
        } else if (period == MONTHLY && daySetting == LAST_DAY_OF_MONTH && startDate.get(Calendar.DAY_OF_MONTH)
                != startDate.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            // Before changing it was repeating on last day, but now the start date isn't on the last
            // day of the month anymore so we change it to repeat on the same day of the month
            daySetting = SAME_DAY_OF_MONTH;
        }

        return this;
    }

    /**
     * Changes the period of the recurrence
     * @param period either NONE, DAILY, WEEKLY, MONTHLY or YEARLY
     *               If setting NONE, all recurrence settings get reset to default
     *               If setting WEEKLY, recurrence will happen on same day of week as start date
     *               If setting MONTHLY, recurrence will happen on same day of month as start date
     * @return the recurrence
     */
    public Recurrence setPeriod(int period) {
        return setPeriod(period, false);
    }

    private Recurrence setPeriod(int period, boolean force) {
        if (period < NONE || period > YEARLY) {
            throw new IllegalArgumentException("Period must be one of Recurrence.NONE, DAILY, WEEKLY, MONTHLY or YEARLY");
        }

        if (!force && period == this.period) return this;

        this.period = period;
        daySetting = 0;

        // Set the default behaviour for special types
        if (period == NONE) {
            // Does not repeat, set default settings
            isDefault = true;
            frequency = 1;
            endType = END_NEVER;
            endDate = null;
            endCount = 0;
        } else if (period == WEEKLY) {
            // Repeat on the same day as starting day
            daySetting = 1 << startDate.get(Calendar.DAY_OF_WEEK);
        } else if (period == MONTHLY) {
            daySetting = SAME_DAY_OF_MONTH;
        }

        return this;
    }

    /**
     * Set the frequency of the period i.e. after how many period to repeat event
     * @param freq frequency, 1 repeats every period
     * @return the recurrence
     */
    public Recurrence setFrequency(int freq) {
        if (freq < 1) {
            throw new IllegalArgumentException("Frequency must be 1 or greater");
        }

        if (period == NONE) return this;
        frequency = freq;
        isDefault = false;
        return this;
    }


    /**
     * If repeating weekly, sets the days of the week on which to repeat
     * @param days bit field of Calendar.SUNDAY to Calendar.SATURDAY constants
     *             ex: {@code int days = Calendar.SUNDAY | Calendar.FRIDAY}
     *             If setting 0 or 1 (no days), the recurrence will become Does not repeat
     *             If setting all days and frequency is 1, the recurrence becomes daily
     *             So make sure to set frequency before weekly settings
     * @return the recurrence
     */
    public Recurrence setWeeklySetting(int days) {
        if (days < 0 || days > EVERY_DAY_OF_WEEK) {
            throw new IllegalArgumentException("Weekly setting isn't a bit field of Calendar.SUNDAY - SATURDAY");
        }

        if (period == WEEKLY && days != daySetting) {
            if (days <= 1) {
                // Does not repeat on any day -> change to does not repeat
                return setPeriod(NONE);

            } else if (frequency == 1 && days == EVERY_DAY_OF_WEEK) {
                // Repeating weekly on every day -> change to daily
                period = DAILY;
                daySetting = 0;
            }
            daySetting = days;
            isDefault = false;
        }
        return this;
    }

    /**
     * If repeating monthly, sets on which day of the month to repeat
     * @param option either SAME_DAY_OF_MONTH, SAME_DAY_OF_WEEK or LAST_DAY_OF_MONTH
     * @return the recurrence
     */
    public Recurrence setMonthlySetting(int option) {
        if (option < SAME_DAY_OF_MONTH || option > LAST_DAY_OF_MONTH) {
            throw new IllegalArgumentException("Monthly setting isn't one of Recurrence.SAME_DAY_OF_MONTH, SAME_DAY_OF_WEEK or LAST_DAY_OF_MONTH");
        }

        if (period == MONTHLY && option != daySetting) {
            daySetting = option;
            isDefault = false;
        }
        return this;
    }

    /**
     * Set recurrence to never end
     * @return the recurrence
     */
    public Recurrence setEndNever() {
        if (period == NONE) return this;
        endType = END_NEVER;

        endDate = null;
        endCount = 0;
        return this;
    }

    /**
     * Set recurrence to end on a date
     * @param date end date, time in millis
     * @return the recurrence
     */
    public Recurrence setEndByDate(long date) {
        if (period == NONE) return this;
        endType = END_BY_DATE;
        if (endDate == null) endDate = Calendar.getInstance();
        endDate.setTimeInMillis(date);

        isDefault = false;
        endCount = 0;
        return this;
    }

    /**
     * Set recurrence to end after a number of events
     * @param count number of events, must be > 1
     * @return the recurrence
     */
    public Recurrence setEndByCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("End count must be 1 or greater");
        }

        if (period == NONE) return this;
        endType = END_BY_COUNT;
        endCount = count;

        isDefault = false;
        endDate = null;
        return this;
    }

    /**
     * Set recurrence to end after both a date or a number of events depending on which one comes first
     * @param date end date, time in millis
     * @param count number of events, must be > 1
     * @return the recurrence
     */
    public Recurrence setEndByDateOrCount(long date, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("End count must be 1 or greater");
        }

        if (period == NONE) return this;
        endType = END_BY_DATE_OR_COUNT;
        if (endDate == null) endDate = Calendar.getInstance();
        endDate.setTimeInMillis(date);
        endCount = count;

        isDefault = false;
        return this;
    }

    /**
     * Changes the default flag on the recurrence
     * If changing to true, the recurrence must match these criteria to change:
     * -> Does not repeat, or;
     * -> Repeats
     *    -> With a frequency of 1
     *    -> Never ends
     *    -> If repeating weekly, only repeats on the same day as start date
     *    -> If repeating monthly, repeats on the same day of each month
     * @param flag value to change to
     * @return the recurrence
     */
    public Recurrence setDefault(boolean flag) {
        if (flag != isDefault) {
            if (flag) {
                if (period == NONE || frequency == 1 && endType == END_NEVER &&
                        (period != WEEKLY || daySetting == 1 << startDate.get(Calendar.DAY_OF_WEEK)) &&
                        (period != MONTHLY || daySetting == SAME_DAY_OF_MONTH)) {
                    isDefault = true;
                }
            } else {
                isDefault = false;
            }
        }
        return this;
    }

    /**
     * Gets the starting date of the recurrence
     * @return time in millis of starting date
     */
    public long getStartDate() {
        return startDate.getTimeInMillis();
    }

    /**
     * Gets the period of the recurrence
     * @return either NONE, DAILY, WEEKLY, MONTHLY or YEARLY
     */
    public int getPeriod() {
        return period;
    }

    /**
     * Gets the frequency of the period
     * @return frequency, 1 repeats every period
     */
    public int getFrequency() {
        return frequency;
    }

    /**
     * Gets the set weekly or monthly settings on which day to repeat on
     * @return If repeated weekly, returns the bit-flag with days of week repeated
     *         In this case you can use {@code repeated = (daySetting & (1 << day)) == (1 << day)},
     *         where day is any of Calendar.SUNDAY to SATURDAY
     *         If repeated monthly, returns either SAME_DAY_OF_MONTH, SAME_DAY_OF_WEEK or LAST_DAY_OF_MONTH
     *         If not repeated weekly or monthly, returns 0
     */
    public int getDaySetting() {
        return daySetting;
    }

    /**
     * Gets the end type of the recurrence
     * @return either END_NEVER, END_BY_DATE, END_BY_COUNT or END_BY_DATE_OR_COUNT
     */
    public int getEndType() {
        return endType;
    }

    /**
     * Gets the number of events for which the recurrence happens
     * @return number of events, -1 if repeating forever or until date
     */
    public int getEndCount() {
        return endCount != 0 ? endCount : -1;
    }

    /**
     * Gets the end date of the recurrence
     * @return time in millis of end date, -1 if forever or for a number of events
     */
    public long getEndDate() {
        return endDate != null ? endDate.getTimeInMillis() : -1;
    }

    /**
     * Check if a recurrence is a default preset or customized
     * @return true if recurrence is default
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * If repeating weekly, checks if event is repeated on a day of week
     * @param day which day, use Calendar.SUNDAY-SATURDAY
     * @return true if repeated on this day
     */
    public boolean isRepeatedOnDayOfWeek(int day) {
        return period == WEEKLY && (daySetting & (1 << day)) == (1 << day);
    }

    /**
     * Get recurrences after a date
     * This method computes each recurrence based on a previous one until it meets given date and amount
     * It is useful if you want to find the 1000th recurrence and you already know the 999th, because
     * it prevents 999 useless iterations. Just make sure to use a correct recurrence as the base.
     * @param base recurrence on which next ones will be based
     * @param baseRepeats how many events were already repeated when base event happened.
     *                    This is important if recurrence ends by count, otherwise set 0
     * @param fromDate get recurrences after this date, set to -1 if get after start date
     * @param amount number of dates to get
     * @return ArrayList of dates, empty if none
     */
    public List<Long> findRecurrencesBasedOn(long base, int baseRepeats, long fromDate, int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Amount must be 1 or greater");
        }

        List<Long> list = new ArrayList<>();

        if (from == null) from = Calendar.getInstance();
        if (fromDate < 0) from.setTimeInMillis(startDate.getTimeInMillis());  // No date specified, find all recurrences
        else from.setTimeInMillis(fromDate);

        // Check if repeat has already stopped as of this date, or not repeating
        if (period == NONE || endDate != null && !isOnSameDayOrAfter(endDate, from)) return list;

        if (current == null) current = Calendar.getInstance();
        current.setTimeInMillis(base);
        int repeats = baseRepeats;
        int amnt = 0;
        boolean after = false;
        if (period == DAILY || period == YEARLY) {
            while (true) {
                if (endCount != 0 && repeats >= endCount) return list;
                current.add((period == DAILY ? Calendar.DATE : Calendar.YEAR), frequency);
                if (endDate != null && !isOnSameDayOrAfter(endDate, current)) return list;
                repeats++;
                if (!after && isOnSameDayOrAfter(current, from)) after = true;
                if (after && amnt < amount) {
                    list.add(current.getTimeInMillis());
                    amnt++;
                    if (amnt == amount) return list;
                }
            }

        } else if (period == WEEKLY) {
            int skipped = 0;
            boolean firstWeek = true;
            while (true) {
                for (int day = 1; day <= 7; day++) {
                    if (firstWeek && current.get(Calendar.DAY_OF_WEEK) >= day) continue;  // Day is before start date on first week, wait until day after
                    if (endCount != 0 && repeats >= endCount) return list;
                    skipped++;
                    if (isRepeatedOnDayOfWeek(day)) {
                        current.add(Calendar.DATE, skipped);
                        if (endDate != null && !isOnSameDayOrAfter(endDate, current)) return list;
                        skipped = 0;
                        repeats++;
                        if (!after && isOnSameDayOrAfter(current, from)) after = true;
                        if (after && amnt < amount) {
                            list.add(current.getTimeInMillis());
                            amnt++;
                            if (amnt == amount) return list;
                        }
                    }
                }
                if (frequency > 1) current.add(Calendar.DATE, 7*(frequency -1));  // Skip weeks if needed
                firstWeek = false;
            }

        } else if (period == MONTHLY) {
            int rday = current.get(Calendar.DAY_OF_WEEK);
            int rmonthDay = current.get(Calendar.DAY_OF_MONTH);
            int rweek = current.get(Calendar.DAY_OF_WEEK_IN_MONTH);
            while (true) {
                if (endCount != 0 && repeats >= endCount) return list;
                current.add(Calendar.MONTH, frequency);
                if (daySetting == LAST_DAY_OF_MONTH) {
                    // Set day of month to the last day
                    current.set(Calendar.DAY_OF_MONTH, current.getActualMaximum(Calendar.DAY_OF_MONTH));

                } else if (daySetting == SAME_DAY_OF_MONTH) {
                    // If start date is on Jan 30 and we add a month, we get Feb 28. That's not the same day of month, skip month.
                    if (rmonthDay > current.getActualMaximum(Calendar.DAY_OF_MONTH)) continue;
                    else current.set(Calendar.DAY_OF_MONTH, rmonthDay);
                } else {
                    current.set(Calendar.DAY_OF_MONTH, 15);  // Set to 15, so when we change day of week, it won't go back to last month
                    if (rweek == 5) {  // Day of last week, there may not always be 5 mondays for example so consider it last
                        current.set(Calendar.DAY_OF_WEEK, rday);
                        current.set(Calendar.DAY_OF_WEEK_IN_MONTH, -1);
                    } else {
                        current.set(Calendar.DAY_OF_WEEK, rday);
                        current.set(Calendar.DAY_OF_WEEK_IN_MONTH, rweek);
                    }
                }
                if (endDate != null && !isOnSameDayOrAfter(endDate, current)) return list;
                repeats++;
                if (!after && isOnSameDayOrAfter(current, from)) after = true;
                if (after && amnt < amount) {
                    list.add(current.getTimeInMillis());
                    amnt++;
                    if (amnt == amount) return list;
                }
            }
        }

        return list;
    }

    /**
     * Get repeat dates after a date
     * This method computes each repetition based on the start date until it meets given date and amount
     * @param from get events after this date (time in millis)
     * @param amount number of dates to get
     * @return ArrayList of dates, empty if none
     */
    public List<Long> findRecurrences(long from, int amount) {
        return findRecurrencesBasedOn(startDate.getTimeInMillis(), 0, from, amount);
    }

    /**
     * Format the recurrence object into readable text
     * @param context any context, used to get the strings
     * @param dateFormat used to format date if recurrence ends on date, can be null if you know it's not
     * @return text of formatted recurrence
     */
    public String format(@NonNull Context context, @NonNull DateFormat dateFormat) {

        // Generate first part of the text -> ex: Repeats every 2 days
        String[] recurFormats = context.getResources().getStringArray(R.array.recur_formats);
        String recurFormat;
        if (period == NONE) {
            recurFormat = recurFormats[0];  // Does not repeat

        } else if (period == DAILY) {
            recurFormat = MessageFormat.format(recurFormats[1], frequency);

        } else if (period == WEEKLY) {
            StringBuilder dayList = null;
            if (!isDefault) {
                // Make a list of days of week
                dayList = new StringBuilder();
                if (daySetting == EVERY_DAY_OF_WEEK) {
                    dayList.append(context.getString(R.string.days_of_week_list_all));
                } else {
                    String[] daysAbbr = context.getResources().getStringArray(R.array.days_of_week_abbr);
                    String listSep = context.getString(R.string.days_of_week_list_sep);
                    for (int day = 1; day <= 7; day++) {
                        if (isRepeatedOnDayOfWeek(day)) {
                            dayList.append(daysAbbr[day - 1]);
                            dayList.append(listSep);
                        }
                    }
                    dayList.delete(dayList.length() - listSep.length(), dayList.length());  // Remove extra separator
                }
            }
            recurFormat = MessageFormat.format(recurFormats[2], frequency, dayList == null ? 0 : 1, dayList);

        } else if (period == MONTHLY) {
            String when = "";
            if (!isDefault && daySetting == SAME_DAY_OF_MONTH) when = context.getString(R.string.repeat_monthly_same_day);
            else if (daySetting == SAME_DAY_OF_WEEK) when = getSameDayOfSameWeekString(context);
            else if (daySetting == LAST_DAY_OF_MONTH) when = context.getString(R.string.repeat_monthly_last_day);

            recurFormat = MessageFormat.format(recurFormats[3], frequency, when.isEmpty() ? 0 : 1, when);

        } else {
            recurFormat = MessageFormat.format(recurFormats[4], frequency);
        }

        // Generate second part of the text (how recurrence ends) -> ex: until 31-12-2017
        String[] endFormats = context.getResources().getStringArray(R.array.end_formats);
        String endFormat;
        if (endType == END_NEVER) {
            endFormat = endFormats[endType];
        } else if (endType == END_BY_DATE) {
            endFormat = MessageFormat.format(endFormats[1], dateFormat.format(endDate.getTime()));
        } else if (endType == END_BY_COUNT) {
            endFormat = MessageFormat.format(endFormats[2], endCount);
        } else {
            endFormat = MessageFormat.format(endFormats[3], dateFormat.format(endDate.getTime()), endCount);
        }

        String result;
        if (endFormat.isEmpty()) {
            result = recurFormat;
        } else {
            result = MessageFormat.format(context.getString(R.string.merge_format), recurFormat, endFormat);
        }

        return result;
    }

    public byte[] toByteArray() {
        ByteBuffer bb = ByteBuffer.allocate(BYTE_ARRAY_LENGTH);
        bb.putInt(BYTE_ARRAY_KEY);
        bb.put(isDefault ? (byte) 1 : 0);
        bb.putLong(startDate.getTimeInMillis());
        bb.putInt(period);
        bb.putInt(frequency);
        bb.putInt(daySetting);
        bb.putInt(endType);
        bb.putInt(endCount);
        bb.putLong(endDate == null ? 0 : endDate.getTimeInMillis());

        return bb.array();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Recurrence)) return false;

        Recurrence r = (Recurrence) obj;
        return  r.isDefault == isDefault &&
                r.period == period &&
                r.frequency == frequency &&
                r.daySetting == daySetting &&
                r.endType == endType &&
                (endCount == 0 || r.endCount == endCount) &&
                isOnSameDay(r.startDate, startDate) &&
                (endDate == null || isOnSameDay(r.endDate, endDate));
    }

    @Override
    public int hashCode() {
        return Objects.hash(isDefault, period, frequency, daySetting, endType, getDaysInCalendar(startDate),
                (endDate != null ? getDaysInCalendar(endDate) : 0), endCount);
    }

    @Override
    public String toString() {
        // Example:
        //    com.maltaisn.recurpicker.Recurrence@faa78f66[default=false, startDate=20-12-2017, period=WEEKLY, frequency=3,
        //    daySetting=SUNDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, endType=DATE, endDate=23-03-2018]
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);

        sb.append(super.toString());  // Recurrence@Hashcode
        sb.append("[default=");
        sb.append(isDefault);
        sb.append(", startDate=");
        sb.append(sdf.format(startDate.getTime()));
        sb.append(", period=");
        sb.append(new String[]{"NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"}[period+1]);
        sb.append(", frequency=");
        sb.append(frequency);
        if (period == WEEKLY) {
            sb.append(", daySetting=");
            if (daySetting == EVERY_DAY_OF_WEEK) {
                sb.append("EVERYDAY");
            } else {
                String[] days = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY",
                        "THURSDAY", "FRIDAY", "SATURDAY"};
                for (int d = 0; d < 7; d++) {
                    if (isRepeatedOnDayOfWeek(d+1)) {
                        sb.append(days[d]);
                        sb.append(", ");
                    }
                }
                sb.delete(sb.length()-2, sb.length());  // Remove extra ", "
            }
        } else if (period == MONTHLY) {
            sb.append(", daySetting=");
            sb.append(new String[]{"SAME_DAY_OF_MONTH", "SAME_DAY_OF_WEEK", "LAST_DAY_OF_MONTH"}[daySetting]);
        }
        sb.append(", endType=");
        sb.append(new String[]{"NEVER", "DATE", "COUNT", "DATE_OR_COUNT"}[endType]);
        if (endDate != null) {
            sb.append(", endDate=");
            sb.append(sdf.format(endDate.getTime()));
        }
        if (endCount != 0) {
            sb.append(", endCount=");
            sb.append(endCount);
        }
        sb.append("]");
        return sb.toString();
    }

    // HELPER METHODS

    // Get the text for a date to display on a monthly recurrence repeated on the same day of week of same week
    // ex: "on third Sunday" or "on last Friday"
    String getSameDayOfSameWeekString(Context context) {
        String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
        String[] ordinalNbs = context.getResources().getStringArray(R.array.ordinal_numbers);
        String weekOrd = ordinalNbs[startDate.get(Calendar.DAY_OF_WEEK_IN_MONTH)-1].toLowerCase();
        String dayOfWeek = daysOfWeek[startDate.get(Calendar.DAY_OF_WEEK)-1];
        return MessageFormat.format(context.getString(R.string.repeat_monthly_same_week), weekOrd, dayOfWeek);
    }

    /**
     * Checks if two calendars are on the same day
     * @param c1 calendar 1
     * @param c2 calendar 2
     * @return true if they are on same day
     */
    public static boolean isOnSameDay(@NonNull Calendar c1, @NonNull Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) >= c2.get(Calendar.DAY_OF_YEAR);
    }

    private static int getDaysInCalendar(@NonNull Calendar cal) {
        return cal.get(Calendar.YEAR)*365 + cal.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Checks if c1's date if on the same day or after c2's date
     * @param c1 calendar to compare
     * @param c2 calendar to compare to
     * @return true if c1's date is after c2's date
     */
    public static boolean isOnSameDayOrAfter(@NonNull Calendar c1, @NonNull Calendar c2) {
        return c1.get(Calendar.YEAR) > c2.get(Calendar.YEAR) ||
                c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                        c1.get(Calendar.DAY_OF_YEAR) >= c2.get(Calendar.DAY_OF_YEAR);
    }


    // PARCELABLE CREATION
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(isDefault ? 1 : 0);
        out.writeLong(startDate == null ? 0 : startDate.getTimeInMillis());
        out.writeInt(period);
        out.writeInt(frequency);
        out.writeInt(daySetting);

        out.writeInt(endType);
        out.writeInt(endCount);
        out.writeLong(endDate == null ? 0 : endDate.getTimeInMillis());
    }

    public static final Parcelable.Creator<Recurrence> CREATOR = new Parcelable.Creator<Recurrence>() {
        public Recurrence createFromParcel(Parcel in) {
            return new Recurrence(in);
        }

        public Recurrence[] newArray(int size) {
            return new Recurrence[size];
        }
    };

    private Recurrence(Parcel in) {
        isDefault = in.readInt() == 1;
        long start = in.readLong();
        if (start == 0) {
            startDate = null;
        } else {
            startDate = Calendar.getInstance();
            startDate.setTimeInMillis(start);
        }
        period = in.readInt();
        frequency = in.readInt();
        daySetting = in.readInt();

        endType = in.readInt();
        endCount = in.readInt();
        long end = in.readLong();
        if (end == 0) {
            endDate = null;
        } else {
            endDate = Calendar.getInstance();
            endDate.setTimeInMillis(end);
        }
    }
}
